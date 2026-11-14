package net.shehane.watching.data

/**
 * Who worked on what, and what two people have in common.
 *
 * The sorting and filtering rules live here rather than in [Tmdb] so they can be
 * tested without a network, and so the screen can stay about drawing columns.
 *
 * TMDB lists everyone, which for a film means a hundred crew entries covering
 * every assistant and runner. Only the jobs below are kept, because the question
 * this screen answers is "who made this", not "who was on the payroll".
 */
object Cast {

    /**
     * Crew jobs worth a row. Plain "Producer" is deliberately absent: a film can
     * carry a dozen, and they crowd out the people the question was about.
     */
    val CREW_JOBS = listOf(
        "Director",
        "Creator",
        "Writer",
        "Screenplay",
        "Story",
        "Original Music Composer",
        "Music",
        "Composer",
        "Director of Photography",
        "Executive Producer",
    )

    /**
     * Turning up as yourself is not work.
     *
     * A director's credits are otherwise topped by every talk show they have
     * ever sat on, because a nightly show that has run for ten years carries a
     * far higher popularity score than any film. These still appear, at the
     * bottom, because an appearance in a documentary can be the thing you were
     * looking for.
     */
    private val SELF_ROLES = listOf("self", "himself", "herself", "themselves")

    fun isAppearance(character: String?): Boolean {
        val c = character?.trim()?.lowercase() ?: return false
        // "Self - Guest" and "Self (archive footage)" are the same thing.
        return SELF_ROLES.any { c == it || c.startsWith("$it ") || c.startsWith("$it-") }
    }

    /**
     * Whether a cast credit is a part they played.
     *
     * A blank character counts as an appearance, not as work. TMDB leaves it
     * blank mostly for chat shows and panel shows, and a credit that can name
     * neither a character nor a job is no evidence of either.
     */
    private fun isWork(character: String?): Boolean {
        val c = character?.trim().orEmpty()
        return c.isNotEmpty() && !isAppearance(c)
    }

    /** One title somebody worked on. */
    data class Credit(
        val item: Tmdb.SearchItem,
        /** The character they played, or the job they did. */
        val role: String,
        val year: Int?,
        val popularity: Double,
        /** They turned up as themselves rather than working on it. */
        val appearance: Boolean = false,
        /** How many episodes, for a series. Null for a film, and often for a series. */
        val episodes: Int? = null,
    ) {
        /** Same title in both catalogs is two different things, so the key is both. */
        val key: Pair<Int, String> get() = item.id to item.medium

        /**
         * How much of a series somebody was in, from none of it to all of it.
         *
         * A guest in three episodes is not what the show is about, and should not
         * outrank the films they led. Ten episodes counts as fully involved.
         */
        val involvement: Double
            get() = if (!item.isMovie) {
                ((episodes ?: 1).coerceAtLeast(1) / EPISODES_FOR_FULL_WEIGHT).coerceAtMost(1.0)
            } else {
                1.0
            }

        /** Popularity, discounted by how small a part of the series this was. */
        val weight: Double get() = popularity * involvement
    }

    /** Beyond this many episodes, somebody counts as properly in the show. */
    private const val EPISODES_FOR_FULL_WEIGHT = 10.0

    /** One person on a title. */
    data class Member(
        val id: Int,
        val name: String,
        val profilePath: String?,
        val role: String,
        /** Billed cast come before crew, and higher billing comes first. */
        val order: Int,
        val isCast: Boolean,
    )

    /** A title two people both worked on, with what each of them did. */
    data class Shared(
        val item: Tmdb.SearchItem,
        val roleA: String,
        val roleB: String,
        val year: Int?,
        val popularity: Double,
        /** Both of them only turned up as themselves. */
        val appearance: Boolean = false,
        val episodes: Int? = null,
    ) {
        val weight: Double
            get() = popularity * if (!item.isMovie) {
                ((episodes ?: 1).coerceAtLeast(1) / EPISODES_FOR_FULL_WEIGHT).coerceAtMost(1.0)
            } else {
                1.0
            }
    }

    /**
     * One person's credits, acting and crew folded into one list.
     *
     * Somebody who directed and scored the same film appears once, with both
     * jobs, because two rows for one title would read as two pieces of work.
     */
    fun creditsFrom(cast: List<Tmdb.CreditRow>, crew: List<Tmdb.CreditRow>): List<Credit> {
        val byKey = LinkedHashMap<Pair<Int, String>, Credit>()

        for (row in cast) {
            if (row.title.isBlank()) continue
            add(
                byKey,
                row,
                row.character?.takeIf { it.isNotBlank() } ?: "Appears",
                work = isWork(row.character),
            )
        }
        for (row in crew) {
            if (row.title.isBlank()) continue
            val job = row.job?.takeIf { it in CREW_JOBS } ?: continue
            add(byKey, row, job, work = true)
        }

        // Work first, then appearances. Within each, the two catalogs are ranked
        // separately and then interleaved, because their popularity numbers are
        // not on the same scale: a long-running series scores in the hundreds
        // where a well known film scores in the tens. Comparing them directly
        // buries every film a person made under guest spots they did once.
        return arrange(
            byKey.values.toList(),
            appearance = { it.appearance },
            isMovie = { it.item.isMovie },
            weight = { it.weight },
            year = { it.year },
        )
    }

    /**
     * The order every list on this screen uses.
     *
     * Work first and appearances after, then within each: best film, best
     * series, second best film, second best series, and so on. The two catalogs
     * are ranked separately because their popularity numbers are not on the same
     * scale. A long-running series scores in the hundreds where a well known
     * film scores in the tens, so comparing them directly buries every film a
     * person made under television they passed through once.
     */
    private fun <T> arrange(
        items: List<T>,
        appearance: (T) -> Boolean,
        isMovie: (T) -> Boolean,
        weight: (T) -> Double,
        year: (T) -> Int?,
    ): List<T> {
        val order = compareByDescending<T> { weight(it) }.thenByDescending { year(it) ?: 0 }

        fun interleave(list: List<T>): List<T> {
            val films = list.filter(isMovie).sortedWith(order)
            val series = list.filterNot(isMovie).sortedWith(order)
            return buildList {
                for (i in 0 until maxOf(films.size, series.size)) {
                    films.getOrNull(i)?.let(::add)
                    series.getOrNull(i)?.let(::add)
                }
            }
        }

        val (work, extras) = items.partition { !appearance(it) }
        return interleave(work) + interleave(extras)
    }

    private fun add(
        into: LinkedHashMap<Pair<Int, String>, Credit>,
        row: Tmdb.CreditRow,
        role: String,
        work: Boolean,
    ) {
        val key = row.id to row.medium
        val existing = into[key]
        into[key] = if (existing == null) {
            Credit(row.asItem(), role, row.year, row.popularity, !work, row.episodes)
        } else {
            // Directing something you also turned up in is work. One credit of
            // either kind on a title is enough to make the whole title work.
            val both = !existing.appearance || work
            if (role in existing.role.split(", ")) existing.copy(appearance = !both)
            else existing.copy(role = existing.role + ", " + role, appearance = !both)
        }
    }

    /** The billed cast, then the key crew, each in the order they are credited. */
    fun membersFrom(cast: List<Tmdb.PersonRow>, crew: List<Tmdb.PersonRow>): List<Member> {
        val members = mutableListOf<Member>()

        for (row in cast) {
            if (row.name.isBlank()) continue
            members += Member(
                id = row.id,
                name = row.name,
                profilePath = row.profilePath,
                role = row.character?.takeIf { it.isNotBlank() } ?: "Appears",
                order = row.order,
                isCast = true,
            )
        }
        for (row in crew) {
            if (row.name.isBlank()) continue
            val job = row.job?.takeIf { it in CREW_JOBS } ?: continue
            members += Member(
                id = row.id,
                name = row.name,
                profilePath = row.profilePath,
                role = job,
                order = CREW_JOBS.indexOf(job),
                isCast = false,
            )
        }

        // One person who both acted and directed keeps the acting row, and the
        // directing is added to it, so nobody appears in the column twice.
        val byId = LinkedHashMap<Int, Member>()
        for (m in members.sortedWith(compareByDescending<Member> { it.isCast }.thenBy { it.order })) {
            val existing = byId[m.id]
            byId[m.id] =
                if (existing == null) m
                else existing.copy(role = existing.role + ", " + m.role)
        }
        return byId.values.toList()
    }

    /**
     * What two people both worked on.
     *
     * Two people who have each sat on the same chat show sofa have not worked
     * together, so those land at the bottom the same way they do anywhere else.
     * A title counts as work when either of them did more than turn up.
     */
    fun overlap(a: List<Credit>, b: List<Credit>): List<Shared> {
        val mine = a.associateBy { it.key }
        val shared = b.mapNotNull { theirs ->
            val ours = mine[theirs.key] ?: return@mapNotNull null
            Shared(
                item = ours.item,
                roleA = ours.role,
                roleB = theirs.role,
                year = ours.year ?: theirs.year,
                popularity = minOf(ours.popularity, theirs.popularity),
                appearance = ours.appearance && theirs.appearance,
                episodes = maxOf(ours.episodes ?: 0, theirs.episodes ?: 0).takeIf { it > 0 },
            )
        }
        return arrange(
            shared,
            appearance = { it.appearance },
            isMovie = { it.item.isMovie },
            weight = { it.weight },
            year = { it.year },
        )
    }
}
