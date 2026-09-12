package net.shehane.watching.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import net.shehane.watching.model.Library
import net.shehane.watching.model.Show
import net.shehane.watching.ui.PosterCache

/**
 * Handing a show to somebody else, through the system share sheet.
 *
 * The three openers are the three reasons this ever comes up on our couch, and
 * they are fixed on purpose: typing a message is the thing that stops you sending
 * one, and picking from three is one tap.
 */
object Share {

    enum class Opener(val text: String) {
        WAIT("Interested, should we wait for you?"),
        RECOMMEND("Loved this, you should check out."),
        CONFIRM("Is this the show you suggested?"),
    }

    /**
     * The message body. The opener leads, because that is the part addressed to a
     * person; the show is the attachment to it.
     */
    fun message(library: Library, show: Show, opener: Opener): String = buildString {
        appendLine(opener.text)
        appendLine()

        append(show.title)
        show.year?.let { append(" ($it)") }

        // The service is the useful half of "where": the profile is our own filing
        // and means nothing to whoever is reading this.
        library.serviceOrNull(show.serviceId)?.let {
            appendLine()
            append("On ").append(it.name)
        }

        show.wikipediaUrl?.let {
            appendLine()
            appendLine()
            append(it)
        }
    }

    /**
     * A share sheet carrying the message, and the poster when we already have it.
     *
     * Artwork is only attached if it is already on disk. Downloading it here would
     * put a wait in front of the sheet, and a share that opens late is a share
     * that does not get sent.
     */
    fun intentFor(context: Context, library: Library, show: Show, opener: Opener): Intent {
        val text = message(library, show, opener)
        val poster = PosterCache.fileFor(PosterCache.dirIn(context), show.posterPath)

        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, show.title)
            if (poster == null) {
                type = "text/plain"
            } else {
                type = "image/jpeg"
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".posters",
                    poster,
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                // The grant travels with the intent, and dies with it. Without this
                // the other app gets a URI it is not allowed to open.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        return Intent.createChooser(send, null)
    }
}
