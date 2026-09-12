package net.shehane.watching.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shehane.watching.model.Library
import net.shehane.watching.model.Show
import net.shehane.watching.data.Tmdb
import net.shehane.watching.ui.theme.Clip
import net.shehane.watching.ui.theme.Ink
import net.shehane.watching.ui.theme.Type

/**
 * Search is the whole add flow. Each result already carries the service and the
 * profile it would be filed under, so you can see where a show is going before
 * you tap it.
 */
@Composable
fun AddScreen(
    library: Library,
    query: String,
    results: List<Tmdb.SearchItem>,
    searching: Boolean,
    error: String?,
    seatedNames: String,
    seatedPeople: List<net.shehane.watching.model.Person>,
    existingFor: (Int) -> Show?,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
    onPick: (Tmdb.SearchItem) -> Unit,
    onOpenExisting: (String) -> Unit,
    onBack: () -> Unit,
    topInset: PaddingValues,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

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
            BasicText("Add a show", style = Type.Screen.copy(fontSize = 22.sp))
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
                        BasicText("Title", style = Type.Label.copy(fontSize = 17.sp, color = Ink.Ghost))
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChanged,
                        singleLine = true,
                        textStyle = Type.Label.copy(fontSize = 17.sp, color = Ink.Text),
                        cursorBrush = SolidColor(Ink.Amber),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Ink.SurfaceLift)
                            .clickable(onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) { Draw.Cross(12.dp, Ink.Muted) }
                }
            }
        }

        // --- who it is being added for ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 13.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("Adding for", style = Type.Meta.copy(fontSize = 11.5.sp))
            HGap(9.dp)
            AvatarRow(seatedPeople, 24.dp, Ink.Ground)
            Spacer(Modifier.weight(1f))
        }

        // --- results ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        ) {
            if (error != null) {
                item("error") { EmptyNote(error) }
            } else if (query.isBlank()) {
                item("hint") { EmptyNote("Type a few letters of the title.") }
            } else if (results.isEmpty() && !searching) {
                item("none") { EmptyNote("Nothing found for “$query”.") }
            }

            items(results, key = { it.id }) { item ->
                val existing = existingFor(item.id)
                ResultRow(
                    library = library,
                    item = item,
                    existing = existing,
                    onPick = { if (existing != null) onOpenExisting(existing.id) else onPick(item) },
                )
            }
        }
    }
}

@Composable
private fun ResultRow(
    library: Library,
    item: Tmdb.SearchItem,
    existing: Show?,
    onPick: () -> Unit,
) {
    // Before it is added there is no service on record, so the badge shows what the
    // show would be filed under: the service TMDB reports, once the sheet resolves
    // it. In the list we only have what we already know about a tracked show.
    val service = library.serviceOrNull(existing?.serviceId)
    val profile = library.profileOrNull(existing?.serviceId, existing?.profileId)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onPick)
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Poster(item.name, item.posterPath, 42.dp, 62.dp, corner = 6.dp)
            HGap(12.dp)
            Column(Modifier.weight(1f)) {
                BasicText(
                    item.name,
                    style = Type.ShowTitleSm.copy(
                        fontSize = 19.sp,
                        color = if (existing != null) Ink.Muted else Ink.Text,
                    ),
                    maxLines = 1,
                    overflow = Clip,
                )
                VGap(4.dp)
                BasicText(
                    listOfNotNull(item.year?.toString(), item.overview?.takeIf { it.isNotBlank() }?.let { "TV series" })
                        .joinToString(" · ")
                        .ifBlank { "TV series" },
                    style = Type.Meta.copy(fontSize = 11.5.sp),
                    maxLines = 1,
                    overflow = Clip,
                )
                if (existing != null) {
                    VGap(5.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceProfileBadge(service, profile, small = true)
                        HGap(7.dp)
                        BasicText(
                            when {
                                existing.isAbandoned -> "On your shelf — stopped ${existing.position.short}"
                                existing.isSnoozed -> "Snoozed — ${existing.position.short}"
                                else -> "Already tracked — ${existing.position.short}"
                            },
                            style = Type.Meta.copy(
                                color = if (existing.isAbandoned) Ink.Rust else Ink.Amber,
                            ),
                            maxLines = 1,
                            overflow = Clip,
                        )
                    }
                }
            }
            HGap(8.dp)
            if (existing != null) {
                PillButton(
                    text = if (existing.isActive) "Open" else "Restart",
                    onClick = onPick,
                    height = 32.dp,
                    tint = Ink.Muted,
                )
            } else {
                Draw.Chevron(16.dp, Ink.Amber)
            }
        }
        Divider(Ink.LineSoft)
    }
}

// -------------------------------------------------------------------- the sheet

/**
 * Everything is already filled in. Who comes from the couch bar, the service from
 * where TMDB says it streams, the profile from the default marked for that
 * service, the episode from the beginning. One tap finishes.
 */
@Composable
fun AddSheet(
    library: Library,
    draft: net.shehane.watching.Draft,
    onUpdate: ((net.shehane.watching.Draft) -> net.shehane.watching.Draft) -> Unit,
    onAddProfile: (String, String) -> String,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
    bottomInset: PaddingValues,
) {
    // The search keyboard is still up when the sheet slides over it, covering the
    // button that finishes the add.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(draft.result.id) { keyboard?.hide() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Ground.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss),
    )

    // The sheet is taller than the screen on a phone once the fields are in, so it
    // scrolls, stops short of the status bar, and keeps the one button that
    // finishes the job pinned where a thumb can always reach it.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = bottomInset.calculateTopPadding() + 12.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Ink.Surface),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ink.Line))
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {

            // --- what we matched ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Poster(draft.result.name, draft.detail?.posterPath ?: draft.result.posterPath, 58.dp, 84.dp, corner = 9.dp)
                HGap(13.dp)
                Column(Modifier.weight(1f)) {
                    BasicText(draft.result.name, style = Type.ShowTitle.copy(fontSize = 26.sp), maxLines = 2, overflow = Clip)
                    VGap(6.dp)
                    BasicText(
                        buildString {
                            draft.detail?.year?.let { append(it) } ?: draft.result.year?.let { append(it) }
                            draft.detail?.episodeCount?.let {
                                if (isNotEmpty()) append(" · ")
                                append("$it episodes")
                            }
                            if (isEmpty()) append("TV series")
                        },
                        style = Type.BodyTight,
                    )
                    if (draft.wikipediaUrl != null) {
                        VGap(5.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText("Wikipedia", style = Type.Meta.copy(color = Ink.Amber, fontSize = 11.5.sp))
                            HGap(5.dp)
                            Draw.External(11.dp)
                        }
                    }
                }
            }

            VGap(16.dp)
            Divider(Ink.Line)
            VGap(14.dp)

            BasicText(
                if (draft.loading) "Looking up where it streams…"
                else "Everything below is filled in. Change what is wrong, or just add it.",
                style = Type.Meta.copy(fontSize = 11.sp),
            )
            VGap(14.dp)

            // --- who ---
            SectionHeader("WATCHING", Ink.Faint)
            VGap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (person in library.people) {
                    val me = person.id == net.shehane.watching.data.LibraryStore.ME_ID
                    val on = me || person.id in draft.watchedWith
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .then(
                                if (on) Modifier.background(hexColor(person.color))
                                else Modifier.border(1.dp, Ink.Line, RoundedCornerShape(11.dp))
                            )
                            .clickable(enabled = !me) {
                                onUpdate { d ->
                                    val next = d.watchedWith.toMutableList()
                                    if (!next.remove(person.id)) next.add(person.id)
                                    d.copy(watchedWith = next)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            person.name,
                            style = Type.Meta.copy(
                                fontSize = 11.5.sp,
                                color = if (on) Ink.Ground else Ink.Ghost,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            ),
                            maxLines = 1,
                            overflow = Clip,
                        )
                    }
                }
            }

            VGap(15.dp)

            // --- service ---
            SectionHeader("SERVICE", Ink.Faint)
            VGap(8.dp)
            // TMDB's answer sorted to the front: a selected chip off the right-hand
            // edge looks like nothing was chosen at all.
            val orderedServices = library.services.sortedByDescending { it.id == draft.serviceId }
            ChipRow {
                for (service in orderedServices) {
                    val on = service.id == draft.serviceId
                    Chip(
                        text = service.name,
                        selected = on,
                        tint = hexColor(service.tint),
                        onClick = {
                            onUpdate { d ->
                                d.copy(
                                    serviceId = service.id,
                                    profileId = service.defaultProfile?.id,
                                )
                            }
                        },
                    )
                }
            }

            VGap(15.dp)

            // --- profile ---
            val service = library.serviceOrNull(draft.serviceId)
            SectionHeader(
                if (service != null) "PROFILE ON ${service.name.uppercase()}" else "PROFILE",
                Ink.Faint,
            )
            VGap(8.dp)
            if (service == null) {
                BasicText("Pick a service first.", style = Type.Meta)
            } else {
                var adding by androidx.compose.runtime.remember(service.id) {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                ChipRow {
                    for (profile in service.profiles) {
                        Chip(
                            text = profile.name,
                            selected = profile.id == draft.profileId,
                            tint = Ink.Amber,
                            onClick = { onUpdate { d -> d.copy(profileId = profile.id) } },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .border(1.dp, Ink.Line, RoundedCornerShape(11.dp))
                            .clickable { adding = true },
                        contentAlignment = Alignment.Center,
                    ) { Draw.Plus(15.dp, Ink.Muted) }
                }
                if (adding) {
                    VGap(8.dp)
                    InlineNameField(
                        placeholder = "Profile name, as ${service.name} spells it",
                        onCommit = { name ->
                            adding = false
                            if (name.isNotBlank()) {
                                val id = onAddProfile(service.id, name)
                                onUpdate { d -> d.copy(profileId = id) }
                            }
                        },
                        onCancel = { adding = false },
                    )
                }
            }

            VGap(15.dp)

            // --- where to start ---
            SectionHeader("STARTING AT", Ink.Faint)
            VGap(8.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SquareButton(onClick = {
                    onUpdate { d ->
                        val e = (d.position.episode - 1).coerceAtLeast(1)
                        d.copy(position = d.position.copy(episode = e))
                    }
                }) { Draw.Minus(16.dp) }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Ink.SurfaceLift),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(draft.position.toString(), style = Type.ShowTitle.copy(fontSize = 20.sp))
                    HGap(9.dp)
                    BasicText(
                        if (draft.position.season == 1 && draft.position.episode == 1) "from the start" else "",
                        style = Type.Meta,
                    )
                }
                SquareButton(onClick = {
                    onUpdate { d -> d.copy(position = d.position.copy(episode = d.position.episode + 1)) }
                }) { Draw.Plus(16.dp, Ink.Text) }
            }
            VGap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Season −", { onUpdate { d ->
                    d.copy(position = d.position.copy(season = (d.position.season - 1).coerceAtLeast(1), episode = 1))
                } }, height = 38.dp, tint = Ink.Muted)
                PillButton("Season +", { onUpdate { d ->
                    d.copy(position = d.position.copy(season = d.position.season + 1, episode = 1))
                } }, height = 38.dp, tint = Ink.Muted)
            }

                VGap(18.dp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 22.dp + bottomInset.calculateBottomPadding())
                    .height(56.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Ink.Amber)
                    .clickable(onClick = onCommit),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Draw.Check(19.dp, Ink.Ground)
                    HGap(9.dp)
                    BasicText(
                        "Put it on the couch",
                        style = Type.Button.copy(color = Ink.Ground, fontSize = 16.sp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ small bits

/** Chips run off the edge when there are many, so the row scrolls sideways. */
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .then(
                if (selected) Modifier.background(tint)
                else Modifier.border(1.dp, Ink.Line, RoundedCornerShape(11.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = Type.BodyTight.copy(
                color = if (selected) Ink.Ground else Ink.Muted,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold
                else androidx.compose.ui.text.font.FontWeight.Normal,
            ),
            maxLines = 1,
        )
    }
}

/** A one-line field that commits on the keyboard's Done and vanishes. */
@Composable
fun InlineNameField(
    placeholder: String,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
    initial: String = "",
) {
    var text by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(initial)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Ink.SurfaceLift)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                BasicText(placeholder, style = Type.BodyTight.copy(color = Ink.Ghost), maxLines = 1, overflow = Clip)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = Type.Label.copy(fontSize = 14.sp),
                cursorBrush = SolidColor(Ink.Amber),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { onCommit(text.trim()) },
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        HGap(8.dp)
        RoundButtonPlain({ onCommit(text.trim()) }, 36.dp) { Draw.Check(16.dp, Ink.Amber) }
        RoundButtonPlain(onCancel, 36.dp) { Draw.Cross(14.dp, Ink.Ghost) }
    }
}
