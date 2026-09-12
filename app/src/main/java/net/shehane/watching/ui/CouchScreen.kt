package net.shehane.watching.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shehane.watching.data.Clock
import net.shehane.watching.data.Couch
import net.shehane.watching.data.LibraryStore
import net.shehane.watching.model.Library
import net.shehane.watching.model.Show
import net.shehane.watching.ui.theme.Clip
import net.shehane.watching.ui.theme.Ink
import net.shehane.watching.ui.theme.Type
import kotlin.math.roundToInt

/** How far you drag before each demotion arms itself. */
private const val SNOOZE_AT = -70f
private const val SHELVE_AT = -150f

@Composable
fun CouchScreen(
    library: Library,
    seated: Set<String>,
    result: Couch.Result,
    onToggleSeat: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onBump: (String) -> Unit,
    onSnooze: (String) -> Unit,
    onAbandon: (String) -> Unit,
    onReactivate: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    insets: androidx.compose.foundation.layout.PaddingValues,
) {
    Box(Modifier.fillMaxSize().background(Ink.Ground)) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // The app draws edge to edge, so the list has to keep itself clear of
            // the status bar at the top and the nav bar plus the button at the foot.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = insets.calculateTopPadding() + 8.dp,
                bottom = 96.dp + insets.calculateBottomPadding(),
            ),
        ) {
            item("header") {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = "Who's on the couch?",
                            style = Type.Screen,
                            modifier = Modifier.weight(1f),
                        )
                        RoundButtonPlain(onClick = onSearch) { Draw.Search() }
                        RoundButtonPlain(onClick = onSettings) { Draw.Gear() }
                    }
                    VGap(12.dp)
                    CouchBar(library, seated, onToggleSeat)
                    VGap(10.dp)
                    BasicText(text = summaryLine(library, seated, result), style = Type.Body)
                    VGap(10.dp)
                    Divider(Ink.LineSoft)
                    VGap(14.dp)
                }
            }

            if (result.primary.isNotEmpty()) {
                item("primary-header") {
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) {
                        SectionHeader(
                            label = (if (seated.size == 1) "ON YOUR OWN" else "ALL OF YOU") +
                                " · ${result.primary.size}",
                            colour = Ink.Amber,
                        )
                    }
                }
                items(result.primary, key = { it.show.id }) { match ->
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 4.5.dp)) {
                        SwipeToDemote(
                            onSnooze = { onSnooze(match.show.id) },
                            onAbandon = { onAbandon(match.show.id) },
                        ) {
                            FullCard(library, match.show, onOpen = { onOpenShow(match.show.id) }, onBump = { onBump(match.show.id) })
                        }
                    }
                }
            }

            if (result.secondary.isNotEmpty()) {
                item("secondary-header") {
                    Box(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp)) {
                        SectionHeader(label = "SOME OF YOU · ${result.secondary.size}", colour = Ink.Cool)
                    }
                }
                items(result.secondary, key = { it.show.id }) { match ->
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                        SwipeToDemote(
                            onSnooze = { onSnooze(match.show.id) },
                            onAbandon = { onAbandon(match.show.id) },
                        ) {
                            PartialCard(library, match, onOpen = { onOpenShow(match.show.id) })
                        }
                    }
                }
            }

            if (result.primary.isEmpty() && result.secondary.isEmpty()) {
                item("empty") {
                    EmptyNote(
                        if (seated.isEmpty()) "Nobody is seated. Tap a face above."
                        else if (library.shows.none { it.isActive }) "Nothing on the list yet. The button below adds one."
                        else "Nothing here that all of you are part-way through."
                    )
                }
            }

            // --- the diminished tiers ---

            if (result.snoozed.isNotEmpty()) {
                item("snoozed-header") {
                    Box(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)) {
                        SectionHeader(
                            label = "NOT IN THE MOOD · ${result.snoozed.size}",
                            colour = Ink.Faint,
                            leading = { Draw.Moon(13.dp, Ink.Faint, Ink.Ground) },
                        )
                    }
                }
                items(result.snoozed, key = { "s-${it.id}" }) { show ->
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 3.5.dp)) {
                        SnoozedRow(show, onOpen = { onOpenShow(show.id) }, onRestore = { onReactivate(show.id) })
                    }
                }
            }

            if (result.abandoned.isNotEmpty()) {
                item("abandoned-header") {
                    Box(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp)) {
                        SectionHeader(
                            label = "DONE WITH · ${result.abandoned.size}",
                            colour = Ink.Ghost,
                            leading = { Draw.Cross(13.dp, Ink.Ghost, circled = true) },
                        )
                    }
                }
                items(result.abandoned, key = { "a-${it.id}" }) { show ->
                    Box(Modifier.padding(horizontal = 18.dp)) {
                        AbandonedRow(library, show, onOpen = { onOpenShow(show.id) })
                    }
                }
            }
        }

        // A fade so the list reads as continuing under the bar rather than ending.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 84.dp + insets.calculateBottomPadding())
                .height(40.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.Ground))),
        )
    }
}

// ------------------------------------------------------------------ couch bar

@Composable
private fun CouchBar(library: Library, seated: Set<String>, onToggle: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (person in library.people) {
            val on = person.id in seated
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggle(person.id) }
                    .padding(vertical = 2.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (on) {
                        Box(
                            Modifier
                                .size(54.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .border(1.5.dp, hexColor(person.color), androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                    Avatar(person, size = 46.dp, seated = on)
                }
                VGap(6.dp)
                BasicText(
                    text = person.name,
                    style = Type.Meta.copy(
                        fontSize = 11.sp,
                        color = if (on) Ink.Text else Ink.Ghost,
                    ),
                    maxLines = 1,
                    overflow = Clip,
                )
            }
        }
    }
}

private fun summaryLine(library: Library, seated: Set<String>, result: Couch.Result): String {
    if (seated.isEmpty()) return "Nobody seated. Tap a face above and the list fills in."
    val who = Couch.nameList(library, library.people.map { it.id }.filter { it in seated })

    // Counting to zero twice reads badly on a fresh install, and the empty note
    // below says what to do about it.
    if (result.primary.isEmpty() && result.secondary.isEmpty()) {
        return if (seated.size == 1) "$who, on your own." else "$who, on the couch."
    }
    return "$who — ${result.primary.size} you are all part-way through, " +
        "${result.secondary.size} that only some of you are on."
}

// ---------------------------------------------------------------------- cards

@Composable
private fun FullCard(library: Library, show: Show, onOpen: () -> Unit, onBump: () -> Unit) {
    val people = show.watchedWith.mapNotNull { library.personOrNull(it) }
    val me = library.personOrNull(LibraryStore.ME_ID)
    val everyone = (listOfNotNull(me) + people)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Ink.Surface)
            .border(1.dp, Ink.Line, RoundedCornerShape(15.dp))
            .clickable(onClick = onOpen)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(show.title, show.posterPath, 52.dp, 72.dp, showTitle = true)
        HGap(12.dp)
        Column(Modifier.weight(1f)) {
            BasicText(show.title, style = Type.ShowTitle, maxLines = 1, overflow = Clip)
            VGap(5.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    show.position.toString(),
                    style = Type.BodyTight.copy(
                        color = Ink.Amber,
                        fontSize = 12.5.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    ),
                )
                HGap(8.dp)
                ServiceProfileBadge(
                    library.serviceOrNull(show.serviceId),
                    library.profileOrNull(show.serviceId, show.profileId),
                )
            }
            VGap(5.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarRow(everyone, 17.dp, Ink.Surface)
                HGap(10.dp)
                BasicText(Clock.ago(show.lastWatchedAt ?: show.addedAt), style = Type.Meta)
            }
        }
        HGap(8.dp)
        RoundButton(onClick = onBump) {
            BasicText(
                "+1",
                style = Type.BodyTight.copy(
                    color = Ink.Amber,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun PartialCard(library: Library, match: Couch.Match, onOpen: () -> Unit) {
    val show = match.show
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .border(1.dp, Ink.LineSoft, RoundedCornerShape(13.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(show.title, show.posterPath, 36.dp, 50.dp, corner = 6.dp)
        HGap(11.dp)
        Column(Modifier.weight(1f)) {
            BasicText(
                show.title,
                style = Type.ShowTitleSm.copy(color = Ink.Text.copy(alpha = 0.88f)),
                maxLines = 1,
                overflow = Clip,
            )
            VGap(4.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(show.position.short, style = Type.Meta.copy(fontSize = 11.5.sp, color = Ink.Muted))
                HGap(7.dp)
                ServiceProfileBadge(
                    library.serviceOrNull(show.serviceId),
                    library.profileOrNull(show.serviceId, show.profileId),
                    small = true,
                )
            }
            VGap(4.dp)
            // Two different reasons to be here, and the difference matters: someone
            // left behind is a stronger objection than someone watching along.
            BasicText(
                text = when {
                    match.absent.isNotEmpty() ->
                        "${Couch.nameList(library, match.absent)} would fall behind"
                    else ->
                        "for ${Couch.nameList(library, match.matched)} — not ${Couch.nameList(library, match.missing)}"
                },
                style = Type.Meta.copy(color = Ink.Cool),
                maxLines = 1,
                overflow = Clip,
            )
        }
        HGap(6.dp)
        Draw.Chevron(16.dp, Ink.Ghost)
    }
}

/** Half the height, no artwork, and it comes back on its own. */
@Composable
private fun SnoozedRow(show: Show, onOpen: () -> Unit, onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Ink.SurfaceSunk)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 8.dp, height = 12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(net.shehane.watching.ui.theme.posterTintFor(show.title).second.copy(alpha = 0.55f)),
        )
        HGap(10.dp)
        BasicText(
            show.title,
            style = Type.ShowTitleXs.copy(fontSize = 16.sp),
            maxLines = 1,
            overflow = Clip,
            modifier = Modifier.weight(1f),
        )
        BasicText(Clock.until(show.snoozeUntil), style = Type.Meta.copy(color = Ink.Ghost))
        RoundButtonPlain(onClick = onRestore, size = 44.dp) { Draw.Undo(16.dp, Ink.Muted) }
    }
}

/** The smallest row on the screen. Still searchable, which is why it is kept. */
@Composable
private fun AbandonedRow(library: Library, show: Show, onOpen: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(onClick = onOpen)
                .alpha(0.45f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(show.title, style = Type.ShowTitleXs.copy(fontSize = 14.sp, color = Ink.Text), maxLines = 1, overflow = Clip)
            HGap(10.dp)
            BasicText(
                if (show.isFinished) "finished" else "stopped ${show.position.short}",
                style = Type.Meta,
            )
            Spacer(Modifier.weight(1f))
            val names = Couch.nameList(library, show.watchedWith)
            if (names.isNotEmpty()) {
                BasicText(names, style = Type.Meta.copy(fontSize = 10.sp), maxLines = 1, overflow = Clip)
            }
        }
        Divider(Ink.LineSoft.copy(alpha = 0.6f))
    }
}

// -------------------------------------------------------------------- gesture

/**
 * Swipe left to demote. The first zone snoozes, dragging further shelves, so the
 * two outcomes differ by how far you commit rather than by which button you find.
 */
@Composable
private fun SwipeToDemote(
    onSnooze: () -> Unit,
    onAbandon: () -> Unit,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    val settled by animateFloatAsState(targetValue = offset, label = "swipe")
    val armed = when {
        settled <= SHELVE_AT -> 2
        settled <= SNOOZE_AT -> 1
        else -> 0
    }

    Box(Modifier.fillMaxWidth()) {
        // The rail behind the card, revealed as it moves.
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(15.dp)),
            horizontalArrangement = Arrangement.End,
        ) {
            RailZone(
                label = "Not in\nthe mood",
                background = if (armed >= 1) Ink.Amber.copy(alpha = 0.85f) else Ink.AmberWash,
            ) { Draw.Moon(19.dp, if (armed >= 1) Ink.Ground else Ink.Amber, if (armed >= 1) Ink.Amber else Ink.AmberWash) }
            RailZone(
                label = "Won't\nfinish",
                background = if (armed >= 2) Ink.Rust else Ink.RustDim,
            ) { Draw.Cross(19.dp, if (armed >= 2) Ink.Ground else Ink.Rust) }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(settled.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                offset <= SHELVE_AT -> { onAbandon(); offset = 0f }
                                offset <= SNOOZE_AT -> { onSnooze(); offset = 0f }
                                else -> offset = 0f
                            }
                        },
                        onDragCancel = { offset = 0f },
                    ) { _, delta ->
                        // Left only, and never further than the rail is wide.
                        offset = (offset + delta).coerceIn(-190f, 0f)
                    }
                },
        ) {
            content()
        }
    }
}

@Composable
private fun RailZone(label: String, background: Color, icon: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(66.dp)
            .fillMaxHeight()
            .background(background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        VGap(6.dp)
        BasicText(
            label,
            style = Type.Meta.copy(
                fontSize = 9.sp,
                color = Ink.Text,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 11.sp,
            ),
        )
    }
}

/** A borderless 44dp tap target, used in headers where a ring would be noise. */
@Composable
fun RoundButtonPlain(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Suppress("unused")
private val unusedContentScale = ContentScale.Crop
