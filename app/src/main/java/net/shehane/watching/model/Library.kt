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
    val shows: List<Show> = emptyList(),
    val deleted: List<Tombstone> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
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

@Serializable
data class Position(
    val season: Int = 1,
    val episode: Int = 1,
) {
    override fun toString(): String = "S$season · E$episode"
    val short: String get() = "S$season E$episode"
}

@Serializable
data class Show(
    val id: String,

    // --- from TMDB, written once when the show is added ---
    val tmdbId: Int? = null,
    val title: String,
    val year: Int? = null,
    val posterPath: String? = null,
    val wikipediaUrl: String? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
    val overview: String? = null,

    // --- yours ---
    val serviceId: String? = null,
    val profileId: String? = null,
    /** Everyone except you. Every show in this app is one you watch. */
    @SerialName("watchedWith") val watchedWith: List<String> = emptyList(),
    val position: Position = Position(),
    val state: String = STATE_ACTIVE,
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
    }

    val isActive: Boolean get() = state == STATE_ACTIVE
    val isSnoozed: Boolean get() = state == STATE_SNOOZED
    val isAbandoned: Boolean get() = state == STATE_ABANDONED
    val isFinished: Boolean get() = state == STATE_FINISHED
    /** Both sit below everything else, but they mean different things. */
    val isDone: Boolean get() = isAbandoned || isFinished

    /** Everyone on the show, you included. The couch match runs against this. */
    fun watcherIds(meId: String): List<String> = listOf(meId) + watchedWith.filter { it != meId }
}

@Serializable
data class Tombstone(
    val id: String,
    /** "show", "person" or "service". */
    val kind: String,
    val deletedAt: String,
)
