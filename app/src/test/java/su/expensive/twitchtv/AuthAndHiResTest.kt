package su.expensive.twitchtv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import su.expensive.twitchtv.data.Net
import su.expensive.twitchtv.data.TwitchAuth
import su.expensive.twitchtv.data.TwitchAuth.PollResult
import su.expensive.twitchtv.data.Variant
import su.expensive.twitchtv.player.DiagLog
import su.expensive.twitchtv.player.QualityPolicy

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@org.robolectric.annotation.Config(sdk = [34])
class AuthAndHiResTest {
    private val tok = "abcd1234efgh5678ijkl9012mnop34"

    @Test fun pollResponses() {
        assertEquals(PollResult.Pending, TwitchAuth.parsePoll(400, JSONObject("""{"status":400,"message":"authorization_pending"}"""), 0))
        assertEquals(PollResult.SlowDown, TwitchAuth.parsePoll(400, JSONObject("""{"message":"slow_down"}"""), 0))
        assertEquals(PollResult.Denied, TwitchAuth.parsePoll(400, JSONObject("""{"message":"access_denied"}"""), 0))
        assertEquals(PollResult.Expired, TwitchAuth.parsePoll(400, JSONObject("""{"message":"invalid device code"}"""), 0))
        val done = TwitchAuth.parsePoll(200, JSONObject("""{"access_token":"$tok","refresh_token":"r","expires_in":100}"""), 1_000)
        assertTrue(done is PollResult.Done)
        done as PollResult.Done
        assertEquals(tok, done.tokens.access); assertEquals(101_000L, done.tokens.expiresAtMs)
    }

    @Test fun tokensNeverReachLog() {
        val lines = listOf(
            "Authorization: OAuth $tok",
            "header Authorization=OAuth $tok",
            """{"access_token":"$tok","refresh_token":"zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz","device_code":"dddddddddddddddddddd"}""",
            "grant_type=refresh_token&refresh_token=$tok&device_code=$tok",
            "sent OAuth $tok to gql",
        )
        for (l in lines) assertFalse("leaked: $l", DiagLog.sanitize(l).contains(tok))
        assertFalse(DiagLog.sanitize(lines[2]).contains("zzzzzzzz"))
    }

    private fun v(h: Int, codecs: String = "hev1.1.2.L150.90.0.0.0.0.0,mp4a.40.2") =
        Variant("${h}p60", "${h}p60", h * 16 / 9, h, 60.0, 8_000_000, codecs, "u")

    @Test fun maxHeight() {
        assertEquals(1440, QualityPolicy.maxHeight(listOf(v(1080), v(1440), v(720))))
        assertEquals(0, QualityPolicy.maxHeight(emptyList()))
    }

    @Test fun hiResGuardDroppedFrames() {
        val g = QualityPolicy.HiResGuard()
        assertNull(g.onTick(0, 0, 60.0, 60.0))
        assertNull(g.onTick(3_000, 50, 60.0, 60.0)) // warm-up: decoder start drops are ignored
        var why: String? = null
        for (t in 4_000..20_000 step 1_000) { why = why ?: g.onTick(t.toLong(), 50 + (t - 4_000) / 250, 60.0, 60.0) }
        assertNotNull(why); assertTrue(why!!.contains("кадров"))
    }

    @Test fun hiResGuardSmoothIsQuiet() {
        val g = QualityPolicy.HiResGuard()
        for (t in 0..120_000 step 1_000) assertNull(g.onTick(t.toLong(), t / 20_000, 59.9, 60.0))
    }

    @Test fun hiResGuardSlowRender() {
        val g = QualityPolicy.HiResGuard()
        var why: String? = null
        for (t in 0..30_000 step 1_000) why = why ?: g.onTick(t.toLong(), 0, 40.0, 60.0)
        assertNotNull(why); assertTrue(why!!.contains("fps"))
    }

    /** Real Twitch: the device flow starts for the TV client id and is refused for the web one. */
    @Test fun liveDeviceFlowStarts() {
        assumeTrue(System.getProperty("live") == "true")
        Net.initForTests()
        val dc = TwitchAuth.startDevice()
        assertEquals(8, dc.userCode.length)
        assertTrue(dc.verificationUri.contains("twitch.tv/activate"))
        assertTrue(dc.intervalSec in 1..30)
    }
}
