package net.shehane.watching.data

import java.time.Duration
import java.time.LocalTime
import net.shehane.watching.model.Show

/**
 * How much of an evening is left, and what fits in it.
 *
 * The whole thing is deliberately approximate. Nobody knows exactly when the
 * adverts stop or how long it takes everyone to sit down, so an episode that runs
 * ten minutes past bedtime still counts. Being strict here would make the answer
 * wrong more often than being loose does.
 *
 * Pure, like [Couch] and [Suggest], so the arithmetic can be tested without a
 * clock or a device.
 */
object TimeLeft {

    /** An episode may overrun the budget by this much and still count. */
    const val SLACK_MINUTES = 10

    /** Used when TMDB has no runtime and nobody has set one. Roughly a drama hour. */
    const val ASSUMED_MINUTES = 45

    /** How the budget was set, which changes how it is worded. */
    enum class Kind { NONE, DURATION, BEDTIME }

    data class Budget(
        val kind: Kind = Kind.NONE,
        /** Minutes available, for [Kind.DURATION]. */
        val minutes: Int = 0,
        /** Minutes past midnight, for [Kind.BEDTIME]. */
        val endsAt: Int = 0,
    ) {
        val isSet: Boolean get() = kind != Kind.NONE
    }

    data class Fits(
        val episodes: Int,
        /** Minutes those episodes take. */
        val runtime: Int,
        /** True when the runtime was a guess rather than something TMDB knew. */
        val assumed: Boolean,
        /** How far past the budget the last episode runs. Zero when it fits cleanly. */
        val overrunMinutes: Int,
    ) {
        val fitsNothing: Boolean get() = episodes == 0
    }

    /** Minutes from [now] until the budget runs out. Negative when it already has. */
    fun remaining(budget: Budget, now: LocalTime): Int = when (budget.kind) {
        Kind.NONE -> 0
        Kind.DURATION -> budget.minutes
        Kind.BEDTIME -> {
            val nowMinutes = now.hour * 60 + now.minute
            val diff = budget.endsAt - nowMinutes
            // A bedtime earlier than now means tomorrow: 11pm set at 1am is two
            // hours away, not twenty-two hours ago.
            if (diff <= -MINUTES_A_DAY / 2) diff + MINUTES_A_DAY
            else if (diff < 0) diff
            else diff
        }
    }

    /**
     * The usual length of an episode, from a list of individual runtimes.
     *
     * This uses the median. A double-length finale or a missing value would pull
     * an average away from what a normal episode takes.
     */
    fun typicalRuntime(values: List<Int?>): Int? {
        val known = values.filterNotNull().filter { it > 0 }.sorted()
        if (known.isEmpty()) return null
        return known[(known.size - 1) / 2]
    }

    /** One episode of [show], in minutes, falling back to a plain assumption. */
    fun episodeLength(show: Show): Int = show.runtimeMinutes ?: ASSUMED_MINUTES

    /**
     * How many episodes of [show] fit in [availableMinutes].
     *
     * The last one is allowed to run over by [SLACK_MINUTES], because stopping ten
     * minutes before the end of an episode is not a thing anybody does.
     */
    fun fits(show: Show, availableMinutes: Int): Fits {
        val each = episodeLength(show)
        val assumed = show.runtimeMinutes == null

        if (availableMinutes <= 0) return Fits(0, 0, assumed, 0)

        // Whole episodes that fit outright, then one more if it only just spills.
        var count = availableMinutes / each
        if ((count + 1) * each - availableMinutes in 1..SLACK_MINUTES) count++

        val runtime = count * each
        return Fits(
            episodes = count,
            runtime = runtime,
            assumed = assumed,
            overrunMinutes = (runtime - availableMinutes).coerceAtLeast(0),
        )
    }

    /** "1h 45m", "45m", "2h". Empty for nothing left. */
    fun spell(minutes: Int): String {
        if (minutes <= 0) return ""
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** "10:30 pm", in the phone's own clock style being too much trouble for one label. */
    fun spellClock(minutesPastMidnight: Int): String {
        val total = ((minutesPastMidnight % MINUTES_A_DAY) + MINUTES_A_DAY) % MINUTES_A_DAY
        val h24 = total / 60
        val m = total % 60
        val suffix = if (h24 < 12) "am" else "pm"
        val h12 = when {
            h24 % 12 == 0 -> 12
            else -> h24 % 12
        }
        return "%d:%02d %s".format(h12, m, suffix)
    }

    /** When the last episode would end, given a start of [now]. */
    fun endsAt(now: LocalTime, runtimeMinutes: Int): String =
        spellClock(now.hour * 60 + now.minute + runtimeMinutes)

    /** The presets the couch offers, in minutes. */
    val PRESETS = listOf(30, 60, 90, 120, 180)

    private const val MINUTES_A_DAY = 24 * 60

    /** Rounded to the nearest half hour, which is how people say bedtimes. */
    fun defaultBedtime(now: LocalTime): Int {
        val nowMinutes = now.hour * 60 + now.minute
        // Two hours from now, rounded up to the next half hour.
        val target = nowMinutes + 120
        return ((target + 29) / 30) * 30
    }

    fun duration(minutes: Int) = Budget(Kind.DURATION, minutes = minutes)
    fun bedtime(minutesPastMidnight: Int) = Budget(Kind.BEDTIME, endsAt = minutesPastMidnight)
    fun none() = Budget()
}
