package su.expensive.twitchtv.ui.core

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.media3.common.util.UnstableApi
import su.expensive.twitchtv.net.Connection
import su.expensive.twitchtv.net.ConnectionMode
import su.expensive.twitchtv.net.CustomProxy
import su.expensive.twitchtv.net.ProxyKind
import su.expensive.twitchtv.net.Route

/** The ReYohoho warning. Every design shows exactly these words, in its own visual system. */
object ConsentText {
    const val TITLE = "⚠️ Сторонний прокси"
    val BODY = listOf(
        "Для работы Reyohoho Proxy приложению может потребоваться передать OAuth-токен вашей учётной записи Twitch на серверы Reyohoho.",
        "Это сторонний сервис, не связанный с разработчиком Twitch TV.",
        "Передача токена может создавать риски для безопасности аккаунта.",
        "Использование прокси добровольно. Вы можете отключить его в настройках в любое время.",
    )
    /** What exactly happens, stated plainly (requirement: do not hide how the token travels). */
    const val DETAILS = "Получатели: proxy4/5/6/7.rte.net.ru по HTTPS. Токен уходит только в заголовке X-Auth-Token " +
        "запроса плейлиста — не в адресе, поэтому не попадает в журналы адресов. Видео через прокси не идёт. " +
        "Без входа в Twitch токен не передаётся вовсе."
    const val CANCEL = "Отмена"
    const val ACCEPT = "Понимаю, включить"
}

/** Editable copy of the user's proxy. Saved only on «Сохранить»; the password is never shown. */
@Stable
class ProxyForm(initial: CustomProxy) {
    var kind by mutableStateOf(initial.kind)
    var address by mutableStateOf(if (initial.host.isNotEmpty() && initial.port > 0) "${initial.host}:${initial.port}" else "")
    var user by mutableStateOf(initial.user)
    var password by mutableStateOf("")
    private val hadPassword = initial.password.isNotEmpty()
    private val oldPassword = initial.password
    var error by mutableStateOf<String?>(null)

    val passwordHint: String get() = if (hadPassword && password.isEmpty()) "сохранён — оставьте пустым, чтобы не менять" else "пароль (необязательно)"

    fun toggleKind() { kind = if (kind == ProxyKind.HTTP) ProxyKind.SOCKS5 else ProxyKind.HTTP }

    /** null when the address is not usable; the form then shows [error]. */
    fun build(): CustomProxy? {
        val parsed = CustomProxy.parseAddress(address, kind)
        if (parsed == null) { error = "Адрес вида host:порт, например 192.168.1.10:8080. Логин и пароль — в отдельных полях"; return null }
        error = null
        kind = parsed.first
        return CustomProxy(parsed.first, parsed.second, parsed.third, user.trim(), password.ifEmpty { if (hadPassword) oldPassword else "" })
    }
}

@Composable
fun rememberProxyForm(): ProxyForm = remember { ProxyForm(Connection.settings.customProxy) }

/**
 * A plain text field for the TV keyboard. OK opens the on-screen keyboard; «Далее» moves on.
 * Designs give it their own colours and frame.
 */
@Composable
fun NetTextField(value: String, onValue: (String) -> Unit, style: TextStyle, cursor: Color, modifier: Modifier = Modifier,
                 secret: Boolean = false, keyboard: KeyboardType = KeyboardType.Uri) {
    val fm = LocalFocusManager.current
    BasicTextField(
        value = value, onValueChange = { onValue(it.take(200).replace("\n", "")) }, singleLine = true, textStyle = style,
        cursorBrush = SolidColor(cursor), modifier = modifier,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onNext = { fm.moveFocus(FocusDirection.Down) }),
    )
}

/** One line about how the current stream is connected, for every design's quality panel. */
@UnstableApi
fun connectionLine(c: su.expensive.twitchtv.player.PlaybackController): String {
    val d = c.diag
    val via = d.route.takeIf { it != "—" } ?: return "Подключение: ${Connection.settings.mode.short}"
    val fb = d.fallback?.let { " · прокси недоступен, прямое" } ?: ""
    return "Подключение: $via$fb · максимум ${d.maxOffered}"
}

/** Label for the one-shot ReYohoho retry, or null when it does not apply to this stream. */
@UnstableApi
fun proxyRetryLabel(c: su.expensive.twitchtv.player.PlaybackController): String? {
    if (c.proxyRetrying) return "Reyohoho: запрашиваю…"
    c.proxyRetryNote?.let { return it }
    if (!c.canRetryViaProxy) return null
    val best = c.variants.firstOrNull()?.height ?: 0
    return if (best >= 1440) null else "Повторить через Reyohoho"
}

val Route.isProxy get() = this != Route.DIRECT
val ConnectionMode.needsConsent get() = this == ConnectionMode.REYOHOHO
