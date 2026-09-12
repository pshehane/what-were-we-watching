package net.shehane.watching

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.shehane.watching.ui.AboutScreen
import net.shehane.watching.ui.AddScreen
import net.shehane.watching.ui.AddSheet
import net.shehane.watching.data.Couch
import net.shehane.watching.ui.CatchUpScreen
import net.shehane.watching.ui.CouchScreen
import net.shehane.watching.ui.Draw
import net.shehane.watching.ui.HGap
import net.shehane.watching.ui.ProfilesScreen
import net.shehane.watching.ui.ShowScreen
import net.shehane.watching.ui.SuggestScreen
import net.shehane.watching.ui.WishlistScreen
import net.shehane.watching.ui.VGap
import net.shehane.watching.ui.theme.Ink
import net.shehane.watching.ui.theme.Type

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Plain enableEdgeToEdge() puts a light scrim behind the navigation bar,
        // which paints a white strip under an app this dark. Both bars are forced
        // to the dark treatment: transparent, with light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
private fun App() {
    val vm: MainViewModel = viewModel()

    val library by vm.library.collectAsStateWithLifecycle()
    val seated by vm.seated.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val searchError by vm.searchError.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val viewingCountry by vm.viewingCountry.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val availability by vm.availability.collectAsStateWithLifecycle()
    val popularHere by vm.popularHere.collectAsStateWithLifecycle()
    val travelLoading by vm.travelLoading.collectAsStateWithLifecycle()
    val countryPickerOpen by vm.countryPickerOpen.collectAsStateWithLifecycle()
    val searchRegion by vm.searchRegion.collectAsStateWithLifecycle()
    val regionPickerOpen by vm.regionPickerOpen.collectAsStateWithLifecycle()
    val searchAvailability by vm.searchAvailability.collectAsStateWithLifecycle()
    val guesses by vm.guesses.collectAsStateWithLifecycle()
    val guessLoading by vm.guessLoading.collectAsStateWithLifecycle()
    val verdicts by vm.verdicts.collectAsStateWithLifecycle()
    val catchUpLoading by vm.catchUpLoading.collectAsStateWithLifecycle()

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    // Google's sign-in screen is another activity; this catches what it hands back.
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> vm.onSignInResult(result.data) }

    LaunchedEffect(Unit) { vm.onResumed() }

    // Back undoes the last thing that happened, whatever that was, and never
    // leaves the app: at the root it is simply consumed. Order matters - a sheet
    // or a picker sitting over a screen is what back should shut first.
    BackHandler {
        when {
            draft != null -> vm.cancelAdd()
            screen is Screen.Search && regionPickerOpen -> vm.setRegionPickerOpen(false)
            screen is Screen.Search && query.isNotEmpty() -> vm.clearQuery()
            screen is Screen.Wishlist && countryPickerOpen -> vm.setCountryPickerOpen(false)
            screen is Screen.Wishlist && viewingCountry != library.homeCountry ->
                vm.viewCountry(library.homeCountry)
            else -> vm.popScreen()
        }
    }

    Box(Modifier.fillMaxSize().background(Ink.Ground)) {
        when (val current = screen) {
            is Screen.Couch -> {
                CouchScreen(
                    library = library,
                    seated = seated,
                    result = vm.couch(),
                    onToggleSeat = vm::toggleSeat,
                    onOpenShow = { vm.go(Screen.ShowDetail(it)) },
                    onBump = vm::bump,
                    onSnooze = vm::snooze,
                    onAbandon = vm::abandon,
                    onReactivate = vm::reactivate,
                    onSearch = { vm.go(Screen.Search) },
                    onSettings = { vm.go(Screen.Profiles) },
                    insets = insets,
                )
                BottomBar(
                    insets = insets,
                    selected = Screen.Couch,
                    onCouch = { vm.go(Screen.Couch) },
                    onIdeas = { vm.go(Screen.Ideas) },
                    onAdd = { vm.go(Screen.Search) },
                    onWishlist = { vm.go(Screen.Wishlist) },
                )
            }

            is Screen.Search -> {
                LaunchedEffect(Unit) { vm.loadCountries() }
                AddScreen(
                    library = library,
                    query = query,
                    results = results,
                    searching = searching,
                    error = searchError,
                    seatedNames = "",
                    seatedPeople = library.people.filter { it.id in seated },
                    existingFor = vm::existingFor,
                    searchRegion = searchRegion,
                    countries = countries,
                    regionPickerOpen = regionPickerOpen,
                    onRegionPickerOpen = vm::setRegionPickerOpen,
                    onSetRegion = vm::setSearchRegion,
                    searchAvailability = searchAvailability,
                    onQueryChanged = vm::onQueryChanged,
                    onClear = vm::clearQuery,
                    onPick = vm::beginAdd,
                    onOpenExisting = { vm.go(Screen.ShowDetail(it)) },
                    onBack = { vm.popScreen() },
                    topInset = insets,
                )
            }

            is Screen.ShowDetail -> {
                val show = library.showOrNull(current.showId)
                if (show == null) {
                    LaunchedEffect(current.showId) { vm.popScreen() }
                } else {
                    ShowScreen(
                        library = library,
                        show = show,
                        onBack = { vm.popScreen() },
                        onStep = { vm.step(show.id, it) },
                        onFinishSeason = { vm.finishSeason(show.id) },
                        onSetPosition = { vm.setPosition(show.id, it) },
                        onSetWatchedWith = { vm.setWatchedWith(show.id, it) },
                        onSetPlacement = { s, p -> vm.setPlacement(show.id, s, p) },
                        onSnooze = { vm.snooze(show.id); vm.popScreen() },
                        onAbandon = { vm.abandon(show.id); vm.popScreen() },
                        onFinish = { vm.finish(show.id); vm.popScreen() },
                        onReactivate = { vm.reactivate(show.id) },
                        onDelete = { vm.deleteShow(show.id) },
                        insets = insets,
                    )
                }
            }

            is Screen.Profiles -> {
                ProfilesScreen(
                    library = library,
                    syncState = syncState,
                    onBack = { vm.popScreen() },
                    onAddProfile = { serviceId, name -> vm.addProfile(serviceId, name) },
                    onRenameProfile = vm::renameProfile,
                    onSetDefault = vm::setDefaultProfile,
                    onDeleteProfile = vm::deleteProfile,
                    onAddService = { name -> vm.addService(name, nextServiceTint(library.services.size)) },
                    onDeleteService = vm::deleteService,
                    onAddPerson = { name -> vm.addPerson(name, nextPersonColour(library.people.size)) },
                    onDeletePerson = vm::deletePerson,
                    onSignIn = { signInLauncher.launch(vm.signInIntent()) },
                    onSignOut = vm::signOut,
                    onSyncNow = { vm.syncNow() },
                    onAbout = { vm.go(Screen.About) },
                    insets = insets,
                )
            }

            is Screen.About -> {
                AboutScreen(onBack = { vm.popScreen() }, insets = insets)
            }

            is Screen.Wishlist -> {
                LaunchedEffect(Unit) { vm.loadCountries(); vm.loadTravel() }
                WishlistScreen(
                    library = library,
                    wishlist = vm.wishlist(),
                    viewingCountry = viewingCountry,
                    countries = countries,
                    availability = availability,
                    watchableIn = vm::watchableIn,
                    popularHere = popularHere,
                    loading = travelLoading,
                    picking = countryPickerOpen,
                    onPickingChange = vm::setCountryPickerOpen,
                    onViewCountry = vm::viewCountry,
                    onSetHome = vm::setHomeCountry,
                    onStartWatching = vm::startWatching,
                    onOpenShow = { vm.go(Screen.ShowDetail(it)) },
                    onRemove = vm::deleteShow,
                    onAdd = { vm.go(Screen.Search) },
                    insets = insets,
                )
                BottomBar(
                    insets = insets,
                    selected = Screen.Wishlist,
                    onCouch = { vm.go(Screen.Couch) },
                    onIdeas = { vm.go(Screen.Ideas) },
                    onAdd = { vm.go(Screen.Search) },
                    onWishlist = { vm.go(Screen.Wishlist) },
                )
            }

            is Screen.Ideas -> {
                // Keyed on whether there is a list rather than on the library, so a
                // vote does not pull the seed out from under the eight rows you
                // were about to answer. A new seed is picked up when the list runs
                // out, and when you come back to the tab.
                LaunchedEffect(guesses.isEmpty()) { vm.loadGuesses() }
                SuggestScreen(
                    library = library,
                    seatedPeople = library.people.filter { it.id in seated },
                    seatedNames = Couch.nameList(library, seated.toList()),
                    result = vm.suggestions(),
                    guesses = guesses,
                    guessLoading = guessLoading,
                    onStart = vm::startWatching,
                    onOpenShow = { vm.go(Screen.ShowDetail(it)) },
                    onPickUp = { vm.go(Screen.ShowDetail(it)) },
                    onShelve = vm::abandon,
                    onVote = vm::vote,
                    onWishlist = vm::wishlistFromSuggestion,
                    onCatchUp = { vm.go(Screen.CatchUp) },
                    insets = insets,
                )
                BottomBar(
                    insets = insets,
                    selected = Screen.Ideas,
                    onCouch = { vm.go(Screen.Couch) },
                    onIdeas = { vm.go(Screen.Ideas) },
                    onAdd = { vm.go(Screen.Search) },
                    onWishlist = { vm.go(Screen.Wishlist) },
                )
            }

            is Screen.CatchUp -> {
                LaunchedEffect(Unit) { vm.loadCatchUp() }
                CatchUpScreen(
                    candidates = vm.catchUpCandidates(),
                    loading = catchUpLoading,
                    finishedCount = vm.suggestions().finishedCount,
                    verdicts = verdicts,
                    onVote = { item, loved -> vm.vote(item, loved, quiet = true) },
                    onClearVote = vm::clearVote,
                    onBack = { vm.popScreen() },
                    insets = insets,
                )
            }
        }

        draft?.let {
            AddSheet(
                library = library,
                draft = it,
                onUpdate = vm::updateDraft,
                onAddProfile = vm::addProfile,
                onCommit = { vm.commitAdd(toWishlist = false) },
                onWishlist = { vm.commitAdd(toWishlist = true) },
                onDismiss = vm::cancelAdd,
                bottomInset = insets,
            )
        }

        toast?.let { note ->
            Toast(note, insets) { vm.consumeToast() }
        }
    }
}

/** New services and people get a colour without anyone having to pick one. */
private val SERVICE_TINTS = listOf(
    "#E0574C", "#A88BE8", "#4FD98F", "#7FA8E8", "#86BEDD",
    "#D6D2CC", "#E8C15C", "#6C9BE0", "#DE8FB4", "#8FD9C4",
)
private val PERSON_COLOURS = listOf(
    "#E9A06A", "#F09FBC", "#6FC0DE", "#85CE9A", "#D9B36B", "#B49BE0",
)

private fun nextServiceTint(count: Int) = SERVICE_TINTS[count % SERVICE_TINTS.size]
private fun nextPersonColour(count: Int) = PERSON_COLOURS[count % PERSON_COLOURS.size]

// ------------------------------------------------------------------ bottom bar

@Composable
private fun BottomBar(
    insets: PaddingValues,
    selected: Screen,
    onCouch: () -> Unit,
    onIdeas: () -> Unit,
    onAdd: () -> Unit,
    onWishlist: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink.SurfaceSunk)
                .padding(bottom = insets.calculateBottomPadding())
                .height(72.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val onCouchTab = selected is Screen.Couch
            BarItem("Couch", selected = onCouchTab, onClick = onCouch) {
                Draw.Couch(22.dp, if (onCouchTab) Ink.Amber else Ink.Faint)
            }
            Spacer(Modifier.weight(1f))
            val onIdeasTab = selected is Screen.Ideas
            BarItem("Ideas", selected = onIdeasTab, onClick = onIdeas) {
                Draw.Star(22.dp, if (onIdeasTab) Ink.Amber else Ink.Faint)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Ink.Amber)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Draw.Plus(26.dp, Ink.Ground) }
            Spacer(Modifier.weight(1f))
            val onWishTab = selected is Screen.Wishlist
            BarItem("Wishlist", selected = onWishTab, onClick = onWishlist) {
                Draw.Shelf(22.dp, if (onWishTab) Ink.Amber else Ink.Faint)
            }
        }
    }
}

@Composable
private fun BarItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(width = 64.dp, height = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        icon()
        VGap(4.dp)
        BasicText(
            label,
            style = Type.Meta.copy(
                fontSize = 10.sp,
                color = if (selected) Ink.Amber else Ink.Faint,
            ),
        )
    }
}

/** A one-line confirmation that fades itself out. */
@Composable
private fun Toast(note: Note, insets: PaddingValues, onDone: () -> Unit) {
    // Five seconds when there is something to take back, less when the message is
    // only telling you what happened.
    val span = if (note.undo != null) 5000L else 2600L
    var left by remember(note) { mutableStateOf(1f) }

    LaunchedEffect(note) {
        val step = 50L
        var spent = 0L
        while (spent < span) {
            kotlinx.coroutines.delay(step)
            spent += step
            left = 1f - spent.toFloat() / span
        }
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = 96.dp + insets.calculateBottomPadding(), start = 14.dp, end = 14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(Ink.SurfaceLift)
                .border(1.dp, Ink.Line, RoundedCornerShape(13.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 15.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    note.message,
                    style = Type.BodyTight.copy(color = Ink.Text),
                    modifier = Modifier.weight(1f),
                )
                note.undo?.let { undo ->
                    HGap(8.dp)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { undo(); onDone() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        BasicText(
                            "UNDO",
                            style = Type.Eyebrow.copy(color = Ink.Amber, fontSize = 12.sp),
                        )
                    }
                }
            }
            if (note.undo != null) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Ink.Line)) {
                    Box(
                        Modifier
                            .fillMaxWidth(left.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(Ink.Amber)
                    )
                }
            }
        }
    }
}
