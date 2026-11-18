package su.expensive.twitchtv.ui.glass

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import su.expensive.twitchtv.R

/**
 * Liquid Glass tokens. See docs/design/liquid-glass.md.
 *
 * Glass on API 30 without RenderEffect: the backdrop is the focused stream's preview decoded at
 * 32×18 px and upscaled with bilinear filtering — a free, GPU-cheap "blur" that never touches the
 * video pipeline. Panels are translucent fills with a top-lit sheen and a hairline rim.
 */
object G {
    val ink = Color(0xFF070A14)
    val text = Color(0xF2FFFFFF)
    val dim = Color(0xA6FFFFFF)
    val faint = Color(0x66FFFFFF)
    val fill = Color(0x1FFFFFFF)
    val fillHi = Color(0x3DFFFFFF)
    val solid = Color(0xF2F4F7FF)
    val onSolid = Color(0xFF0B1020)
    val live = Color(0xFFFF453A)
    val tint = Color(0xFF7FD4FF)
    val glow = Color(0xFF9FDBFF)

    fun sheen(alpha: Float = 1f) = Brush.verticalGradient(listOf(Color.White.copy(0.16f * alpha), Color.White.copy(0.04f * alpha)))
    fun rim(alpha: Float = 1f) = Brush.verticalGradient(listOf(Color.White.copy(0.42f * alpha), Color.White.copy(0.06f * alpha)))
}

val Onest = FontFamily(
    Font(R.font.onest, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.onest, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.onest, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.onest, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

object GType {
    val display = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.6).sp)
    val title = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp)
    val body = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp)
    val label = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
    val small = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp)
}

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = G.tint, background = G.ink, surface = G.ink, onSurface = G.text, border = Color.White),
        typography = Typography(
            headlineSmall = GType.title, titleMedium = GType.label, bodyMedium = GType.body,
            bodySmall = GType.small, labelLarge = GType.label, bodyLarge = GType.body,
        ),
        content = content,
    )
}

/** A static glass slab: translucent fill, top-lit sheen, hairline rim. Cheap: no offscreen blur. */
fun Modifier.glass(shape: Shape, strength: Float = 1f, rim: Dp = 1.dp): Modifier = this
    .background(Color.White.copy(alpha = 0.10f * strength), shape)
    .background(G.sheen(strength), shape)
    .border(rim, G.rim(strength), shape)

/**
 * The scene behind every Glass screen: the focused stream's preview, decoded tiny and stretched
 * (soft blur on any GPU), under a deep ink gradient. Crossfades when focus moves.
 */
@Composable
fun GlassBackdrop(image: String?) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(G.ink)) {
        Crossfade(targetState = image, animationSpec = tween(600), label = "backdrop") { url ->
            if (!url.isNullOrEmpty()) AsyncImage(
                model = ImageRequest.Builder(ctx).data(url).size(32, 18).build(),
                contentDescription = null, contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low, alpha = 0.85f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66070A14), Color(0xB3070A14), Color(0xF2070A14)))))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x2E7FD4FF), Color.Transparent), radius = 1400f)))
    }
}
