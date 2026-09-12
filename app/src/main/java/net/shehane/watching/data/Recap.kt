package net.shehane.watching.data

import net.shehane.watching.model.Position
import net.shehane.watching.model.Show

/**
 * Two questions this app could not answer before: "have we already seen this
 * one?" and "what was going on when we stopped?"
 *
 * Both are answered strictly from what is behind your position. Nothing at or
 * after it is ever included, which is the whole point: a recap that spoils the
 * episode you are about to watch is worse than no recap.
 *
 * Pure over TMDB's season responses, like [Couch] and [Suggest], so the cut-off
 * can be tested without a network.
 */
object Recap {

    /** Beyond this, a recap is a reading assignment. Older episodes are counted, not listed. */
    const val EPISODES_SHOWN = 8

    data class NextUp(
        val season: Int,
        val episode: Int,
        val title: String,
        val summary: String?,
        val airDate: String?,
        /** True when TMDB lists it but it has not gone out yet. */
        val unaired: Boolean,
    )

    data class Entry(
        val season: Int,
        val episode: Int,
        val title: String,
        val summary: String?,
    )

    data class CatchUp(
        /** Most recent first: what you saw last is what you have half-forgotten. */
        val recent: List<Entry>,
        /** Season number to its own synopsis, newest first, for everything further back. */
        val earlier: List<Pair<Int, String>>,
        /** Episodes behind you that are neither listed nor covered by a season synopsis. */
        val olderCount: Int,
        /** Which seasons those uncovered episodes belong to, so the note can name them. */
        val olderSeasons: List<Int>,
    ) {
        val isEmpty: Boolean get() = recent.isEmpty() && earlier.isEmpty()
    }

    /**
     * Which episode comes next, resolved against what the season actually holds.
     *
     * The label on the screen says "S3 E11" by adding one, which is wrong at the
     * end of a season. Once the season is loaded we know better, and rolling into
     * the next season is the honest answer.
     */
    fun nextUp(show: Show, seasons: Map<Int, Tmdb.Season>, today: String = Clock.now()): NextUp? {
        val wanted = resolveNext(show.position, seasons, show.seasonCount) ?: return null
        val episode = seasons[wanted.season]
            ?.episodes
            ?.firstOrNull { it.episode == wanted.episode }
            ?: return null

        return NextUp(
            season = wanted.season,
            episode = wanted.episode,
            title = episode.name.ifBlank { "Episode ${wanted.episode}" },
            summary = episode.summary,
            airDate = episode.airDate,
            unaired = episode.airDate.isNullOrBlank() || episode.airDate > today.take(10),
        )
    }

    /** Null when the show is over and there is nothing after where you are. */
    fun resolveNext(
        position: Position,
        seasons: Map<Int, Tmdb.Season>,
        seasonCount: Int?,
    ): Position? {
        val here = seasons[position.season]
        val next = position.episode + 1

        // Either the season is not loaded, in which case adding one is the best
        // guess available, or the episode genuinely exists.
        if (here == null || here.episodes.any { it.episode == next }) {
            return Position(position.season, next)
        }

        val after = position.season + 1
        if (seasonCount != null && after > seasonCount) return null
        return Position(after, 1)
    }

    /**
     * Everything behind the position and nothing at or after it.
     *
     * Recent episodes are listed in full because they are what you actually need.
     * Further back collapses to one synopsis per season, because nobody reads
     * ninety paragraphs to remember where they were.
     */
    fun catchUp(show: Show, seasons: Map<Int, Tmdb.Season>): CatchUp {
        val watched = mutableListOf<Entry>()

        for (season in seasons.values.sortedBy { it.number }) {
            if (season.number > show.position.season) continue
            for (episode in season.episodes.sortedBy { it.episode }) {
                // The cut: strictly before the position, in the position's season.
                val past = season.number < show.position.season ||
                    episode.episode <= show.position.episode
                if (!past) continue
                watched += Entry(
                    season = season.number,
                    episode = episode.episode,
                    title = episode.name.ifBlank { "Episode ${episode.episode}" },
                    summary = episode.summary,
                )
            }
        }

        val newestFirst = watched.reversed()
        val recent = newestFirst.take(EPISODES_SHOWN)
        val rest = newestFirst.drop(EPISODES_SHOWN)

        // Season synopses only for seasons no listed episode came from, so the same
        // ground is never covered twice.
        val listedSeasons = recent.map { it.season }.toSet()
        val earlier = seasons.values
            .filter { it.number < show.position.season && it.number !in listedSeasons }
            .sortedByDescending { it.number }
            .mapNotNull { season -> season.summary?.let { season.number to it } }

        // What is left over: episodes too far back to list, from a season that is
        // not summarised either because part of it is already listed above.
        val coveredByEarlier = earlier.map { it.first }.toSet()
        val uncovered = rest.filter { it.season !in coveredByEarlier }

        return CatchUp(
            recent = recent,
            earlier = earlier,
            olderCount = uncovered.size,
            olderSeasons = uncovered.map { it.season }.distinct().sorted(),
        )
    }

    /** Which seasons a full catch-up needs. Everything up to and including where you are. */
    fun seasonsNeeded(show: Show): List<Int> = (1..show.position.season).toList()
}
