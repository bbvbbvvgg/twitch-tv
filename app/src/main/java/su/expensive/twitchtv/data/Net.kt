package su.expensive.twitchtv.data

import android.content.Context
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Single shared OkHttp client.
 *
 * - Uses the system default network, so a system VPN (Happ) routes it automatically.
 *   We never bind sockets to a specific Network and never set a proxy.
 * - Normal TLS verification (no custom TrustManager).
 * - Small connection pool; [onNetworkChanged] drops stale sockets when the VPN reconnects.
 */
object Net {
    @Volatile private var client: OkHttpClient? = null

    fun init(context: Context) {
        if (client != null) return
        synchronized(this) {
            if (client != null) return
            client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(ConnectionPool(6, 2, TimeUnit.MINUTES))
                // Only small API JSON responses are cached here; video never touches disk.
                .cache(Cache(File(context.cacheDir, "http"), 8L * 1024 * 1024))
                .build()
        }
    }

    /** For JVM tests. */
    fun initForTests() {
        if (client == null) client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    val http: OkHttpClient get() = client ?: error("Net.init not called")

    /**
     * Short-timeout client for ReYohoho master playlists. Built once from [http] (shares its pool
     * and dispatcher, no extra threads) so a dead proxy costs seconds, not the 20 s API timeout.
     */
    val proxyApi: OkHttpClient by lazy {
        http.newBuilder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS).retryOnConnectionFailure(false).cache(null).build()
    }

    @Volatile private var custom: Pair<su.expensive.twitchtv.net.CustomProxy, OkHttpClient>? = null

    /**
     * Client that reaches the user's own proxy. Cached per configuration (rebuilt only when the
     * user changes it). Only gql.twitch.tv and usher.ttvnw.net requests use it; segments do not.
     */
    @Synchronized
    fun viaProxy(p: su.expensive.twitchtv.net.CustomProxy): OkHttpClient {
        custom?.let { (cfg, c) -> if (cfg == p) return c }
        val type = if (p.kind == su.expensive.twitchtv.net.ProxyKind.SOCKS5) java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP
        val b = http.newBuilder()
            .proxy(java.net.Proxy(type, java.net.InetSocketAddress.createUnresolved(p.host.trim(), p.port)))
            .connectTimeout(6, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).cache(null)
        if (p.user.isNotEmpty() && p.kind == su.expensive.twitchtv.net.ProxyKind.HTTP) {
            b.proxyAuthenticator { _, resp ->
                if (resp.request.header("Proxy-Authorization") != null) null // wrong credentials: no loop
                else resp.request.newBuilder().header("Proxy-Authorization", okhttp3.Credentials.basic(p.user, p.password)).build()
            }
        }
        return b.build().also { custom = p to it }
    }

    fun onNetworkChanged() {
        client?.connectionPool?.evictAll()
    }

    @Throws(IOException::class)
    fun getText(url: String, headers: Map<String, String> = emptyMap(), via: OkHttpClient = http): HttpText {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        via.newCall(b.build()).execute().use { r ->
            return HttpText(r.code, r.body?.string() ?: "")
        }
    }
}

data class HttpText(val code: Int, val body: String) {
    val ok get() = code in 200..299
}
