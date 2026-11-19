package su.expensive.twitchtv.ui.gtv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
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
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import su.expensive.twitchtv.net.Connection
import su.expensive.twitchtv.ui.core.ConsentText
import su.expensive.twitchtv.ui.core.NetTextField
import su.expensive.twitchtv.ui.core.SettingsModel
import su.expensive.twitchtv.ui.core.rememberProxyForm

/** Google TV: the proxy form opens in the detail pane, Material-style filled fields. */
@UnstableApi
@Composable
fun GtvProxyForm(m: SettingsModel, onClose: () -> Unit) {
    val f = rememberProxyForm()
    Column(Modifier.padding(start = 18.dp, top = 6.dp, bottom = 10.dp)) {
        Pill("Тип: ${f.kind.title}", onClick = { f.toggleKind() })
        GtvField("Адрес (host:порт)", f.address) { f.address = it }
        GtvField("Логин", f.user) { f.user = it }
        GtvField(f.passwordHint, f.password, secret = true) { f.password = it }
        f.error?.let { Text(it, style = TType.caption, color = T.live, modifier = Modifier.padding(top = 6.dp)) }
        Text("Через прокси идут только запросы плейлиста. Пароль зашифрован и не показывается.",
            style = TType.caption, color = T.dim, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Сохранить", selected = true) { f.build()?.let { m.saveProxy(it); onClose() } }
            Pill("Удалить") { m.clearProxy(); onClose() }
            Pill("Отмена") { onClose() }
        }
    }
}

@Composable
private fun GtvField(label: String, value: String, secret: Boolean = false, onValue: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 10.dp).width(560.dp)
        .background(if (focused) T.surfaceHi else T.bg, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = TType.caption, color = if (focused) T.accent else T.dim)
        NetTextField(value, onValue, TType.body.copy(color = T.text, fontSize = 18.sp), T.accent,
            Modifier.fillMaxWidth().padding(top = 2.dp).onFocusChanged { focused = it.isFocused }, secret = secret)
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(if (focused) 2.dp else 1.dp).background(if (focused) T.accent else T.faint))
    }
}

/** Google TV: system-style confirmation — icon and title on the left, text and actions on the right. */
@UnstableApi
@Composable
fun GtvConsent(m: SettingsModel) {
    Connection.version
    val cancel = remember { FocusRequester() }
    BackHandler { m.declineConsent() }
    Box(Modifier.fillMaxSize().background(Color(0xFF0E1013))) {
        Row(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 72.dp)
            .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
            Column(Modifier.width(380.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.Shield, null, Modifier.size(56.dp), tint = T.accent)
                Text(ConsentText.TITLE, style = TType.hero.copy(fontSize = 36.sp, lineHeight = 42.sp), color = T.text,
                    modifier = Modifier.padding(top = 18.dp))
                Text("Reyohoho Proxy", style = TType.headline, color = T.dim, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.width(48.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ConsentText.BODY.forEach { Text(it, style = TType.body.copy(fontSize = 20.sp, lineHeight = 28.sp), color = T.text) }
                    Text(ConsentText.DETAILS, style = TType.body.copy(fontSize = 16.sp, lineHeight = 23.sp), color = T.dim,
                        modifier = Modifier.background(T.surface, RoundedCornerShape(16.dp)).border(1.dp, T.surfaceHi, RoundedCornerShape(16.dp)).padding(18.dp))
                }
                Column(Modifier.padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(ConsentText.CANCEL, modifier = Modifier.width(360.dp).focusRequester(cancel)) { m.declineConsent() }
                    Pill(ConsentText.ACCEPT, modifier = Modifier.width(360.dp), selected = true) { m.acceptConsent() }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(60); runCatching { cancel.requestFocus() } }
}
