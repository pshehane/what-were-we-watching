package net.shehane.watching.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import net.shehane.watching.data.Clock
import net.shehane.watching.data.Suggest
import net.shehane.watching.data.Tmdb
import net.shehane.watching.model.Library
import net.shehane.watching.model.Person
import net.shehane.watching.model.Show
import net.shehane.watching.ui.theme.Clip
import net.shehane.watching.ui.theme.Ink
import net.shehane.watching.ui.theme.Type

/**
 * What to put on when nothing is already part-way through.
 *
 * The sections are ordered by how much guessing each one does, and they say so.
 * Ready tonight and Stalled are both built entirely from the library, so they are
 * facts about your own shelf. Only the last section is a guess, and its heading
 * names the show the guess came from.
 */
@Composable
fun SuggestScreen(
    library: Library,
    seatedPeople: List<Person>,
    seatedNames: String,
    result: Suggest.Result,
    guesses: List<Tmdb.SearchItem>,
    guessLoading: Boolean,
    onStart: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onPickUp: (String) -> Unit,
    onShelve: (String) -> Unit,
    onVote: (Tmdb.SearchItem, Boolean) -> Unit,
    onWishlist: (Tmdb.SearchItem) -> Unit,
    onCatchUp: () -> Unit,
    insets: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = insets.calculateTopPadding() + 8.dp,
            bottom = 108.dp + insets.calculateBottomPadding(),
        ),
    ) {
        item("head") {
            Column {
                BasicText("What should we watch?", style = Type.Screen)
                if (seatedPeople.isNotEmpty()) {
                    VGap(11.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarRow(seatedPeople, 26.dp, Ink.Ground)
                        HGap(12.dp)
                        BasicText(
                            seatedNames,
                            style = Type.BodyTight.copy(color = Ink.Muted),
                            maxLines = 1,
                            overflow = Clip,
                        )
                    }
                }
                VGap(10.dp)
                Divider(Ink.Line)
                VGap(14.dp)
            }
        }

        // --- 1. no guessing at all ---

        if (result.ready.isNotEmpty()) {
            item("ready-head") {
                Column {
                    SectionHeader("READY TONIGHT · ${result.ready.size}", Ink.Amber)
                    VGap(7.dp)
                    BasicText(
                        "On your wishlist, on a service you pay for.",
                        style = Type.Meta,
                    )
                    VGap(10.dp)
                }
            }
            items(result.ready, key = { "r-" + it.show.id }) { ready ->
                ReadyCard(library, ready, onStart = { onStart(ready.show.id) }, onOpen = { onOpenShow(ready.show.id) })
                VGap(9.dp)
            }
        }

        // --- 2. still no guessing: your own shows, waiting on a decision ---

        if (result.stalled.isNotEmpty()) {
            item("stalled-head") {
                Column {
                    VGap(6.dp)
                    SectionHeader("STALLED · ${result.stalled.size}", Ink.Cool)
                    VGap(10.dp)
                }
            }
            items(result.stalled, key = { "s-" + it.show.id }) { stalled ->
                StalledCard(
                    stalled = stalled,
                    onOpen = { onOpenShow(stalled.show.id) },
                    onPickUp = { onPickUp(stalled.show.id) },
                    onShelve = { onShelve(stalled.show.id) },
                )
                VGap(9.dp)
            }
        }

        // --- 3. the only guess on the screen, and it says where it came from ---

        val seed = result.seed
        if (!result.enoughToGuess || seed == null) {
            item("cold") {
                Column {
                    VGap(6.dp)
                    ColdStart(result.finishedCount, onCatchUp)
                }
            }
        } else {
            item("guess-head") {
                Column {
                    VGap(6.dp)
                    SectionHeader("BECAUSE YOU FINISHED ${seed.title.uppercase()}", Ink.Faint)
                    VGap(7.dp)
                    BasicText(
                        "Swipe right for loved it, left for didn't. Both clear the row " +
                            "and both count, so an old favourite is as useful as a no.",
                        style = Type.Meta.copy(lineHeight = 15.sp),
                    )
                    VGap(10.dp)
                }
            }
            if (guesses.isEmpty()) {
                item("guess-empty") {
                    EmptyNote(
                        if (guessLoading) "Asking TMDB what sits next to it…"
                        else "TMDB had nothing to put next to it."
                    )
                }
            }
            items(guesses, key = { "g-" + it.id }) { item ->
                SwipeToVote(
                    key = item.id.toString(),
                    onLove = { onVote(item, true) },
                    onNotLove = { onVote(item, false) },
                ) {
                    GuessRow(item, onWishlist = { onWishlist(item) })
                }
                Divider()
            }
        }
    }
}

// ------------------------------------------------------------------ sections

@Composable
private fun ReadyCard(
    library: Library,
    ready: Suggest.Ready,
    onStart: () -> Unit,
    onOpen: () -> Unit,
) {
    val show = ready.show
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Ink.Surface)
            .border(1.dp, Ink.Line, RoundedCornerShape(15.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(show.title, show.posterPath, 48.dp, 70.dp)
        HGap(12.dp)
        Column(Modifier.weight(1f)) {
            BasicText(show.title, style = Type.ShowTitleSm, maxLines = 2, overflow = Clip)
            VGap(5.dp)
            library.serviceOrNull(show.serviceId)?.let { service ->
                ServiceProfileBadge(
                    service,
                    library.profileOrNull(show.serviceId, show.profileId),
                    small = true,
                )
            }
            VGap(4.dp)
            BasicText(
                if (ready.exact) "Everyone here is on it" else "Wishlisted ${Clock.ago(show.addedAt)}",
                style = Type.Meta,
                maxLines = 1,
                overflow = Clip,
            )
        }
        HGap(8.dp)
        PillButton("Start", onStart, filled = true, height = 38.dp)
    }
}

@Composable
private fun StalledCard(
    stalled: Suggest.Stalled,
    onOpen: () -> Unit,
    onPickUp: () -> Unit,
    onShelve: () -> Unit,
) {
    val show = stalled.show
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .border(1.dp, Ink.LineSoft, RoundedCornerShape(13.dp))
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Poster(show.title, show.posterPath, 34.dp, 49.dp, corner = 6.dp)
            HGap(11.dp)
            Column(Modifier.weight(1f)) {
                BasicText(show.title, style = Type.ShowTitleSm, maxLines = 1, overflow = Clip)
                VGap(3.dp)
                BasicText(
                    "${show.position.short} — last watched ${Clock.ago(show.lastWatchedAt ?: show.addedAt)}",
                    style = Type.Meta,
                    maxLines = 1,
                    overflow = Clip,
                )
            }
        }
        VGap(9.dp)
        Row(Modifier.fillMaxWidth()) {
            PillButton("Pick it back up", onPickUp, Modifier.weight(1f), height = 38.dp)
            HGap(8.dp)
            PillButton("Shelve it", onShelve, Modifier.weight(1f), tint = Ink.Faint, height = 38.dp)
        }
    }
}

@Composable
private fun GuessRow(item: Tmdb.SearchItem, onWishlist: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.Ground)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(item.name, item.posterPath, 34.dp, 49.dp, corner = 6.dp)
        HGap(10.dp)
        Column(Modifier.weight(1f)) {
            BasicText(item.name, style = Type.ShowTitleSm, maxLines = 1, overflow = Clip)
            VGap(3.dp)
            BasicText(
                item.year?.toString() ?: "TV series",
                style = Type.Meta,
                maxLines = 1,
                overflow = Clip,
            )
        }
        HGap(8.dp)
        // Only the wishlist needs a button here. The two verdicts are the swipes,
        // and the rail names them the moment you start dragging.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.dp, Ink.AmberDim, CircleShape)
                .clickable(onClick = onWishlist),
            contentAlignment = Alignment.Center,
        ) { Draw.Plus(17.dp, Ink.Amber) }
    }
}

@Composable
private fun ColdStart(finished: Int, onCatchUp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Ink.Line, RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        BasicText("Not enough to go on yet", style = Type.ShowTitle)
        VGap(9.dp)
        BasicText(
            "Guessing from $finished finished ${if (finished == 1) "show" else "shows"} " +
                "would only be guessing. ${Suggest.ENOUGH_FINISHED} is about where this " +
                "starts being worth reading.",
            style = Type.Body,
        )
        VGap(16.dp)
        Row(Modifier.fillMaxWidth()) {
            repeat(Suggest.ENOUGH_FINISHED) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i < finished) Ink.Amber else Ink.Line)
                )
                if (i < Suggest.ENOUGH_FINISHED - 1) HGap(5.dp)
            }
        }
        VGap(9.dp)
        BasicText(
            "$finished finished. Voting on a show you have already seen counts straight away.",
            style = Type.Meta,
        )
        VGap(14.dp)
        PillButton("Mark what you have already seen", onCatchUp, Modifier.fillMaxWidth(), height = 42.dp)
    }
}

// -------------------------------------------------------------------- gesture

private val VOTE_AT_DP = 78.dp
private val MAX_DRAG_DP = 128.dp

/**
 * Drag right for loved it, left for didn't. Both resolve the row, because a
 * suggestion can be a perfectly fair one and still be finished business.
 *
 * The thresholds are in dp rather than pixels. In raw pixels the same number is a
 * deliberate drag on one phone and a flick on another, and the consequence here is
 * a show filed without being asked.
 */
@Composable
private fun SwipeToVote(
    key: String,
    onLove: () -> Unit,
    onNotLove: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val voteAt = with(density) { VOTE_AT_DP.toPx() }
    val maxDrag = with(density) { MAX_DRAG_DP.toPx() }

    // Keyed on the row. Without the key, a resolved row leaves its offset behind
    // in the slot and whichever row moves up looks already swiped.
    var offset by remember(key) { mutableFloatStateOf(0f) }
    val settled by animateFloatAsState(targetValue = offset, label = "vote")
    val armed = settled >= voteAt || settled <= -voteAt

    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        settled > 0f -> if (armed) Ink.Rust else Ink.RustDim
                        settled < 0f -> if (armed) Ink.Cool.copy(alpha = 0.75f) else Ink.Cool.copy(alpha = 0.28f)
                        else -> Color.Transparent
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (settled >= 0f) Arrangement.Start else Arrangement.End,
        ) {
            if (settled > 0f) {
                RailLabel("Loved it") { Draw.Heart(20.dp, Ink.Text, filled = armed) }
            } else if (settled < 0f) {
                RailLabel("Didn't love it") { Draw.ThumbDown(20.dp, Ink.Text, filled = armed) }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(settled.roundToInt(), 0) }
                .pointerInput(key) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                offset >= voteAt -> { onLove(); offset = 0f }
                                offset <= -voteAt -> { onNotLove(); offset = 0f }
                                else -> offset = 0f
                            }
                        },
                        onDragCancel = { offset = 0f },
                    ) { _, delta ->
                        offset = (offset + delta).coerceIn(-maxDrag, maxDrag)
                    }
                },
        ) {
            content()
        }
    }
}

@Composable
private fun RailLabel(label: String, icon: @Composable () -> Unit) {
    Column(
        modifier = Modifier.width(92.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        VGap(5.dp)
        BasicText(
            label,
            style = Type.Meta.copy(
                fontSize = 9.5.sp,
                color = Ink.Text,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp,
            ),
        )
    }
}
