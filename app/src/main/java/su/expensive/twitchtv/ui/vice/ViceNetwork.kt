package su.expensive.twitchtv.ui.vice

import android.view.KeyEvent as AKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import su.expensive.twitchtv.net.Connection
import su.expensive.twitchtv.net.ConnectionMode
import su.expensive.twitchtv.ui.core.ConsentText
import su.expensive.twitchtv.ui.core.NetTextField
import su.expensive.twitchtv.ui.core.SettingsModel
import su.expensive.twitchtv.ui.core.rememberProxyForm

/**
 * GTA VI: the route selector is a "radio dial" — ←/→ tunes a candidate, OK locks it in.
 * Locking Reyohoho without consent brings up the warning; the dial never applies it on its own.
 */
@UnstableApi
@Composable
fun ViceModeRow(m: SettingsModel) {
    Connection.version
    val modes = ConnectionMode.entries
    val active = Connection.settings.mode
    var cand by remember(active) { mutableIntStateOf(active.ordinal) }
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { m.chooseMode(modes[cand]) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (!it.isFocused) cand = active.ordinal }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    AKeyEvent.KEYCODE_DPAD_LEFT -> { cand = (cand + modes.size - 1) % modes.size; true }
                    AKeyEvent.KEYCODE_DPAD_RIGHT -> { cand = (cand + 1) % modes.size; true }
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(SlantShape(0.06f)),
        colors = ClickableSurfaceDefaults.colors(containerColor = V.panel.copy(alpha = 0.6f), contentColor = V.cream,
            focusedContainerColor = V.cream, focusedContentColor = V.night),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("МАРШРУТ", style = VType.row.copy(fontSize = 19.sp))
                Text(if (cand != active.ordinal) "OK — включить «${modes[cand].short}»" else "Сейчас: ${active.title}",
                    style = VType.small, color = LocalContentColor.current.copy(alpha = 0.7f))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "назад", Modifier.size(26.dp).alpha(if (focused) 1f else 0.35f))
            Text(modes[cand].short.uppercase(), style = VType.row.copy(fontSize = 19.sp),
                color = if (cand != active.ordinal) V.coral else if (focused) V.magenta else V.teal,
                modifier = Modifier.width(210.dp).padding(horizontal = 6.dp), maxLines = 1, textAlign = TextAlign.Center)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "вперёд", Modifier.size(26.dp).alpha(if (focused) 1f else 0.35f))
        }
    }
}

/** GTA VI: proxy form as a "safehouse terminal" board under the row. */
@UnstableApi
@Composable
fun ViceProxyBoard(m: SettingsModel, onClose: () -> Unit) {
    val f = rememberProxyForm()
    Column(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 10.dp).border(2.dp, V.teal, SlantShape(0.03f))
        .background(V.panel, SlantShape(0.03f)).padding(horizontal = 26.dp, vertical = 18.dp)) {
        Text("СВОЙ ПРОКСИ", style = VType.title.copy(shadow = VType.neon(V.teal, 14f)), color = V.cream)
        Text("Только плейлист: gql.twitch.tv и usher.ttvnw.net. Видео идёт напрямую.", style = VType.small, color = V.dim)
        MenuItem("Тип: ${f.kind.title}", false, Modifier.padding(top = 8.dp), VType.row) { f.toggleKind() }
        ViceField("АДРЕС HOST:ПОРТ", f.address) { f.address = it }
        ViceField("ЛОГИН", f.user) { f.user = it }
        ViceField(f.passwordHint.uppercase(), f.password, secret = true) { f.password = it }
        f.error?.let { Text(it, style = VType.small, color = V.coral, modifier = Modifier.padding(top = 6.dp)) }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MenuItem("Сохранить", true, style = VType.row) { f.build()?.let { m.saveProxy(it); onClose() } }
            MenuItem("Удалить", false, style = VType.row) { m.clearProxy(); onClose() }
            MenuItem("Отмена", false, style = VType.row) { onClose() }
        }
    }
}

@Composable
private fun ViceField(label: String, value: String, secret: Boolean = false, onValue: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 10.dp).width(560.dp)) {
        Text(label, style = VType.tag, color = if (focused) V.magenta else V.teal)
        Box(Modifier.fillMaxWidth().padding(top = 4.dp).background(if (focused) V.cream else V.night.copy(alpha = 0.7f), SlantShape(0.03f))
            .padding(horizontal = 16.dp, vertical = 10.dp)) {
            NetTextField(value, onValue, VType.row.copy(fontSize = 20.sp, color = if (focused) V.night else V.cream), V.magenta,
                Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }, secret = secret)
        }
    }
}

/** GTA VI: the warning as a pause-screen "mission briefing" on the night water, coral border. */
@UnstableApi
@Composable
fun ViceConsent(m: SettingsModel) {
    val cancel = remember { FocusRequester() }
    BackHandler { m.declineConsent() }
    Box(Modifier.fillMaxSize().background(Color(0xE6120A1F)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(900.dp).border(3.dp, V.coral, SlantShape(0.03f)).background(V.panel, SlantShape(0.03f))
            .padding(horizontal = 48.dp, vertical = 34.dp)
            .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
            Text("ВНИМАНИЕ", style = VType.tag, color = V.coral)
            Text(ConsentText.TITLE.uppercase(), style = VType.mega.copy(fontSize = 42.sp, lineHeight = 48.sp, shadow = VType.neon(V.coral, 24f)), color = V.cream)
            Box(Modifier.padding(top = 6.dp).width(96.dp).height(4.dp).background(V.dusky))
            Column(Modifier.padding(top = 18.dp).weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ConsentText.BODY.forEach { Text(it, style = VType.body.copy(fontSize = 20.sp, lineHeight = 28.sp), color = V.cream) }
                Text(ConsentText.DETAILS, style = VType.body.copy(fontSize = 16.sp, lineHeight = 22.sp), color = V.dim,
                    modifier = Modifier.padding(top = 4.dp).border(1.dp, V.teal.copy(alpha = 0.6f), SlantShape(0.02f)).padding(16.dp))
            }
            Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuItem(ConsentText.CANCEL, false, Modifier.focusRequester(cancel), VType.row) { m.declineConsent() }
                MenuItem(ConsentText.ACCEPT, true, style = VType.row) { m.acceptConsent() }
            }
        }
    }
    LaunchedEffect(Unit) { delay(60); runCatching { cancel.requestFocus() } }
}
