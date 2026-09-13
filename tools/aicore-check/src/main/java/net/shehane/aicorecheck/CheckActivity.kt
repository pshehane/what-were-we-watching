package net.shehane.aicorecheck

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import java.time.ZonedDateTime
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Checks whether AICore answers on this phone, using the smallest possible
 * requests, and writes a report that can be pasted into a bug.
 *
 * Every call is a public ML Kit GenAI API. Nothing needs root or a system
 * permission. Each step prints OK or FAIL, the time it took, and for a failure
 * the exception class, the ML Kit error code and the message, including causes.
 */
class CheckActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var running: Job? = null

    private lateinit var output: TextView
    private val report = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        column.addView(button("Run all checks") { runAll() })
        column.addView(button("Download prompt model") { downloadPromptModel() })
        column.addView(button("Copy report") { copyReport() })
        column.addView(output)

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column)
        })

        writeHeader()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    // ----------------------------------------------------------------- report

    private fun writeHeader() {
        line("AICore check, " + ZonedDateTime.now())
        line("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        line("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), build ${Build.ID}")
        line("Security patch: ${Build.VERSION.SECURITY_PATCH}")
        line("Fingerprint: ${Build.FINGERPRINT}")
        line("AICore: " + versionOf("com.google.android.aicore"))
        line("Private Compute Services: " + versionOf("com.google.android.as.oss"))
        line("Calling package: $packageName")
        line("ML Kit: genai-prompt 1.0.0-beta4, genai-summarization 1.0.0-beta1")
        line("")
    }

    private fun versionOf(pkg: String): String = runCatching {
        val info = packageManager.getPackageInfo(pkg, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrElse { "not installed" }

    private fun line(text: String) {
        Log.i(TAG, text)
        report.appendLine(text)
        output.append(text + "\n")
    }

    private fun copyReport() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("AICore check", report.toString()))
        Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ checks

    private fun runAll() {
        if (running?.isActive == true) return
        running = scope.launch {
            line("--- Prompt API ---")
            val model = Generation.getClient()
            try {
                val status = step("checkStatus()", ::statusName) { model.checkStatus() }
                when (status) {
                    FeatureStatus.AVAILABLE -> {
                        step("getBaseModelName()") { model.getBaseModelName() }
                        step("getTokenLimit()") { model.getTokenLimit() }
                        // First without warmup, because the main app found that
                        // warmup itself can fail on a model reported available.
                        step("generateContent(\"$HELLO\")", ::firstCandidate) {
                            model.generateContent(HELLO)
                        }
                        step("warmup()") { model.warmup(); "returned" }
                        step("generateContent(\"$HELLO\") after warmup", ::firstCandidate) {
                            model.generateContent(HELLO)
                        }
                    }

                    FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING ->
                        line("      The model is not on the phone. Tap \"Download prompt model\", then run again.")
                }
            } finally {
                model.close()
            }

            line("")
            line("--- Summarization API ---")
            val summarizer = Summarization.getClient(
                SummarizerOptions.builder(this@CheckActivity)
                    .setInputType(SummarizerOptions.InputType.ARTICLE)
                    .setOutputType(SummarizerOptions.OutputType.ONE_BULLET)
                    .setLanguage(SummarizerOptions.Language.ENGLISH)
                    .build()
            )
            try {
                val status = step("checkFeatureStatus()", ::statusName) {
                    summarizer.checkFeatureStatus().await()
                }
                if (status == FeatureStatus.AVAILABLE) {
                    step("getBaseModelName()") { summarizer.getBaseModelName().await() }
                    step("prepareInferenceEngine()") { summarizer.prepareInferenceEngine().await(); "returned" }
                    step("runInference(${PARAGRAPH.length} chars)", { it.summary.trim() }) {
                        summarizer.runInference(SummarizationRequest.builder(PARAGRAPH).build()).await()
                    }
                }
            } finally {
                summarizer.close()
            }

            line("")
            line("--- finished ---")
            line("")
        }
    }

    private fun downloadPromptModel() {
        if (running?.isActive == true) return
        running = scope.launch {
            line("--- Download prompt model ---")
            val model = Generation.getClient()
            try {
                var last = ""
                step("download()", { "flow completed" }, timeoutMs = DOWNLOAD_TIMEOUT_MS) {
                    model.download().collect { status ->
                        val name = status::class.simpleName ?: status.toString()
                        if (name != last) {
                            last = name
                            withContext(Dispatchers.Main) { line("      $status") }
                        }
                    }
                }
                step("checkStatus()", ::statusName) { model.checkStatus() }
            } finally {
                model.close()
            }
            line("")
        }
    }

    /**
     * Runs one call off the main thread and prints the outcome. Returns the value,
     * or null when the call failed or timed out.
     */
    private suspend fun <T> step(
        label: String,
        describe: (T) -> String = { it.toString() },
        timeoutMs: Long = CALL_TIMEOUT_MS,
        block: suspend () -> T,
    ): T? {
        val started = System.nanoTime()
        return try {
            val value = withContext(Dispatchers.Default) { withTimeout(timeoutMs) { block() } }
            line("OK    $label -> ${describe(value)} [${msSince(started)} ms]")
            value
        } catch (e: TimeoutCancellationException) {
            line("FAIL  $label -> no answer after ${timeoutMs / 1000} s")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, label, e)
            line("FAIL  $label [${msSince(started)} ms]")
            describeError(e).forEach { line("      $it") }
            null
        }
    }

    private fun describeError(error: Throwable): List<String> {
        val lines = mutableListOf<String>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 6) {
            val prefix = if (depth == 0) "" else "caused by "
            val code = (current as? GenAiException)?.let { " errorCode=${it.errorCode} (${errorName(it.errorCode)})" }
            lines += prefix + current.javaClass.name + (code ?: "")
            current.message?.let { lines += "  message: $it" }
            current = current.cause?.takeIf { it !== current }
            depth++
        }
        return lines
    }

    private fun firstCandidate(response: com.google.mlkit.genai.prompt.GenerateContentResponse): String {
        val candidate = response.candidates.firstOrNull() ?: return "no candidates"
        return "\"" + candidate.text.trim() + "\""
    }

    private fun msSince(started: Long) = (System.nanoTime() - started) / 1_000_000

    private fun statusName(status: Int): String = when (status) {
        FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
        FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
        FeatureStatus.DOWNLOADING -> "DOWNLOADING"
        FeatureStatus.AVAILABLE -> "AVAILABLE"
        else -> "unknown"
    } + " ($status)"

    /** Names from GenAiException.ErrorCode in genai-common 1.0.0-beta4. */
    private fun errorName(code: Int): String = when (code) {
        0 -> "UNKNOWN"
        4 -> "REQUEST_PROCESSING_ERROR"
        7 -> "CANCELLED"
        8 -> "NOT_AVAILABLE"
        9 -> "BUSY"
        11 -> "RESPONSE_PROCESSING_ERROR"
        12 -> "REQUEST_TOO_LARGE"
        15 -> "RESPONSE_GENERATION_ERROR"
        16 -> "NOT_SUPPORTED"
        27 -> "PER_APP_BATTERY_USE_QUOTA_EXCEEDED"
        30 -> "BACKGROUND_USE_BLOCKED"
        501 -> "NOT_ENOUGH_DISK_SPACE"
        604 -> "NEEDS_SYSTEM_UPDATE"
        // Not in ML Kit's ErrorCode, but AICore passes them through and names
        // them in the exception message.
        601 -> "BINDING_FAILURE (AICore)"
        606 -> "FEATURE_NOT_FOUND (AICore)"
        -100 -> "REQUEST_TOO_SMALL"
        -101 -> "AICORE_INCOMPATIBLE"
        else -> "not in ErrorCode"
    }

    /** The summarization API returns Guava futures. */
    private suspend fun <T> ListenableFuture<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            addListener(
                {
                    try {
                        cont.resume(get())
                    } catch (e: Throwable) {
                        cont.resumeWithException(e.cause ?: e)
                    }
                },
                Executor { it.run() },
            )
            cont.invokeOnCancellation { cancel(false) }
        }

    private companion object {
        const val TAG = "AICoreCheck"
        const val HELLO = "Say hello."
        const val CALL_TIMEOUT_MS = 60_000L
        const val DOWNLOAD_TIMEOUT_MS = 30 * 60_000L

        /** About 600 characters of plain prose, written for this check. */
        const val PARAGRAPH =
            "The town library reopened on Saturday after eight months of repairs. " +
                "The roof, which had leaked into the reading room for years, was replaced, " +
                "and the old heating system was swapped for heat pumps. The children's " +
                "section moved to the ground floor so that families with pushchairs no " +
                "longer need the lift. Opening hours are longer on weekdays, and the " +
                "library now stays open until eight on Thursdays. The council paid for " +
                "most of the work, with the rest raised by a volunteer group that held " +
                "book sales every month. About four hundred people came on the first day, " +
                "and the librarians said the most borrowed books were cookery titles."
    }
}
