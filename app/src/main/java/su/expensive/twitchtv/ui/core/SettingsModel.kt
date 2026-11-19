package su.expensive.twitchtv.ui.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import su.expensive.twitchtv.BuildConfig
import su.expensive.twitchtv.data.Account
import su.expensive.twitchtv.data.BufferMode
import su.expensive.twitchtv.data.DefaultQuality
import su.expensive.twitchtv.data.Prefs
import su.expensive.twitchtv.player.DeviceCaps
import su.expensive.twitchtv.player.DiagLog
import su.expensive.twitchtv.net.Connection
import su.expensive.twitchtv.net.ConnectionMode
import su.expensive.twitchtv.net.CustomProxy
import su.expensive.twitchtv.net.NetCheck
import su.expensive.twitchtv.net.Reyohoho
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings as data. Every design renders the same settings with its own structure and
 * interaction (Classic: OK cycles; Glass: grouped switches; Google TV: category pane + detail
 * pane; GTA VI: tabbed pause menu where ←/→ change values). Behaviour lives here once.
 */
sealed interface Setting {
    val id: String
    val title: String
    val hint: String?

    /** On/off switch. */
    class Toggle(override val id: String, override val title: String, override val hint: String?,
                 val get: () -> Boolean, private val set: (Boolean) -> Unit) : Setting {
        fun flip() = set(!get())
    }

    /** One of several values; [step] moves forward/back and wraps. */
    class Choice(override val id: String, override val title: String, override val hint: String?,
                 val options: List<String>, val index: () -> Int, private val set: (Int) -> Unit) : Setting {
        val value: String get() = options[index()]
        fun step(d: Int) = set(((index() + d) % options.size + options.size) % options.size)
    }

    /** A command with a status value (export logs, clear history). */
    class Action(override val id: String, override val title: String, override val hint: String?,
                 val value: () -> String, val run: () -> Unit) : Setting

    /** Opens the design's own sub-screen: Twitch sign-in or the design picker. */
    class Screen(override val id: String, override val title: String, override val hint: String?,
                 val value: () -> String) : Setting
}

data class SettingGroup(val title: String, val items: List<Setting>)

@UnstableApi
class SettingsModel(private val ctx: Context, private val prefs: Prefs, private val app: AppState) {
    /** Bumped on every change; settings screens read it so values recompose. */
    var version by mutableIntStateOf(0); private set
    var exportMessage by mutableStateOf<String?>(null); private set

    private fun changed() { version++ }
    private fun onOff(b: Boolean) = if (b) "Вкл" else "Выкл"

    val account = Setting.Screen("account", "Аккаунт Twitch",
        "Вход через код на twitch.tv/activate — открывает 1440p60") { Account.login ?: "не выполнен" }

    val design = Setting.Screen("design", "Дизайн интерфейса",
        "Classic, Liquid Glass, Google TV или GTA VI — меняется сразу") { app.design.title }

    // ---------------- Сеть и прокси (one engine for all four designs) ----------------
    private val net get() = Connection.settings
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** True while a design must show the ReYohoho token warning. Nothing is sent before [acceptConsent]. */
    var consentAsk by mutableStateOf(false); private set
    var checkResult by mutableStateOf<String?>(null); private set
    var checking by mutableStateOf(false); private set

    val connMode = Setting.Choice("conn", "Режим подключения",
        "Прямое работает и с Happ VPN. Reyohoho и свой прокси — только для плейлиста; видео идёт напрямую",
        ConnectionMode.entries.map { it.title }, { net.mode.ordinal }) { chooseMode(ConnectionMode.entries[it]) }

    /** Every design sets the mode through here, so the warning can never be skipped. */
    fun chooseMode(mode: ConnectionMode) {
        if (mode == ConnectionMode.REYOHOHO && !net.reyohohoConsented) { consentAsk = true; return }
        if (mode == net.mode) return
        net.mode = mode; checkResult = null
        DiagLog.log("Режим подключения: ${mode.title}")
        Connection.changed(); changed()
    }

    fun acceptConsent() {
        net.grantReyohohoConsent()
        net.mode = ConnectionMode.REYOHOHO
        consentAsk = false; checkResult = null
        DiagLog.log("Reyohoho: согласие дано, режим включён")
        Connection.onAccountChanged(); changed()
    }

    /** «Отмена»: nothing changes, the previous mode stays. */
    fun declineConsent() { consentAsk = false; DiagLog.log("Reyohoho: отказ, режим не изменён"); changed() }

    val rteServer = Setting.Choice("rteServer", "Сервер Reyohoho", "Авто — первый ответивший; упавший сервер пропускается на 5 минут",
        listOf("Авто") + Reyohoho.SERVERS.map { it.id },
        { (Reyohoho.SERVERS.indexOfFirst { it.id == net.reyohohoServer } + 1).coerceAtLeast(0) }) {
        net.reyohohoServer = if (it == 0) "auto" else Reyohoho.SERVERS[it - 1].id
        Connection.onAccountChanged(); changed()
    }

    val rteToken = Setting.Toggle("rteToken", "Передавать токен Twitch в Reyohoho",
        "Нужен для 1440p. Без токена — анонимно, обычно до 1080p60", { net.reyohohoSendToken }) {
        net.reyohohoSendToken = it; Connection.changed(); changed()
    }

    val rteRevoke = Setting.Action("rteRevoke", "Отозвать согласие Reyohoho", "Режим сразу вернётся к прямому подключению",
        { if (net.reyohohoConsented) "дано" else "не дано" }) {
        if (net.reyohohoConsented) {
            net.revokeReyohohoConsent(); DiagLog.log("Reyohoho: согласие отозвано")
            checkResult = null; Connection.changed(); changed()
        }
    }

    val proxy = Setting.Screen("proxy", "Свой прокси", "HTTP или SOCKS5 для gql.twitch.tv и usher.ttvnw.net") { net.customProxy.display }

    val netCheck = Setting.Action("netcheck", "Проверить подключение", "Разовая проверка выбранного режима на канале lofigirl, без токена",
        { if (checking) "проверяю…" else checkResult ?: "—" }) { runCheck() }

    fun runCheck() {
        if (checking) return
        checking = true; changed()
        ioScope.launch {
            val r = withTimeoutOrNull(30_000) { withContext(Dispatchers.IO) { NetCheck.run() } } ?: "тайм-аут 30 с"
            checkResult = r; checking = false; changed()
        }
    }

    fun saveProxy(p: CustomProxy) { net.customProxy = p; checkResult = null; Connection.changed(); changed() }
    fun clearProxy() {
        net.clearCustomProxy()
        if (net.mode == ConnectionMode.CUSTOM) net.mode = ConnectionMode.DIRECT
        checkResult = null; Connection.changed(); changed()
    }

    fun signOut() { Account.signOut(); changed() }
    fun signedIn() { changed() }

    val groups: List<SettingGroup> = listOf(
        SettingGroup("Аккаунт", listOf(account)),
        SettingGroup("Воспроизведение", listOf(
            Setting.Toggle("ads", "Обход рекламы", "Чистый резервный поток во время рекламы; при неудаче показывается реклама",
                { prefs.adBlock }) { prefs.adBlock = it; changed() },
            Setting.Toggle("auto", "Автокачество", "Максимум, снижение только при повторных зависаниях",
                { prefs.autoQuality }) { prefs.autoQuality = it; changed() },
            Setting.Choice("defq", "Качество по умолчанию", null, DefaultQuality.entries.map { it.title },
                { prefs.defaultQuality.ordinal }) { prefs.defaultQuality = DefaultQuality.entries[it]; changed() },
            Setting.Choice("buf", "Режим буфера", "Применяется при следующем открытии стрима", BufferMode.entries.map { it.title },
                { prefs.bufferMode.ordinal }) { prefs.bufferMode = BufferMode.entries[it]; changed() },
            Setting.Toggle("hevc", "HEVC / 1440p (Enhanced Broadcasting)", hevcSupport(),
                { prefs.allowHevc }) { prefs.allowHevc = it; changed() },
        )),
        SettingGroup(NET_GROUP, listOf(connMode, rteServer, rteToken, proxy, netCheck, rteRevoke)),
        SettingGroup("Чат", listOf(
            Setting.Toggle("chat", "Чат при открытии стрима", null, { prefs.chatEnabled }) { prefs.chatEnabled = it; changed() },
            Setting.Toggle("emotes", "Эмоуты 7TV / BTTV / FFZ", null, { prefs.chatThirdPartyEmotes }) { prefs.chatThirdPartyEmotes = it; changed() },
        )),
        SettingGroup("Интерфейс", listOf(design)),
        SettingGroup("Диагностика", listOf(
            Setting.Toggle("diag", "Диагностика поверх видео", null, { prefs.showDiagnostics }) { prefs.showDiagnostics = it; changed() },
            Setting.Action("export", "Экспорт диагностических логов", "Файл без токенов в Download/",
                { exportMessage ?: "без токенов" }) { exportMessage = exportLogs(ctx, prefs, null); changed() },
            Setting.Action("hist", "Очистить историю", null, { "${prefs.history().size}" }) {
                prefs.clearHistory(); StreamRepo.invalidate(StreamRepo.Key.HISTORY); changed()
            },
        )),
    )

    fun valueOf(s: Setting): String = when (s) {
        is Setting.Toggle -> onOff(s.get())
        is Setting.Choice -> s.value
        is Setting.Action -> s.value()
        is Setting.Screen -> s.value()
    }

    val aboutLines: List<String> get() = listOf(
        "Пакет: ${BuildConfig.APPLICATION_ID} · версия ${BuildConfig.VERSION_NAME}",
        "Добавьте этот пакет в правила маршрутизации Happ, если включён выборочный режим.",
        DeviceCaps.summary(),
    )

    companion object {
        const val NET_GROUP = "Сеть и прокси"
        fun hevcSupport(): String = when {
            DeviceCaps.hevc1440p60 -> "аппаратный HEVC 1440p60 есть"
            DeviceCaps.hevcHardware -> "аппаратный HEVC есть (1440p60 не заявлен)"
            else -> "аппаратного HEVC нет — 1440p недоступно"
        }
    }
}

/** Writes a sanitized diagnostics file. Returns a human-readable location. */
fun exportLogs(ctx: Context, prefs: Prefs, playerDiag: String?): String {
    val name = "twitchtv-diag-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
    val text = buildString {
        appendLine("TwitchTV ${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
        appendLine("Настройки: ${prefs.dumpSettings()}")
        if (Connection.isInitialized) appendLine("Сеть: ${Connection.settings.dump()}")
        appendLine(DeviceCaps.summary())
        playerDiag?.let { appendLine("--- Плеер ---"); appendLine(it) }
        appendLine("--- Журнал ---")
        DiagLog.snapshot().forEach { appendLine(it) }
    }.let(DiagLog::sanitize)
    return runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: error("MediaStore")
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
            "Сохранено: Download/$name"
        } else {
            val f = File(ctx.getExternalFilesDir(null), name); f.writeText(text)
            "Сохранено: ${f.absolutePath}"
        }
    }.getOrElse {
        val f = File(ctx.getExternalFilesDir(null), name); f.writeText(text)
        "Сохранено: ${f.absolutePath}"
    }
}
