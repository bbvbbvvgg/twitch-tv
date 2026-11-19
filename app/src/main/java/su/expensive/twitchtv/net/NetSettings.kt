package su.expensive.twitchtv.net

import android.content.Context
import android.content.SharedPreferences
import su.expensive.twitchtv.data.KeystoreSecretBox
import su.expensive.twitchtv.data.SecretCodec

/** How the app reaches Twitch for the master playlist (PlaybackAccessToken + usher). */
enum class ConnectionMode(val title: String, val short: String) {
    /** Android's default network. A system VPN (Happ or any other) routes it by its own rules. */
    DIRECT("Прямое / системный VPN", "Прямое"),
    /** Master playlist from a ReYohoho server; segments stay direct. Opt-in, needs consent. */
    REYOHOHO("Reyohoho Proxy", "Reyohoho"),
    /** The user's own HTTP or SOCKS5 proxy for gql.twitch.tv + usher.ttvnw.net only. */
    CUSTOM("Свой прокси", "Свой прокси"),
}

enum class ProxyKind(val title: String) { HTTP("HTTP / HTTPS (CONNECT)"), SOCKS5("SOCKS5") }

/** A user-entered proxy. The password is never part of this object's text form. */
data class CustomProxy(val kind: ProxyKind, val host: String, val port: Int, val user: String, val password: String) {
    val isValid: Boolean get() = host.isNotBlank() && port in 1..65535 && HOST_RE.matches(host.trim())
    /** Safe to show and to log: no credentials. */
    val display: String get() = if (isValid) "${kind.name.lowercase()}://${host.trim()}:$port" + (if (user.isNotEmpty()) " (с логином)" else "") else "не задан"
    override fun toString(): String = display

    companion object {
        private val HOST_RE = Regex("""^[A-Za-z0-9.\-]{1,253}$|^\[?[0-9A-Fa-f:]+]?$""")

        /** Parses "host:port" / "socks5://host:port" / "http://host:port"; credentials are separate fields. */
        fun parseAddress(text: String, fallbackKind: ProxyKind): Triple<ProxyKind, String, Int>? {
            var t = text.trim()
            var kind = fallbackKind
            when {
                t.startsWith("socks5://", true) || t.startsWith("socks://", true) -> { kind = ProxyKind.SOCKS5; t = t.substringAfter("://") }
                t.startsWith("http://", true) || t.startsWith("https://", true) -> { kind = ProxyKind.HTTP; t = t.substringAfter("://") }
            }
            if ('@' in t) return null // credentials go into their own fields, never into an address
            t = t.trimEnd('/')
            val host = t.substringBeforeLast(':', "").removePrefix("[").removeSuffix("]")
            val port = t.substringAfterLast(':', "").toIntOrNull() ?: return null
            if (host.isBlank() || port !in 1..65535 || !HOST_RE.matches(host)) return null
            return Triple(kind, host, port)
        }
    }
}

/**
 * Network settings, stored apart from the rest of Prefs. The ReYohoho consent is stored as the
 * exact [Reyohoho.CONSENT_VERSION] the user agreed to: if the recipient servers or the data sent
 * ever change, the stored version no longer matches and the app asks again.
 */
class NetSettings(private val sp: SharedPreferences, private val box: SecretCodec) {
    constructor(context: Context) : this(
        context.getSharedPreferences("twitchtv_net", Context.MODE_PRIVATE), KeystoreSecretBox("twitchtv_proxy_v1"))

    var mode: ConnectionMode
        get() = runCatching { ConnectionMode.valueOf(sp.getString("mode", "DIRECT")!!) }.getOrDefault(ConnectionMode.DIRECT)
            // Defensive: without a valid consent the Reyohoho mode is never effective.
            .let { if (it == ConnectionMode.REYOHOHO && !reyohohoConsented) ConnectionMode.DIRECT else it }
        set(v) { sp.edit().putString("mode", v.name).apply() }

    /** "auto" or a server id from [Reyohoho.SERVERS]. */
    var reyohohoServer: String
        get() = sp.getString("rteServer", "auto")!!.let { id -> if (id == "auto" || Reyohoho.SERVERS.any { it.id == id }) id else "auto" }
        set(v) { sp.edit().putString("rteServer", v).apply() }

    val reyohohoConsented: Boolean get() = sp.getString("rteConsent", null) == Reyohoho.CONSENT_VERSION

    /** Agreed in the warning dialog. Only this sets the consent. */
    fun grantReyohohoConsent() { sp.edit().putString("rteConsent", Reyohoho.CONSENT_VERSION).putLong("rteConsentAt", System.currentTimeMillis()).apply() }

    /** Withdraws the consent; the Reyohoho mode switches back to direct at once. */
    fun revokeReyohohoConsent() {
        val e = sp.edit().remove("rteConsent").remove("rteConsentAt")
        if (sp.getString("mode", "DIRECT") == ConnectionMode.REYOHOHO.name) e.putString("mode", ConnectionMode.DIRECT.name)
        e.apply()
    }

    /** The user may use ReYohoho without sending the token (anonymous playlists, 1080p at most). */
    var reyohohoSendToken: Boolean
        get() = sp.getBoolean("rteToken", true)
        set(v) { sp.edit().putBoolean("rteToken", v).apply() }

    var customProxy: CustomProxy
        get() = CustomProxy(
            runCatching { ProxyKind.valueOf(sp.getString("pxKind", "HTTP")!!) }.getOrDefault(ProxyKind.HTTP),
            sp.getString("pxHost", "")!!, sp.getInt("pxPort", 0), sp.getString("pxUser", "")!!,
            sp.getString("pxPass", null)?.let { box.open(it) } ?: "",
        )
        set(v) {
            val e = sp.edit().putString("pxKind", v.kind.name).putString("pxHost", v.host.trim()).putInt("pxPort", v.port)
                .putString("pxUser", v.user)
            if (v.password.isEmpty()) e.remove("pxPass") else e.putString("pxPass", box.seal(v.password))
            e.apply()
        }

    fun clearCustomProxy() { sp.edit().remove("pxHost").remove("pxPort").remove("pxUser").remove("pxPass").apply() }

    /** Safe summary for the diagnostics export: no passwords, no tokens. */
    fun dump(): String = "mode=${mode.name} rteServer=$reyohohoServer rteConsent=$reyohohoConsented rteToken=$reyohohoSendToken custom=${customProxy.display}"
}
