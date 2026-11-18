package su.expensive.twitchtv

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import su.expensive.twitchtv.data.AdFilter
import su.expensive.twitchtv.data.Hls
import su.expensive.twitchtv.data.Net
import su.expensive.twitchtv.data.TwitchApi
import java.io.File

/**
 * Hits the real Twitch API/CDN (run with -Dlive=true). Verifies the exact request chain the
 * app uses: lists → GTA VI category → token per player type → usher → media playlist → segment.
 */
class LiveTwitchTest {
    private val enabled = System.getProperty("live") == "true"
    private val out = File(System.getProperty("liveOut", "build/live-report.txt"))

    private fun log(s: String) { println(s); out.parentFile?.mkdirs(); out.appendText(s + "\n") }

    @Test fun realChain() {
        assumeTrue(enabled)
        Net.initForTests()
        val top = TwitchApi.topStreams(30)
        log("top streams: ${top.size}; first=${top.firstOrNull()?.login} ${top.firstOrNull()?.viewers}")
        assertTrue(top.isNotEmpty())

        val cat = TwitchApi.findCategory(TwitchApi.GTA6_NAME)
        assertNotNull(cat)
        log("category: ${cat!!.name} id=${cat.id} viewers=${cat.viewers}")
        val gta = TwitchApi.categoryStreams(cat.id, 10)
        log("GTA VI streams: ${gta.joinToString { it.login }}")
        assertTrue(gta.isNotEmpty())

        val found = TwitchApi.search(gta.first().login.take(5))
        log("search '${gta.first().login.take(5)}': ${found.take(3).joinToString { it.login + if (it.live) "(live)" else "" }}")

        val fav = TwitchApi.channels(listOf(gta.first().login, "this_user_should_not_exist_zz9"))
        log("channels(): ${fav.joinToString { it.login + ":" + it.live }}")

        for (s in gta.take(4)) {
            for (type in listOf("site", "popout", "embed", "autoplay")) {
                val tok = TwitchApi.playbackToken(s.login, type, TwitchApi.newDeviceId())
                val mv = Net.getText(TwitchApi.usherUrl(s.login, tok, "av1,h265,h264"))
                if (!mv.ok) { log("${s.login} $type usher HTTP ${mv.code}"); continue }
                val v = Hls.sortVideo(Hls.parseMultivariant(mv.body))
                val best = v.first()
                val pl = Net.getText(best.url)
                val ads = AdFilter.hasAds(pl.body)
                log("${s.login} $type: ${v.joinToString(" ") { it.label + "/" + it.codecLabel }} | best=${best.label} ads=$ads liveSegs=${AdFilter.liveSegmentCount(pl.body)}")
                if (ads) {
                    val st = AdFilter.strip(pl.body)
                    log("   strip: removed=${st.removed} remaining=${st.remaining}")
                }
            }
        }

        // Download one real segment of the first clean popout stream so it can be probed with ffprobe.
        val s = gta.first()
        val tok = TwitchApi.playbackToken(s.login, "popout", TwitchApi.newDeviceId())
        val v = Hls.sortVideo(Hls.parseMultivariant(Net.getText(TwitchApi.usherUrl(s.login, tok, "h265,h264")).body)).first()
        val pl = Net.getText(v.url).body
        val segUrl = pl.lines().let { lines -> lines.indices.last { lines[it].startsWith("#EXTINF") && lines[it].contains(",live") }.let { lines[it + 1] } }
        log("media playlist head:\n" + pl.lines().take(12).joinToString("\n") { "   " + it.take(140) })
        val map = Regex("#EXT-X-MAP:URI=\"([^\"]+)\"").find(pl)?.groupValues?.get(1)
        if (map != null) Net.http.newCall(okhttp3.Request.Builder().url(map).build()).execute().use {
            File(out.parentFile, "init.mp4").writeBytes(it.body!!.bytes()); log("init segment: HTTP ${it.code}")
        }
        val req = okhttp3.Request.Builder().url(segUrl).build()
        Net.http.newCall(req).execute().use { r ->
            val bytes = r.body!!.bytes()
            File(out.parentFile, "segment.ts").writeBytes(bytes)
            log("segment ${s.login} ${v.label}: HTTP ${r.code}, ${bytes.size} bytes")
            assertTrue(bytes.size > 10_000)
        }
    }
}
