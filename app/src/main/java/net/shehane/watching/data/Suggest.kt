package net.shehane.watching.data

import net.shehane.watching.model.Library
import net.shehane.watching.model.Show

/**
 * What to put on when nothing is already part-way through.
 *
 * The sections are deliberately ordered by how much guessing each one does, and
 * only the last one guesses at all:
 *
 *  - [ready] is the wishlist crossed with the services you pay for. No guess.
 *  - [stalled] is your own started shows that have not moved. No guess either.
 *  - [seed] names the show whose neighbours are worth asking TMDB about. That
 *    request happens elsewhere, because it needs the network; this file only
 *    decides which show to ask about, and why.
 *
 * Pure over the library, like [Couch], so it can be tested without a device.
 */
object Suggest {

    /** Below this many finished shows, the guessing is not worth reading. */
    const val ENOUGH_FINISHED = 5

    /** An active show untouched for this long is a decision waiting to be made. */
    const val STALE_DAYS = 21L

    data class Ready(
        val show: Show,
        /** The couch and the show's people are the same set, so nobody is left out. */
        val exact: Boolean,
    )

    data class Stalled(
        val show: Show,
        val days: Long,
    )

    data class Result(
        val ready: List<Ready>,
        val stalled: List<Stalled>,
        /** The finished show the guesses hang off, and the name in the heading. */
        val seed: Show?,
        val finishedCount: Int,
    ) {
        val enoughToGuess: Boolean get() = finishedCount >= ENOUGH_FINISHED
        val isEmpty: Boolean get() = ready.isEmpty() && stalled.isEmpty() && seed == null
    }

    fun build(library: Library, seated: Set<String>): Result {
        val ready = mutableListOf<Ready>()
        val stalled = mutableListOf<Stalled>()
        var finished = 0

        for (show in library.shows) {
            if (show.isFinished) finished++

            when {
                show.isWishlist -> readyOrNot(library, show, seated)?.let { ready += it }

                // Snoozed shows are excluded on purpose: you already said not now,
                // and they come back by themselves.
                show.isActive -> staleOrNot(show)?.let { stalled += it }
            }
        }

        return Result(
            // Everyone in, then the longest wait, so a full couch wins over an
            // old entry nobody can watch together tonight.
            ready = ready.sortedWith(
                compareByDescending<Ready> { it.exact }
                    .thenBy { Clock.parseOrNull(it.show.addedAt) }
            ),
            stalled = stalled.sortedByDescending { it.days },
            seed = seedFor(library),
            finishedCount = finished,
        )
    }

    /**
     * A wishlist entry is ready tonight when you already pay for the service it is
     * on. Without a service there is nothing to open, so it is not an answer to
     * "what should we watch" however much you want to see it.
     */
    private fun readyOrNot(library: Library, show: Show, seated: Set<String>): Ready? {
        if (library.serviceOrNull(show.serviceId) == null) return null
        if (seated.isEmpty()) return Ready(show, exact = false)

        val watchers = show.watcherIds(LibraryStore.ME_ID).toSet()
        // Nobody seated who is not on it, and nobody on it who is not seated:
        // the same rule the couch screen uses for a full match.
        return Ready(show, exact = watchers == seated)
    }

    private fun staleOrNot(show: Show): Stalled? {
        val last = show.lastWatchedAt ?: show.addedAt
        val days = Clock.daysSince(last) ?: return null
        if (days < STALE_DAYS) return null
        return Stalled(show, days)
    }

    /**
     * The show the guesses are built from. A loved one is worth far more than a
     * merely finished one, so it wins even when it is older; within each group the
     * most recent is the best guide to what we want now.
     */
    private fun seedFor(library: Library): Show? {
        val finished = library.shows.filter { it.isFinished && it.tmdbId != null }
        if (finished.isEmpty()) return null
        return finished.maxWithOrNull(
            compareBy<Show> { if (it.isLoved) 1 else 0 }
                .thenBy { Clock.parseOrNull(it.lastWatchedAt ?: it.updatedAt) }
        )
    }

    /**
     * Drops anything already in the library, so a recommendation never offers back
     * a show you are part-way through or have already voted on.
     */
    fun unseen(library: Library, items: List<Tmdb.SearchItem>): List<Tmdb.SearchItem> {
        val known = library.shows.mapNotNull { it.tmdbId }.toSet()
        return items.filterNot { it.id in known }
    }
}
