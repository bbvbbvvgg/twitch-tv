package su.expensive.twitchtv.ui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import su.expensive.twitchtv.data.Account
import su.expensive.twitchtv.data.TwitchAuth

@Stable
class DeviceLoginState {
    var code by mutableStateOf<TwitchAuth.DeviceCode?>(null); internal set
    var message by mutableStateOf("Запрашиваю код у Twitch…"); internal set
    var done by mutableStateOf(false); internal set
    val site: String get() = code?.verificationUri?.removePrefix("https://")?.removePrefix("www.") ?: "twitch.tv/activate"
}

const val LOGIN_PRIVACY = "Пароль вводится только на сайте Twitch. Токен хранится в зашифрованном виде (Android Keystore) и не попадает в логи."

/**
 * Device Code Flow, shared by every design's sign-in screen: shows a code to confirm on
 * twitch.tv/activate from a phone and polls until Twitch confirms. Leaving the screen cancels
 * polling (the LaunchedEffect scope).
 */
@Composable
fun rememberDeviceLogin(onFinished: () -> Unit): DeviceLoginState {
    val st = remember { DeviceLoginState() }
    LaunchedEffect(Unit) {
        val dc = runCatching { withContext(Dispatchers.IO) { TwitchAuth.startDevice() } }
            .getOrElse { st.message = "Не удалось получить код: ${it.message}"; return@LaunchedEffect }
        st.code = dc
        st.message = "Ожидаю подтверждения на телефоне…"
        var interval = dc.intervalSec * 1000L
        val deadline = System.currentTimeMillis() + dc.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval)
            when (val r = withContext(Dispatchers.IO) { TwitchAuth.poll(dc.deviceCode) }) {
                TwitchAuth.PollResult.Pending -> {}
                TwitchAuth.PollResult.SlowDown -> interval += 5_000
                TwitchAuth.PollResult.Denied -> { st.message = "Вход отклонён на телефоне"; st.code = null; return@LaunchedEffect }
                TwitchAuth.PollResult.Expired -> break
                is TwitchAuth.PollResult.Error -> { st.message = "Ошибка входа: ${r.message}"; st.code = null; return@LaunchedEffect }
                is TwitchAuth.PollResult.Done -> {
                    val v = runCatching { withContext(Dispatchers.IO) { TwitchAuth.validate(r.tokens.access) } }.getOrNull()
                    if (v == null) { st.message = "Twitch не подтвердил токен"; st.code = null; return@LaunchedEffect }
                    Account.onSignedIn(r.tokens, v.login)
                    st.message = "Готово: ${v.login}"; st.done = true
                    onFinished(); return@LaunchedEffect
                }
            }
        }
        st.message = "Код истёк — откройте вход заново"; st.code = null
    }
    return st
}
