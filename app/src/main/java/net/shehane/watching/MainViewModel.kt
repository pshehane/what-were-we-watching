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
    data object Wishlist : Screen
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

    /**
     * A real back stack, so back means "where I came from" rather than a guess.
     * Opening a show from the wishlist and pressing back returns to the wishlist,
     * not to the couch.
     *
     * The two tabs are roots: switching tabs replaces the stack rather than piling
     * one tab on top of the other, which would make back walk backwards through
     * every tab you had ever touched.
     */
    private val stack = ArrayDeque<Screen>().apply { addLast(Screen.Couch) }

    private val _screen = MutableStateFlow<Screen>(Screen.Couch)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** Hoisted out of the wishlist screen so back can close it before anything else. */
    private val _countryPickerOpen = MutableStateFlow(false)
    val countryPickerOpen: StateFlow<Boolean> = _countryPickerOpen.asStateFlow()

    fun setCountryPickerOpen(open: Boolean) {
        _countryPickerOpen.value = open
    }

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

    // --- wishlist and travelling ---

    /** The country the wishlist screen is showing. Starts at home. */
    private val _viewingCountry = MutableStateFlow(library.value.homeCountry)
    val viewingCountry: StateFlow<String> = _viewingCountry.asStateFlow()

    private val _countries = MutableStateFlow<List<Tmdb.Country>>(emptyList())
    val countries: StateFlow<List<Tmdb.Country>> = _countries.asStateFlow()

    /** Availability per show id, for whichever country is being viewed. */
    private val _availability = MutableStateFlow<Map<String, Tmdb.Availability>>(emptyMap())
    val availability: StateFlow<Map<String, Tmdb.Availability>> = _availability.asStateFlow()

    private val _popularHere = MutableStateFlow<List<Tmdb.SearchItem>>(emptyList())
    val popularHere: StateFlow<List<Tmdb.SearchItem>> = _popularHere.asStateFlow()

    private val _travelLoading = MutableStateFlow(false)
    val travelLoading: StateFlow<Boolean> = _travelLoading.asStateFlow()

    private var travelJob: Job? = null

    private var searchJob: Job? = null

    init {
        _seated.value = store.library.value.people.map { it.id }.toSet()
        _syncState.value = drive.state()
        if (drive.isSignedIn) syncNow(quiet = true)
    }

    // ------------------------------------------------------------ navigation

    fun go(target: Screen) {
        when (target) {
            is Screen.Couch, is Screen.Wishlist -> {
                stack.clear()
                stack.addLast(target)
            }
            else -> if (stack.lastOrNull() != target) stack.addLast(target)
        }
        if (target !is Screen.Wishlist) _countryPickerOpen.value = false
        _screen.value = stack.last()
    }

    /**
     * One step back. Returns false when there is nowhere left to go, which is the
     * signal to stay put: back never leaves this app.
     */
    fun popScreen(): Boolean {
        if (stack.size <= 1) return false
        stack.removeLast()
        _screen.value = stack.last()
        return true
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
        // Leave the detail screen for a show that no longer exists, back to
        // whichever list you opened it from.
        if (_screen.value is Screen.ShowDetail) popScreen()
        // Any other screen showing it - the wishlist - just drops the row.
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

    // -------------------------------------------------------------- wishlist

    fun wishlist(): List<Show> =
        library.value.shows.filter { it.isWishlist }.sortedBy { it.title.lowercase() }

    fun toWishlist(showId: String) {
        store.toWishlist(showId)
        _toast.value = "Parked on the wishlist."
        pushSoon()
    }

    fun startWatching(showId: String) {
        store.startWatching(showId)
        _toast.value = "On the couch, from the start."
        pushSoon()
    }

    // ------------------------------------------------------------ travelling

    val isAwayFromHome: Boolean
        get() = _viewingCountry.value != library.value.homeCountry

    fun setHomeCountry(code: String) {
        store.setHomeCountry(code)
        _viewingCountry.value = code
        loadTravel()
        pushSoon()
    }

    fun viewCountry(code: String) {
        _countryPickerOpen.value = false
        if (_viewingCountry.value == code) return
        _viewingCountry.value = code
        loadTravel()
    }

    fun loadCountries() {
        if (_countries.value.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { Tmdb.countries() }.onSuccess { _countries.value = it }
        }
    }

    /**
     * Looks up, for the country being viewed, where each wishlist show can be
     * watched, plus what is popular there. One call per wishlist item is fine at
     * this size and it is the only honest way to get it: availability is per
     * country and changes, so it is never stored.
     */
    fun loadTravel() {
        travelJob?.cancel()
        val region = _viewingCountry.value
        val shows = wishlist().filter { it.tmdbId != null }
        travelJob = viewModelScope.launch {
            _travelLoading.value = true
            _availability.value = emptyMap()
            _popularHere.value = emptyList()

            val found = mutableMapOf<String, Tmdb.Availability>()
            for (show in shows) {
                val id = show.tmdbId ?: continue
                runCatching { Tmdb.availability(id, region) }
                    .onSuccess { found[show.id] = it; _availability.value = found.toMap() }
            }
            runCatching { Tmdb.popularIn(region) }
                .onSuccess { _popularHere.value = it.take(12) }

            _travelLoading.value = false
        }
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
    fun commitAdd(toWishlist: Boolean = false) {
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
            state = if (toWishlist) Show.STATE_WISHLIST else Show.STATE_ACTIVE,
        )
        _draft.value = null
        clearQuery()
        _screen.value = if (toWishlist) Screen.Wishlist else Screen.Couch
        _toast.value =
            if (toWishlist) "${d.result.name} is on the wishlist."
            else "${d.result.name} is on the couch."
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
