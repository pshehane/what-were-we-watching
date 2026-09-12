package net.shehane.watching.data

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Summarising on the phone, through AICore.
 *
 * This is ML Kit's Summarization API, which runs on the system's own model. It
 * costs nothing, needs no network and sends nothing anywhere. What it will not do
 * is take an instruction: the only controls are how many bullets to emit and what
 * text to hand it, so the shape of the answer is the model's decision, not ours.
 *
 * Plenty of phones have no AICore at all. Everything here answers "no" quietly in
 * that case rather than throwing, because "no" is an ordinary outcome.
 */
object OnDevice {

    private const val TAG = "Watching.OnDevice"

    data class Answer(val text: String, val modelName: String?)

    /** Runs the callbacks straight through; the work is already off the main thread. */
    private val direct = Executor { it.run() }

    /**
     * Whether this phone can do it at all.
     *
     * DOWNLOADABLE counts as yes. The model is fetched on first use, which is slow
     * once and then not again, and offering it is better than pretending the phone
     * cannot do something it can.
     */
    suspend fun isAvailable(context: Context): Boolean = runCatching {
        val summarizer = Summarization.getClient(options(context, bullets = 3))
        try {
            val status = summarizer.checkFeatureStatus().await()
            // Logged because this is the one answer that decides the whole feature
            // and it varies by phone, region and system update. Without it, "no
            // on-device model" is unexplainable from outside.
            Log.i(TAG, "feature status = " + nameOf(status))
            when (status) {
                FeatureStatus.AVAILABLE, FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> true
                else -> false
            }
        } finally {
            summarizer.close()
        }
    }.onFailure { Log.w(TAG, "availability check failed", it) }.getOrDefault(false)

    private fun nameOf(status: Int): String = when (status) {
        FeatureStatus.AVAILABLE -> "AVAILABLE"
        FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
        FeatureStatus.DOWNLOADING -> "DOWNLOADING"
        FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
        else -> "unknown($status)"
    }

    /**
     * [bullets] must be 1, 2 or 3. The API has no other settings, so the count is
     * the only part of the caller's intent that survives.
     */
    suspend fun summarise(context: Context, text: String, bullets: Int): Answer {
        val summarizer = Summarization.getClient(options(context, bullets))
        try {
            if (summarizer.checkFeatureStatus().await() == FeatureStatus.DOWNLOADABLE) {
                summarizer.downloadFeature(silentDownload).await()
            }
            summarizer.prepareInferenceEngine().await()

            Log.i(TAG, "summarising " + text.length + " chars into " + bullets + " bullets")
            val result = summarizer.runInference(SummarizationRequest.builder(text).build()).await()
            val name = runCatching { summarizer.getBaseModelName().await() }.getOrNull()
            return Answer(result.summary.trim(), name?.takeIf { it.isNotBlank() })
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

    /**
     * The download reports progress nobody is watching. The screen already says it
     * is working, and a first run that takes a while is better explained by that
     * one line than by a percentage that means nothing to the reader.
     */
    private val silentDownload = object : DownloadCallback {
        override fun onDownloadStarted(bytesToDownload: Long) = Unit
        override fun onDownloadProgress(totalBytesDownloaded: Long) = Unit
        override fun onDownloadCompleted() = Unit
        override fun onDownloadFailed(e: GenAiException) = Unit
    }

    /** ML Kit hands back Guava futures; this is the only bridge the app needs. */
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
