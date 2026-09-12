package net.shehane.watching.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shehane.watching.BuildConfig
import net.shehane.watching.R
import net.shehane.watching.data.LibraryStore
import net.shehane.watching.ui.theme.Ink
import net.shehane.watching.ui.theme.Type

/**
 * TMDB's terms require their attribution notice and their logo to be shown. Both
 * are here. See licenses/TMDB-logo.md for where the artwork came from.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    insets: PaddingValues,
) {
    val context = LocalContext.current

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
            BasicText("About", style = Type.Screen.copy(fontSize = 22.sp))
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            BasicText("What Were We Watching", style = Type.Screen.copy(fontSize = 26.sp))
            VGap(6.dp)
            BasicText(
                "Version ${BuildConfig.VERSION_NAME}",
                style = Type.Meta,
            )

            VGap(24.dp)
            SectionHeader("SHOW DATA", Ink.Faint)
            VGap(10.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Ink.Line, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Column {
                    // TMDB's terms require their mark alongside the notice. The logo
                    // is their supplied artwork, converted to a vector drawable
                    // without altering it.
                    Image(
                        painter = painterResource(R.drawable.tmdb_logo),
                        contentDescription = "The Movie Database",
                        modifier = Modifier.height(20.dp),
                    )
                    VGap(14.dp)
                    BasicText(
                        "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                        style = Type.BodyTight.copy(lineHeight = 19.sp, color = Ink.Text),
                    )
                    VGap(10.dp)
                    BasicText(
                        "Titles, artwork, episode counts and streaming availability all come from " +
                            "The Movie Database. Streaming availability is supplied to TMDB by JustWatch.",
                        style = Type.BodyTight.copy(lineHeight = 18.sp),
                    )
                    VGap(10.dp)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://www.themoviedb.org/"),
                                        )
                                    )
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText("themoviedb.org", style = Type.Meta.copy(color = Ink.Amber, fontSize = 12.sp))
                        HGap(5.dp)
                        Draw.External(11.dp)
                    }
                }
            }

            VGap(24.dp)
            SectionHeader("ARTICLE LINKS", Ink.Faint)
            VGap(10.dp)
            BasicText(
                "Wikipedia links are resolved through Wikidata, using the identifier TMDB " +
                    "records for each series. Text on Wikipedia is available under the " +
                    "Creative Commons Attribution-ShareAlike licence.",
                style = Type.BodyTight.copy(lineHeight = 19.sp),
            )

            VGap(24.dp)
            SectionHeader("YOUR DATA", Ink.Faint)
            VGap(10.dp)
            BasicText(
                "Everything you record lives in ${LibraryStore.JSON_NAME} on this phone, with " +
                    "${LibraryStore.CSV_NAME} written beside it for reading in a spreadsheet. " +
                    "Nothing is sent anywhere except the copy you put on your own Google Drive.",
                style = Type.BodyTight.copy(lineHeight = 19.sp),
            )

            VGap(40.dp + insets.calculateBottomPadding())
        }
    }
}
