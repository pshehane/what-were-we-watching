package net.shehane.watching.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shehane.watching.model.Person
import net.shehane.watching.model.Profile
import net.shehane.watching.model.Service
import net.shehane.watching.ui.theme.Clip
import net.shehane.watching.ui.theme.Ink
import net.shehane.watching.ui.theme.Type
import net.shehane.watching.ui.theme.posterTintFor

/** "#E9A06A" to a Color. Falls back to muted ink rather than crashing on bad data. */
fun hexColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Ink.Faint
    val cleaned = hex.trim().removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return Ink.Faint
    return when (cleaned.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> Ink.Faint
    }
}

// --------------------------------------------------------------------- avatars

@Composable
fun Avatar(
    person: Person,
    size: Dp = 48.dp,
    seated: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colour = hexColor(person.color)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (seated) Modifier.background(colour)
                else Modifier.background(Ink.Surface).border(1.dp, Ink.Line, CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = person.name.take(1).uppercase(),
            style = TextStyle(
                fontSize = (size.value * 0.36f).sp,
                color = if (seated) Ink.Ground else Ink.Ghost,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/**
 * The overlapping row of who is on a show. Negative spacing does the overlap, so
 * the row still measures correctly and nothing is positioned by hand.
 */
@Composable
fun AvatarRow(
    people: List<Person>,
    size: Dp = 17.dp,
    ringColour: Color = Ink.Surface,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(-(size * 0.29f))) {
        for (person in people) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(ringColour)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(hexColor(person.color)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = person.name.take(1).uppercase(),
                    style = TextStyle(
                        fontSize = (size.value * 0.48f).sp,
                        color = Ink.Ground,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

// ------------------------------------------------------------------- the badge

/**
 * The two-part badge: which service, and which profile to pick once you are there.
 * This is the piece of information that actually gets you watching, so it is the
 * same object on every screen.
 */
@Composable
fun ServiceProfileBadge(
    service: Service?,
    profile: Profile?,
    small: Boolean = false,
) {
    if (service == null) return
    val tint = hexColor(service.tint)
    val h = if (small) 17.dp else 19.dp
    val radius = if (small) 4.dp else 5.dp

    Row(
        modifier = Modifier
            .height(h)
            .clip(RoundedCornerShape(radius))
            .border(1.dp, tint, RoundedCornerShape(radius)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .background(tint)
                .padding(horizontal = if (small) 4.dp else 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = service.name.uppercase(),
                style = Type.Badge.copy(fontSize = if (small) 8.5.sp else 9.5.sp),
                maxLines = 1,
            )
        }
        if (profile != null) {
            BasicText(
                text = profile.name,
                style = TextStyle(fontSize = if (small) 9.5.sp else 10.5.sp, color = tint),
                maxLines = 1,
                overflow = Clip,
                modifier = Modifier.padding(
                    start = if (small) 4.dp else 5.dp,
                    end = if (small) 5.dp else 6.dp,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------- poster

/**
 * Real artwork when TMDB gave us a path and the phone has already fetched it,
 * otherwise a flat tinted card. A placeholder that looks deliberate beats a grey
 * rectangle, and the list must never wait on the network to lay itself out.
 */
@Composable
fun Poster(
    title: String,
    posterPath: String?,
    width: Dp,
    height: Dp,
    showTitle: Boolean = false,
    corner: Dp = 8.dp,
    large: Boolean = false,
) {
    val (base, mark) = posterTintFor(title)
    val bitmap = rememberPoster(posterPath, large)

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(corner))
            .background(base),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = mark,
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * 0.55f, size.height * 0.38f),
                )
            }
            if (showTitle) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                ) {
                    BasicText(
                        text = title,
                        style = Type.ShowTitleXs.copy(fontSize = 10.sp, color = Ink.Text),
                        maxLines = 2,
                        overflow = Clip,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ small bits

/** "ALL OF YOU · 3" with a rule running to the right edge. */
@Composable
fun SectionHeader(
    label: String,
    colour: Color = Ink.Faint,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(7.dp))
        }
        BasicText(text = label, style = Type.Eyebrow.copy(color = colour))
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Ink.LineSoft),
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = Ink.Amber,
    height: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(11.dp))
            .then(
                if (filled) Modifier.background(tint)
                else Modifier.border(1.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(11.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                fontSize = 13.sp,
                color = if (filled) Ink.Ground else tint,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

/** A round 44dp target. Everything tappable in this app is at least this big. */
@Composable
fun RoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    filled: Boolean = false,
    tint: Color = Ink.Amber,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(tint)
                else Modifier.border(1.dp, tint.copy(alpha = 0.55f), CircleShape)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** A square-ish 44dp target for steppers. */
@Composable
fun SquareButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    filled: Boolean = false,
    tint: Color = Ink.Amber,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (filled) Modifier.background(tint)
                else Modifier.border(1.dp, Ink.Line, RoundedCornerShape(12.dp))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = Type.Body.copy(textAlign = TextAlign.Center, color = Ink.Faint))
    }
}

@Composable fun VGap(height: Dp) { Spacer(Modifier.height(height)) }
@Composable fun HGap(width: Dp) { Spacer(Modifier.width(width)) }

@Composable
fun Divider(colour: Color = Ink.LineSoft) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(colour))
}

// ------------------------------------------------------------------ line icons

/** Strokes on a square grid, so nothing here depends on an icon package. */
object Draw {

    @Composable
    fun Chevron(size: Dp = 16.dp, colour: Color = Ink.Ghost, pointsLeft: Boolean = false) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                if (pointsLeft) {
                    moveTo(w * 0.63f, h * 0.20f); lineTo(w * 0.34f, h * 0.5f); lineTo(w * 0.63f, h * 0.80f)
                } else {
                    moveTo(w * 0.37f, h * 0.20f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.37f, h * 0.80f)
                }
            }
            drawPath(path, colour, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round))
        }
    }

    @Composable
    fun Plus(size: Dp = 20.dp, colour: Color = Ink.Amber) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val s = w * 0.12f
            drawLine(colour, Offset(w * 0.5f, w * 0.22f), Offset(w * 0.5f, w * 0.78f), s, StrokeCap.Round)
            drawLine(colour, Offset(w * 0.22f, w * 0.5f), Offset(w * 0.78f, w * 0.5f), s, StrokeCap.Round)
        }
    }

    @Composable
    fun Minus(size: Dp = 20.dp, colour: Color = Ink.Text) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            drawLine(colour, Offset(w * 0.22f, w * 0.5f), Offset(w * 0.78f, w * 0.5f), w * 0.12f, StrokeCap.Round)
        }
    }

    @Composable
    fun Check(size: Dp = 18.dp, colour: Color = Ink.Amber) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.18f, h * 0.52f); lineTo(w * 0.40f, h * 0.75f); lineTo(w * 0.83f, h * 0.27f)
            }
            drawPath(path, colour, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round))
        }
    }

    @Composable
    fun Cross(size: Dp = 18.dp, colour: Color = Ink.Rust, circled: Boolean = false) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val s = w * 0.11f
            if (circled) {
                drawCircle(colour, radius = w * 0.38f, center = Offset(w / 2f, w / 2f), style = Stroke(s))
                drawLine(colour, Offset(w * 0.35f, w * 0.35f), Offset(w * 0.65f, w * 0.65f), s, StrokeCap.Round)
                drawLine(colour, Offset(w * 0.65f, w * 0.35f), Offset(w * 0.35f, w * 0.65f), s, StrokeCap.Round)
            } else {
                drawLine(colour, Offset(w * 0.26f, w * 0.26f), Offset(w * 0.74f, w * 0.74f), s, StrokeCap.Round)
                drawLine(colour, Offset(w * 0.74f, w * 0.26f), Offset(w * 0.26f, w * 0.74f), s, StrokeCap.Round)
            }
        }
    }

    /**
     * A crescent, drawn as a filled disc with a second disc punched out of it in
     * the surrounding colour. Cheaper and more predictable than path arithmetic.
     */
    @Composable
    fun Moon(size: Dp = 18.dp, colour: Color = Ink.Amber, behind: Color = Ink.Ground) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            drawCircle(colour, radius = w * 0.40f, center = Offset(w * 0.50f, w * 0.50f))
            drawCircle(behind, radius = w * 0.34f, center = Offset(w * 0.66f, w * 0.36f))
        }
    }

    @Composable
    fun Search(size: Dp = 20.dp, colour: Color = Ink.Muted) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val s = w * 0.09f
            drawCircle(colour, radius = w * 0.29f, center = Offset(w * 0.44f, w * 0.44f), style = Stroke(s))
            drawLine(colour, Offset(w * 0.66f, w * 0.66f), Offset(w * 0.88f, w * 0.88f), s, StrokeCap.Round)
        }
    }

    @Composable
    fun Gear(size: Dp = 20.dp, colour: Color = Ink.Muted) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val c = Offset(w / 2f, w / 2f)
            val s = w * 0.08f
            drawCircle(colour, radius = w * 0.15f, center = c, style = Stroke(s))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val inner = w * 0.26f
                val outer = w * 0.41f
                drawLine(
                    colour,
                    Offset(c.x + (Math.cos(a) * inner).toFloat(), c.y + (Math.sin(a) * inner).toFloat()),
                    Offset(c.x + (Math.cos(a) * outer).toFloat(), c.y + (Math.sin(a) * outer).toFloat()),
                    s, StrokeCap.Round,
                )
            }
        }
    }

    @Composable
    fun Couch(size: Dp = 22.dp, colour: Color = Ink.Amber) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val s = w * 0.085f
            val body = Path().apply {
                moveTo(w * 0.13f, w * 0.52f)
                cubicTo(w * 0.13f, w * 0.40f, w * 0.30f, w * 0.40f, w * 0.30f, w * 0.52f)
                lineTo(w * 0.30f, w * 0.64f)
                lineTo(w * 0.70f, w * 0.64f)
                lineTo(w * 0.70f, w * 0.52f)
                cubicTo(w * 0.70f, w * 0.40f, w * 0.87f, w * 0.40f, w * 0.87f, w * 0.52f)
                lineTo(w * 0.87f, w * 0.78f)
                lineTo(w * 0.13f, w * 0.78f)
                close()
            }
            drawPath(body, colour, style = Stroke(s, cap = StrokeCap.Round))
            val back = Path().apply {
                moveTo(w * 0.23f, w * 0.46f)
                lineTo(w * 0.23f, w * 0.34f)
                cubicTo(w * 0.23f, w * 0.26f, w * 0.30f, w * 0.26f, w * 0.35f, w * 0.26f)
                lineTo(w * 0.65f, w * 0.26f)
                cubicTo(w * 0.72f, w * 0.26f, w * 0.77f, w * 0.28f, w * 0.77f, w * 0.34f)
                lineTo(w * 0.77f, w * 0.46f)
            }
            drawPath(back, colour, style = Stroke(s, cap = StrokeCap.Round))
        }
    }

    @Composable
    fun Shelf(size: Dp = 22.dp, colour: Color = Ink.Faint) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val s = w * 0.085f
            drawRoundRect(
                colour,
                topLeft = Offset(w * 0.13f, w * 0.18f),
                size = Size(w * 0.74f, w * 0.19f),
                cornerRadius = CornerRadius(w * 0.05f),
                style = Stroke(s),
            )
            val box = Path().apply {
                moveTo(w * 0.20f, w * 0.37f); lineTo(w * 0.20f, w * 0.80f)
                lineTo(w * 0.80f, w * 0.80f); lineTo(w * 0.80f, w * 0.37f)
            }
            drawPath(box, colour, style = Stroke(s, cap = StrokeCap.Round))
            drawLine(colour, Offset(w * 0.41f, w * 0.55f), Offset(w * 0.59f, w * 0.55f), s, StrokeCap.Round)
        }
    }

    @Composable
    fun Undo(size: Dp = 16.dp, colour: Color = Ink.Muted) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val s = w * 0.11f
            drawArc(
                color = colour,
                startAngle = 150f, sweepAngle = 250f, useCenter = false,
                topLeft = Offset(w * 0.20f, w * 0.20f),
                size = Size(w * 0.60f, w * 0.60f),
                style = Stroke(s, cap = StrokeCap.Round),
            )
            val arrow = Path().apply {
                moveTo(w * 0.14f, w * 0.36f); lineTo(w * 0.40f, w * 0.36f); lineTo(w * 0.40f, w * 0.10f)
            }
            drawPath(arrow, colour, style = Stroke(s, cap = StrokeCap.Round))
        }
    }

    @Composable
    fun Clock(size: Dp = 17.dp, colour: Color = Ink.Faint) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val s = w * 0.085f
            drawCircle(colour, radius = w * 0.40f, center = Offset(w / 2f, w / 2f), style = Stroke(s))
            val hands = Path().apply {
                moveTo(w * 0.5f, w * 0.28f); lineTo(w * 0.5f, w * 0.52f); lineTo(w * 0.68f, w * 0.62f)
            }
            drawPath(hands, colour, style = Stroke(s, cap = StrokeCap.Round))
        }
    }

    @Composable
    fun External(size: Dp = 11.dp, colour: Color = Ink.Amber) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val s = w * 0.16f
            drawLine(colour, Offset(w * 0.30f, w * 0.70f), Offset(w * 0.72f, w * 0.28f), s, StrokeCap.Round)
            val corner = Path().apply {
                moveTo(w * 0.40f, w * 0.28f); lineTo(w * 0.72f, w * 0.28f); lineTo(w * 0.72f, w * 0.60f)
            }
            drawPath(corner, colour, style = Stroke(s, cap = StrokeCap.Round))
        }
    }

    /** The empty and filled states of the "this is the default" radio. */
    @Composable
    fun Radio(selected: Boolean, size: Dp = 19.dp) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val colour = if (selected) Ink.Amber else Ink.Line
            drawCircle(colour, radius = w * 0.44f, center = Offset(w / 2f, w / 2f), style = Stroke(w * 0.11f))
            if (selected) {
                drawCircle(Ink.Amber, radius = w * 0.22f, center = Offset(w / 2f, w / 2f))
            }
        }
    }
}

/** A rect helper kept next to the icons that use it. */
@Suppress("unused")
private fun rectOf(left: Float, top: Float, size: Float) = Rect(Offset(left, top), Size(size, size))
