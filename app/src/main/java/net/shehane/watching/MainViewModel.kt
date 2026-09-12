package net.shehane.watching

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.shehane.watching.data.Clock
import net.shehane.watching.data.Couch
import net.shehane.watching.data.DriveSync
import net.shehane.watching.data.LibraryStore
import net.shehane.watching.data.Tmdb
import net.shehane.watching.model.Library
import net.shehane.watching.model.Position
import net.shehane.watching.model.Show

/** Where you are in the app. Six screens do not need a navigation library. */
sealed interface Screen {
    data object Couch : Screen
    data object Search : Screen
    data class ShowDetail(val showId: String) : Screen
    data object Profiles : Screen
    data object About : Screen
}

/** What the add sheet is holding while you decide. */
data class Draft(
    val result: Tmdb.SearchItem,
    val serviceId: String?,
    val profileId: String?,
    val watchedWith: List<String>,
    val position: Position = Position(),
    val detail: Tmdb.Detail? = null,
    val wikipediaUrl: String? = null,
    val loading: Boolean = true,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store: LibraryStore = (app as WatchingApp).store
    val drive = DriveSync(app)

    val library: StateFlow<Library> = store.library

    private val _screen = MutableStateFlow<Screen>(Screen.Couch)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** Who is on the couch. Starts as everyone, which is the common case. */
    private val _seated = MutableStateFlow<Set<String>>(emptySet())
    val seated: StateFlow<Set<String>> = _seated.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Tmdb.SearchItem>>(emptyList())
    val results: StateFlow<List<Tmdb.SearchItem>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft.asStateFlow()

    private val _syncState = MutableStateFlow<DriveSync.State>(DriveSync.State.SignedOut)
    val syncState: StateFlow<DriveSync.State> = _syncState.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private var searchJob: Job? = null

    init {
        _seated.value = store.library.value.people.map { it.id }.toSet()
        _syncState.value = drive.state()
        if (drive.isSignedIn) syncNow(quiet = true)
    }

    // ------------------------------------------------------------ navigation

    fun go(screen: Screen) {
        _screen.value = screen
    }

    fun back() {
        _screen.value = when (_screen.value) {
            is Screen.Couch -> Screen.Couch
            else -> Screen.Couch
        }
    }

    fun onResumed() {
        store.wakeSnoozed()
        if (drive.isSignedIn) syncNow(quiet = true)
    }

    // ----------------------------------------------------------------- couch

    fun toggleSeat(personId: String) {
        val next = _seated.value.toMutableSet()
        if (!next.remove(personId)) next.add(personId)
        _seated.value = next
    }

    fun seatEveryone() {
        _seated.value = library.value.people.map { it.id }.toSet()
    }

    fun seatOnly(personId: String) {
        _seated.value = setOf(personId)
    }

    fun couch(): Couch.Result = Couch.build(library.value, _seated.value)

    // ----------------------------------------------------------------- shows

    fun bump(showId: String) {
        store.bumpEpisode(showId)
        pushSoon()
    }

    fun step(showId: String, delta: Int) {
        store.stepEpisode(showId, delta)
        pushSoon()
    }

    fun finishSeason(showId: String) {
        store.finishSeason(showId)
        pushSoon()
    }

    fun setPosition(showId: String, position: Position) {
        store.setPosition(showId, position)
        pushSoon()
    }

    fun snooze(showId: String) {
        store.snooze(showId)
        _toast.value = "Not in the mood. Back in ${LibraryStore.SNOOZE_DAYS} days."
        pushSoon()
    }

    fun abandon(showId: String) {
        store.abandon(showId)
        _toast.value = "Moved to the shelf."
        pushSoon()
    }

    fun finish(showId: String) {
        store.finish(showId)
        _toast.value = "Marked finished."
        pushSoon()
    }

    fun reactivate(showId: String) {
        store.reactivate(showId)
        pushSoon()
    }

    fun deleteShow(showId: String) {
        store.deleteShow(showId)
        if (_screen.value is Screen.ShowDetail) _screen.value = Screen.Couch
        pushSoon()
    }

    fun setWatchedWith(showId: String, people: List<String>) {
        store.setWatchedWith(showId, people)
        pushSoon()
    }

    fun setPlacement(showId: String, serviceId: String?, profileId: String?) {
        store.setPlacement(showId, serviceId, profileId)
        pushSoon()
    }

    // ------------------------------------------------------------ setup data

    fun addProfile(serviceId: String, name: String): String {
        val id = store.addProfile(serviceId, name)
        pushSoon()
        return id
    }

    fun renameProfile(serviceId: String, profileId: String, name: String) {
        store.renameProfile(serviceId, profileId, name); pushSoon()
    }

    fun setDefaultProfile(serviceId: String, profileId: String) {
        store.setDefaultProfile(serviceId, profileId); pushSoon()
    }

    fun deleteProfile(serviceId: String, profileId: String) {
        store.deleteProfile(serviceId, profileId); pushSoon()
    }

    fun addService(name: String, tint: String) {
        store.addService(name, tint); pushSoon()
    }

    fun deleteService(serviceId: String) {
        store.deleteService(serviceId); pushSoon()
    }

    fun addPerson(name: String, color: String) {
        val id = store.addPerson(name, color)
        _seated.value = _seated.value + id
        pushSoon()
    }

    fun deletePerson(personId: String) {
        store.deletePerson(personId)
        _seated.value = _seated.value - personId
        pushSoon()
    }

    // ---------------------------------------------------------------- search

    /**
     * Debounced so a fast typist makes one call rather than eight, and each new
     * keystroke cancels the one in flight. This is the only call on the path
     * between wanting to add a show and seeing it.
     */
    fun onQueryChanged(text: String) {
        _query.value = text
        searchJob?.cancel()
        _searchError.value = null

        if (text.isBlank()) {
            _results.value = emptyList()
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            _searching.value = true
            runCatching { Tmdb.search(text) }
                .onSuccess { _results.value = it; _searchError.value = null }
                .onFailure {
                    _results.value = emptyList()
                    _searchError.value = when {
                        !Tmdb.isConfigured -> "No TMDB key is compiled in."
                        else -> "Could not reach TMDB. ${it.message.orEmpty()}".trim()
                    }
                }
            _searching.value = false
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        _query.value = ""
        _results.value = emptyList()
        _searchError.value = null
    }

    /** The show already on your list, if this search result is one of them. */
    fun existingFor(tmdbId: Int): Show? = library.value.shows.firstOrNull { it.tmdbId == tmdbId }

    // ------------------------------------------------------------- add sheet

    /**
     * Opening the sheet fills in every field before you look at it. Who comes from
     * the couch bar, the service from where TMDB says it streams, the profile from
     * the default you marked for that service, and the episode from the beginning.
     */
    fun beginAdd(item: Tmdb.SearchItem) {
        val lib = library.value
        _draft.value = Draft(
            result = item,
            serviceId = null,
            profileId = null,
            watchedWith = _seated.value.filter { it != LibraryStore.ME_ID },
            loading = true,
        )
        viewModelScope.launch {
            val detail = runCatching { Tmdb.detail(item.id) }.getOrNull()
            val providers = runCatching { Tmdb.providerNames(item.id) }.getOrDefault(emptyList())
            val service = Tmdb.matchService(lib.services, providers)
            val wiki = runCatching { Tmdb.wikipediaUrl(item.id, item.name, item.year) }.getOrNull()

            _draft.value = _draft.value?.copy(
                detail = detail,
                serviceId = service?.id,
                profileId = service?.defaultProfile?.id,
                wikipediaUrl = wiki,
                loading = false,
            )
        }
    }

    fun updateDraft(transform: (Draft) -> Draft) {
        _draft.value = _draft.value?.let(transform)
    }

    fun cancelAdd() {
        _draft.value = null
    }

    /** One tap from here to a tracked show. */
    fun commitAdd() {
        val d = _draft.value ?: return
        val id = store.addShow(
            title = d.result.name,
            tmdbId = d.result.id,
            year = d.detail?.year ?: d.result.year,
            posterPath = d.detail?.posterPath ?: d.result.posterPath,
            wikipediaUrl = d.wikipediaUrl,
            seasonCount = d.detail?.seasonCount,
            episodeCount = d.detail?.episodeCount,
            overview = d.detail?.overview ?: d.result.overview,
            serviceId = d.serviceId,
            profileId = d.profileId,
            watchedWith = d.watchedWith,
            position = d.position,
        )
        _draft.value = null
        clearQuery()
        _screen.value = Screen.Couch
        _toast.value = "${d.result.name} is on the couch."
        pushSoon()
        // Silence the unused warning without losing the id, which the detail screen
        // would want if we ever jump straight there after adding.
        lastAddedId = id
    }

    var lastAddedId: String? = null
        private set

    // ------------------------------------------------------------------ sync

    fun signInIntent(): android.content.Intent = drive.signInIntent()

    /** Called with whatever the Google sign-in screen handed back. */
    fun onSignInResult(data: android.content.Intent?) {
        drive.onSignInResult(data)
            .onSuccess {
                _syncState.value = drive.state()
                _toast.value = "Connected to Drive."
                syncNow()
            }
            .onFailure {
                _syncState.value = DriveSync.State.Failed(
                    it.message ?: "Google turned the sign-in down"
                )
            }
    }

    fun signOut() {
        drive.signOut()
        _syncState.value = drive.state()
    }

    fun syncNow(quiet: Boolean = false) {
        if (!drive.isSignedIn) {
            _syncState.value = drive.state()
            return
        }
        viewModelScope.launch {
            drive.sync(store)
                .onSuccess { changed ->
                    _syncState.value = drive.state()
                    if (!quiet) {
                        _toast.value =
                            if (changed) "Drive had newer changes; merged." else "Drive is up to date."
                    }
                }
                .onFailure {
                    _syncState.value = DriveSync.State.Failed(it.message ?: "Sync failed")
                    if (!quiet) _toast.value = "Could not sync: ${it.message}"
                }
        }
    }

    private var pushJob: Job? = null

    /** Batches a burst of taps into one upload a few seconds after the last one. */
    private fun pushSoon() {
        if (!drive.isSignedIn) return
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            delay(4_000)
            syncNow(quiet = true)
        }
    }

    fun consumeToast() {
        _toast.value = null
    }
}
