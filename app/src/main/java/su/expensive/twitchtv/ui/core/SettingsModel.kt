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
