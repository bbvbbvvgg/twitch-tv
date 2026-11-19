package su.expensive.twitchtv.net

import su.expensive.twitchtv.data.Hls
import su.expensive.twitchtv.data.Net
import su.expensive.twitchtv.data.TwitchApi
import su.expensive.twitchtv.data.Variant
import su.expensive.twitchtv.player.DiagLog

/**
 * "Проверить подключение": one bounded, user-started check of the selected mode on a 24/7
 * channel. Never sends the OAuth token (the check is anonymous in every mode) and never touches
 * the running stream. Returns a short line for the settings screen.
 */
object NetCheck {
    private const val CHANNEL = "lofigirl"

    fun run(): String {
        val s = Connection.settings
        val started = System.currentTimeMillis()
        val line = runCatching {
            when (s.mode) {
                ConnectionMode.DIRECT -> twitch(Net.http, "прямое")
                ConnectionMode.CUSTOM -> {
                    val p = s.customProxy
                    if (!p.isValid) "свой прокси не задан" else twitch(Net.viaProxy(p), "свой прокси")
                }
                ConnectionMode.REYOHOHO -> reyohoho()
            }
        }.getOrElse { "ошибка: ${it.javaClass.simpleName}" }
        val out = "$line · ${System.currentTimeMillis() - started} мс"
        DiagLog.log("Проверка подключения (${s.mode.short}): $out")
        return out
    }

    private fun summary(master: String): String {
        val country = Hls.twitchInfo(master)["USER-COUNTRY"] ?: "?"
        val best: Variant? = Hls.sortVideo(Hls.parseMultivariant(master)).firstOrNull()
        return "страна $country · до ${best?.label ?: "—"}"
    }

    private fun twitch(via: okhttp3.OkHttpClient, what: String): String {
        val tok = TwitchApi.playbackToken(CHANNEL, "site", TwitchApi.newDeviceId(), via = via)
        val r = Net.getText(TwitchApi.usherUrl(CHANNEL, tok, "h264"), via = via)
        return if (r.ok) "$what: OK · ${summary(r.body)}" else "$what: usher HTTP ${r.code}"
    }

    private fun reyohoho(): String {
        val e = Connection.engine
        val chosen = Connection.settings.reyohohoServer
        val servers = Reyohoho.order(chosen, System.currentTimeMillis(), emptyMap())
        // Status probes stop at the first usable server: at most 4 short requests, usually 1.
        val ok = servers.firstOrNull { e.probe(it) } ?: return "Reyohoho: серверы недоступны или перегружены"
        val r = Net.getText(Reyohoho.masterUrl(ok, CHANNEL, "h264"), via = Net.proxyApi)
        return if (r.ok && r.body.startsWith("#EXTM3U")) "Reyohoho ${ok.id}: OK · ${summary(r.body)}"
        else "Reyohoho ${ok.id}: плейлист HTTP ${r.code}"
    }
}
