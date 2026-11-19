package su.expensive.twitchtv.net

import su.expensive.twitchtv.data.HttpText
import su.expensive.twitchtv.player.DiagLog
import java.io.IOException
import java.io.InterruptedIOException

/** Where a master playlist actually came from, for the player and the diagnostics. */
enum class Route(val title: String) {
    DIRECT("прямое"),
    CUSTOM("свой прокси"),
    REYOHOHO_AUTH("Reyohoho, с токеном"),
    REYOHOHO_ANON("Reyohoho, без токена"),
}

/**
 * One master playlist fetch.
 * @param fallbackReason set when the chosen route failed and the direct connection was used instead.
 */
data class MasterResult(
    val text: HttpText,
    val route: Route,
    val server: String? = null,
    val fallbackReason: String? = null,
    val tokenRejected: Boolean = false,
)

/**
 * The single network engine every design uses. It only decides how the *master playlist* is
 * obtained; media playlists and video segments always go straight to Twitch's CDN through the
 * normal client (system VPN applies to them as usual).
 *
 * Bounded by construction: at most 2 ReYohoho servers per call, one anonymous retry after a
 * rejected token, then one direct attempt. No background polling, no timers.
 */
class ConnectionEngine(
    private val settings: () -> NetSnapshot,
    /** GET through the normal client, with optional headers. */
    private val get: (url: String, headers: Map<String, String>) -> HttpText,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Immutable view of the settings for one call (so a settings change mid-call is not half-applied). */
    data class NetSnapshot(val mode: ConnectionMode, val server: String, val consented: Boolean, val sendToken: Boolean)

    private val cooldown = HashMap<String, Long>()
    /** Server ids that rejected this app's token; reset when the user signs in again or changes server. */
    private val tokenRejectedBy = HashSet<String>()

    @Volatile var lastServer: String? = null; private set
    @Volatile var lastError: String? = null; private set

    @Synchronized fun resetTokenMemory() = tokenRejectedBy.clear()

    /**
     * @param direct fetches the master the normal way (PAT + usher) via the given route's client;
     *   [Route.DIRECT] or [Route.CUSTOM] are passed in by the caller.
     * @param oauth returns the viewer's token, or null when signed out. Only called for ReYohoho
     *   with consent, so a direct connection never even reads the token for the proxy.
     */
    fun fetchMaster(login: String, codecs: String, direct: () -> HttpText,
                    modeOverride: ConnectionMode? = null, oauth: () -> String?): MasterResult {
        val s = settings().let { if (modeOverride != null) it.copy(mode = modeOverride) else it }
        if (s.mode != ConnectionMode.REYOHOHO || !s.consented) {
            return MasterResult(direct(), if (s.mode == ConnectionMode.CUSTOM) Route.CUSTOM else Route.DIRECT)
        }
        val failure = tryReyohoho(login, codecs, s, oauth)
        if (failure.result != null) return failure.result
        lastError = failure.reason
        DiagLog.log("Reyohoho недоступен (${failure.reason}) — прямое подключение")
        return MasterResult(direct(), Route.DIRECT, fallbackReason = failure.reason, tokenRejected = failure.tokenRejected)
    }

    private class Attempt(val result: MasterResult?, val reason: String, val tokenRejected: Boolean = false)

    private fun tryReyohoho(login: String, codecs: String, s: NetSnapshot, oauth: () -> String?): Attempt {
        val now = clock()
        val servers = synchronized(this) { Reyohoho.order(s.server, now, cooldown) }.take(MAX_SERVERS)
        var reason = "нет ответа"
        var rejected = false
        for (srv in servers) {
            val url = Reyohoho.masterUrl(srv, login, codecs)
            val token = if (s.sendToken && srv.id !in synchronized(this) { tokenRejectedBy }) oauth() else null
            var r = call(url, token)
            if (r != null && r.code == 403 && token != null) {
                // The server could not use this token: never retry it there, go on anonymously.
                synchronized(this) { tokenRejectedBy += srv.id }
                rejected = true
                DiagLog.log("Reyohoho ${srv.id}: токен не принят (403) — повтор без токена")
                r = call(url, null)
                if (r != null && r.ok && r.body.startsWith("#EXTM3U")) {
                    lastServer = srv.host; lastError = "токен не принят сервером"
                    return Attempt(MasterResult(r, Route.REYOHOHO_ANON, srv.host, tokenRejected = true), "")
                }
            }
            when {
                r == null -> { reason = "${srv.id}: тайм-аут или нет соединения"; cool(srv, now) }
                r.ok && r.body.startsWith("#EXTM3U") -> {
                    lastServer = srv.host; lastError = null
                    return Attempt(MasterResult(r, if (token != null) Route.REYOHOHO_AUTH else Route.REYOHOHO_ANON, srv.host,
                        tokenRejected = rejected), "")
                }
                r.ok -> { reason = "${srv.id}: ответ не похож на плейлист"; cool(srv, now) }
                r.code == 429 -> { reason = "${srv.id}: лимит запросов (429)"; cool(srv, now) }
                // 403/404 without a token = the channel is offline or refused: direct will tell which.
                r.code == 403 || r.code == 404 -> return Attempt(null, "${srv.id}: HTTP ${r.code}", rejected)
                else -> { reason = "${srv.id}: HTTP ${r.code}"; cool(srv, now) }
            }
        }
        return Attempt(null, reason, rejected)
    }

    private fun call(url: String, token: String?): HttpText? = try {
        get(url, if (token != null) mapOf(Reyohoho.TOKEN_HEADER to token) else emptyMap())
    } catch (e: InterruptedIOException) { null } catch (e: IOException) { null }

    @Synchronized private fun cool(srv: Reyohoho.Server, now: Long) { cooldown[srv.id] = now + COOLDOWN_MS }

    /** For the "check connection" button: one status probe per server, no tokens. */
    fun probe(server: Reyohoho.Server): Boolean = try {
        val r = get(Reyohoho.statusUrl(server), emptyMap())
        r.ok && Reyohoho.parseStatus(r.body)
    } catch (e: IOException) { false }

    companion object {
        const val MAX_SERVERS = 2
        const val COOLDOWN_MS = 5 * 60_000L
    }
}
