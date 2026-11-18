package su.expensive.twitchtv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import su.expensive.twitchtv.data.Account
import su.expensive.twitchtv.data.TwitchAuth

/**
 * Device Code Flow UI: shows the code to confirm on twitch.tv/activate from a phone and polls
 * until Twitch confirms. Leaving the screen cancels polling (LaunchedEffect scope).
 */
@Composable
fun LoginPanel(onFinished: () -> Unit) {
    var code by remember { mutableStateOf<TwitchAuth.DeviceCode?>(null) }
    var msg by remember { mutableStateOf("Запрашиваю код у Twitch…") }

    LaunchedEffect(Unit) {
        val dc = runCatching { withContext(Dispatchers.IO) { TwitchAuth.startDevice() } }
            .getOrElse { msg = "Не удалось получить код: ${it.message}"; return@LaunchedEffect }
        code = dc
        msg = "Ожидаю подтверждения на телефоне…"
        var interval = dc.intervalSec * 1000L
        val deadline = System.currentTimeMillis() + dc.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval)
            when (val r = withContext(Dispatchers.IO) { TwitchAuth.poll(dc.deviceCode) }) {
                TwitchAuth.PollResult.Pending -> {}
                TwitchAuth.PollResult.SlowDown -> interval += 5_000
                TwitchAuth.PollResult.Denied -> { msg = "Вход отклонён на телефоне"; code = null; return@LaunchedEffect }
                TwitchAuth.PollResult.Expired -> break
                is TwitchAuth.PollResult.Error -> { msg = "Ошибка входа: ${r.message}"; code = null; return@LaunchedEffect }
                is TwitchAuth.PollResult.Done -> {
                    val v = runCatching { withContext(Dispatchers.IO) { TwitchAuth.validate(r.tokens.access) } }.getOrNull()
                    if (v == null) { msg = "Twitch не подтвердил токен"; code = null; return@LaunchedEffect }
                    Account.onSignedIn(r.tokens, v.login)
                    msg = "Готово: ${v.login}"
                    onFinished(); return@LaunchedEffect
                }
            }
        }
        msg = "Код истёк — откройте вход заново"; code = null
    }

    Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp)) {
        code?.let {
            Text("На телефоне откройте", style = MaterialTheme.typography.bodyLarge)
            Text(it.verificationUri.removePrefix("https://").removePrefix("www."), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.accent)
            Text("и введите код", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            Text(it.userCode, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp, color = Palette.accent)
        }
        Text(msg, color = Palette.dim, modifier = Modifier.padding(top = 8.dp))
        Text("Пароль вводится только на сайте Twitch. Токен хранится в зашифрованном виде (Android Keystore) и не попадает в логи.",
            style = MaterialTheme.typography.bodySmall, color = Palette.dim, modifier = Modifier.padding(top = 4.dp))
    }
}
