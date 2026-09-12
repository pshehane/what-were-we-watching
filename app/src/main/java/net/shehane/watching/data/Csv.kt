package net.shehane.watching.data

import net.shehane.watching.model.Library
import net.shehane.watching.model.Show

/**
 * The human-readable copy. Rewritten from scratch every time the JSON changes.
 *
 * This file is never read back, so it is shaped for reading rather than for
 * round-tripping: names joined with semicolons instead of nested commas, plain
 * YYYY-MM-DD dates, and snoozed and abandoned shows included with their status in
 * a column, because being able to find them is the reason they are kept at all.
 */
object Csv {

    private val HEADER = listOf(
        "Title", "Year", "Service", "Profile", "Watched with",
        "Season", "Episode", "Status", "Comes back",
        "Last watched", "Added", "TMDB id", "Wikipedia",
    )

    fun render(library: Library): String {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",") { escape(it) }).append("\r\n")

        val ordered = library.shows.sortedWith(
            compareBy({ statusRank(it) }, { it.title.lowercase() })
        )
        for (show in ordered) {
            sb.append(row(library, show).joinToString(",") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun statusRank(show: Show): Int = when (show.state) {
        Show.STATE_ACTIVE -> 0
        Show.STATE_SNOOZED -> 1
        Show.STATE_WISHLIST -> 2
        else -> 3
    }

    private fun row(library: Library, show: Show): List<String> {
        val service = library.serviceOrNull(show.serviceId)
        val profile = library.profileOrNull(show.serviceId, show.profileId)
        val withNames = show.watchedWith
            .mapNotNull { library.personOrNull(it)?.name }
            .joinToString("; ")

        return listOf(
            show.title,
            show.year?.toString() ?: "",
            service?.name ?: "",
            profile?.name ?: "",
            withNames,
            // A wishlist entry was never started, so writing S1 E1 would be a
            // small lie in a file meant for reading.
            if (show.isWishlist) "" else show.position.season.toString(),
            if (show.isWishlist) "" else show.position.episode.toString(),
            show.state,
            if (show.isSnoozed) Clock.toCsvDate(show.snoozeUntil) else "",
            Clock.toCsvDate(show.lastWatchedAt),
            Clock.toCsvDate(show.addedAt),
            show.tmdbId?.toString() ?: "",
            show.wikipediaUrl ?: "",
        )
    }

    /**
     * RFC 4180: quote when the value contains a comma, quote, or line break, and
     * double any embedded quotes. Show titles genuinely contain commas.
     */
    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
