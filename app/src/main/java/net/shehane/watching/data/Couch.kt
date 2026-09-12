package net.shehane.watching.data

import net.shehane.watching.model.Library
import net.shehane.watching.model.Show

/**
 * The rule the whole app exists for: given who is sitting down, what can we put on?
 *
 * Kept as a pure function over the library so it can be tested without a device,
 * and so the couch screen can re-run it on every tap of a face.
 */
object Couch {

    data class Match(
        val show: Show,
        /** Seated people who are on this show. */
        val matched: List<String>,
        /** Seated people who are not. Empty for a full match. */
        val missing: List<String>,
    )

    data class Result(
        val primary: List<Match>,
        val secondary: List<Match>,
        val snoozed: List<Show>,
        /** Abandoned and finished together: both sit below everything else. */
        val abandoned: List<Show>,
    ) {
        val isEmpty: Boolean
            get() = primary.isEmpty() && secondary.isEmpty() && snoozed.isEmpty() && abandoned.isEmpty()
    }

    /**
     * [seated] is the set of person ids on the couch.
     *
     * A show is a full match only when *every* seated person is on it. A show all
     * four watch is deliberately not a full match when only two are seated: the
     * other two would fall behind. Carrying on without them is an edit to the
     * show's people, not a softening of this rule.
     */
    fun build(library: Library, seated: Set<String>): Result {
        val primary = mutableListOf<Match>()
        val secondary = mutableListOf<Match>()
        val snoozed = mutableListOf<Show>()
        val abandoned = mutableListOf<Show>()

        for (show in library.shows) {
            when {
                show.isSnoozed -> { snoozed += show; continue }
                show.isDone -> { abandoned += show; continue }
            }
            if (seated.isEmpty()) continue

            val watchers = show.watcherIds(LibraryStore.ME_ID).toSet()
            val matched = seated.filter { it in watchers }
            if (matched.isEmpty()) continue

            val missing = seated.filter { it !in watchers }
            val match = Match(show, matched, missing)
            if (missing.isEmpty()) primary += match else secondary += match
        }

        return Result(
            primary = primary.sortedWith(recency),
            // Most of the room first, then the most recently watched.
            secondary = secondary.sortedWith(
                compareByDescending<Match> { it.matched.size }.then(recency)
            ),
            snoozed = snoozed.sortedBy { Clock.parseOrNull(it.snoozeUntil) },
            abandoned = abandoned.sortedBy { it.title.lowercase() },
        )
    }

    /** Most recently watched first; never-watched shows fall back to when they were added. */
    private val recency = Comparator<Match> { a, b ->
        val ta = Clock.parseOrNull(a.show.lastWatchedAt ?: a.show.addedAt)
        val tb = Clock.parseOrNull(b.show.lastWatchedAt ?: b.show.addedAt)
        when {
            ta == null && tb == null -> a.show.title.compareTo(b.show.title)
            ta == null -> 1
            tb == null -> -1
            else -> tb.compareTo(ta)
        }
    }

    /** "Ana & Dee", "Ana, Ben & Cal". */
    fun nameList(library: Library, ids: List<String>): String {
        val names = ids.mapNotNull { library.personOrNull(it)?.name }
        return when (names.size) {
            0 -> ""
            1 -> names[0]
            2 -> "${names[0]} & ${names[1]}"
            else -> names.dropLast(1).joinToString(", ") + " & " + names.last()
        }
    }
}
