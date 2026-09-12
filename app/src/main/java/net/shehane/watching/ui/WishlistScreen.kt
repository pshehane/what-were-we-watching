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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shehane.watching.data.Tmdb
import net.shehane.watching.model.Library
import net.shehane.watching.model.Show
import net.shehane.watching.ui.theme.Clip
import net.shehane.watching.ui.theme.Ink
import net.shehane.watching.ui.theme.Type

/**
 * Things you mean to watch, and — when you set the country to somewhere you are
 * visiting — whether you actually can while you are there.
 *
 * It is one screen rather than two because Tourist TV is the same list asked a
 * different question. Streaming rights are sold per country, so a show on Hulu at
 * home can be on Netflix abroad, or missing entirely.
 */
@Composable
fun WishlistScreen(
    library: Library,
    wishlist: List<Show>,
    viewingCountry: String,
    countries: List<Tmdb.Country>,
    availability: Map<String, Map<String, Tmdb.Availability>>,
    watchableIn: (String) -> List<String>,
    popularHere: List<Tmdb.SearchItem>,
    loading: Boolean,
    picking: Boolean,
    onPickingChange: (Boolean) -> Unit,
    onViewCountry: (String) -> Unit,
    onSetHome: (String) -> Unit,
    onStartWatching: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    insets: PaddingValues,
) {
    var filter by remember { mutableStateOf("") }
    val away = viewingCountry != library.homeCountry
    val countryName = countries.firstOrNull { it.code == viewingCountry }?.name ?: viewingCountry

    val shownCountries = remember(countries, filter) {
        if (filter.isBlank()) countries
        else countries.filter { it.name.contains(filter.trim(), ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize().background(Ink.Ground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding() + 8.dp,
                bottom = 96.dp + insets.calculateBottomPadding(),
            ),
        ) {
            item("head") {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    BasicText(
                        if (away) "Tourist TV" else "The wishlist",
                        style = Type.Screen,
                    )
                    VGap(4.dp)
                    BasicText(
                        if (away) {
                            "Where these can be watched in $countryName, and what is worth " +
                                "knowing about while you are there."
                        } else {
                            "Things you mean to watch. Nothing here counts as started."
                        },
                        style = Type.Body,
                    )
                    VGap(12.dp)

                    // The country control is the whole feature; it is not buried.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .then(
                                    if (away) Modifier.background(Ink.Cool.copy(alpha = 0.18f))
                                        .border(1.dp, Ink.Cool, RoundedCornerShape(11.dp))
                                    else Modifier.border(1.dp, Ink.Line, RoundedCornerShape(11.dp))
                                )
                                .clickable { onPickingChange(!picking) } 
                                .padding(horizontal = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText(
                                if (away) "In $countryName" else "At home in $countryName",
                                style = Type.BodyTight.copy(color = if (away) Ink.Cool else Ink.Muted),
                                maxLines = 1,
                                overflow = Clip,
                            )
                            HGap(8.dp)
                            Draw.Chevron(14.dp, if (away) Ink.Cool else Ink.Ghost)
                        }
                        if (away) {
                            HGap(8.dp)
                            PillButton("Back home", { onViewCountry(library.homeCountry) }, tint = Ink.Muted, height = 44.dp)
                        }
                    }
                    if (away) {
                        VGap(8.dp)
                        // Setting home is one action in one place. It used to sit on
                        // every row of the picker, where it was louder than the
                        // country names and easy to hit by accident.
                        BasicText(
                            "Moved? Make $countryName your home country.",
                            style = Type.Meta.copy(color = Ink.Amber),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSetHome(viewingCountry) }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                        )
                    }
                    VGap(14.dp)
                    Divider(Ink.LineSoft)
                    VGap(12.dp)
                }
            }

            if (picking) {
                item("picker-head") {
                    Column(Modifier.padding(horizontal = 18.dp)) {
                        SectionHeader("WHERE ARE YOU", Ink.Faint)
                        VGap(10.dp)
                        // 251 countries is a lot to scroll past to reach Japan.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Ink.Surface)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Draw.Search(17.dp, Ink.Faint)
                            HGap(9.dp)
                            Box(Modifier.weight(1f)) {
                                if (filter.isEmpty()) {
                                    BasicText("Type a country", style = Type.BodyTight.copy(color = Ink.Ghost))
                                }
                                BasicTextField(
                                    value = filter,
                                    onValueChange = { filter = it },
                                    singleLine = true,
                                    textStyle = Type.Label,
                                    cursorBrush = SolidColor(Ink.Amber),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (filter.isNotEmpty()) {
                                RoundButtonPlain({ filter = "" }, 36.dp) { Draw.Cross(13.dp, Ink.Muted) }
                            }
                        }
                        VGap(6.dp)
                    }
                }
                items(shownCountries, key = { "c-${it.code}" }) { country ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .height(46.dp)
                            .clickable { onViewCountry(country.code) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            country.name,
                            style = Type.Label.copy(
                                color = if (country.code == viewingCountry) Ink.Amber else Ink.Text
                            ),
                            maxLines = 1,
                            overflow = Clip,
                            modifier = Modifier.weight(1f),
                        )
                        if (country.code == library.homeCountry) {
                            BasicText("home", style = Type.Meta.copy(color = Ink.Amber))
                        }
                    }
                }
                if (shownCountries.isEmpty()) {
                    item("picker-empty") {
                        EmptyNote(
                            if (countries.isEmpty()) "Loading the country list…"
                            else "No country matches “$filter”."
                        )
                    }
                }
                return@LazyColumn
            }

            // --- the wishlist itself ---

            if (wishlist.isEmpty()) {
                item("empty") {
                    EmptyNote(
                        "Nothing on the wishlist. Add a show and choose " +
                            "“just the wishlist” instead of putting it on the couch."
                    )
                }
            } else {
                item("list-head") {
                    Box(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp)) {
                        SectionHeader(
                            if (away) "CAN YOU WATCH IT HERE · ${wishlist.size}" else "WANT TO WATCH · ${wishlist.size}",
                            if (away) Ink.Cool else Ink.Amber,
                        )
                    }
                }
                items(wishlist, key = { it.id }) { show ->
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                        WishRow(
                            library = library,
                            show = show,
                            away = away,
                            viewingCountry = viewingCountry,
                            byCountry = availability[show.id],
                            countryName = { code ->
                                countries.firstOrNull { it.code == code }?.name ?: code
                            },
                            watchableIn = watchableIn(show.id),
                            loading = loading && availability[show.id] == null,
                            onOpen = { onOpenShow(show.id) },
                            onStart = { onStartWatching(show.id) },
                            onRemove = { onRemove(show.id) },
                        )
                    }
                }
            }

            // --- what is worth knowing about locally ---
            if (away && popularHere.isNotEmpty()) {
                item("popular-head") {
                    Box(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)) {
                        SectionHeader("POPULAR IN ${countryName.uppercase()}", Ink.Faint)
                    }
                }
                item("popular-note") {
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) {
                        BasicText(
                            "Included with a subscription there, most watched first.",
                            style = Type.Meta,
                        )
                    }
                }
                items(popularHere, key = { "p-${it.id}" }) { item ->
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                        PopularRow(item)
                    }
                }
            }

            if (loading) {
                item("loading") { EmptyNote("Checking $countryName…") }
            }
        }
    }
}

// --------------------------------------------------------------------- rows

@Composable
private fun WishRow(
    library: Library,
    show: Show,
    away: Boolean,
    viewingCountry: String,
    byCountry: Map<String, Tmdb.Availability>?,
    countryName: (String) -> String,
    watchableIn: List<String>,
    loading: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Surface)
            .border(1.dp, Ink.Line, RoundedCornerShape(14.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Poster(show.title, show.posterPath, 46.dp, 64.dp, corner = 7.dp)
            HGap(12.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        show.title,
                        style = Type.ShowTitle.copy(fontSize = 19.sp),
                        maxLines = 1,
                        overflow = Clip,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    show.year?.let {
                        HGap(8.dp)
                        BasicText("$it", style = Type.Meta)
                    }
                }
                VGap(5.dp)

                val here = byCountry?.get(viewingCountry)
                val elsewhere = watchableIn.filter { it != viewingCountry }

                when {
                    loading && byCountry == null ->
                        BasicText("checking\u2026", style = Type.Meta)

                    byCountry == null ->
                        BasicText("could not check", style = Type.Meta)

                    // Watchable where you are. At home that is the ordinary case,
                    // so it gets no flag and no colour: just where it lives.
                    here?.isStreamable == true -> {
                        val service = library.serviceOrNull(show.serviceId)
                        if (!away && service != null) {
                            ServiceProfileBadge(
                                service,
                                library.profileOrNull(show.serviceId, show.profileId),
                                small = true,
                            )
                        } else {
                            BasicText(
                                here.included.joinToString(", "),
                                style = Type.Meta.copy(
                                    color = if (away) Ink.Cool else Ink.Muted,
                                    fontSize = 11.5.sp,
                                ),
                                maxLines = 2,
                                overflow = Clip,
                            )
                        }
                    }

                    // Not on a subscription where you are, but it exists somewhere.
                    // Naming those places is the point: "no" on its own is useless.
                    elsewhere.isNotEmpty() -> Column {
                        BasicText(
                            "Not in " + countryName(viewingCountry),
                            style = Type.Meta.copy(color = Ink.Amber, fontSize = 11.5.sp),
                            maxLines = 1,
                            overflow = Clip,
                        )
                        VGap(3.dp)
                        BasicText(
                            buildString {
                                append(elsewhere.take(3).joinToString(", ") { countryName(it) })
                                val rest = elsewhere.size - 3
                                if (rest > 0) append(" +" + rest + " more")
                            },
                            style = Type.Meta.copy(color = Ink.Cool, fontSize = 11.sp),
                            maxLines = 2,
                            overflow = Clip,
                        )
                    }

                    here?.rentOrBuy?.isNotEmpty() == true -> BasicText(
                        "rent or buy only \u00b7 " + here.rentOrBuy.take(2).joinToString(", "),
                        style = Type.Meta.copy(color = Ink.Amber),
                        maxLines = 2,
                        overflow = Clip,
                    )

                    else -> BasicText(
                        "Not on any subscription, anywhere",
                        style = Type.Meta.copy(color = Ink.Rust),
                        maxLines = 2,
                        overflow = Clip,
                    )
                }
            }
            HGap(8.dp)
            RoundButtonPlain(onOpen, 40.dp) { Draw.Chevron(16.dp, Ink.Ghost) }
        }

        VGap(9.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Start watching", onStart, height = 40.dp, filled = true)
            PillButton("Remove", onRemove, height = 40.dp, tint = Ink.Muted)
        }
    }
}

@Composable
private fun PopularRow(item: Tmdb.SearchItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(item.name, item.posterPath, 38.dp, 54.dp, corner = 6.dp)
        HGap(11.dp)
        Column(Modifier.weight(1f)) {
            BasicText(
                item.name,
                style = Type.ShowTitleSm.copy(color = Ink.Text.copy(alpha = 0.9f)),
                maxLines = 1,
                overflow = Clip,
            )
            item.year?.let {
                VGap(3.dp)
                BasicText("$it", style = Type.Meta)
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}
