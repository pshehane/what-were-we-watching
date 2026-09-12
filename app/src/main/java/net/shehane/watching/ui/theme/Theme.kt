package net.shehane.watching.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.shehane.watching.R
import androidx.compose.ui.unit.sp

/**
 * The night palette from the mockups, converted from oklch to sRGB.
 * One warm near-black ground, one amber accent, one cool accent for the
 * partial-match note, one rust for the harder demotion.
 */
object Ink {
    val Ground = Color(0xFF17140F)
    val Surface = Color(0xFF2A251E)
    val SurfaceLift = Color(0xFF332D25)
    val SurfaceSunk = Color(0xFF201C16)
    val Line = Color(0xFF433B31)
    val LineSoft = Color(0xFF322C25)

    val Text = Color(0xFFF3EEE6)
    val Muted = Color(0xFFB0A79A)
    val Faint = Color(0xFF7F776C)
    val Ghost = Color(0xFF5C554C)

    val Amber = Color(0xFFF0A84B)
    val AmberDim = Color(0xFF7A5A2C)
    val AmberWash = Color(0xFF3A2E1C)
    val Cool = Color(0xFF6EB2F0)
    val Rust = Color(0xFFE06A4E)
    val RustDim = Color(0xFF6A2E22)
}

/** Poster placeholder colours, picked per show from its title hash. */
val PosterTints: List<Pair<Color, Color>> = listOf(
    Color(0xFF3A2E4D) to Color(0xFFB08BD6),
    Color(0xFF2C3A4D) to Color(0xFF86B4D9),
    Color(0xFF4D3A2C) to Color(0xFFD9A06B),
    Color(0xFF2C4D3C) to Color(0xFF7FCFA4),
    Color(0xFF4D2C33) to Color(0xFFD98B9B),
    Color(0xFF3E4D2C) to Color(0xFFB7D07A),
    Color(0xFF2C464D) to Color(0xFF7FC8CF),
    Color(0xFF4A3350) to Color(0xFFC792D8),
)

fun posterTintFor(key: String): Pair<Color, Color> {
    var h = 7
    for (c in key) h = h * 31 + c.code
    val i = ((h % PosterTints.size) + PosterTints.size) % PosterTints.size
    return PosterTints[i]
}

/**
 * The two voices from the mockups, bundled as font resources rather than fetched
 * at runtime, so the app looks right on first launch and with no signal.
 *
 * Space Grotesk ships as a variable font with a weight axis. Compose derives the
 * variation settings from the FontWeight on each entry, so naming the weights here
 * is all it takes to get real bold rather than a synthesised smear.
 *
 * Both are SIL Open Font Licence; the licences are in /licenses at the repo root.
 */
object Face {
    val Display: FontFamily = FontFamily(
        Font(R.font.instrument_serif, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
    )
    val Ui: FontFamily = FontFamily(
        Font(R.font.space_grotesk, FontWeight.Normal),
        Font(R.font.space_grotesk, FontWeight.Medium),
        Font(R.font.space_grotesk, FontWeight.Bold),
    )
    val Mono: FontFamily = FontFamily.Monospace
}

object Type {
    val Screen = TextStyle(fontFamily = Face.Display, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Normal, color = Ink.Text)
    val Hero = TextStyle(fontFamily = Face.Display, fontSize = 38.sp, lineHeight = 39.sp, fontWeight = FontWeight.Normal, color = Ink.Text)
    val ShowTitle = TextStyle(fontFamily = Face.Display, fontSize = 20.sp, lineHeight = 22.sp, color = Ink.Text)
    val ShowTitleSm = TextStyle(fontFamily = Face.Display, fontSize = 17.sp, lineHeight = 19.sp, color = Ink.Text)
    val ShowTitleXs = TextStyle(fontFamily = Face.Display, fontSize = 15.sp, lineHeight = 17.sp, color = Ink.Muted)
    val Numeral = TextStyle(fontFamily = Face.Display, fontSize = 30.sp, lineHeight = 31.sp, color = Ink.Amber)

    val Body = TextStyle(fontFamily = Face.Ui, fontSize = 13.sp, lineHeight = 19.sp, color = Ink.Muted)
    val BodyTight = TextStyle(fontFamily = Face.Ui, fontSize = 12.sp, lineHeight = 16.sp, color = Ink.Muted)
    val Meta = TextStyle(fontFamily = Face.Ui, fontSize = 11.sp, lineHeight = 14.sp, color = Ink.Faint)
    val Label = TextStyle(fontFamily = Face.Ui, fontSize = 14.sp, lineHeight = 18.sp, color = Ink.Text)
    val Button = TextStyle(fontFamily = Face.Ui, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = Ink.Text)

    /** Small-caps section headings: "ALL OF YOU · 3". */
    val Eyebrow = TextStyle(
        fontFamily = Face.Ui, fontSize = 10.5.sp, lineHeight = 13.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = Ink.Faint,
    )
    val Badge = TextStyle(
        fontFamily = Face.Ui, fontSize = 9.5.sp, lineHeight = 12.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = Ink.Ground,
    )
}

object Dim {
    val Gutter = 18.dp
    val Touch = 44.dp
    val CardRadius = 15.dp
    val ChipRadius = 11.dp
    val BarHeight = 84.dp
}

/** Overflow default used everywhere a title might be too long for its row. */
val Clip = TextOverflow.Ellipsis

private val LocalNothing = staticCompositionLocalOf { Unit }

@Composable
fun WatchingTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNothing provides Unit, content = content)
}
