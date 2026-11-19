package su.expensive.twitchtv.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import su.expensive.twitchtv.net.Connection
import su.expensive.twitchtv.net.ConnectionMode
import su.expensive.twitchtv.ui.core.ConsentText
import su.expensive.twitchtv.ui.core.NetTextField
import su.expensive.twitchtv.ui.core.SettingsModel
import su.expensive.twitchtv.ui.core.rememberProxyForm

/** Glass: connection modes as large lit capsules with a one-line caption each. */
@UnstableApi
@Composable
fun GlassModeSheet(m: SettingsModel, onDone: () -> Unit) {
    Connection.version
    val fr = remember { FocusRequester() }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Подключение", style = GType.title, color = G.text)
        Text("Видео всегда идёт напрямую с CDN Twitch. Режим меняет только путь плейлиста.", style = GType.small, color = G.dim,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        ConnectionMode.entries.forEach { mode ->
            val sel = Connection.settings.mode == mode
            GlassButton(mode.title, if (sel) Icons.Rounded.Check else null,
                Modifier.fillMaxWidth().padding(top = 6.dp).then(if (sel) Modifier.focusRequester(fr) else Modifier),
                selected = sel, shape = RoundedCornerShape(22.dp)) { m.chooseMode(mode); onDone() }
            Text(glassHint(mode), style = GType.small, color = G.faint, modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp))
        }
    }
    LaunchedEffect(Unit) { delay(60); runCatching { fr.requestFocus() } }
}

private fun glassHint(mode: ConnectionMode) = when (mode) {
    ConnectionMode.DIRECT -> "Сеть Android. Happ или другой VPN работают как обычно"
    ConnectionMode.REYOHOHO -> "Сторонний сервер rte.net.ru выдаёт плейлист. Нужно согласие"
    ConnectionMode.CUSTOM -> "Ваш HTTP или SOCKS5 сервер"
}

/** Glass: proxy form on the floating sheet, fields are frosted capsules that light up on focus. */
@UnstableApi
@Composable
fun GlassProxySheet(m: SettingsModel, onClose: () -> Unit) {
    val f = rememberProxyForm()
    val first = remember { FocusRequester() }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Свой прокси", style = GType.title, color = G.text)
        Text("Только для gql.twitch.tv и usher.ttvnw.net. Видео идёт напрямую.", style = GType.small, color = G.dim,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        GlassButton(f.kind.title, modifier = Modifier.fillMaxWidth().focusRequester(first), shape = RoundedCornerShape(22.dp)) { f.toggleKind() }
        GlassField("Адрес host:порт", f.address) { f.address = it }
        GlassField("Логин", f.user) { f.user = it }
        GlassField(f.passwordHint, f.password, secret = true) { f.password = it }
        f.error?.let { Text(it, style = GType.small, color = G.live, modifier = Modifier.padding(top = 8.dp)) }
        Text("Пароль хранится зашифрованным (Android Keystore) и не показывается.", style = GType.small, color = G.faint,
            modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton("Сохранить", selected = true) { f.build()?.let { m.saveProxy(it); onClose() } }
            GlassButton("Удалить") { m.clearProxy(); onClose() }
        }
    }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }
}

@Composable
private fun GlassField(label: String, value: String, secret: Boolean = false, onValue: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 12.dp)) {
        Text(label, style = GType.small, color = G.dim, modifier = Modifier.padding(start = 18.dp, bottom = 4.dp))
        Box(Modifier.fillMaxWidth()
            .background(if (focused) G.solid else G.fill, RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = if (focused) 1f else 0.2f), RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp)) {
            NetTextField(value, onValue, GType.body.copy(color = if (focused) G.onSolid else G.text), G.tint,
                Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }, secret = secret)
        }
    }
}

/** Glass: the warning floats as a thick glass card over a dimmed, blurred-looking scene. */
@UnstableApi
@Composable
fun GlassConsent(m: SettingsModel) {
    val cancel = remember { FocusRequester() }
    BackHandler { m.declineConsent() }
    Box(Modifier.fillMaxSize().background(Color(0xB3070A14)), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(40.dp)
        Column(Modifier.width(860.dp).background(G.ink.copy(alpha = 0.92f), shape).glass(shape, 1.6f).padding(44.dp)
            .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
            Text(ConsentText.TITLE, style = GType.display.copy(fontSize = 36.sp), color = G.text)
            Column(Modifier.padding(top = 18.dp).weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConsentText.BODY.forEach { Text(it, style = GType.body.copy(fontSize = 20.sp, lineHeight = 28.sp), color = G.text) }
                Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), 0.8f).padding(18.dp)) {
                    Text(ConsentText.DETAILS, style = GType.small.copy(fontSize = 16.sp, lineHeight = 22.sp), color = G.dim)
                }
            }
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassButton(ConsentText.CANCEL, modifier = Modifier.focusRequester(cancel)) { m.declineConsent() }
                GlassButton(ConsentText.ACCEPT, selected = true) { m.acceptConsent() }
            }
        }
    }
    LaunchedEffect(Unit) { delay(60); runCatching { cancel.requestFocus() } }
}
