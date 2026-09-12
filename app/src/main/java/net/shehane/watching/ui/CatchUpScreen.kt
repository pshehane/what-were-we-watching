package net.shehane.watching.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shehane.watching.data.Suggest
import net.shehane.watching.data.Tmdb
import net.shehane.watching.ui.theme.Clip
import net.shehane.watching.ui.theme.Ink
import net.shehane.watching.ui.theme.Type

/**
 * The fast way to say what we have already watched, and the answer to a library
 * with no history in it.
 *
 * Most tiles get no tap at all, which is the point: you are looking for the few
 * you have seen, not answering every one. Either verdict files the show as
 * finished without asking for a service, a profile or an episode number, because
 * nobody is going to supply those for something they watched years ago.
 */
@Composable
fun CatchUpScreen(
    candidates: List<Tmdb.SearchItem>,
    loading: Boolean,
    finishedCount: Int,
    /** tmdb id to verdict, for the ones answered since this screen opened. */
    verdicts: Map<Int, Boolean>,
    onVote: (Tmdb.SearchItem, Boolean) -> Unit,
    onBack: () -> Unit,
    insets: PaddingValues,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tileWidth: Dp = (maxWidth - 36.dp - 21.dp) / 3
        val tileHeight: Dp = tileWidth * 1.46f
        val rows = candidates.chunked(3)
        val loved = verdicts.values.count { it }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = insets.calculateTopPadding() + 8.dp,
                bottom = 32.dp + insets.calculateBottomPadding(),
            ),
        ) {
            item("head") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundButtonPlain(onBack) { Draw.Chevron(20.dp, Ink.Text, pointsLeft = true) }
                        HGap(4.dp)
                        BasicText("Have you seen these?", style = Type.Screen.copy(fontSize = 27.sp))
                    }
                    VGap(11.dp)
                    BasicText(
                        "Say which of these you have already watched, and whether you loved " +
                            "it. Either answer files it as finished, so you never pick a " +
                            "service or an episode for it.",
                        style = Type.Body,
                    )
                    VGap(16.dp)
                    Progress(finishedCount)
                    VGap(18.dp)
                    SectionHeader("POPULAR ON WHAT YOU PAY FOR", Ink.Faint)
                    VGap(12.dp)
                }
            }

            if (rows.isEmpty()) {
                item("empty") {
                    EmptyNote(
                        if (loading) "Looking up what is on the services you have…"
                        else "Nothing came back. Either the services on the setup screen do " +
                            "not match anything TMDB knows, or you have already recorded all " +
                            "of it."
                    )
                }
            }

            items(rows, key = { row -> "row-" + row.first().id }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.5.dp)) {
                    for (item in row) {
                        Tile(
                            item = item,
                            width = tileWidth,
                            height = tileHeight,
                            verdict = verdicts[item.id],
                            onVote = { loved2 -> onVote(item, loved2) },
                        )
                    }
                    // Keep the last row's tiles the same width as every other row.
                    repeat(3 - row.size) { Box(Modifier.width(tileWidth)) }
                }
                VGap(13.dp)
            }

            if (loved > 0) {
                item("tally") {
                    Column {
                        VGap(6.dp)
                        BasicText(
                            "$loved marked as loved on this screen. They count straight away.",
                            style = Type.Meta,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Progress(finished: Int) {
    val target = Suggest.ENOUGH_FINISHED
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(target) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i < finished) Ink.Rust else Ink.Line)
                    )
                }
            }
            HGap(10.dp)
            BasicText("${minOf(finished, target)} of $target", style = Type.Meta)
        }
        VGap(7.dp)
        BasicText(
            if (finished >= target) "Enough to go on. Anything more only sharpens it."
            else "${target - finished} more and the suggestions start working.",
            style = Type.Meta,
        )
    }
}

@Composable
private fun Tile(
    item: Tmdb.SearchItem,
    width: Dp,
    height: Dp,
    verdict: Boolean?,
    onVote: (Boolean) -> Unit,
) {
    Column(Modifier.width(width)) {
        Box(
            modifier = Modifier
                .size(width = width, height = height)
                // Answered and not loved dims in place rather than disappearing, so
                // a mistap is obvious while the screen is still open.
                .alpha(if (verdict == false) 0.42f else 1f)
                .then(
                    if (verdict == true) {
                        Modifier.clip(RoundedCornerShape(8.dp)).border(2.dp, Ink.Rust, RoundedCornerShape(8.dp))
                    } else Modifier
                ),
        ) {
            Poster(item.name, item.posterPath, width, height)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(31.dp)
                    .align(Alignment.BottomCenter)
                    .background(Ink.Ground.copy(alpha = 0.72f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (verdict == null) Arrangement.SpaceAround else Arrangement.Center,
            ) {
                when (verdict) {
                    true -> Draw.Heart(19.dp, Ink.Rust, filled = true)
                    false -> Draw.ThumbDown(19.dp, Ink.Cool, filled = false)
                    null -> {
                        Box(
                            Modifier.size(31.dp).clickable { onVote(true) },
                            contentAlignment = Alignment.Center,
                        ) { Draw.Heart(19.dp, Ink.Text) }
                        Box(
                            Modifier.size(31.dp).clickable { onVote(false) },
                            contentAlignment = Alignment.Center,
                        ) { Draw.ThumbDown(19.dp, Ink.Text) }
                    }
                }
            }
        }
        VGap(6.dp)
        BasicText(item.name, style = Type.ShowTitleXs.copy(color = Ink.Text), maxLines = 2, overflow = Clip)
    }
}
