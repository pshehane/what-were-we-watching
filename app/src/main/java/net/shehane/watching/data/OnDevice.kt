package net.shehane.watching.data

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Generating on the phone, through AICore. Nothing here leaves the device, and
 * none of it costs anything.
 *
 * Two APIs, because AICore provisions its features separately and a phone can
 * easily have one and not the other:
 *
 *  - the prompt API takes an instruction, so it can be asked for one point on the
 *    story and one per character, the same thing the cloud model is asked for;
 *  - the summarisation API takes no instruction at all. One, two or three bullets
 *    of whatever it decides, and that is the whole interface.
 *
 * The prompt one is tried first for that reason, and the caller is told which
 * answered so the screen never implies the formula was honoured when it was not.
 *
 * All of this is the public ML Kit surface. Nothing here needs a rooted phone,
 * a hidden API or a system permission, which matters because most people running
 * this will have none of those.
 */
object OnDevice {

    private const val TAG = "Watching.OnDevice"

    enum class Kind {
        /** Took the instruction. The recap is shaped the way it was asked for. */
        PROMPT,

        /** Bullets only. The phone chose what went in them. */
        SUMMARY,
    }

    data class Answer(val text: String, val kind: Kind, val modelName: String?)

    /** Which APIs this phone actually exposes. Both can be false, and often are. */
    data class Capability(val prompt: Boolean, val summary: Boolean) {
        val any: Boolean get() = prompt || summary
    }

    /** Runs the callbacks straight through; the work is already off the main thread. */
    private val direct = Executor { it.run() }

    suspend fun capability(context: Context): Capability {
        val cap = Capability(prompt = promptReady(), summary = summaryReady(context))
        Log.i(TAG, "prompt=${cap.prompt} summary=${cap.summary}")
        return cap
    }

    /**
     * Asks for the recap in the shape the caller wants, and settles for bullets
     * when that is all the phone offers.
     *
     * [body] is the synopses; [instruction] is the whole prompt with the synopses
     * already inside it. The summarisation path can only use the first.
     */
    suspend fun generate(
        context: Context,
        instruction: String,
        body: String,
        bullets: Int,
        capability: Capability,
    ): Answer {
        if (capability.prompt) {
            runCatching { viaPrompt(instruction) }
                .onSuccess { return it }
                .onFailure { Log.w(TAG, "prompt API failed, trying summarisation", it) }
        }
        if (capability.summary) return viaSummary(context, body, bullets)
        throw IllegalStateException("No on-device generation on this phone")
    }

    // ------------------------------------------------------------ prompt API

    private suspend fun promptReady(): Boolean = runCatching {
        val model = Generation.getClient()
        try {
            val status = model.checkStatus()
            Log.i(TAG, "prompt status = ${nameOf(status)}")
            status == FeatureStatus.AVAILABLE || status == FeatureStatus.DOWNLOADABLE ||
                status == FeatureStatus.DOWNLOADING
        } finally {
            model.close()
        }
    }.onFailure { Log.w(TAG, "prompt availability failed", it) }.getOrDefault(false)

    private suspend fun viaPrompt(instruction: String): Answer {
        val model = Generation.getClient()
        try {
            if (model.checkStatus() == FeatureStatus.DOWNLOADABLE) {
                // A Flow that completes when the download does. Progress is not
                // reported to the screen: the one line it already shows says more
                // to a reader than a percentage would.
                model.download().collect {}
            }
            model.warmup()

            Log.i(TAG, "prompting with ${instruction.length} chars")
            val text = model.generateContent(instruction)
                .candidates.firstOrNull()?.text?.trim()
                ?: throw IllegalStateException("Prompt API returned no candidates")

            val name = runCatching { model.getBaseModelName() }.getOrNull()
            return Answer(text, Kind.PROMPT, name?.takeIf { it.isNotBlank() })
        } finally {
            model.close()
        }
    }

    // ----------------------------------------------------- summarisation API

    private suspend fun summaryReady(context: Context): Boolean = runCatching {
        val summarizer = Summarization.getClient(options(context, bullets = 3))
        try {
            val status = summarizer.checkFeatureStatus().await()
            Log.i(TAG, "summarisation status = ${nameOf(status)}")
            status == FeatureStatus.AVAILABLE || status == FeatureStatus.DOWNLOADABLE ||
                status == FeatureStatus.DOWNLOADING
        } finally {
            summarizer.close()
        }
    }.onFailure { Log.w(TAG, "summarisation availability failed", it) }.getOrDefault(false)

    private suspend fun viaSummary(context: Context, text: String, bullets: Int): Answer {
        val summarizer = Summarization.getClient(options(context, bullets))
        try {
            if (summarizer.checkFeatureStatus().await() == FeatureStatus.DOWNLOADABLE) {
                summarizer.downloadFeature(silentDownload).await()
            }
            summarizer.prepareInferenceEngine().await()

            Log.i(TAG, "summarising ${text.length} chars into $bullets bullets")
            val result = summarizer.runInference(SummarizationRequest.builder(text).build()).await()
            val name = runCatching { summarizer.getBaseModelName().await() }.getOrNull()
            return Answer(result.summary.trim(), Kind.SUMMARY, name?.takeIf { it.isNotBlank() })
        } finally {
            summarizer.close()
        }
    }

    private fun options(context: Context, bullets: Int) =
        SummarizerOptions.builder(context)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(
                when (bullets) {
                    1 -> SummarizerOptions.OutputType.ONE_BULLET
                    2 -> SummarizerOptions.OutputType.TWO_BULLETS
                    else -> SummarizerOptions.OutputType.THREE_BULLETS
                }
            )
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            // A long show is more text than the model takes. Truncating beats
            // refusing, and the caller already trims to the most useful part.
            .setLongInputAutoTruncationEnabled(true)
            .build()

    private val silentDownload = object : DownloadCallback {
        override fun onDownloadStarted(bytesToDownload: Long) = Unit
        override fun onDownloadProgress(totalBytesDownloaded: Long) = Unit
        override fun onDownloadCompleted() = Unit
        override fun onDownloadFailed(e: GenAiException) = Unit
    }

    // ------------------------------------------------------------------ plumbing

    private fun nameOf(status: Int): String = when (status) {
        FeatureStatus.AVAILABLE -> "AVAILABLE"
        FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
        FeatureStatus.DOWNLOADING -> "DOWNLOADING"
        FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
        else -> "unknown($status)"
    }

    /** The summarisation API hands back Guava futures. The prompt one does not. */
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
                direct,
            )
            cont.invokeOnCancellation { cancel(false) }
        }
}
