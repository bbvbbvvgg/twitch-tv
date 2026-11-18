package su.expensive.twitchtv.ui.glass

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** Glass capsule button: translucent at rest, lifts into a lit white slab when focused. */
@Composable
fun GlassButton(
    text: String?,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    shape: Shape = CircleShape,
    padding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick, onLongClick = onLongClick, modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) G.fillHi else G.fill, contentColor = G.text,
            focusedContainerColor = G.solid, focusedContentColor = G.onSolid,
            pressedContainerColor = G.solid, pressedContentColor = G.onSolid,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.5f else 0.18f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.dp, Color.White), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(G.glow.copy(alpha = 0.55f), 14.dp)),
    ) {
        Row(Modifier.align(Alignment.Center).padding(padding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) Icon(icon, contentDescription = text, modifier = Modifier.size(24.dp))
            if (icon != null && text != null) Spacer(Modifier.width(10.dp))
            if (text != null) Text(text, style = GType.label, maxLines = 1)
        }
    }
}

/** Round icon-only glass control (player dock). */
@Composable
fun GlassOrb(icon: ImageVector, label: String, modifier: Modifier = Modifier, active: Boolean = false, size: Dp = 64.dp, onClick: () -> Unit) {
    GlassButton(null, icon, modifier.size(size), selected = active, padding = PaddingValues(0.dp), onClick = onClick)
}

/** Centered glass panel for empty, error and loading-failed states. */
@Composable
fun GlassNotice(icon: ImageVector, title: String, body: String?, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).glass(RoundedCornerShape(32.dp)).padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(64.dp).glass(CircleShape, 1.4f), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = G.text, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.size(16.dp))
            Text(title, style = GType.title, color = G.text, textAlign = TextAlign.Center)
            if (body != null) Text(body, style = GType.body, color = G.dim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            if (action != null && onAction != null) {
                Spacer(Modifier.size(20.dp))
                GlassButton(action, onClick = onAction)
            }
        }
    }
}

@Composable
fun GlassError(message: String, onRetry: () -> Unit) =
    GlassNotice(Icons.Rounded.CloudOff, "Нет связи с Twitch", message, "Повторить", onRetry)

/** Breathing glass placeholder used while lists load. */
@Composable
fun GlassPlaceholder(modifier: Modifier, shape: Shape) {
    val t = rememberInfiniteTransition(label = "ph")
    val a by t.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.alpha(a).glass(shape))
}

/** Small glass pill with a red live dot. */
@Composable
fun LivePill(text: String, modifier: Modifier = Modifier) {
    Row(modifier.glass(CircleShape, 1.3f).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(G.live, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = GType.small, color = G.text)
    }
}
