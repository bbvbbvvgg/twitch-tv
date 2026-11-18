package su.expensive.twitchtv.ui.core

import android.view.KeyEvent as AKeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import su.expensive.twitchtv.player.PlayStatus

/** Remote keys that reveal a design's player controls when nothing is shown yet. */
val PLAYER_OPEN_KEYS = setOf(
    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_NUMPAD_ENTER, AKeyEvent.KEYCODE_MENU,
    AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN, AKeyEvent.KEYCODE_DPAD_LEFT, AKeyEvent.KEYCODE_DPAD_RIGHT, AKeyEvent.KEYCODE_INFO,
)

/**
 * Remote handling every design shares: CH+/CH− switch streams, Play/Pause jumps back to the
 * live edge, and any navigation key reveals the design's controls when they are hidden.
 * [onAnyKey] lets designs reset their auto-hide timer.
 */
@UnstableApi
fun Modifier.playerKeys(s: PlayerSession, controlsVisible: () -> Boolean, onReveal: (Int) -> Unit, onAnyKey: () -> Unit = {}): Modifier =
    onPreviewKeyEvent { ev ->
        onAnyKey()
        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (val code = ev.key.nativeKeyCode) {
            AKeyEvent.KEYCODE_CHANNEL_UP -> { s.step(1); true }
            AKeyEvent.KEYCODE_CHANNEL_DOWN -> { s.step(-1); true }
            AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AKeyEvent.KEYCODE_MEDIA_PLAY -> { s.resumeLive(); true }
            else -> if (!controlsVisible() && code in PLAYER_OPEN_KEYS) { onReveal(code); true } else false
        }
    }

/**
 * The video surface. The PlayerView is bound to the session's ExoPlayer; when a design is
 * swapped only this view is recreated, never the player. While the stream is not yet playing
 * the channel's preview image is shown as a poster.
 */
@UnstableApi
@Composable
fun VideoSurface(s: PlayerSession, modifier: Modifier = Modifier.fillMaxSize(), poster: Boolean = true) {
    val ctx = LocalContext.current
    // Poster only until the first frame of the current channel; a later rebuffer keeps the frozen frame.
    var started by remember(s.item.login) { mutableStateOf(false) }
    if (s.controller.status == PlayStatus.PLAYING) started = true
    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    isFocusable = false
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    player = s.controller.player
                }
            },
            update = { if (it.player !== s.controller.player) it.player = s.controller.player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        val img = s.item.thumbnail
        if (poster && !started && img.isNotEmpty()) AsyncImage(
            model = ImageRequest.Builder(ctx).data(img).size(1280, 720).build(),
            contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(), alpha = 0.55f,
        )
    }
}

/** Status line every design shows while loading / reconnecting / offline; null while playing. */
@UnstableApi
fun statusMessage(s: PlayerSession): String? = when (val st = s.controller.status) {
    PlayStatus.PLAYING, PlayStatus.IDLE -> null
    PlayStatus.OFFLINE -> "${s.item.displayName}: трансляция не идёт"
    else -> s.controller.statusText.ifEmpty { if (st == PlayStatus.ERROR) "Ошибка воспроизведения" else "Загрузка…" }
}

/** Clears the session toast after a moment; designs render it their own way. */
@UnstableApi
@Composable
fun ToastTimer(s: PlayerSession) {
    LaunchedEffect(s.toast) { if (s.toast != null) { delay(2_500); s.toast = null } }
}
