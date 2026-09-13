package net.shehane.watching.data

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

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

    /** Long enough for a slow phone, short enough that a hang is not forever. */
    private const val INFERENCE_TIMEOUT_MS = 45_000L

    /** Used only when the model will not say how big its window is. */
    private const val DEFAULT_TOKEN_LIMIT = 1024

    /** English prose runs near four characters a token. Only used as a first guess. */
    private const val CHARS_PER_TOKEN = 4

    enum class Kind {
        /** Took the instruction. The recap is shaped the way it was asked for. */
        PROMPT,

        /** Bullets only. The phone chose what went in them. */
        SUMMARY,
    }

    data class Answer(val text: String, val kind: Kind, val modelName: String?)

    /** Which APIs this phone actually exposes. Both can be false, and often are. */
    data class Capability(
        /** Supported at all, whether or not the model has been fetched. */
        val prompt: Boolean,
        val summary: Boolean,
        /** On disk and able to answer. Only these are ever actually called. */
        val promptReady: Boolean = false,
        val summaryReady: Boolean = false,
    ) {
        val any: Boolean get() = prompt || summary

        /**
         * Able to answer now, without a wait nobody asked for. A model still being
         * fetched is hundreds of megabytes away from being useful, which is a very
         * different thing from being unavailable.
         */
        val readyNow: Boolean get() = promptReady || summaryReady

        val needsDownload: Boolean get() = any && !readyNow
    }

    /** Runs the callbacks straight through; the work is already off the main thread. */
    private val direct = Executor { it.run() }

    suspend fun capability(context: Context): Capability {
        val prompt = statusOf("prompt") {
            val model = Generation.getClient()
            try {
                model.checkStatus()
            } finally {
                model.close()
            }
        }
        val summary = statusOf("summarisation") {
            val summarizer = Summarization.getClient(options(context, bullets = 3))
            try {
                summarizer.checkFeatureStatus().await()
            } finally {
                summarizer.close()
            }
        }

        val cap = Capability(
            prompt = prompt.usable,
            summary = summary.usable,
            promptReady = prompt.status == FeatureStatus.AVAILABLE,
            summaryReady = summary.status == FeatureStatus.AVAILABLE,
        )
        Log.i(TAG, "prompt=" + cap.prompt + "/" + cap.promptReady +
            " summary=" + cap.summary + "/" + cap.summaryReady +
            " readyNow=" + cap.readyNow)
        return cap
    }

    private data class Status(val status: Int) {
        val usable: Boolean
            get() = status == FeatureStatus.AVAILABLE ||
                status == FeatureStatus.DOWNLOADABLE ||
                status == FeatureStatus.DOWNLOADING
    }

    private suspend fun statusOf(what: String, read: suspend () -> Int): Status =
        runCatching { Status(read().also { Log.i(TAG, what + " status = " + nameOf(it)) }) }
            .onFailure { Log.w(TAG, what + " availability failed", it) }
            .getOrDefault(Status(FeatureStatus.UNAVAILABLE))

    @Volatile
    private var downloading = false

    /**
     * Starts fetching the model and returns immediately.
     *
     * Deliberately not awaited. The model is hundreds of megabytes, and making
     * somebody watch "reading it back" for five minutes to get a recap they could
     * have had at once from the synopses is a bad trade. It finishes in the
     * background, and the next recap uses it.
     */
    fun startDownload(scope: CoroutineScope) {
        if (downloading) return
        downloading = true
        scope.launch {
            runCatching {
                val model = Generation.getClient()
                try {
                    if (model.checkStatus() == FeatureStatus.DOWNLOADABLE) {
                        Log.i(TAG, "fetching the prompt model in the background")
                        model.download().collect { Log.i(TAG, "download: " + it) }
                        Log.i(TAG, "prompt model is on disk")
                    }
                } finally {
                    model.close()
                }
            }.onFailure { Log.w(TAG, "background download failed", it) }
            downloading = false
        }
    }

    /**
     * Asks for the recap in the shape the caller wants, and settles for bullets
     * when that is all the phone offers.
     *
     * [instruction] builds a prompt that fits a character budget, because a phone
     * model has a smaller context than a cloud one. [body] is the synopses alone,
     * which is all the summarisation path can use.
     */
    suspend fun generate(
        context: Context,
        instruction: (Int) -> String,
        body: String,
        bullets: Int,
        capability: Capability,
    ): Answer {
        // Only what is on disk. Calling a feature whose model is still downloading
        // fails with the same "preparation failed" as a broken one, which makes a
        // missing model look like an error.
        if (capability.promptReady) {
            runCatching { withTimeout(INFERENCE_TIMEOUT_MS) { viaPrompt(instruction) } }
                .onSuccess { return it }
                .onFailure { Log.w(TAG, "prompt API failed", it) }
        }
        if (capability.summaryReady) {
            return withTimeout(INFERENCE_TIMEOUT_MS) { viaSummary(context, body, bullets) }
        }
        throw IllegalStateException("No on-device model ready on this phone")
    }

    // ------------------------------------------------------------ prompt API

    private suspend fun viaPrompt(instruction: (Int) -> String): Answer {
        val model = Generation.getClient()
        try {
            // No download and no warmup. The download is a background job, and
            // warmup is optional: on one phone here it threw "preparation failed"
            // on a model the same API had just reported as available, while going
            // straight to the request works.
            val prompt = fitToWindow(model, instruction)

            Log.i(TAG, "prompting with ${prompt.length} chars")
            val text = model.generateContent(prompt)
                .candidates.firstOrNull()?.text?.trim()
                ?: throw IllegalStateException("Prompt API returned no candidates")

            val name = runCatching { model.getBaseModelName() }.getOrNull()
            return Answer(text, Kind.PROMPT, name?.takeIf { it.isNotBlank() })
        } finally {
            model.close()
        }
    }

    /**
     * Shrinks the prompt until the model will take it.
     *
     * The window is the model's, not ours, and half of it has to be left for the
     * answer. Counting is exact where the API offers it and a rough four
     * characters per token where it does not; either way the loop stops when the
     * count fits rather than trusting the estimate.
     */
    private suspend fun fitToWindow(
        model: com.google.mlkit.genai.prompt.GenerativeModel,
        instruction: (Int) -> String,
    ): String {
        val limit = runCatching { model.getTokenLimit() }.getOrNull()
        Log.i(TAG, "token limit = " + (limit ?: "unknown"))

        // Half the window for the answer; a recap of nine bullets is not short.
        val budgetTokens = ((limit ?: DEFAULT_TOKEN_LIMIT) / 2).coerceAtLeast(256)

        var chars = budgetTokens * CHARS_PER_TOKEN
        repeat(5) {
            val prompt = instruction(chars)
            val tokens = runCatching {
                model.countTokens(generateContentRequest(TextPart(prompt)) {}).totalTokens
            }.getOrNull()

            if (tokens == null || tokens <= budgetTokens) {
                Log.i(TAG, "prompt fits: " + prompt.length + " chars, " + (tokens ?: -1) + " tokens")
                return prompt
            }
            Log.i(TAG, "prompt too long: " + tokens + " > " + budgetTokens + ", shrinking")
            chars = (chars * 2) / 3
        }
        return instruction(chars)
    }

    // ----------------------------------------------------- summarisation API

    private suspend fun viaSummary(context: Context, text: String, bullets: Int): Answer {
        val summarizer = Summarization.getClient(options(context, bullets))
        try {
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
