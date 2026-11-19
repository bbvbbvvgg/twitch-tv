package su.expensive.twitchtv.net

import java.net.URLEncoder

/**
 * ReYohoho Twitch quality proxy (github.com/reyohoho/twitch_quality_proxy, ext.rte.net.ru).
 *
 * What was verified against the live servers (2026-11-19) and the extension's own source:
 * - Request: GET https://proxyN.rte.net.ru/https://usher.ttvnw.net/api/channel/hls/<login>.m3u8?<usher params>
 *   The server builds the PlaybackAccessToken itself (via gql.twitch.tv on its side) and asks usher
 *   from its own exit; the sig/token in the client's query are ignored. The answer is an ordinary
 *   HLS master playlist whose media playlists point at Twitch's CDN, so video never goes through it.
 * - Signed-in playback: the extension passes the Twitch OAuth token to the server (in the URL as
 *   &auth=…, and in its health check as the X-Auth-Token header). The server reads the header too,
 *   so this app sends it ONLY as a header — never in the URL, which would land in access logs.
 * - A token the server cannot use answers 403 with an empty body. This app's token is issued to
 *   Twitch's Android TV client id, not the web one the extension's cookie token belongs to, so the
 *   server may reject it; then the app retries once without the token (anonymous, ≤1080p60).
 * - GET /proxy/status answers JSON (CORS *). The extension treats a server as unusable when it
 *   does not answer or reports overload (inflight ≥ 90 % of maxInflight).
 * - No API key, registration or extra headers are required; the servers answer any HTTPS client.
 * - The repository has no LICENSE file (package.json says "MIT"); nothing is copied from it —
 *   this is an independent client of the documented request shape.
 */
object Reyohoho {
    data class Server(val id: String, val base: String) { val host: String get() = base.removePrefix("https://").trimEnd('/') }

    /** Same order as the extension's PROXY_SERVERS. Changing this list invalidates the consent. */
    val SERVERS = listOf("proxy4", "proxy7", "proxy5", "proxy6").map { Server(it, "https://$it.rte.net.ru/") }

    const val TOKEN_HEADER = "X-Auth-Token"

    /**
     * What the user agreed to: which servers receive what, and how. Any change here (new host,
     * token moved into the URL, new data) changes the string and the consent is asked again.
     */
    val CONSENT_VERSION: String = "rte-1|" + SERVERS.joinToString(",") { it.host } + "|oauth-in-$TOKEN_HEADER-header|usher-master-only"

    /** Server order for one attempt: the chosen one first, then the rest; servers in cooldown last. */
    fun order(chosen: String, now: Long, cooldown: Map<String, Long>): List<Server> {
        val base = SERVERS.firstOrNull { it.id == chosen }?.let { listOf(it) + (SERVERS - it) } ?: SERVERS
        return base.sortedBy { if ((cooldown[it.id] ?: 0L) > now) 1 else 0 }
    }

    /** Master playlist URL through [server]. No Twitch token or signature is put into it. */
    fun masterUrl(server: Server, login: String, codecs: String): String {
        fun e(s: String) = URLEncoder.encode(s, "UTF-8")
        return server.base + "https://usher.ttvnw.net/api/channel/hls/${e(login.lowercase())}.m3u8" +
            "?platform=web&allow_source=true&allow_audio_only=true&fast_bread=true&playlist_include_framerate=true" +
            "&reassignments_supported=true&player_backend=mediaplayer&supported_codecs=${e(codecs)}"
    }

    fun statusUrl(server: Server) = server.base + "proxy/status"

    /** Parses /proxy/status. Returns null when the server should not be used. */
    fun parseStatus(json: String): Boolean = runCatching {
        val o = org.json.JSONObject(json)
        if (!o.optBoolean("enabled", true)) return false
        val load = o.optJSONObject("overload") ?: return true
        load.optInt("inflight") < load.optInt("maxInflight", Int.MAX_VALUE) * 0.9
    }.getOrDefault(false)
}
