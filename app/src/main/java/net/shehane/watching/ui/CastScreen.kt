package net.shehane.watching.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shehane.watching.CastPane
import net.shehane.watching.data.Cast
import net.shehane.watching.data.Tmdb
import net.shehane.watching.model.Library
import net.shehane.watching.model.Show
import net.shehane.watching.ui.theme.Clip
import net.shehane.watching.ui.theme.Ink
import net.shehane.watching.ui.theme.Type

/**
 * Who was in what, and who else was in that.
 *
 * The screen is a row of columns you push onto the right. A person's column
 * lists what they worked on; tapping one of those titles adds a column of
 * everyone on it; tapping a name there adds that person's work; and so on, for
 * as long as the thread is worth pulling. Nothing opens a show, because the
 * point is to keep following the thread rather than to land somewhere.
 *
 * Two people at once answers a different question: what they worked on
 * together. That is the whole reason the picker holds two.
 */
@Composable
fun CastScreen(
    library: Library,
    query: String,
    hits: List<Tmdb.PersonHit>,
    searching: Boolean,
    picked: List<Tmdb.PersonHit>,
    panes: List<CastPane>,
    onQueryChanged: (String) -> Unit,
    onPick: (Tmdb.PersonHit) -> Unit,
    onDrop: (Int) -> Unit,
    onOpenTitle: (Int, Tmdb.SearchItem) -> Unit,
    onOpenPerson: (Int, Cast.Member) -> Unit,
    onAdd: (Tmdb.SearchItem) -> Unit,
    onBack: () -> Unit,
    topInset: PaddingValues,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Picking somebody is the end of typing. Leaving the keyboard up would cover
    // the columns that the pick just created.
    val keyboard = LocalSoftwareKeyboardController.current
    val pick = { person: Tmdb.PersonHit ->
        keyboard?.hide()
        onPick(person)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Ground)
            .padding(top = topInset.calculateTopPadding()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButtonPlain(onBack) { Draw.Chevron(22.dp, Ink.Text, pointsLeft = true) }
            BasicText("Cast search", style = Type.Screen.copy(fontSize = 22.sp))
        }

        // --- the field ---
        Box(Modifier.padding(horizontal = 18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Ink.Surface)
                    .border(1.5.dp, Ink.Amber, RoundedCornerShape(15.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Draw.Search(19.dp, Ink.Amber)
                HGap(10.dp)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        BasicText(
                            if (picked.isEmpty()) "Actor, director, composer"
                            else "And somebody else",
                            style = Type.Label.copy(fontSize = 17.sp, color = Ink.Ghost),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChanged,
                        singleLine = true,
                        textStyle = Type.Label.copy(fontSize = 17.sp, color = Ink.Text),
                        cursorBrush = SolidColor(Ink.Amber),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onQueryChanged("") },
                        contentAlignment = Alignment.Center,
                    ) { Draw.Cross(15.dp, Ink.Faint) }
                }
            }
        }

        // --- who we are following ---
        if (picked.isNotEmpty()) {
            VGap(10.dp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (person in picked) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(Ink.AmberWash)
                            .clickable { onDrop(person.id) }
                            .padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            person.name,
                            style = Type.BodyTight.copy(color = Ink.Amber),
                            maxLines = 1,
                            overflow = Clip,
                        )
                        HGap(6.dp)
                        Draw.Cross(12.dp, Ink.Amber)
                    }
                }
                if (picked.size == 1) {
                    BasicText("add a second for what they share", style = Type.Meta)
                }
            }
        }

        VGap(12.dp)

        when {
            // Searching takes the whole screen: the columns are no use while you
            // are still deciding whose thread to pull.
            query.isNotBlank() -> PeopleResults(hits, searching, pick)
            panes.isEmpty() -> Hint(picked.isEmpty())
            else -> Cascade(library, panes, onOpenTitle, onOpenPerson, onAdd)
        }
    }
}

@Composable
private fun Hint(nobodyPicked: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        EmptyNote(
            if (nobodyPicked) {
                "Search for anyone who worked on something: actors, directors, " +
                    "composers. Their work appears as a column. Tap a title to see " +
                    "who else was on it, then tap a name to follow that person, and " +
                    "keep going. Swipe left and right to move between columns."
            } else {
                "Nothing to show for them."
            }
        )
    }
}

@Composable
private fun PeopleResults(
    hits: List<Tmdb.PersonHit>,
    searching: Boolean,
    onPick: (Tmdb.PersonHit) -> Unit,
) {
    if (hits.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            EmptyNote(if (searching) "Looking…" else "Nobody by that name.")
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(hits, key = { it.id }) { person ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(person) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Face(person.name, person.profilePath, 44.dp)
                HGap(12.dp)
                Column(Modifier.weight(1f)) {
                    BasicText(person.name, style = Type.ShowTitleSm, maxLines = 1, overflow = Clip)
                    VGap(3.dp)
                    BasicText(
                        listOf(person.role, person.placing)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = Type.Meta,
                        maxLines = 1,
                        overflow = Clip,
                    )
                }
            }
            Divider(Ink.LineSoft.copy(alpha = 0.5f))
        }
    }
}

/**
 * The columns themselves.
 *
 * A LazyRow because the thread can run to a dozen columns and only two or three
 * are ever on screen. The newest column is scrolled to as it arrives, since the
 * tap that created it was on the far left of it.
 */
@Composable
private fun Cascade(
    library: Library,
    panes: List<CastPane>,
    onOpenTitle: (Int, Tmdb.SearchItem) -> Unit,
    onOpenPerson: (Int, Cast.Member) -> Unit,
    onAdd: (Tmdb.SearchItem) -> Unit,
) {
    val scroll = rememberLazyListState()
    LaunchedEffect(panes.size) {
        if (panes.isNotEmpty()) runCatching { scroll.animateScrollToItem(panes.lastIndex) }
    }

    LazyRow(
        state = scroll,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(panes, key = { i, _ -> i }) { index, pane ->
            Column(
                Modifier
                    .width(252.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(15.dp))
                    .background(Ink.Surface)
                    .border(1.dp, Ink.Line, RoundedCornerShape(15.dp)),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
                    BasicText(pane.heading, style = Type.ShowTitleSm, maxLines = 2, overflow = Clip)
                    pane.subheading?.let {
                        VGap(3.dp)
                        BasicText(it, style = Type.Meta, maxLines = 2, overflow = Clip)
                    }
                }
                Divider(Ink.Line)

                when {
                    pane.loading -> Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        BasicText("Looking…", style = Type.Meta)
                    }

                    pane is CastPane.Work -> WorkList(library, pane, index, onOpenTitle, onAdd)
                    pane is CastPane.People -> PeopleList(pane, index, onOpenPerson)
                }
            }
        }
    }
}

@Composable
private fun WorkList(
    library: Library,
    pane: CastPane.Work,
    index: Int,
    onOpenTitle: (Int, Tmdb.SearchItem) -> Unit,
    onAdd: (Tmdb.SearchItem) -> Unit,
) {
    if (pane.rows.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            BasicText(
                if (pane.isOverlap) "Nothing the two of them both worked on."
                else "Nothing listed.",
                style = Type.Meta,
            )
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
        items(pane.rows, key = { it.item.id.toString() + it.item.medium }) { row ->
            val tracked = library.shows.any {
                it.tmdbId == row.item.id && it.medium == row.item.medium
            }
            val chosen = pane.selected == row.item.id.toString() + row.item.medium

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (chosen) Modifier.background(Ink.AmberWash) else Modifier)
                    .clickable { onOpenTitle(index, row.item) }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Poster(row.item.name, row.item.posterPath, 32.dp, 46.dp, corner = 5.dp)
                HGap(9.dp)
                Column(Modifier.weight(1f)) {
                    BasicText(
                        row.item.name,
                        style = Type.Label.copy(
                            fontSize = 13.sp,
                            color = if (chosen) Ink.Amber else Ink.Text,
                        ),
                        maxLines = 2,
                        overflow = Clip,
                    )
                    VGap(2.dp)
                    BasicText(
                        buildString {
                            row.year?.let { append(it); append(" · ") }
                            append(if (row.item.isMovie) "Film" else "TV")
                            if (tracked) append(" · tracked")
                        },
                        style = Type.Meta.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = Clip,
                    )
                    VGap(2.dp)
                    BasicText(
                        row.role,
                        style = Type.Meta.copy(fontSize = 10.5.sp, color = Ink.Cool),
                        maxLines = 2,
                        overflow = Clip,
                    )
                }
                // Tapping the row follows the thread, so adding needs its own
                // target. Small on purpose: following is the common move.
                if (!tracked) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(1.dp, Ink.Line, CircleShape)
                            .clickable { onAdd(row.item) },
                        contentAlignment = Alignment.Center,
                    ) { Draw.Plus(12.dp, Ink.Muted) }
                }
            }
        }
    }
}

@Composable
private fun PeopleList(
    pane: CastPane.People,
    index: Int,
    onOpenPerson: (Int, Cast.Member) -> Unit,
) {
    if (pane.members.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            BasicText("Nobody listed for this one.", style = Type.Meta)
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
        items(pane.members, key = { it.id.toString() + it.role }) { member ->
            val chosen = pane.selected == member.id.toString()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (chosen) Modifier.background(Ink.AmberWash) else Modifier)
                    .clickable { onOpenPerson(index, member) }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Face(member.name, member.profilePath, 34.dp)
                HGap(9.dp)
                Column(Modifier.weight(1f)) {
                    BasicText(
                        member.name,
                        style = Type.Label.copy(
                            fontSize = 13.sp,
                            color = if (chosen) Ink.Amber else Ink.Text,
                        ),
                        maxLines = 1,
                        overflow = Clip,
                    )
                    VGap(2.dp)
                    BasicText(
                        member.role,
                        style = Type.Meta.copy(
                            fontSize = 10.5.sp,
                            color = if (member.isCast) Ink.Faint else Ink.Cool,
                        ),
                        maxLines = 2,
                        overflow = Clip,
                    )
                }
            }
        }
    }
}

/** A round portrait, falling back to the same tinted initial a poster uses. */
@Composable
private fun Face(name: String, profilePath: String?, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape)) {
        Poster(name, profilePath, size, size, corner = 0.dp)
    }
}
