package net.shehane.watching.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.shehane.watching.model.Library
import net.shehane.watching.model.Person
import net.shehane.watching.model.Position
import net.shehane.watching.model.Profile
import net.shehane.watching.model.Service
import net.shehane.watching.model.Show
import net.shehane.watching.model.Tombstone
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * The library, in memory, backed by library.json in app storage.
 *
 * There is deliberately no database. A hundred shows is a 40 KB file that loads
 * at launch and filters in memory, which is also why the couch screen can sort
 * itself on every tap of a face without feeling slow. The same file is what goes
 * to Drive, so there is one format from the phone to a spreadsheet.
 */
class LibraryStore(private val context: Context) {

    companion object {
        /**
         * The owner's person record. Every show in this app is one you watch, so
         * this id is implicit on all of them. Deliberately not a name: the display
         * name comes from the starter file or defaults to "Me".
         */
        const val ME_ID = "me"

        const val JSON_NAME = "library.json"
        const val CSV_NAME = "library.csv"

        /** How long "not in the mood" lasts before the show comes back on its own. */
        const val SNOOZE_DAYS = 14L
    }

    // prettyPrintIndent is still marked experimental, and the JSON being readable
    // by a person is the whole reason this format was chosen.
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val jsonFile: File get() = File(context.filesDir, JSON_NAME)
    private val csvFile: File get() = File(context.filesDir, CSV_NAME)

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    /** Fires after every persisted change, so the Drive mirror knows to push. */
    private val _dirty = MutableSharedFlow<Unit>(
        replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val dirty: MutableSharedFlow<Unit> get() = _dirty

    init {
        _library.value = readFromDisk() ?: seed()
        wakeSnoozed()
    }

    // ------------------------------------------------------------------ disk

    private fun readFromDisk(): Library? {
        if (!jsonFile.exists()) return null
        return runCatching { json.decodeFromString(Library.serializer(), jsonFile.readText()) }
            .getOrNull()
    }

    /** Atomic enough: write a sibling then rename, so a crash cannot truncate the real file. */
    private fun writeToDisk(library: Library) {
        runCatching {
            val tmp = File(context.filesDir, "$JSON_NAME.tmp")
            tmp.writeText(json.encodeToString(Library.serializer(), library))
            if (jsonFile.exists()) jsonFile.delete()
            tmp.renameTo(jsonFile)
            csvFile.writeText(Csv.render(library))
        }
    }

    fun jsonText(): String = json.encodeToString(Library.serializer(), _library.value)

    fun csvText(): String = Csv.render(_library.value)

    fun parse(text: String): Library? =
        runCatching { json.decodeFromString(Library.serializer(), text) }.getOrNull()

    /** Fold a copy fetched from Drive into what is on this phone. */
    fun mergeIn(remote: Library) = update { local -> Merge.libraries(local, remote) }

    // --------------------------------------------------------------- mutation

    private fun update(transform: (Library) -> Library) {
        val next = transform(_library.value).copy(updatedAt = Clock.now())
        _library.value = next
        scope.launch {
            writeToDisk(next)
            _dirty.tryEmit(Unit)
        }
    }

    private fun updateShow(id: String, transform: (Show) -> Show) = update { lib ->
        lib.copy(shows = lib.shows.map { if (it.id == id) transform(it).copy(updatedAt = Clock.now()) else it })
    }

    // --- shows ---

    fun addShow(
        title: String,
        tmdbId: Int? = null,
        year: Int? = null,
        posterPath: String? = null,
        wikipediaUrl: String? = null,
        seasonCount: Int? = null,
        episodeCount: Int? = null,
        overview: String? = null,
        serviceId: String?,
        profileId: String?,
        watchedWith: List<String>,
        position: Position = Position(),
    ): String {
        val id = UUID.randomUUID().toString()
        val now = Clock.now()
        update { lib ->
            lib.copy(
                shows = lib.shows + Show(
                    id = id,
                    tmdbId = tmdbId,
                    title = title,
                    year = year,
                    posterPath = posterPath,
                    wikipediaUrl = wikipediaUrl,
                    seasonCount = seasonCount,
                    episodeCount = episodeCount,
                    overview = overview,
                    serviceId = serviceId,
                    profileId = profileId,
                    watchedWith = watchedWith.filter { it != ME_ID },
                    position = position,
                    state = Show.STATE_ACTIVE,
                    addedAt = now,
                    updatedAt = now,
                )
            )
        }
        return id
    }

    /** One episode forward, rolling into the next season when the count is known. */
    fun bumpEpisode(id: String) = updateShow(id) { show ->
        show.copy(position = show.position.copy(episode = show.position.episode + 1), lastWatchedAt = Clock.now())
    }

    fun stepEpisode(id: String, delta: Int) = updateShow(id) { show ->
        val e = (show.position.episode + delta).coerceAtLeast(1)
        show.copy(position = show.position.copy(episode = e), lastWatchedAt = Clock.now())
    }

    /** For the weeks you only open this once a season. */
    fun finishSeason(id: String) = updateShow(id) { show ->
        show.copy(
            position = Position(season = show.position.season + 1, episode = 1),
            lastWatchedAt = Clock.now(),
        )
    }

    fun setPosition(id: String, position: Position) = updateShow(id) { it.copy(position = position) }

    fun setPlacement(id: String, serviceId: String?, profileId: String?) =
        updateShow(id) { it.copy(serviceId = serviceId, profileId = profileId) }

    fun setWatchedWith(id: String, people: List<String>) =
        updateShow(id) { it.copy(watchedWith = people.filter { p -> p != ME_ID }) }

    /** Not in the mood: still listed, smaller, and it returns on its own. */
    fun snooze(id: String, days: Long = SNOOZE_DAYS) = updateShow(id) {
        it.copy(state = Show.STATE_SNOOZED, snoozeUntil = Clock.plusDays(days))
    }

    /** Decided not to finish: the smallest row on the screen, and it stays down. */
    fun abandon(id: String) = updateShow(id) {
        it.copy(state = Show.STATE_ABANDONED, snoozeUntil = null)
    }

    /** Watched to the end. Different from giving up, and the CSV says so. */
    fun finish(id: String) = updateShow(id) {
        it.copy(state = Show.STATE_FINISHED, snoozeUntil = null, lastWatchedAt = Clock.now())
    }

    fun reactivate(id: String) = updateShow(id) {
        it.copy(state = Show.STATE_ACTIVE, snoozeUntil = null)
    }

    fun deleteShow(id: String) = update { lib ->
        lib.copy(
            shows = lib.shows.filterNot { it.id == id },
            deleted = lib.deleted + Tombstone(id, "show", Clock.now()),
        )
    }

    /** Anything whose snooze has run out comes back by itself. Called on every open. */
    fun wakeSnoozed() {
        val due = _library.value.shows.any { it.isSnoozed && Clock.isPast(it.snoozeUntil) }
        if (!due) return
        update { lib ->
            lib.copy(shows = lib.shows.map { show ->
                if (show.isSnoozed && Clock.isPast(show.snoozeUntil)) {
                    show.copy(state = Show.STATE_ACTIVE, snoozeUntil = null, updatedAt = Clock.now())
                } else show
            })
        }
    }

    // --- people ---

    fun addPerson(name: String, color: String): String {
        val id = slug(name, _library.value.people.map { it.id })
        update { lib ->
            lib.copy(
                people = lib.people + Person(
                    id = id, name = name.trim(), color = color,
                    order = lib.people.size, updatedAt = Clock.now(),
                )
            )
        }
        return id
    }

    fun renamePerson(id: String, name: String) = update { lib ->
        lib.copy(people = lib.people.map {
            if (it.id == id) it.copy(name = name.trim(), updatedAt = Clock.now()) else it
        })
    }

    fun deletePerson(id: String) = update { lib ->
        if (id == ME_ID) lib else lib.copy(
            people = lib.people.filterNot { it.id == id },
            shows = lib.shows.map { show ->
                if (show.watchedWith.contains(id)) {
                    show.copy(watchedWith = show.watchedWith - id, updatedAt = Clock.now())
                } else show
            },
            deleted = lib.deleted + Tombstone(id, "person", Clock.now()),
        )
    }

    // --- services and profiles ---

    fun addService(name: String, tint: String): String {
        val id = slug(name, _library.value.services.map { it.id })
        update { lib ->
            lib.copy(services = lib.services + Service(id = id, name = name.trim(), tint = tint, updatedAt = Clock.now()))
        }
        return id
    }

    fun deleteService(id: String) = update { lib ->
        lib.copy(
            services = lib.services.filterNot { it.id == id },
            deleted = lib.deleted + Tombstone(id, "service", Clock.now()),
        )
    }

    /** The first profile added to a service becomes its default, so setup is never required. */
    fun addProfile(serviceId: String, name: String): String {
        val service = _library.value.serviceOrNull(serviceId)
        val id = slug("$serviceId-$name", service?.profiles?.map { it.id } ?: emptyList())
        update { lib ->
            lib.copy(services = lib.services.map { s ->
                if (s.id != serviceId) s else s.copy(
                    profiles = s.profiles + Profile(id, name.trim()),
                    defaultProfileId = s.defaultProfileId ?: id,
                    updatedAt = Clock.now(),
                )
            })
        }
        return id
    }

    fun renameProfile(serviceId: String, profileId: String, name: String) = update { lib ->
        lib.copy(services = lib.services.map { s ->
            if (s.id != serviceId) s else s.copy(
                profiles = s.profiles.map { p -> if (p.id == profileId) p.copy(name = name.trim()) else p },
                updatedAt = Clock.now(),
            )
        })
    }

    fun setDefaultProfile(serviceId: String, profileId: String) = update { lib ->
        lib.copy(services = lib.services.map { s ->
            if (s.id != serviceId) s else s.copy(defaultProfileId = profileId, updatedAt = Clock.now())
        })
    }

    fun deleteProfile(serviceId: String, profileId: String) = update { lib ->
        lib.copy(services = lib.services.map { s ->
            if (s.id != serviceId) s else {
                val left = s.profiles.filterNot { it.id == profileId }
                s.copy(
                    profiles = left,
                    defaultProfileId = s.defaultProfileId?.takeIf { it != profileId } ?: left.firstOrNull()?.id,
                    updatedAt = Clock.now(),
                )
            }
        })
    }

    // ----------------------------------------------------------------- seeding

    private fun slug(raw: String, taken: List<String>): String {
        val base = raw.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "item" }
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }

    /**
     * First run. What gets seeded lives in [Starter], which reads an optional
     * file from the builder's home directory. No household's names are in this
     * repository.
     */
    private fun seed(): Library {
        val library = Starter.seed(ME_ID)
        scope.launch { writeToDisk(library) }
        return library
    }
}
