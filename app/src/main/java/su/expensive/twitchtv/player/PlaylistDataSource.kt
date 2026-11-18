package su.expensive.twitchtv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Wraps the network DataSource. HLS media playlists (*.m3u8 that are not the usher master)
 * are read fully, passed through [hook] (ad detection / stripping) and served from memory.
 * Video segments pass straight through to the Twitch CDN — no local proxy, no extra hop.
 */
@UnstableApi
class PlaylistDataSource(
    private val upstream: DataSource,
    private val hook: (uri: String, text: String) -> String,
) : DataSource {
    private var buffer: ByteArray? = null
    private var pos = 0
    private var uri: Uri? = null
    private var passthrough = false

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    private fun isMediaPlaylist(u: Uri): Boolean {
        val path = u.path ?: return false
        return path.endsWith(".m3u8") && u.host?.startsWith("usher.") != true
    }

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        if (!isMediaPlaylist(dataSpec.uri)) {
            passthrough = true
            return upstream.open(dataSpec)
        }
        passthrough = false
        val bytes = try {
            upstream.open(dataSpec)
            val out = ByteArrayOutputStream(16 * 1024)
            val tmp = ByteArray(16 * 1024)
            while (true) {
                val n = upstream.read(tmp, 0, tmp.size)
                if (n == C.RESULT_END_OF_INPUT) break
                out.write(tmp, 0, n)
            }
            out.toByteArray()
        } finally {
            upstream.close()
        }
        val processed = runCatching { hook(dataSpec.uri.toString(), String(bytes, Charsets.UTF_8)) }
            .getOrElse { String(bytes, Charsets.UTF_8) }
        buffer = processed.toByteArray(Charsets.UTF_8)
        pos = 0
        return buffer!!.size.toLong()
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(target, offset, length)
        val b = buffer ?: return C.RESULT_END_OF_INPUT
        if (length == 0) return 0
        if (pos >= b.size) return C.RESULT_END_OF_INPUT
        val n = min(length, b.size - pos)
        System.arraycopy(b, pos, target, offset, n)
        pos += n
        return n
    }

    override fun getUri(): Uri? = uri
    override fun getResponseHeaders(): Map<String, List<String>> = if (passthrough) upstream.responseHeaders else emptyMap()

    override fun close() {
        if (passthrough) upstream.close()
        buffer = null
        passthrough = false
    }

    class Factory(private val upstream: DataSource.Factory, private val hook: (String, String) -> String) : DataSource.Factory {
        override fun createDataSource(): DataSource = PlaylistDataSource(upstream.createDataSource(), hook)
    }
}
