package su.expensive.twitchtv.ui.classic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import su.expensive.twitchtv.net.Connection
import su.expensive.twitchtv.net.ConnectionMode
import su.expensive.twitchtv.ui.core.ConsentText
import su.expensive.twitchtv.ui.core.NetTextField
import su.expensive.twitchtv.ui.core.SettingsModel
import su.expensive.twitchtv.ui.core.rememberProxyForm

/** Classic: the connection modes as a plain inline list under the row, like the design list. */
@UnstableApi
@Composable
fun ClassicModeList(m: SettingsModel, onPicked: () -> Unit) {
    Connection.version
    Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp)) {
        ConnectionMode.entries.forEach { mode ->
            val on = Connection.settings.mode == mode
            ListItem(
                selected = on, onClick = { m.chooseMode(mode); onPicked() },
                headlineContent = { Text(mode.title) },
                supportingContent = { Text(modeHint(mode), color = Palette.dim) },
                trailingContent = { if (on) Text("Выбран", color = Palette.accent) },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

fun modeHint(mode: ConnectionMode) = when (mode) {
    ConnectionMode.DIRECT -> "Стандартная сеть Android. Если включён Happ или другой VPN, маршрут выбирает он"
    ConnectionMode.REYOHOHO -> "Сторонний сервис: плейлист и качество через rte.net.ru, видео напрямую. Нужно согласие"
    ConnectionMode.CUSTOM -> "Ваш HTTP или SOCKS5 сервер для запросов плейлиста"
}

/** Classic: inline proxy form. Plain outlined fields, OK on a field opens the TV keyboard. */
@UnstableApi
@Composable
fun ClassicProxyPanel(m: SettingsModel, onClose: () -> Unit) {
    val f = rememberProxyForm()
    val style = TextStyle(fontSize = 18.sp, color = Palette.text)
    Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ListItem(selected = false, onClick = { f.toggleKind() }, headlineContent = { Text("Тип") },
            trailingContent = { Text(f.kind.title, color = Palette.accent) })
        ClassicField("Адрес (host:порт)", f.address, { f.address = it }, style)
        ClassicField("Логин (необязательно)", f.user, { f.user = it }, style)
        ClassicField(f.passwordHint, f.password, { f.password = it }, style, secret = true)
        f.error?.let { Text(it, color = Palette.live, style = MaterialTheme.typography.bodySmall) }
        Text("Через прокси идут только gql.twitch.tv и usher.ttvnw.net. Пароль хранится зашифрованным и нигде не показывается.",
            color = Palette.dim, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { f.build()?.let { m.saveProxy(it); onClose() } }) { Text("Сохранить") }
            OutlinedButton(onClick = { m.clearProxy(); onClose() }) { Text("Удалить") }
            OutlinedButton(onClick = onClose) { Text("Закрыть") }
        }
    }
}

@Composable
private fun ClassicField(label: String, value: String, onValue: (String) -> Unit, style: TextStyle, secret: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Text(label, color = Palette.dim, style = MaterialTheme.typography.bodySmall)
        Box(Modifier.width(520.dp).padding(top = 4.dp)
            .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Palette.accent else Palette.surfaceHi), RoundedCornerShape(8.dp))
            .background(Palette.surface, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
            NetTextField(value, onValue, style, Palette.accent, Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }, secret = secret)
        }
    }
}

/** Classic: a centred dark dialog with the standard buttons; Back = «Отмена». */
@UnstableApi
@Composable
fun ClassicConsent(m: SettingsModel) {
    val cancel = remember { FocusRequester() }
    BackHandler { m.declineConsent() }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(820.dp).background(Palette.surface, RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Palette.surfaceHi), RoundedCornerShape(12.dp)).padding(36.dp)
            .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
            Text(ConsentText.TITLE, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.text)
            Column(Modifier.padding(top = 18.dp).verticalScroll(rememberScrollState()).weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConsentText.BODY.forEach { Text(it, fontSize = 20.sp, color = Palette.text, lineHeight = 28.sp) }
                Text(ConsentText.DETAILS, fontSize = 16.sp, color = Palette.dim, lineHeight = 23.sp)
            }
            Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { m.declineConsent() }, modifier = Modifier.focusRequester(cancel)) { Text(ConsentText.CANCEL, fontSize = 18.sp) }
                Button(onClick = { m.acceptConsent() },
                    colors = ButtonDefaults.colors(containerColor = Palette.accent, contentColor = Color.White)) { Text(ConsentText.ACCEPT, fontSize = 18.sp) }
            }
        }
    }
    // Safe default: focus starts on «Отмена».
    LaunchedEffect(Unit) { delay(60); runCatching { cancel.requestFocus() } }
}
