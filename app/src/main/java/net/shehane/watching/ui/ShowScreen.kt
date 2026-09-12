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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shehane.watching.data.Clock
import net.shehane.watching.data.LibraryStore
import net.shehane.watching.model.Library
import net.shehane.watching.model.Position
import net.shehane.watching.model.Show
import net.shehane.watching.ui.theme.Clip
import net.shehane.watching.ui.theme.Ink
import net.shehane.watching.ui.theme.Type
import net.shehane.watching.ui.theme.posterTintFor

/**
 * One position, and it is yours. The app never claims to know where anyone else
 * has got to; "watched with" is a tag, not four separate counters.
 */
@Composable
fun ShowScreen(
    library: Library,
    show: Show,
    onBack: () -> Unit,
    onStep: (Int) -> Unit,
    onFinishSeason: () -> Unit,
    onSetPosition: (Position) -> Unit,
    onSetWatchedWith: (List<String>) -> Unit,
    onSetPlacement: (String?, String?) -> Unit,
    onSnooze: () -> Unit,
    onAbandon: () -> Unit,
    onFinish: () -> Unit,
    onReactivate: () -> Unit,
    onDelete: () -> Unit,
    insets: PaddingValues,
) {
    val context = LocalContext.current
    val service = library.serviceOrNull(show.serviceId)
    val profile = library.profileOrNull(show.serviceId, show.profileId)
    val (base, mark) = posterTintFor(show.title)
    var editingPeople by remember { mutableStateOf(false) }
    var editingPlacement by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Ink.Ground)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // --- hero ---
            Box(Modifier.fillMaxWidth().height(268.dp)) {
                val poster = rememberPoster(show.posterPath, large = true)
                if (poster != null) {
                    androidx.compose.foundation.Image(
                        bitmap = poster,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(base))
                    Box(
                        Modifier
                            .size(230.dp)
                            .align(Alignment.TopEnd)
                            .padding(top = 0.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(mark),
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Ink.Ground.copy(alpha = 0.60f),
                            0.30f to Color.Transparent,
                            0.86f to Ink.Ground.copy(alpha = 0.94f),
                            1f to Ink.Ground,
                        )
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = insets.calculateTopPadding(), start = 6.dp, end = 6.dp)
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButtonPlain(onBack) { Draw.Chevron(22.dp, Ink.Text, pointsLeft = true) }
                    Spacer(Modifier.weight(1f))
                    RoundButtonPlain({ confirmDelete = !confirmDelete }) {
                        Draw.Cross(18.dp, Ink.Text, circled = true)
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 14.dp),
                ) {
                    BasicText(show.title, style = Type.Hero, maxLines = 3, overflow = Clip)
                    VGap(7.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        show.year?.let {
                            BasicText("$it", style = Type.BodyTight.copy(color = Ink.Muted))
                            HGap(8.dp)
                        }
                        ServiceProfileBadge(service, profile)
                        HGap(8.dp)
                        BasicText(
                            "change",
                            style = Type.Meta.copy(color = Ink.Amber),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { editingPlacement = !editingPlacement }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 18.dp)) {

                if (confirmDelete) {
                    VGap(10.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Ink.RustDim, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            "Remove this show and its history?",
                            style = Type.BodyTight,
                            modifier = Modifier.weight(1f),
                        )
                        PillButton("Remove", onDelete, height = 36.dp, tint = Ink.Rust)
                        HGap(8.dp)
                        PillButton("Keep", { confirmDelete = false }, height = 36.dp, tint = Ink.Muted)
                    }
                }

                VGap(16.dp)

                // --- where you are ---
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ink.Surface)
                        .border(1.dp, Ink.Line, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SquareButton(onClick = { onStep(-1) }) { Draw.Minus(16.dp) }
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BasicText(show.position.toString(), style = Type.Numeral)
                            VGap(2.dp)
                            BasicText(
                                "last watched ${Clock.ago(show.lastWatchedAt ?: show.addedAt)}",
                                style = Type.Meta,
                            )
                        }
                        SquareButton(onClick = { onStep(1) }, filled = true) {
                            Draw.Plus(17.dp, Ink.Ground)
                        }
                    }
                    VGap(11.dp)
                    Divider(Ink.Line)
                    VGap(11.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            "Next up · S${show.position.season} E${show.position.episode + 1}",
                            style = Type.BodyTight,
                        )
                        Spacer(Modifier.weight(1f))
                        // On the last season there is nothing to roll into, so the
                        // button offers the thing you actually mean instead of
                        // inventing a season the show does not have.
                        val onLastSeason = show.seasonCount != null &&
                            show.position.season >= show.seasonCount
                        PillButton(
                            text = if (onLastSeason) "Finished it" else "Finished S${show.position.season}",
                            onClick = if (onLastSeason) onFinish else onFinishSeason,
                            height = 34.dp,
                        )
                    }
                    if (show.seasonCount != null || show.episodeCount != null) {
                        VGap(8.dp)
                        BasicText(
                            listOfNotNull(
                                show.seasonCount?.let { "$it season" + if (it == 1) "" else "s" },
                                show.episodeCount?.let { "$it episode" + if (it == 1) "" else "s" },
                            ).joinToString(" · "),
                            style = Type.Meta,
                        )
                    }
                }

                VGap(18.dp)

                // --- who it is watched with ---
                SectionHeader(
                    "WATCHED WITH",
                    Ink.Faint,
                    trailing = {
                        BasicText(
                            if (editingPeople) "done" else "edit",
                            style = Type.Meta.copy(color = Ink.Amber),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { editingPeople = !editingPeople }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    },
                )
                VGap(9.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (person in library.people) {
                        val me = person.id == LibraryStore.ME_ID
                        val on = me || person.id in show.watchedWith
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (on) Modifier.background(Ink.Surface).border(1.dp, Ink.Line, RoundedCornerShape(12.dp))
                                    else Modifier.border(1.dp, Ink.LineSoft, RoundedCornerShape(12.dp))
                                )
                                .then(
                                    if (editingPeople && !me) Modifier.clickable {
                                        val next = show.watchedWith.toMutableList()
                                        if (!next.remove(person.id)) next.add(person.id)
                                        onSetWatchedWith(next)
                                    } else Modifier
                                )
                                .alpha(if (on) 1f else 0.4f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(person, 21.dp, seated = on)
                            HGap(6.dp)
                            BasicText(
                                person.name,
                                style = Type.Meta.copy(fontSize = 11.5.sp, color = Ink.Text),
                                maxLines = 1,
                                overflow = Clip,
                            )
                        }
                    }
                }
                if (editingPeople) {
                    VGap(7.dp)
                    BasicText(
                        "You are on every show in this app, so you cannot be removed.",
                        style = Type.Meta,
                    )
                }

                // --- where it lives ---
                if (editingPlacement) {
                    VGap(18.dp)
                    SectionHeader("SERVICE", Ink.Faint)
                    VGap(8.dp)
                    ChipRow {
                        for (s in library.services) {
                            Chip(
                                text = s.name,
                                selected = s.id == show.serviceId,
                                tint = hexColor(s.tint),
                                onClick = { onSetPlacement(s.id, s.defaultProfile?.id) },
                            )
                        }
                    }
                    if (service != null) {
                        VGap(12.dp)
                        SectionHeader("PROFILE ON ${service.name.uppercase()}", Ink.Faint)
                        VGap(8.dp)
                        if (service.profiles.isEmpty()) {
                            BasicText(
                                "No profiles yet for ${service.name}. Add one in Services & profiles.",
                                style = Type.Meta,
                            )
                        } else {
                            ChipRow {
                                for (p in service.profiles) {
                                    Chip(
                                        text = p.name,
                                        selected = p.id == show.profileId,
                                        tint = Ink.Amber,
                                        onClick = { onSetPlacement(service.id, p.id) },
                                    )
                                }
                            }
                        }
                    }
                }

                VGap(18.dp)

                // --- reserved for the leaving-soon feature ---
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Ink.LineSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 13.dp, vertical = 12.dp)
                        .alpha(0.5f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Draw.Clock(17.dp, Ink.Muted)
                    HGap(10.dp)
                    BasicText("Leaving ${service?.name ?: "the service"} in — days", style = Type.BodyTight)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, Ink.Line, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        BasicText("LATER", style = Type.Eyebrow.copy(fontSize = 10.sp, color = Ink.Faint))
                    }
                }

                // --- what it is ---
                if (!show.overview.isNullOrBlank() || show.wikipediaUrl != null) {
                    VGap(18.dp)
                    SectionHeader("WHAT IT IS", Ink.Faint)
                    VGap(7.dp)
                    if (!show.overview.isNullOrBlank()) {
                        BasicText(
                            show.overview.trim(),
                            style = Type.BodyTight.copy(lineHeight = 19.sp, color = Ink.Faint),
                            maxLines = 6,
                            overflow = Clip,
                        )
                    }
                    if (show.wikipediaUrl != null) {
                        VGap(8.dp)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(show.wikipediaUrl),
                                            )
                                        )
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText("Read on Wikipedia", style = Type.Meta.copy(color = Ink.Amber, fontSize = 11.5.sp))
                            HGap(5.dp)
                            Draw.External(11.dp)
                        }
                    }
                }

                VGap(22.dp)

                // --- the two demotions, plus the way out ---
                if (show.isActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Not in the mood", Ink.Amber, Modifier.weight(1f), onSnooze) {
                            Draw.Moon(17.dp, Ink.Amber, Ink.Ground)
                        }
                        ActionButton("Won't finish", Ink.Rust, Modifier.weight(1f), onAbandon) {
                            Draw.Cross(17.dp, Ink.Rust, circled = true)
                        }
                        ActionButton("Finished", Ink.Muted, Modifier.weight(1f), onFinish) {
                            Draw.Check(17.dp, Ink.Muted)
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(Ink.Surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                when {
                                    show.isSnoozed -> "Not in the mood"
                                    show.isFinished -> "Finished"
                                    else -> "Decided not to finish"
                                },
                                style = Type.Label,
                            )
                            VGap(3.dp)
                            BasicText(
                                when {
                                    show.isSnoozed -> Clock.until(show.snoozeUntil)
                                    show.isFinished -> "Watched to the end."
                                    else -> "Stays down until you put it back."
                                },
                                style = Type.Meta,
                            )
                        }
                        PillButton("Put it back", onReactivate, height = 40.dp)
                    }
                }

                VGap(28.dp + insets.calculateBottomPadding())
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(13.dp))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        VGap(5.dp)
        BasicText(
            label,
            style = Type.Meta.copy(
                color = tint,
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            ),
            maxLines = 1,
            overflow = Clip,
        )
    }
}
