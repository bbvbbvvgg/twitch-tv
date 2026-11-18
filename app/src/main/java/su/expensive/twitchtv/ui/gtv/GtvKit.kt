package su.expensive.twitchtv.ui.gtv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import su.expensive.twitchtv.data.StreamItem
import su.expensive.twitchtv.ui.core.formatViewers

/**
 * Google TV design tokens. Platform face (Roboto) on Material roles, content-first: imagery is
 * the colour, chrome stays neutral grey; focus is a white frame + 1.1× lift. See docs/design/google-tv.md.
 */
object T {
    val bg = Color(0xFF0E1013)
    val surface = Color(0xFF1C1F24)
    val surfaceHi = Color(0xFF2A2E35)
    val text = Color(0xFFE8EAED)
    val dim = Color(0xFF9AA0A6)
    val faint = Color(0xFF5F6368)
    val accent = Color(0xFF8AB4F8)
    val live = Color(0xFFEA4335)
    val focus = Color(0xFFF1F3F4)
    val onFocus = Color(0xFF202124)
}

object TType {
    private val f = FontFamily.SansSerif
    val hero = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp)
    val headline = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp)
    val rowTitle = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 24.sp)
    val body = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
    val label = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
fun GtvTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = T.accent, background = T.bg, surface = T.surface, onSurface = T.text,
        surfaceVariant = T.surfaceHi, onSurfaceVariant = T.dim, border = T.focus),
    typography = Typography(headlineSmall = TType.headline, titleMedium = TType.rowTitle, bodyMedium = TType.body,
        bodySmall = TType.caption, labelLarge = TType.label, bodyLarge = TType.body),
    content = content,
)

/** Pill button: grey at rest, white with dark text when focused (Google TV focus language). */
@Composable
fun Pill(text: String?, icon: ImageVector? = null, modifier: Modifier = Modifier, selected: Boolean = false,
         padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp), onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (selected) T.surfaceHi else Color.Transparent,
            contentColor = if (selected) T.text else T.dim, focusedContainerColor = T.focus, focusedContentColor = T.onFocus),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(Modifier.padding(padding), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, text, Modifier.size(22.dp))
            if (icon != null && text != null) Spacer(Modifier.width(8.dp))
            if (text != null) Text(text, style = TType.label, maxLines = 1)
        }
    }
}

/** Solid action button used on the hero ("Смотреть"). */
@Composable
fun HeroButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, primary: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (primary) Color(0x33FFFFFF) else Color(0x1FFFFFFF),
            contentColor = T.text, focusedContainerColor = T.focus, focusedContentColor = T.onFocus),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, style = TType.label)
        }
    }
}

/**
 * Landscape content card. Name and viewers sit under the image; the stream title appears only
 * for the focused card, the way Google TV reveals detail on focus.
 */
@Composable
fun MediaCard(s: StreamItem, width: Dp, modifier: Modifier = Modifier, showProgress: Boolean = false, favorite: Boolean = false,
              onFocus: () -> Unit = {}, onLong: () -> Unit = {}, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.width(width)) {
        Surface(
            onClick = onClick, onLongClick = onLong,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = T.surface, focusedContainerColor = T.surface),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, T.focus), shape = shape)),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(ImageRequest.Builder(ctx).data(s.thumbnail.ifEmpty { s.avatar }).size(480, 270).build(), null,
                    contentScale = if (s.thumbnail.isNotEmpty()) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                    alpha = if (s.live) 1f else 0.5f)
                if (s.live) Text("LIVE", style = TType.caption.copy(fontWeight = FontWeight.Bold), color = Color.White,
                    modifier = Modifier.padding(8.dp).background(T.live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
                if (favorite) Icon(androidx.compose.material.icons.Icons.Rounded.Star, "В избранном", tint = Color(0xFFFDD663),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp))
                if (showProgress && s.live) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(T.live))
            }
        }
        Text(s.displayName, style = TType.label, color = if (focused) T.text else T.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp))
        Text(if (focused) s.title.ifEmpty { s.game } else if (s.live) "${formatViewers(s.viewers)} зрителей" else "не в эфире",
            style = TType.caption, color = T.faint.takeIf { !focused } ?: T.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp))
    }
}

/** Round channel avatar with a live ring (Google TV "channels" row). */
@Composable
fun ChannelBubble(s: StreamItem, onFocus: () -> Unit = {}, onLong: () -> Unit = {}, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(118.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick, onLongClick = onLong,
            modifier = Modifier.size(96.dp).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = T.surface, focusedContainerColor = T.surface),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(3.dp, if (s.live) T.live else T.surfaceHi), shape = CircleShape),
                focusedBorder = Border(BorderStroke(3.dp, T.focus), shape = CircleShape)),
        ) {
            AsyncImage(ImageRequest.Builder(ctx).data(s.avatar).size(150, 150).build(), s.displayName,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(5.dp).clip(CircleShape), alpha = if (s.live) 1f else 0.5f)
        }
        Text(s.displayName, style = TType.caption, color = if (focused) T.text else T.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp))
    }
}

/** Cinematic image with Google TV's left + bottom scrims for legible text over artwork. */
@Composable
fun ScrimmedArt(url: String, modifier: Modifier, shape: Shape = RoundedCornerShape(0.dp)) {
    val ctx = LocalContext.current
    Box(modifier.clip(shape)) {
        AsyncImage(ImageRequest.Builder(ctx).data(url).size(1280, 720).build(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to T.bg, 0.45f to T.bg.copy(alpha = 0.75f), 0.8f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to T.bg)))
    }
}

@Composable
fun GtvMessage(title: String, body: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp, start = 4.dp)) {
        Text(title, style = TType.headline, color = T.text)
        if (body != null) Text(body, style = TType.body, color = T.dim, modifier = Modifier.padding(top = 6.dp))
        if (action != null && onAction != null) Pill(action, modifier = Modifier.padding(top = 16.dp), selected = true, onClick = onAction)
    }
}

/** Grey skeleton row while a carousel loads. */
@Composable
fun SkeletonRow(cardWidth: Dp, count: Int = 6) {
    Row {
        repeat(count) {
            Column(Modifier.padding(end = 18.dp).width(cardWidth)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(T.surface, RoundedCornerShape(12.dp)))
                Box(Modifier.padding(top = 10.dp).width(cardWidth * 0.6f).height(14.dp).background(T.surface, RoundedCornerShape(4.dp)))
            }
        }
    }
}
