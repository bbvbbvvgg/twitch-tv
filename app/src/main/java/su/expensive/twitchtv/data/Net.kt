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

    fun onNetworkChanged() {
        client?.connectionPool?.evictAll()
    }

    @Throws(IOException::class)
    fun getText(url: String, headers: Map<String, String> = emptyMap()): HttpText {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        http.newCall(b.build()).execute().use { r ->
            return HttpText(r.code, r.body?.string() ?: "")
        }
    }
}

data class HttpText(val code: Int, val body: String) {
    val ok get() = code in 200..299
}
