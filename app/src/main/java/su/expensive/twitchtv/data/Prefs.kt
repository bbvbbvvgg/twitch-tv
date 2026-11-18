package su.expensive.twitchtv.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class BufferMode(val title: String) {
    LOW_LATENCY("Минимальная задержка"),
    BALANCED("Сбалансированный"),
    STABLE("Максимальная стабильность"),
}

enum class DefaultQuality(val title: String, val height: Int) {
    MAX("Максимальное", 9999), Q1440("1440p", 1440), Q1080("1080p", 1080), Q720("720p", 720), Q480("480p", 480),
}

/** Settings + favorites + history, stored locally only (no account needed). */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("twitchtv", Context.MODE_PRIVATE)

    var adBlock: Boolean
        get() = sp.getBoolean("adBlock", true)
        set(v) = sp.edit().putBoolean("adBlock", v).apply()

    /** Automatic quality: keep max, step down on repeated stalls, step back up when stable. */
    var autoQuality: Boolean
        get() = sp.getBoolean("autoQuality", true)
        set(v) = sp.edit().putBoolean("autoQuality", v).apply()

    var defaultQuality: DefaultQuality
        get() = runCatching { DefaultQuality.valueOf(sp.getString("defQ", "MAX")!!) }.getOrDefault(DefaultQuality.MAX)
        set(v) = sp.edit().putString("defQ", v.name).apply()

    var bufferMode: BufferMode
        get() = runCatching { BufferMode.valueOf(sp.getString("buf", "BALANCED")!!) }.getOrDefault(BufferMode.BALANCED)
        set(v) = sp.edit().putString("buf", v.name).apply()

    var allowHevc: Boolean
        get() = sp.getBoolean("hevc", true)
        set(v) = sp.edit().putBoolean("hevc", v).apply()

    var chatEnabled: Boolean
        get() = sp.getBoolean("chat", false)
        set(v) = sp.edit().putBoolean("chat", v).apply()

    var chatThirdPartyEmotes: Boolean
        get() = sp.getBoolean("emotes3p", true)
        set(v) = sp.edit().putBoolean("emotes3p", v).apply()

    var showDiagnostics: Boolean
        get() = sp.getBoolean("diag", false)
        set(v) = sp.edit().putBoolean("diag", v).apply()

    /** Selected interface design; stored by name so reordering the enum never changes a user's choice. */
    var design: su.expensive.twitchtv.ui.core.Design
        get() = runCatching { su.expensive.twitchtv.ui.core.Design.valueOf(sp.getString("design", "CLASSIC")!!) }
            .getOrDefault(su.expensive.twitchtv.ui.core.Design.CLASSIC)
        set(v) = sp.edit().putString("design", v.name).apply()

    // ---- favorites ----
    fun favorites(): List<String> = readList("fav")
    fun isFavorite(login: String) = favorites().contains(login.lowercase())
    fun toggleFavorite(login: String): Boolean {
        val l = favorites().toMutableList(); val k = login.lowercase()
        val now = if (l.remove(k)) false else { l.add(0, k); true }
        writeList("fav", l); return now
    }

    // ---- history (most recent first, max 50) ----
    fun history(): List<String> = readList("hist")
    fun addHistory(login: String) {
        val l = history().toMutableList(); val k = login.lowercase()
        l.remove(k); l.add(0, k); writeList("hist", l.take(50))
    }
    fun clearHistory() = writeList("hist", emptyList())

    private fun readList(key: String): List<String> = runCatching {
        val a = JSONArray(sp.getString(key, "[]")); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun writeList(key: String, l: List<String>) = sp.edit().putString(key, JSONArray(l).toString()).apply()

    fun dumpSettings(): String = JSONObject()
        .put("adBlock", adBlock).put("autoQuality", autoQuality).put("defaultQuality", defaultQuality.name)
        .put("bufferMode", bufferMode.name).put("allowHevc", allowHevc).put("chat", chatEnabled).put("design", design.name).toString()
}
