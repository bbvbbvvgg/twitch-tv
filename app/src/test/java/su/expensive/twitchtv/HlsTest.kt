package su.expensive.twitchtv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import su.expensive.twitchtv.data.AdFilter
import su.expensive.twitchtv.data.Hls

class HlsTest {
    private val master = """
        #EXTM3U
        #EXT-X-TWITCH-INFO:NODE="video-edge",SERVER-TIME="1795027060.33"
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="1440p60",NAME="1440p60",AUTOSELECT=YES,DEFAULT=YES
        #EXT-X-STREAM-INF:BANDWIDTH=12000000,RESOLUTION=2560x1440,CODECS="hvc1.1.2.L150.90,mp4a.40.2",VIDEO="1440p60",FRAME-RATE=60.000
        https://example.ttvnw.net/v1/playlist/a1440.m3u8
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="chunked",NAME="1080p60 (source)",AUTOSELECT=YES,DEFAULT=YES
        #EXT-X-STREAM-INF:BANDWIDTH=8415671,RESOLUTION=1920x1080,CODECS="avc1.64002A,mp4a.40.2",VIDEO="chunked",FRAME-RATE=60.000
        https://example.ttvnw.net/v1/playlist/a1080.m3u8
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="720p30",NAME="720p",AUTOSELECT=YES,DEFAULT=YES
        #EXT-X-STREAM-INF:BANDWIDTH=2373000,RESOLUTION=1280x720,CODECS="avc1.4D401F,mp4a.40.2",VIDEO="720p30",FRAME-RATE=30.000
        https://example.ttvnw.net/v1/playlist/a720.m3u8
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="audio_only",NAME="audio_only",AUTOSELECT=NO,DEFAULT=NO
        #EXT-X-STREAM-INF:BANDWIDTH=160000,CODECS="mp4a.40.2",VIDEO="audio_only"
        https://example.ttvnw.net/v1/playlist/audio.m3u8
    """.trimIndent()

    @Test fun parsesMaster() {
        val v = Hls.parseMultivariant(master)
        assertEquals(4, v.size)
        assertEquals("1440p60", v[0].label); assertTrue(v[0].isHevc); assertEquals("HEVC", v[0].codecLabel)
        assertEquals("1080p60", v[1].label); assertTrue(v[1].isSource); assertFalse(v[1].isHevc)
        assertEquals("720p", v[2].label)
        assertTrue(v[3].isAudioOnly)
        val sorted = Hls.sortVideo(v)
        assertEquals(listOf(1440, 1080, 720), sorted.map { it.height })
        assertEquals(1080, Hls.closest(v, 1080, 60)!!.height)
        assertEquals(720, Hls.closest(v, 480, 30)!!.height)
    }

    private val adPlaylist = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:5
        #EXT-X-MEDIA-SEQUENCE:100
        #EXT-X-DATERANGE:ID="stitched-ad-1",CLASS="twitch-stitched-ad",START-DATE="2026-11-18T18:37:35Z",DURATION=4
        #EXT-X-DISCONTINUITY
        #EXT-X-PROGRAM-DATE-TIME:2026-11-18T18:37:35.000Z
        #EXTINF:2.000,Amazon|123
        https://ad.example/1.ts
        #EXT-X-PROGRAM-DATE-TIME:2026-11-18T18:37:37.000Z
        #EXTINF:2.000,Amazon|123
        https://ad.example/2.ts
        #EXT-X-DISCONTINUITY
        #EXT-X-PROGRAM-DATE-TIME:2026-11-18T18:37:39.000Z
        #EXTINF:2.000,live
        https://live.example/3.ts
        #EXT-X-PROGRAM-DATE-TIME:2026-11-18T18:37:41.000Z
        #EXTINF:2.000,live
        https://live.example/4.ts
        #EXT-X-TWITCH-PREFETCH:https://live.example/5.ts
    """.trimIndent()

    @Test fun detectsAndStripsAds() {
        assertTrue(AdFilter.hasAds(adPlaylist))
        val s = AdFilter.strip(adPlaylist)
        assertEquals(2, s.removed); assertEquals(2, s.remaining)
        assertFalse(s.text.contains("ad.example"))
        assertFalse(s.text.contains("PREFETCH"))
        assertFalse(s.text.contains("stitched"))
        // Live segments are numbered orig − (ads before them) so the number is stable across refreshes.
        assertTrue(s.text.contains("#EXT-X-MEDIA-SEQUENCE:100"))
        assertTrue(s.text.contains("https://live.example/3.ts"))
        assertFalse(AdFilter.hasAds(s.text))
    }

    @Test fun cleanPlaylistUntouched() {
        val clean = "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:5\n#EXTINF:2.000,live\nhttps://x/1.ts\n#EXTINF:2.000,live\nhttps://x/2.ts"
        assertFalse(AdFilter.hasAds(clean))
        assertEquals(0, AdFilter.strip(clean).removed)
        // Unlabeled playlists (other servers) must not be treated as ads.
        assertFalse(AdFilter.hasAds("#EXTM3U\n#EXTINF:2.000,\nhttps://x/1.ts"))
    }

    @Test fun fmp4MapsKeptForLiveOnly() {
        val pl = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-MAP:URI="https://live.example/init.mp4"
            #EXTINF:2.000,live
            https://live.example/1.m4s
            #EXT-X-DISCONTINUITY
            #EXT-X-MAP:URI="https://ad.example/init.mp4"
            #EXTINF:2.000,Amazon|1
            https://ad.example/a.m4s
            #EXT-X-DISCONTINUITY
            #EXT-X-MAP:URI="https://live.example/init.mp4"
            #EXTINF:2.000,live
            https://live.example/2.m4s
        """.trimIndent()
        val s = AdFilter.strip(pl)
        assertEquals(1, s.removed); assertEquals(2, s.remaining)
        assertFalse(s.text.contains("ad.example"))
        assertEquals(1, Regex("EXT-X-MAP").findAll(s.text).count())
        assertTrue(s.text.indexOf("EXT-X-MAP") < s.text.indexOf("1.m4s"))

        // Ads first, live init only declared before the ads: live segments must still get it.
        val pl2 = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-MAP:URI="https://ad.example/init.mp4"
            #EXTINF:2.000,Amazon|1
            https://ad.example/a.m4s
            #EXT-X-DISCONTINUITY
            #EXT-X-MAP:URI="https://live.example/init.mp4"
            #EXTINF:2.000,live
            https://live.example/2.m4s
        """.trimIndent()
        val s2 = AdFilter.strip(pl2)
        assertFalse(s2.text.contains("ad.example"))
        assertTrue(s2.text.contains("live.example/init.mp4"))
        assertTrue(s2.text.contains("#EXT-X-MEDIA-SEQUENCE:10"))
    }

    // ---------------------------------------------------------------------------------------
    // Regression: ads in the middle of a live window, across playlist refreshes
    // ---------------------------------------------------------------------------------------

    private data class Seg(val uri: String, val seq: Long, val disc: Long, val map: String?)

    /** What a player derives from a playlist: number, discontinuity number and init per segment. */
    private fun segments(pl: String): List<Seg> {
        var seq = 0L; var disc = 0L; var map: String? = null
        val out = ArrayList<Seg>()
        val lines = pl.lines()
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            when {
                l.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> seq = l.substringAfter(':').toLong()
                l.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:") -> disc = l.substringAfter(':').toLong()
                l == "#EXT-X-DISCONTINUITY" -> disc++
                l.startsWith("#EXT-X-MAP") -> map = l.substringAfter("URI=\"").substringBefore('"')
                l.startsWith("#EXTINF") -> { out += Seg(lines[i + 1], seq++, disc, map); i++ }
            }
            i++
        }
        return out
    }

    private fun pl(mseq: Long, dseq: Long?, vararg body: String) =
        (listOf("#EXTM3U", "#EXT-X-VERSION:6", "#EXT-X-TARGETDURATION:2", "#EXT-X-MEDIA-SEQUENCE:$mseq") +
            listOfNotNull(dseq?.let { "#EXT-X-DISCONTINUITY-SEQUENCE:$it" }) + body).joinToString("\n")

    private fun live(n: Int) = arrayOf("#EXTINF:2.000,live", "https://live.example/$n.ts")
    private fun ad(n: Int) = arrayOf("#EXTINF:2.000,Amazon|ad", "https://ad.example/$n.ts")
    private val D = "#EXT-X-DISCONTINUITY"

    @Test fun midPlaylistAdKeepsNumberingStableAcrossRefreshes() {
        val tracker = AdFilter.Tracker()
        // Refresh 1: live 100,101 | ad 102,103 | live 104,105
        val r1 = AdFilter.strip(pl(100, 0, *live(100), *live(101), D, *ad(102), *ad(103), D, *live(104), *live(105)), tracker)
        // Refresh 2: window slid by 3, first discontinuity left the window (DSEQ 0 → 1)
        val r2 = AdFilter.strip(pl(103, 1, *ad(103), D, *live(104), *live(105), *live(106)), tracker)
        // Refresh 3: ads gone entirely
        val r3 = AdFilter.strip(pl(105, 2, *live(105), *live(106), *live(107)), tracker)

        assertEquals(2, r1.removed); assertEquals(4, r1.remaining)
        assertFalse(r1.text.contains("ad.example")); assertFalse(r2.text.contains("ad.example"))

        val all = listOf(r1, r2, r3).flatMap { segments(it.text) }
        // Every live segment gets exactly one (seq, disc) pair no matter which refresh it is seen in.
        all.groupBy { it.uri }.forEach { (uri, seen) ->
            assertEquals("seq of $uri", 1, seen.map { it.seq }.distinct().size)
            assertEquals("disc of $uri", 1, seen.map { it.disc }.distinct().size)
        }
        // Gapless, monotonic numbering in playback order.
        val order = all.distinctBy { it.uri }.sortedBy { it.seq }
        assertEquals((100L..105L).toList(), order.map { it.seq })
        assertEquals(listOf(100, 101, 104, 105, 106, 107).map { "https://live.example/$it.ts" }, order.map { it.uri })
        // Exactly one discontinuity at the live→ad→live junction.
        assertEquals(listOf(0L, 0L, 1L, 1L, 1L, 1L), order.map { it.disc })
        assertEquals(1, Regex("(?m)^#EXT-X-DISCONTINUITY$").findAll(r1.text).count())
    }

    @Test fun windowOfOnlyAdsThenLive() {
        val tracker = AdFilter.Tracker()
        val r0 = AdFilter.strip(pl(10, null, *live(10), *live(11)), tracker)
        val r1 = AdFilter.strip(pl(11, null, *live(11), D, *ad(12), *ad(13)), tracker)
        val r2 = AdFilter.strip(pl(12, null, *ad(12), *ad(13)), tracker)   // nothing but ads
        val r3 = AdFilter.strip(pl(13, 1, *ad(13), D, *live(14), *live(15)), tracker)
        assertEquals(0, r2.remaining)
        val all = listOf(r0, r1, r3).flatMap { segments(it.text) }
        all.groupBy { it.uri }.forEach { (uri, seen) ->
            assertEquals("seq of $uri", 1, seen.map { it.seq }.distinct().size)
            assertEquals("disc of $uri", 1, seen.map { it.disc }.distinct().size)
        }
        val order = all.distinctBy { it.uri }.sortedBy { it.seq }
        assertEquals(listOf(10L, 11L, 12L, 13L), order.map { it.seq })
        assertEquals(listOf(0L, 0L, 1L, 1L), order.map { it.disc })
    }

    @Test fun multiplePodsWithInternalDiscontinuities() {
        val r = AdFilter.strip(pl(0, 0, *live(0), D, *ad(1), D, *ad(2), D, *live(3), D, *ad(4), D, *live(5)))
        val segs = segments(r.text)
        assertEquals(listOf(0, 3, 5).map { "https://live.example/$it.ts" }, segs.map { it.uri })
        assertEquals(listOf(0L, 1L, 2L), segs.map { it.seq })
        assertEquals(listOf(0L, 1L, 2L), segs.map { it.disc })
        assertEquals(2, Regex("(?m)^#EXT-X-DISCONTINUITY$").findAll(r.text).count())
    }

    @Test fun fmp4MidPlaylistAdInitNeverReachesLive() {
        val liveInit = "#EXT-X-MAP:URI=\"https://live.example/init.mp4\""
        val adInit = "#EXT-X-MAP:URI=\"https://ad.example/init.mp4\""
        val liveInit2 = "#EXT-X-MAP:URI=\"https://live.example/init2.mp4\""
        val r = AdFilter.strip(pl(50, 0,
            liveInit, *live(50), D, adInit, *ad(51), *ad(52), D, liveInit, *live(53), D, liveInit2, *live(54)))
        val segs = segments(r.text)
        assertFalse(r.text.contains("ad.example"))
        assertEquals(listOf("https://live.example/init.mp4", "https://live.example/init.mp4", "https://live.example/init2.mp4"), segs.map { it.map })
        assertEquals(listOf(50L, 51L, 52L), segs.map { it.seq })
        // A MAP is only re-emitted when it changes.
        assertEquals(2, Regex("EXT-X-MAP").findAll(r.text).count())
    }

    @Test fun trailingTagsAndHeadersSurvive() {
        val r = AdFilter.strip(pl(7, null, *live(7), D, *ad(8), "#EXT-X-ENDLIST"))
        assertTrue(r.text.contains("#EXT-X-TARGETDURATION:2"))
        assertTrue(r.text.trimEnd().endsWith("#EXT-X-ENDLIST"))
        assertFalse(r.text.contains(D))
        // A discontinuity at the very end is held back until we know what follows it.
        val r2 = AdFilter.strip(pl(7, null, *live(7), D))
        assertFalse(r2.text.contains(D))
    }

    @Test fun twitchInfoCountry() {
        val m = "#EXTM3U\n#EXT-X-TWITCH-INFO:NODE=\"x.hls.ttvnw.net\",USER-IP=\"203.0.113.7\",USER-COUNTRY=\"IE\",B=\"false\""
        assertEquals("IE", Hls.twitchInfo(m)["USER-COUNTRY"])
        assertTrue(Hls.twitchInfo("#EXTM3U").isEmpty())
    }
}
