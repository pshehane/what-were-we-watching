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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shehane.watching.data.DriveSync
import net.shehane.watching.model.Library
import net.shehane.watching.model.Service
import net.shehane.watching.ui.theme.Clip
import net.shehane.watching.ui.theme.Ink
import net.shehane.watching.ui.theme.Type

/**
 * Services, their profiles, the people you watch with, and the Drive connection.
 *
 * Profiles are not linked to people. A profile is only the name a service shows
 * on its own picker, and the one thing marking a default does is decide what the
 * add sheet starts on.
 */
@Composable
fun ProfilesScreen(
    library: Library,
    syncState: DriveSync.State,
    onBack: () -> Unit,
    onAddProfile: (String, String) -> Unit,
    onRenameProfile: (String, String, String) -> Unit,
    onSetDefault: (String, String) -> Unit,
    onDeleteProfile: (String, String) -> Unit,
    onAddService: (String) -> Unit,
    onDeleteService: (String) -> Unit,
    onAddPerson: (String) -> Unit,
    onDeletePerson: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onAbout: () -> Unit,
    cloudSummaryAvailable: Boolean,
    onSetSummaryMode: (String) -> Unit,
    insets: PaddingValues,
) {
    var openService by remember { mutableStateOf<String?>(library.services.firstOrNull()?.id) }
    var addingService by remember { mutableStateOf(false) }
    var addingPerson by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Ground)
            .padding(top = insets.calculateTopPadding()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButtonPlain(onBack) { Draw.Chevron(22.dp, Ink.Text, pointsLeft = true) }
            BasicText("Services & profiles", style = Type.Screen.copy(fontSize = 22.sp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp, end = 18.dp,
                bottom = 32.dp + insets.calculateBottomPadding(),
            ),
        ) {
            item("blurb") {
                BasicText(
                    "Type each profile exactly as it appears on that service. " +
                        "The one you mark is what the add screen starts on.",
                    style = Type.BodyTight.copy(lineHeight = 19.sp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
            }

            // --- services ---
            items(library.services) { service ->
                ServiceBlock(
                    service = service,
                    expanded = openService == service.id,
                    onToggle = { openService = if (openService == service.id) null else service.id },
                    onAddProfile = { name -> onAddProfile(service.id, name) },
                    onRenameProfile = { id, name -> onRenameProfile(service.id, id, name) },
                    onSetDefault = { id -> onSetDefault(service.id, id) },
                    onDeleteProfile = { id -> onDeleteProfile(service.id, id) },
                    onDeleteService = { onDeleteService(service.id) },
                )
                VGap(12.dp)
            }

            item("add-service") {
                if (addingService) {
                    InlineNameField(
                        placeholder = "Service name",
                        onCommit = { name ->
                            addingService = false
                            if (name.isNotBlank()) onAddService(name)
                        },
                        onCancel = { addingService = false },
                    )
                } else {
                    DashedButton("Add a service") { addingService = true }
                }
                VGap(26.dp)
            }

            // --- people ---
            item("people-header") {
                SectionHeader("PEOPLE YOU WATCH WITH", Ink.Faint)
                VGap(9.dp)
            }
            item("people") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Ink.Line, RoundedCornerShape(14.dp)),
                ) {
                    library.people.forEachIndexed { index, person ->
                        if (index > 0) Divider(Ink.LineSoft)
                        Row(
                            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(person, 28.dp)
                            HGap(11.dp)
                            BasicText(person.name, style = Type.Label)
                            Spacer(Modifier.weight(1f))
                            if (person.id == net.shehane.watching.data.LibraryStore.ME_ID) {
                                BasicText("you", style = Type.Meta)
                            } else {
                                RoundButtonPlain({ onDeletePerson(person.id) }, 44.dp) {
                                    Draw.Cross(14.dp, Ink.Ghost)
                                }
                            }
                        }
                    }
                }
                VGap(10.dp)
                if (addingPerson) {
                    InlineNameField(
                        placeholder = "Name",
                        onCommit = { name ->
                            addingPerson = false
                            if (name.isNotBlank()) onAddPerson(name)
                        },
                        onCancel = { addingPerson = false },
                    )
                } else {
                    DashedButton("Add a person") { addingPerson = true }
                }
                VGap(26.dp)
            }

            // --- how catch-up gets written ---
            item("summary") {
                SectionHeader("CATCH ME UP", Ink.Faint)
                VGap(9.dp)
                BasicText(
                    "Which model writes the recap on a show screen. Whatever is not " +
                        "available falls through to the next one down.",
                    style = Type.BodyTight,
                )
                VGap(12.dp)

                SummaryChoice(
                    title = "A cloud model",
                    detail = if (cloudSummaryAvailable) {
                        "Gemini. Writes prose, one point for the story and one for each " +
                            "main character. Needs a network. Sends the synopses you have " +
                            "already seen, and nothing else."
                    } else {
                        "Not in this build. The key is kept outside the repository, so a " +
                            "copy of this app from GitHub has no cloud option at all."
                    },
                    chosen = library.summaryMode == Library.SUMMARY_CLOUD,
                    enabled = cloudSummaryAvailable,
                    onPick = { onSetSummaryMode(Library.SUMMARY_CLOUD) },
                )
                VGap(8.dp)
                SummaryChoice(
                    title = "This phone",
                    detail = "AICore, free and offline. It writes up to three bullet " +
                        "points and cannot be told what to put in them, so the shape is " +
                        "its choice rather than ours. Not every phone has it.",
                    chosen = library.summaryMode == Library.SUMMARY_DEVICE,
                    enabled = true,
                    onPick = { onSetSummaryMode(Library.SUMMARY_DEVICE) },
                )
                VGap(8.dp)
                SummaryChoice(
                    title = "No summary",
                    detail = "The episode synopses from TMDB, as they are. No model, " +
                        "nothing sent anywhere, and every detail kept.",
                    chosen = library.summaryMode == Library.SUMMARY_NONE,
                    enabled = true,
                    onPick = { onSetSummaryMode(Library.SUMMARY_NONE) },
                )
                VGap(26.dp)
            }

            // --- drive ---
            item("sync") {
                SectionHeader("CLOUD COPY", Ink.Faint)
                VGap(9.dp)
                SyncBlock(syncState, onSignIn, onSignOut, onSyncNow)
                VGap(20.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onAbout)
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText("About and credits", style = Type.Label)
                    Spacer(Modifier.weight(1f))
                    Draw.Chevron(15.dp, Ink.Ghost)
                }
            }
        }
    }
}

@Composable
private fun SummaryChoice(
    title: String,
    detail: String,
    chosen: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .border(
                1.dp,
                if (chosen && enabled) Ink.Amber else Ink.Line,
                RoundedCornerShape(13.dp),
            )
            .clickable(enabled = enabled, onClick = onPick)
            .padding(13.dp),
    ) {
        Draw.Radio(selected = chosen && enabled)
        HGap(11.dp)
        Column {
            BasicText(
                title,
                style = Type.Label.copy(color = if (enabled) Ink.Text else Ink.Ghost),
            )
            VGap(4.dp)
            BasicText(
                detail,
                style = Type.Meta.copy(
                    color = if (enabled) Ink.Faint else Ink.Ghost,
                    lineHeight = 15.sp,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------- service block

@Composable
private fun ServiceBlock(
    service: Service,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddProfile: (String) -> Unit,
    onRenameProfile: (String, String) -> Unit,
    onSetDefault: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDeleteService: () -> Unit,
) {
    var adding by remember(service.id) { mutableStateOf(false) }
    var renaming by remember(service.id) { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Ink.Line, RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(if (expanded) Ink.Surface else Ink.Ground)
                .clickable(onClick = onToggle)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(hexColor(service.tint))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            ) {
                BasicText(service.name.uppercase(), style = Type.Badge.copy(fontSize = 10.sp), maxLines = 1)
            }
            HGap(10.dp)
            if (!expanded) {
                BasicText(
                    service.profiles.joinToString(" · ") { it.name }.ifBlank { "no profiles yet" },
                    style = Type.Meta.copy(
                        fontSize = 12.5.sp,
                        color = if (service.profiles.isEmpty()) Ink.Ghost else Ink.Muted,
                    ),
                    maxLines = 1,
                    overflow = Clip,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
                BasicText(
                    "${service.profiles.size} profile" + if (service.profiles.size == 1) "" else "s",
                    style = Type.Meta,
                )
                RoundButtonPlain({ adding = true }, 40.dp) { Draw.Plus(17.dp, Ink.Amber) }
            }
            if (!expanded) Draw.Chevron(15.dp, Ink.Ghost)
        }

        if (expanded) {
            for (profile in service.profiles) {
                Divider(Ink.LineSoft)
                if (renaming == profile.id) {
                    Box(Modifier.padding(8.dp)) {
                        InlineNameField(
                            placeholder = "Profile name",
                            initial = profile.name,
                            onCommit = { name ->
                                renaming = null
                                if (name.isNotBlank()) onRenameProfile(profile.id, name)
                            },
                            onCancel = { renaming = null },
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).padding(start = 4.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButtonPlain({ onSetDefault(profile.id) }, 44.dp) {
                            Draw.Radio(profile.id == service.defaultProfileId)
                        }
                        BasicText(
                            profile.name,
                            style = Type.Label,
                            maxLines = 1,
                            overflow = Clip,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { renaming = profile.id }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                        )
                        if (profile.id == service.defaultProfileId) {
                            HGap(8.dp)
                            BasicText(
                                "DEFAULT",
                                style = Type.Eyebrow.copy(fontSize = 9.5.sp, color = Ink.Amber),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        RoundButtonPlain({ onDeleteProfile(profile.id) }, 44.dp) {
                            Draw.Cross(13.dp, Ink.Ghost)
                        }
                    }
                }
            }

            if (adding) {
                Divider(Ink.LineSoft)
                Box(Modifier.padding(8.dp)) {
                    InlineNameField(
                        placeholder = "Profile name, as ${service.name} spells it",
                        onCommit = { name ->
                            adding = false
                            if (name.isNotBlank()) onAddProfile(name)
                        },
                        onCancel = { adding = false },
                    )
                }
            }

            Divider(Ink.LineSoft)
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                BasicText(
                    "Remove ${service.name}",
                    style = Type.Meta.copy(color = Ink.Rust),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDeleteService)
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// -------------------------------------------------------------------- the sync

@Composable
private fun SyncBlock(
    state: DriveSync.State,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Ink.Line, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        when (state) {
            is DriveSync.State.SignedOut -> {
                BasicText("Kept on this phone only", style = Type.Label)
                VGap(6.dp)
                BasicText(
                    "Connect Drive and both files land in a \u201C" + DriveSync.FOLDER_NAME +
                        "\u201D folder on your own Drive: library.json for the app, " +
                        "library.csv for reading in a spreadsheet. The app can only see files " +
                        "it created there.",
                    style = Type.BodyTight.copy(lineHeight = 18.sp),
                )
                VGap(12.dp)
                PillButton("Connect Drive", onSignIn, filled = true)
            }
            is DriveSync.State.Ready -> {
                BasicText("Syncing to Drive", style = Type.Label)
                VGap(6.dp)
                BasicText(
                    "Folder: ${DriveSync.FOLDER_NAME}" +
                        (state.account?.let { "\nAccount: $it" } ?: "") +
                        (state.lastSync?.let { "\nLast sync: ${net.shehane.watching.data.Clock.ago(it)}" } ?: ""),
                    style = Type.BodyTight.copy(lineHeight = 18.sp),
                )
                VGap(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Sync now", onSyncNow, filled = true)
                    PillButton("Sign out", onSignOut, tint = Ink.Muted)
                }
            }
            is DriveSync.State.Failed -> {
                BasicText("Sync problem", style = Type.Label.copy(color = Ink.Rust))
                VGap(6.dp)
                BasicText(state.message, style = Type.BodyTight)
                VGap(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Try again", onSyncNow, filled = true)
                    PillButton("Sign out", onSignOut, tint = Ink.Muted)
                }
            }
        }
    }
}

@Composable
fun DashedButton(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(13.dp))
            .border(1.dp, Ink.Line, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Draw.Plus(16.dp, Ink.Muted)
        HGap(8.dp)
        BasicText(text, style = Type.Label.copy(fontSize = 13.5.sp, color = Ink.Muted))
    }
}
