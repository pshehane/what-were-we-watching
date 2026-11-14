package net.shehane.watching.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The whole library, exactly as it is written to library.json and to Drive.
 *
 * Every record carries its own [updatedAt] so two phones can be merged record by
 * record instead of one file overwriting the other. See [net.shehane.watching.data.Merge].
 */
@Serializable
data class Library(
    val schemaVersion: Int = SCHEMA_VERSION,
    val updatedAt: String = "",
    val people: List<Person> = emptyList(),
    val services: List<Service> = emptyList(),
    /**
     * Where you normally are, as an ISO 3166-1 code. Streaming rights are sold by
     * country, so this decides which answer the add flow trusts, and it is what
     * the wishlist compares against when you are travelling.
     */
    val homeCountry: String = "US",
    /**
     * Which summariser "Catch me up" should prefer. One of [SUMMARY_CLOUD],
     * [SUMMARY_DEVICE] or [SUMMARY_NONE].
     *
     * A preference, not a guarantee. Cloud needs a key built in and a network;
     * on-device needs a phone with AICore. Whatever is unavailable falls through
     * to the next thing, and the raw synopses are always there underneath.
     */
    val summaryMode: String = SUMMARY_DEVICE,
    val shows: List<Show> = emptyList(),
    /**
     * Recaps already written, at most one per show. A second request for the same
     * stopping point uses this instead of asking a model again, and because it
     * syncs through Drive, so does a request from another phone.
     */
    val recaps: List<SavedRecap> = emptyList(),
    val deleted: List<Tombstone> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1

        const val SUMMARY_CLOUD = "cloud"
        const val SUMMARY_DEVICE = "device"
        /** No summarising at all: the episode synopses, as they come from TMDB. */
        const val SUMMARY_NONE = "none"
    }

    fun personOrNull(id: String): Person? = people.firstOrNull { it.id == id }
    fun serviceOrNull(id: String?): Service? = id?.let { s -> services.firstOrNull { it.id == s } }
    fun showOrNull(id: String?): Show? = id?.let { s -> shows.firstOrNull { it.id == s } }

    fun profileOrNull(serviceId: String?, profileId: String?): Profile? =
        serviceOrNull(serviceId)?.profiles?.firstOrNull { it.id == profileId }
}

@Serializable
data class Person(
    val id: String,
    val name: String,
    /** Hex ARGB or RGB, e.g. "#E8A05C". Kept as text so the JSON stays readable. */
    val color: String,
    val order: Int = 0,
    val updatedAt: String = "",
)

@Serializable
data class Service(
    val id: String,
    val name: String,
    /** Hex colour for the service half of the badge. */
    val tint: String,
    val tmdbProviderId: Int? = null,
    val defaultProfileId: String? = null,
    val profiles: List<Profile> = emptyList(),
    val updatedAt: String = "",
) {
    val defaultProfile: Profile?
        get() = profiles.firstOrNull { it.id == defaultProfileId } ?: profiles.firstOrNull()
}

@Serializable
data class Profile(
    val id: String,
    val name: String,
)

/**
 * The last episode watched.
 *
 * Episode 0 means none of this season yet, which is the only way to say "we have
 * finished everything before season 7" without knowing how many episodes season 6
 * had. Finishing a season lands here, and so does picking a season directly.
 */
@Serializable
data class Position(
    val season: Int = 1,
    val episode: Int = 1,
) {
    val notStarted: Boolean get() = episode <= 0

    override fun toString(): String =
        if (notStarted) "S$season · start" else "S$season · E$episode"

    val short: String get() = if (notStarted) "S$season start" else "S$season E$episode"
}

@Serializable
data class Show(
    val id: String,

    // --- from TMDB, written once when the show is added ---
    val tmdbId: Int? = null,
    /**
     * [MEDIUM_TV] or [MEDIUM_MOVIE]. Defaults to a series, so every record
     * written before movies existed stays exactly what it was.
     *
     * A movie is the same record with one episode and no position. Keeping both
     * in one list is the point: the couch question is "what do we put on",
     * and the answer should not depend on which catalog the answer came from.
     */
    val medium: String = MEDIUM_TV,
    val title: String,
    val year: Int? = null,
    val posterPath: String? = null,
    val wikipediaUrl: String? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
    /**
     * How long one episode runs, in minutes.
     *
     * An average, because that is what TMDB publishes per show and what the couch
     * needs. A drama that is 42 one week and 47 the next is still "about 45", and
     * the time budget is fuzzy by ten minutes anyway.
     *
     * For a movie this is the whole thing, which is why a movie can show it where
     * a series shows its position.
     */
    val runtimeMinutes: Int? = null,
    val overview: String? = null,

    // --- yours ---
    val serviceId: String? = null,
    val profileId: String? = null,
    /** Everyone except you. Every show in this app is one you watch. */
    @SerialName("watchedWith") val watchedWith: List<String> = emptyList(),
    val position: Position = Position(),
    val state: String = STATE_ACTIVE,
    /**
     * What we thought of it. Null means nobody has said, which is not the same as
     * a no: most shows never get a verdict and must not be counted as disliked.
     *
     * Set by the two swipes on a suggestion. A show can be finished without one,
     * and can carry one without ever having been on the couch.
     */
    val liked: Boolean? = null,
    /** ISO instant. Only meaningful while [state] is [STATE_SNOOZED]. */
    val snoozeUntil: String? = null,
    val lastWatchedAt: String? = null,
    val addedAt: String = "",
    val updatedAt: String = "",
) {
    companion object {
        const val STATE_ACTIVE = "active"
        const val STATE_SNOOZED = "snoozed"
        const val STATE_ABANDONED = "abandoned"
        /** Watched to the end. Kept apart from abandoned so the CSV stays truthful. */
        const val STATE_FINISHED = "finished"
        /** Want to watch, not started. No position yet, and never on the couch list. */
        const val STATE_WISHLIST = "wishlist"

        /** A series. The default, and what every record written before movies is. */
        const val MEDIUM_TV = "tv"
        /** One sitting, no position. Same word TMDB uses, so it routes API calls too. */
        const val MEDIUM_MOVIE = "movie"
    }

    val isActive: Boolean get() = state == STATE_ACTIVE
    val isSnoozed: Boolean get() = state == STATE_SNOOZED
    val isAbandoned: Boolean get() = state == STATE_ABANDONED
    val isFinished: Boolean get() = state == STATE_FINISHED
    val isWishlist: Boolean get() = state == STATE_WISHLIST
    /** Both sit below everything else, but they mean different things. */
    val isDone: Boolean get() = isAbandoned || isFinished

    val isMovie: Boolean get() = medium == MEDIUM_MOVIE
    /** Anything that is not explicitly a movie, including records with no medium. */
    val isSeries: Boolean get() = !isMovie

    /**
     * What the cards print where a series prints its position.
     *
     * A movie has no position to show, so it shows how long it is. That doubles
     * as the way you tell the two apart at a glance, which is why no card needs a
     * badge saying "film".
     */
    val marker: String get() = if (isMovie) runtimeLabel else position.toString()

    /** The same thing, in the space a smaller card has. */
    val markerShort: String get() = if (isMovie) runtimeLabel else position.short

    private val runtimeLabel: String
        get() = runtimeMinutes?.takeIf { it > 0 }?.let(::spellRuntime) ?: "Movie"

    val isLoved: Boolean get() = liked == true
    /** Seen and it did not land, or we are never going to. Either way, no more of these. */
    val isNotLoved: Boolean get() = liked == false

    /** Everyone on the show, you included. The couch match runs against this. */
    fun watcherIds(meId: String): List<String> = listOf(meId) + watchedWith.filter { it != meId }
}

/**
 * A "Catch me up" recap that a model has already written.
 *
 * Only the newest one per show is kept. Once the show moves on, an older recap
 * stops before the wrong episode and nobody would ask for it again.
 */
@Serializable
data class SavedRecap(
    val showId: String,
    /** The episode the recap stops before, as the screen labels it: "S3 E5". */
    val upTo: String,
    val text: String,
    /** Who wrote it: [Library.SUMMARY_CLOUD] or [Library.SUMMARY_DEVICE]. */
    val source: String,
    val modelName: String? = null,
    /** False when the phone could only bullet and chose the points itself. */
    val followedTheBrief: Boolean = true,
    val updatedAt: String = "",
)

@Serializable
data class Tombstone(
    val id: String,
    /** "show", "person" or "service". */
    val kind: String,
    val deletedAt: String,
)

/**
 * "1h 52m", "47m", "2h". Lives here because a movie's length is part of how a
 * record describes itself; [net.shehane.watching.data.TimeLeft] uses the same
 * function so a runtime and a budget are never spelled two different ways.
 */
fun spellRuntime(minutes: Int): String {
    if (minutes <= 0) return ""
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}
