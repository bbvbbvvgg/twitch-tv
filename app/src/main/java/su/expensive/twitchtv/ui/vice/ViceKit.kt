package su.expensive.twitchtv.ui.vice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import su.expensive.twitchtv.R

/**
 * GTA VI / Vice City design tokens. Dusk over the Atlantic: violet night, a magenta→coral
 * horizon, a sliced sun, teal neon as the second voice. Type: Oswald (condensed, Cyrillic) for
 * everything that shouts, Yellowtail script only for the Latin "Vice" wordmark.
 * All artwork is vector geometry drawn in code — no game assets. See docs/design/gta-vi.md.
 */
object V {
    val night = Color(0xFF120A1F)
    val dusk = Color(0xFF2A0F3D)
    val plum = Color(0xFF4A1650)
    val magenta = Color(0xFFFF3D8B)
    val coral = Color(0xFFFF7A59)
    val sun = Color(0xFFFFC36B)
    val teal = Color(0xFF2DE2D0)
    val cream = Color(0xFFFFF4E8)
    val dim = Color(0xFFCDB8DC)
    val faint = Color(0xFF8C7699)
    val panel = Color(0xE6150B24)
    val dusky = Brush.horizontalGradient(listOf(magenta, coral))
}

val Oswald = FontFamily(
    Font(R.font.oswald, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.oswald, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.oswald, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.oswald, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.oswald, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
val Script = FontFamily(Font(R.font.yellowtail))

object VType {
    val mega = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 58.sp, letterSpacing = 0.5.sp)
    val menu = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = 1.sp)
    val title = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = 0.5.sp)
    val row = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = 0.5.sp)
    val tag = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 2.sp)
    val body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
    val small = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp)
    fun neon(c: Color, r: Float = 18f) = Shadow(c.copy(alpha = 0.85f), Offset.Zero, r)
}

@Composable
fun ViceTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = V.magenta, background = V.night, surface = V.dusk, onSurface = V.cream, border = V.cream),
    typography = Typography(headlineSmall = VType.title, titleMedium = VType.row, bodyMedium = VType.body, bodySmall = VType.small,
        labelLarge = VType.tag, bodyLarge = VType.body),
    content = content,
)

/** Parallelogram — the menu's highlight bar, leaning forward like a speeding sign. */
class SlantShape(private val lean: Float = 0.18f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = size.height * lean
        return Outline.Generic(Path().apply {
            moveTo(d, 0f); lineTo(size.width, 0f); lineTo(size.width - d, size.height); lineTo(0f, size.height); close()
        })
    }
}

/**
 * The dusk scene: violet sky into a magenta/coral horizon, a sun cut by horizontal slits, and
 * still water with reflection bars. Pure geometry, cached by size; [sunScale] grows it for the
 * Leonida (GTA VI) section. The shimmer on the water is the only motion and is optional.
 */
@Composable
fun DuskBackdrop(sunScale: Float = 1f, shimmer: Boolean = true, horizon: Float = 0.64f, sunX: Float = 0.74f) {
    val t = rememberInfiniteTransition(label = "water")
    val phase by if (shimmer) t.animateFloat(0f, 1f, infiniteRepeatable(tween(5200), RepeatMode.Reverse), label = "p")
    else remember { mutableStateOf(0.5f) }
    val s by animateDpAsState((150 * sunScale).dp, spring(dampingRatio = 0.7f), label = "sun")
    Canvas(Modifier.fillMaxSize()) {
        val h = size.height; val w = size.width; val hy = h * horizon
        drawRect(Brush.verticalGradient(0f to V.night, 0.38f to V.dusk, horizon * 0.86f to V.plum, horizon to V.coral))
        val r = s.toPx()
        val c = Offset(w * sunX, hy)
        drawCircle(Brush.verticalGradient(listOf(V.sun, V.coral, V.magenta), c.y - r, c.y + r * 0.2f), r, c)
        // Slits across the lower half of the sun, widening toward the horizon.
        for (i in 0 until 6) {
            val y = c.y - r * 0.55f + i * r * 0.11f
            drawRect(V.plum, Offset(c.x - r, y), Size(r * 2, (2f + i * 1.6f) * density))
        }
        // Water
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3A1240), V.night), hy, h), Offset(0f, hy), Size(w, h - hy))
        for (i in 0 until 9) {
            val y = hy + (i + 1) * (h - hy) / 11f
            val half = r * (1.1f - i * 0.09f) * (0.85f + 0.3f * phase)
            drawRect(V.coral.copy(alpha = 0.55f - i * 0.05f), Offset(c.x - half, y), Size(half * 2, (1.5f + i * 0.4f) * density))
        }
        // Horizon glow line
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, V.sun.copy(alpha = 0.7f), Color.Transparent)), Offset(0f, hy - density), Size(w, 2 * density))
    }
}

/** Neon-sign heading: cream text with a coloured glow and a short underline bar. */
@Composable
fun NeonTitle(text: String, color: Color = V.magenta, modifier: Modifier = Modifier, style: TextStyle = VType.mega) {
    Column(modifier) {
        Text(text.uppercase(), style = style.copy(shadow = VType.neon(color, 28f)), color = V.cream)
        Box(Modifier.padding(top = 4.dp).width(72.dp).height(4.dp).background(color))
    }
}

/** The Vice menu item: slides forward and lights a slanted dusk bar when focused. */
@Composable
fun MenuItem(text: String, selected: Boolean, modifier: Modifier = Modifier, style: TextStyle = VType.menu, onFocus: (Boolean) -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shift by animateDpAsState(if (focused) 14.dp else 0.dp, spring(dampingRatio = 0.55f, stiffness = 500f), label = "shift")
    Surface(
        onClick = onClick,
        modifier = modifier.offset(x = shift).onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) },
        shape = ClickableSurfaceDefaults.shape(SlantShape()),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = if (selected) V.cream else V.dim,
            focusedContainerColor = V.magenta, focusedContentColor = V.night),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 26.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 6.dp, height = 22.dp).alpha(if (selected && !focused) 1f else 0f).background(V.teal))
            Spacer(Modifier.width(12.dp))
            Text(text.uppercase(), style = style)
        }
    }
}

/** Framed notice for empty / error / loading — a lit sign on the dark water. */
@Composable
fun ViceSign(title: String, body: String? = null, accent: Color = V.teal, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.padding(top = 30.dp).widthIn(max = 560.dp).border(2.dp, accent, SlantShape(0.04f)).background(V.panel, SlantShape(0.04f))
        .padding(horizontal = 30.dp, vertical = 24.dp)) {
        Text(title.uppercase(), style = VType.title.copy(shadow = VType.neon(accent, 16f)), color = V.cream)
        if (body != null) Text(body, style = VType.body, color = V.dim, modifier = Modifier.padding(top = 8.dp))
        if (action != null && onAction != null) MenuItem(action, false, Modifier.padding(top = 14.dp), VType.row) { onAction() }
    }
}

/** Animated "tuning" bars while a list loads. */
@Composable
fun ViceLoading(text: String = "Ловим сигнал") {
    val t = rememberInfiniteTransition(label = "tune")
    Row(Modifier.padding(top = 36.dp), verticalAlignment = Alignment.Bottom) {
        repeat(5) { i ->
            val h by t.animateFloat(8f, 34f, infiniteRepeatable(tween(420 + i * 90), RepeatMode.Reverse), label = "b$i")
            Box(Modifier.padding(end = 6.dp).width(8.dp).height(h.dp).background(if (i % 2 == 0) V.magenta else V.teal))
        }
        Text(text.uppercase(), style = VType.title, color = V.cream, modifier = Modifier.padding(start = 14.dp))
    }
}
