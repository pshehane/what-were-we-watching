package net.shehane.watching

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import net.shehane.watching.data.Gemini
import net.shehane.watching.data.LibraryStore
import net.shehane.watching.data.OnDevice
import net.shehane.watching.data.Recap
import net.shehane.watching.data.Suggest
import net.shehane.watching.data.Summary
import net.shehane.watching.data.Tmdb
import net.shehane.watching.model.Library
import net.shehane.watching.model.Position
import net.shehane.watching.model.Show

/** Where you are in the app. Eight screens do not need a navigation library. */
sealed interface Screen {
    data object Couch : Screen
    data object Search : Screen
    data class ShowDetail(val showId: String) : Screen
    data object Profiles : Screen
    data object About : Screen
    data object Wishlist : Screen
    data object Ideas : Screen
    data object CatchUp : Screen
}

/**
 * A message with a way out of it. Voting is one tap or one flick, and it files a
 * show, so the message has to carry the undo rather than just announce what
 * happened.
 */
data class Note(
    val message: String,
    val undo: (() -> Unit)? = null,
)

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

    private val _toast = MutableStateFlow<Note?>(null)
    val toast: StateFlow<Note?> = _toast.asStateFlow()

    private fun say(message: String, undo: (() -> Unit)? = null) {
        _toast.value = Note(message, undo)
    }

    // --- wishlist and travelling ---

    /** The country the wishlist screen is showing. Starts at home. */
    private val _viewingCountry = MutableStateFlow(library.value.homeCountry)
    val viewingCountry: StateFlow<String> = _viewingCountry.asStateFlow()

    private val _countries = MutableStateFlow<List<Tmdb.Country>>(emptyList())
    val countries: StateFlow<List<Tmdb.Country>> = _countries.asStateFlow()

    /** show id -> country code -> what it costs there. Fetched once per show. */
    private val _availability =
        MutableStateFlow<Map<String, Map<String, Tmdb.Availability>>>(emptyMap())
    val availability: StateFlow<Map<String, Map<String, Tmdb.Availability>>> =
        _availability.asStateFlow()

    private val _popularHere = MutableStateFlow<List<Tmdb.SearchItem>>(emptyList())
    val popularHere: StateFlow<List<Tmdb.SearchItem>> = _popularHere.asStateFlow()

    private val _travelLoading = MutableStateFlow(false)
    val travelLoading: StateFlow<Boolean> = _travelLoading.asStateFlow()

    private var travelJob: Job? = null

    // --- the region the search is answering for ---

    /** Empty means "anywhere": show every result, filter nothing. */
    private val _searchRegion = MutableStateFlow(store.library.value.homeCountry)
    val searchRegion: StateFlow<String> = _searchRegion.asStateFlow()

    private val _regionPickerOpen = MutableStateFlow(false)
    val regionPickerOpen: StateFlow<Boolean> = _regionPickerOpen.asStateFlow()

    /** tmdb id -> country -> availability. Cached across searches. */
    private val _searchAvailability =
        MutableStateFlow<Map<Int, Map<String, Tmdb.Availability>>>(emptyMap())
    val searchAvailability: StateFlow<Map<Int, Map<String, Tmdb.Availability>>> =
        _searchAvailability.asStateFlow()

    private var lookupJob: Job? = null

    private var searchJob: Job? = null

    init {
        _seated.value = store.library.value.people.map { it.id }.toSet()
        _syncState.value = drive.state()
        if (drive.isSignedIn) syncNow(quiet = true)
    }

    // ------------------------------------------------------------ navigation

    fun go(target: Screen) {
        when (target) {
            is Screen.Couch, is Screen.Wishlist, is Screen.Ideas -> {
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
        say("Not in the mood. Back in ${LibraryStore.SNOOZE_DAYS} days.")
        pushSoon()
    }

    fun abandon(showId: String) {
        store.abandon(showId)
        say("Moved to the shelf.")
        pushSoon()
    }

    fun finish(showId: String) {
        store.finish(showId)
        say("Marked finished.")
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

    // ------------------------------------------------------- episodes and recaps

    /** "tmdbId/season" to what TMDB holds for it. Kept for the session only. */
    private val _seasons = MutableStateFlow<Map<String, Tmdb.Season>>(emptyMap())
    val seasons: StateFlow<Map<String, Tmdb.Season>> = _seasons.asStateFlow()

    private val _seasonsLoading = MutableStateFlow(false)
    val seasonsLoading: StateFlow<Boolean> = _seasonsLoading.asStateFlow()

    private var seasonJob: Job? = null

    private fun key(tmdbId: Int, season: Int) = "$tmdbId/$season"

    /** The seasons already fetched for this show, keyed by season number. */
    fun seasonsFor(
        show: Show,
        cache: Map<String, Tmdb.Season> = _seasons.value,
    ): Map<Int, Tmdb.Season> {
        val tmdbId = show.tmdbId ?: return emptyMap()
        return cache.entries
            .filter { it.key.startsWith("$tmdbId/") }
            .associate { it.value.number to it.value }
    }

    /**
     * Fetches the seasons a screen needs, skipping any already held.
     *
     * Tapping the next episode needs one season. A catch-up needs every season up
     * to where you are, which is why it is only fetched when asked for rather than
     * when the show opens.
     */
    fun loadSeasons(show: Show, numbers: List<Int>) {
        val tmdbId = show.tmdbId ?: return
        val missing = numbers.filter { _seasons.value[key(tmdbId, it)] == null }
        if (missing.isEmpty()) return

        seasonJob?.cancel()
        seasonJob = viewModelScope.launch {
            _seasonsLoading.value = true
            for (n in missing) {
                runCatching { Tmdb.season(tmdbId, n) }
                    .onSuccess { _seasons.value = _seasons.value + (key(tmdbId, n) to it) }
            }
            _seasonsLoading.value = false
        }
    }

    // ----------------------------------------------------------- summarising

    data class Recapped(
        val text: String? = null,
        val source: Summary.Source = Summary.Source.RAW,
        val fallback: Summary.Fallback? = null,
        val modelName: String? = null,
        val working: Boolean = false,
    )

    private val _recapped = MutableStateFlow<Recapped?>(null)
    val recapped: StateFlow<Recapped?> = _recapped.asStateFlow()

    private var recapJob: Job? = null

    /** Null until asked once, because the check itself costs a round trip to AICore. */
    private var deviceModelReady: Boolean? = null

    val cloudAvailable: Boolean get() = Gemini.isConfigured

    private fun online(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun setSummaryMode(mode: String) {
        store.setSummaryMode(mode)
        clearRecap()
        pushSoon()
    }

    fun clearRecap() {
        recapJob?.cancel()
        _recapped.value = null
    }

    /**
     * Walks down from the chosen summariser to whatever actually works.
     *
     * Every step can fail for an ordinary reason, so a failure is never an error
     * on screen: it moves to the next thing and the sheet says which one answered
     * and why it was not the one you picked.
     */
    fun summarise(show: Show, recap: Recap.CatchUp, upTo: String) {
        if (_recapped.value != null) return
        val mode = library.value.summaryMode
        if (mode == Library.SUMMARY_NONE || recap.isEmpty) return

        recapJob?.cancel()
        recapJob = viewModelScope.launch {
            _recapped.value = Recapped(working = true)

            if (deviceModelReady == null) {
                deviceModelReady = OnDevice.isAvailable(getApplication())
            }
            val (source, why) = Summary.resolve(
                mode = mode,
                hasCloudKey = Gemini.isConfigured,
                hasNetwork = online(),
                hasDeviceModel = deviceModelReady == true,
            )

            val body = Summary.sourceText(recap)
            when (source) {
                Summary.Source.CLOUD -> {
                    val cast = runCatching {
                        show.tmdbId?.let { Tmdb.mainCast(it, show.episodeCount) } ?: emptyList()
                    }.getOrDefault(emptyList())

                    runCatching {
                        Gemini.summarise(
                            Summary.prompt(
                                show = show,
                                upTo = upTo,
                                characters = cast.mapNotNull { it.character },
                                body = body,
                            )
                        )
                    }
                        .onSuccess { _recapped.value = Recapped(it, Summary.Source.CLOUD, why) }
                        .onFailure {
                            android.util.Log.w("Watching.Summary", "cloud summarise failed", it)
                            runOnDevice(body, recap, why = Summary.Fallback.FAILED)
                        }
                }

                Summary.Source.DEVICE -> runOnDevice(body, recap, why)
                Summary.Source.RAW -> _recapped.value = Recapped(null, Summary.Source.RAW, why)
            }
        }
    }

    private suspend fun runOnDevice(body: String, recap: Recap.CatchUp, why: Summary.Fallback?) {
        if (deviceModelReady != true) {
            _recapped.value = Recapped(
                null,
                Summary.Source.RAW,
                why ?: Summary.Fallback.NO_DEVICE_MODEL,
            )
            return
        }
        val bullets = Summary.deviceBulletCount(recap.recent.size.coerceAtMost(3))
        runCatching { OnDevice.summarise(getApplication(), body, bullets) }
            .onSuccess {
                _recapped.value = Recapped(it.text, Summary.Source.DEVICE, why, it.modelName)
            }
            .onFailure {
                android.util.Log.w("Watching.Summary", "on-device summarise failed", it)
                _recapped.value = Recapped(null, Summary.Source.RAW, Summary.Fallback.FAILED)
            }
    }

    /** Asked once per show per session, so reopening a screen is free. */
    private val detailsAsked = mutableSetOf<String>()

    /**
     * Fetches the season and episode counts for a show that has none.
     *
     * The add flow gets these from TMDB when you add a show. Voting on a
     * suggestion did not, because a search result does not carry them, so every
     * show that arrived that way had no idea how long it was. This repairs those
     * records the first time you open one, and runs after the screen is drawn so
     * nothing waits on it.
     */
    fun fillDetails(showId: String) {
        val show = library.value.showOrNull(showId) ?: return
        val tmdbId = show.tmdbId ?: return
        if (show.seasonCount != null && show.wikipediaUrl != null) return
        if (!detailsAsked.add(showId)) return

        viewModelScope.launch {
            if (show.seasonCount == null) {
                runCatching { Tmdb.detail(tmdbId) }
                    .onSuccess { store.fillDetails(showId, it.seasonCount, it.episodeCount) }
            }
            if (show.wikipediaUrl == null) {
                runCatching { Tmdb.wikipediaUrl(tmdbId, show.title, show.year) }
                    .onSuccess { url -> url?.let { store.setWikipediaUrl(showId, it) } }
            }
            pushSoon()
        }
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
        say("Parked on the wishlist.")
        pushSoon()
    }

    fun startWatching(showId: String) {
        store.startWatching(showId)
        say("On the couch, from the start.")
        pushSoon()
    }

    // ----------------------------------------------------------- suggestions

    private val _guesses = MutableStateFlow<List<Tmdb.SearchItem>>(emptyList())
    val guesses: StateFlow<List<Tmdb.SearchItem>> = _guesses.asStateFlow()

    private val _guessLoading = MutableStateFlow(false)
    val guessLoading: StateFlow<Boolean> = _guessLoading.asStateFlow()

    /** What has been answered on the catch-up grid, so the tiles can show it. */
    private val _verdicts = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val verdicts: StateFlow<Map<Int, Boolean>> = _verdicts.asStateFlow()

    private var guessJob: Job? = null
    private var guessedFrom: Int? = null

    /**
     * Takes the availability map rather than reading the flow, so the screen's
     * dependency on it is visible to Compose. Read behind a call the list would
     * never redraw when a lookup landed.
     */
    fun suggestions(
        availability: Map<String, Map<String, Tmdb.Availability>> = _availability.value,
    ): Suggest.Result {
        val home = library.value.homeCountry
        val here = availability.mapNotNull { (showId, byCountry) ->
            byCountry[home]?.let { showId to it }
        }.toMap()
        return Suggest.build(library.value, _seated.value, here)
    }

    /**
     * The availability half of [loadTravel], without the popular list.
     *
     * The Ideas tab needs it so a wishlist entry you never filed under a service
     * can still be offered: TMDB knows where it is even when you never said.
     */
    fun loadAvailability() {
        val missing = wishlist().filter { it.tmdbId != null && _availability.value[it.id] == null }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            for (show in missing) {
                val id = show.tmdbId ?: continue
                runCatching { Tmdb.availabilityEverywhere(id) }
                    // Merged one at a time against the current value, not a snapshot
                    // taken before the loop, so a concurrent load cannot drop entries.
                    .onSuccess { _availability.value = _availability.value + (show.id to it) }
            }
        }
    }

    /**
     * Asks TMDB what sits next to the seed show. Only re-asks when the seed itself
     * changes, so coming back to the tab does not spend a call to redraw the same
     * list.
     */
    fun loadGuesses() {
        val seed = suggestions().seed?.tmdbId ?: run { _guesses.value = emptyList(); return }
        if (seed == guessedFrom && _guesses.value.isNotEmpty()) return

        guessJob?.cancel()
        guessedFrom = seed
        guessJob = viewModelScope.launch {
            _guessLoading.value = true
            runCatching { Tmdb.recommendations(seed) }
                .onSuccess { _guesses.value = Suggest.unseen(library.value, it).take(12) }
                .onFailure { _guesses.value = emptyList() }
            _guessLoading.value = false
        }
    }

    /**
     * A verdict on something not in the library yet. It is added as finished with
     * the verdict attached, which is the whole point of the fast path: no service,
     * no profile, no episode number.
     */
    fun vote(item: Tmdb.SearchItem, loved: Boolean, quiet: Boolean = false) {
        val id = store.addShow(
            title = item.name,
            tmdbId = item.id,
            year = item.year,
            posterPath = item.posterPath,
            overview = item.overview,
            serviceId = null,
            profileId = null,
            watchedWith = emptyList(),
            state = Show.STATE_FINISHED,
        )
        store.vote(id, loved)

        // The record is thin until this lands: a search result carries no season
        // count. Filled after the fact so the tap itself stays instant.
        fillDetails(id)

        val wasAt = _guesses.value.indexOfFirst { it.id == item.id }
        _guesses.value = _guesses.value.filterNot { it.id == item.id }
        _verdicts.value = _verdicts.value + (item.id to loved)

        // The grid says no to the toast. Its tile already shows the verdict and
        // can be tapped to take it back, and a message over the rows you are
        // working down would cover the next thing you meant to tap.
        if (!quiet) {
            say(
                if (loved) "${item.name} filed as watched and loved"
                else "${item.name} filed as watched, not loved"
            ) {
                store.deleteShow(id)
                _verdicts.value = _verdicts.value - item.id
                putBack(item, wasAt)
                pushSoon()
            }
        }
        pushSoon()
    }

    /**
     * Back into the list at the place it left, not appended. Undo is supposed to
     * look like nothing happened, and a row reappearing at the bottom is a
     * different list from the one you were reading.
     */
    private fun putBack(item: Tmdb.SearchItem, at: Int) {
        if (at < 0 || _guesses.value.any { it.id == item.id }) return
        val next = _guesses.value.toMutableList()
        next.add(at.coerceIn(0, next.size), item)
        _guesses.value = next
    }

    /**
     * Set or change the verdict on a show already in the library, from its own
     * screen. Passing null clears it back to "nobody has said", which is not the
     * same as a no and must stay reachable.
     */
    fun setVerdict(showId: String, loved: Boolean?) {
        if (loved == null) store.clearVerdict(showId) else store.vote(showId, loved)
        pushSoon()
    }

    /** Tapping a tile that already carries a verdict takes the verdict back. */
    fun clearVote(item: Tmdb.SearchItem) {
        if (item.id !in _verdicts.value) return
        library.value.shows.firstOrNull { it.tmdbId == item.id }?.let { store.deleteShow(it.id) }
        _verdicts.value = _verdicts.value - item.id
        pushSoon()
    }

    /** The same verdict, on a show the library already knows about. */
    fun voteOnShow(showId: String, loved: Boolean) {
        val before = library.value.showOrNull(showId) ?: return
        store.vote(showId, loved)
        say(
            if (loved) "${before.title} filed as watched and loved"
            else "${before.title} filed as watched, not loved"
        ) {
            store.restoreShow(before)
            pushSoon()
        }
        pushSoon()
    }

    /**
     * Straight onto the wishlist from a suggestion. No sheet: a suggestion already
     * knows the title, and the service and profile are questions for the day you
     * actually start it.
     */
    fun wishlistFromSuggestion(item: Tmdb.SearchItem) {
        val id = store.addShow(
            title = item.name,
            tmdbId = item.id,
            year = item.year,
            posterPath = item.posterPath,
            overview = item.overview,
            serviceId = null,
            profileId = null,
            watchedWith = emptyList(),
            state = Show.STATE_WISHLIST,
        )
        fillDetails(id)

        val wasAt = _guesses.value.indexOfFirst { it.id == item.id }
        _guesses.value = _guesses.value.filterNot { it.id == item.id }
        say("${item.name} is on the wishlist.") {
            store.deleteShow(id)
            putBack(item, wasAt)
            pushSoon()
        }
        pushSoon()
    }

    private val _catchUp = MutableStateFlow<List<Tmdb.SearchItem>>(emptyList())
    val catchUp: StateFlow<List<Tmdb.SearchItem>> = _catchUp.asStateFlow()

    private val _catchUpLoading = MutableStateFlow(false)
    val catchUpLoading: StateFlow<Boolean> = _catchUpLoading.asStateFlow()

    /**
     * The catch-up grid: what is popular at home, on the services you actually pay
     * for, minus everything the library already knows about.
     *
     * The service filter is the part that makes the heading true. Without TMDB's
     * ids for your own services this would be whatever is popular on any
     * subscription in the country, which is a different claim; when nothing
     * matches, the screen says so rather than quietly widening.
     */
    fun loadCatchUp() {
        _verdicts.value = emptyMap()
        if (_catchUp.value.isNotEmpty()) return

        viewModelScope.launch {
            _catchUpLoading.value = true
            val lib = library.value
            val ids = runCatching { Tmdb.providerIdsFor(lib.homeCountry, lib.services) }
                .getOrDefault(emptyList())
            runCatching { Tmdb.popularIn(lib.homeCountry, ids) }
                .onSuccess { _catchUp.value = Suggest.unseen(library.value, it) }
                .onFailure { _catchUp.value = emptyList() }
            _catchUpLoading.value = false
        }
    }

    /**
     * Everything the library has not heard of, plus anything answered on this
     * screen. Voting adds the show to the library, so without that exception the
     * tile would vanish the moment it was tapped and every row below would jump
     * up a place, under a finger already on its way to the next one.
     */
    fun catchUpCandidates(): List<Tmdb.SearchItem> {
        val answered = _verdicts.value.keys
        val known = library.value.shows.mapNotNull { it.tmdbId }.toSet() - answered
        return _catchUp.value.filterNot { it.id in known }
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
        // Availability is already in memory for every country; only the local
        // popular list depends on where you are looking.
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
    /**
     * Where each wishlist show can be watched, everywhere, plus what is popular in
     * the country being viewed.
     *
     * Availability is per show and not per country, so changing country re-reads
     * what is already in memory instead of asking TMDB again. It is never stored
     * on disk: rights change, and a saved answer would quietly become a lie.
     */
    fun loadTravel(force: Boolean = false) {
        val region = _viewingCountry.value
        val shows = wishlist().filter { it.tmdbId != null }
        val missing = shows.filter { force || _availability.value[it.id] == null }

        travelJob?.cancel()
        travelJob = viewModelScope.launch {
            _travelLoading.value = true
            if (force) _availability.value = emptyMap()

            for (show in missing) {
                val id = show.tmdbId ?: continue
                runCatching { Tmdb.availabilityEverywhere(id) }
                    .onSuccess { _availability.value = _availability.value + (show.id to it) }
            }

            _popularHere.value = emptyList()
            runCatching { Tmdb.popularIn(region) }
                .onSuccess { _popularHere.value = it.take(12) }

            _travelLoading.value = false
        }
    }

    /**
     * The countries a show is included with a subscription in, most useful first.
     *
     * "Most useful" is a guess: markets that share a language with home, then
     * everywhere else alphabetically. There is no signal in the data for which
     * country you might actually get to, so this is a default, not a deduction.
     */
    fun watchableIn(showId: String): List<String> {
        val byCountry = _availability.value[showId] ?: return emptyList()
        val streamable = byCountry.filterValues { it.isStreamable }.keys
        val preferred = listOf("US", "GB", "CA", "AU", "IE", "NZ")
        return streamable.sortedWith(
            compareBy({ preferred.indexOf(it).takeIf { i -> i >= 0 } ?: preferred.size }, { it })
        )
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
                .onSuccess {
                    _results.value = it
                    _searchError.value = null
                    lookUpRegions(it)
                }
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

    fun setSearchRegion(code: String) {
        _searchRegion.value = code
        _regionPickerOpen.value = false
    }

    fun setRegionPickerOpen(open: Boolean) {
        _regionPickerOpen.value = open
        if (open) loadCountries()
    }

    /**
     * Looks up where the results can be watched, after they are already on screen.
     * Typing stays fast; the region answer arrives a moment later and the list
     * regroups itself. Capped, because a search returns twenty results and nobody
     * scrolls to the twentieth.
     */
    private fun lookUpRegions(results: List<Tmdb.SearchItem>) {
        lookupJob?.cancel()
        val wanted = results.take(10).map { it.id }
            .filter { _searchAvailability.value[it] == null }
        if (wanted.isEmpty()) return

        lookupJob = viewModelScope.launch {
            val found = _searchAvailability.value.toMutableMap()
            for (id in wanted) {
                runCatching { Tmdb.availabilityEverywhere(id) }
                    .onSuccess { found[id] = it; _searchAvailability.value = found.toMap() }
            }
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        lookupJob?.cancel()
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
        say(
            if (toWishlist) "${d.result.name} is on the wishlist."
            else "${d.result.name} is on the couch."
        )
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
                say("Connected to Drive.")
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
                        say(if (changed) "Drive had newer changes; merged." else "Drive is up to date.")
                    }
                }
                .onFailure {
                    _syncState.value = DriveSync.State.Failed(it.message ?: "Sync failed")
                    if (!quiet) say("Could not sync: ${it.message}")
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
