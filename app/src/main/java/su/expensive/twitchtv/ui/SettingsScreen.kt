package su.expensive.twitchtv.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
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

@Composable
fun SettingsScreen(prefs: Prefs) {
    var v by remember { mutableIntStateOf(0) } // recomposition trigger
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var loggingIn by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    fun onOff(b: Boolean) = if (b) "Вкл" else "Выкл"

    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxWidth(0.75f)) {
        item { v
            val who = Account.login
            Row("Аккаунт Twitch", if (who != null) who else if (loggingIn) "вход…" else "не выполнен",
                if (who != null) "Нажмите, чтобы выйти (токен будет отозван)" else "Вход через код на twitch.tv/activate — открывает 1440p") {
                if (who != null) { Account.signOut(); v++ } else loggingIn = !loggingIn
            }
            if (loggingIn && who == null) LoginPanel(onFinished = { loggingIn = false; v++ })
        }
        item { v; Row("Обход рекламы", onOff(prefs.adBlock), "Чистый резервный поток во время рекламы; при неудаче показывается реклама") { prefs.adBlock = !prefs.adBlock; v++ } }
        item { v; Row("Автокачество", onOff(prefs.autoQuality), "Максимум, снижение только при повторных зависаниях") { prefs.autoQuality = !prefs.autoQuality; v++ } }
        item { v; Row("Качество по умолчанию", prefs.defaultQuality.title) {
            val all = DefaultQuality.entries; prefs.defaultQuality = all[(prefs.defaultQuality.ordinal + 1) % all.size]; v++ } }
        item { v; Row("Режим буфера", prefs.bufferMode.title, "Применяется при следующем открытии стрима") {
            val all = BufferMode.entries; prefs.bufferMode = all[(prefs.bufferMode.ordinal + 1) % all.size]; v++ } }
        item { v
            val support = when {
                DeviceCaps.hevc1440p60 -> "аппаратный HEVC 1440p60 есть"
                DeviceCaps.hevcHardware -> "аппаратный HEVC есть (1440p60 не заявлен)"
                else -> "аппаратного HEVC нет — 1440p недоступно"
            }
            Row("HEVC / 1440p (Enhanced Broadcasting)", onOff(prefs.allowHevc), support) { prefs.allowHevc = !prefs.allowHevc; v++ }
        }
        item { v; Row("Чат при открытии стрима", onOff(prefs.chatEnabled)) { prefs.chatEnabled = !prefs.chatEnabled; v++ } }
        item { v; Row("Эмоуты 7TV / BTTV / FFZ", onOff(prefs.chatThirdPartyEmotes)) { prefs.chatThirdPartyEmotes = !prefs.chatThirdPartyEmotes; v++ } }
        item { v; Row("Диагностика поверх видео", onOff(prefs.showDiagnostics)) { prefs.showDiagnostics = !prefs.showDiagnostics; v++ } }
        item { v; Row("Экспорт диагностических логов", exportMsg ?: "без токенов") { exportMsg = exportLogs(ctx, prefs, null) } }
        item { v; Row("Очистить историю", "${prefs.history().size}") { prefs.clearHistory(); HomeCache.data.remove(4); v++ } }
        item { v
            Column(Modifier.padding(top = 20.dp, start = 16.dp)) {
                Text("Пакет: ${BuildConfig.APPLICATION_ID} · версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = Palette.dim)
                Text("Добавьте этот пакет в правила маршрутизации Happ, если включён выборочный режим.", style = MaterialTheme.typography.bodySmall, color = Palette.dim)
                Text(DeviceCaps.summary(), style = MaterialTheme.typography.bodySmall, color = Palette.dim, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun Row(title: String, value: String, hint: String? = null, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = hint?.let { { Text(it, color = Palette.dim) } },
        trailingContent = { Text(value, color = Palette.accent) },
        modifier = Modifier.padding(vertical = 2.dp),
    )
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
