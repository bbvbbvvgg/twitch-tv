package su.expensive.twitchtv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import su.expensive.twitchtv.ui.core.Design
import su.expensive.twitchtv.ui.core.StreamRepo
import java.io.File

/**
 * Renders every design with Robolectric's native graphics (real Android Skia/HWUI code paths in a
 * JVM — not an emulator and not the TV) and drives it with D-pad key events against live Twitch
 * data. For each design: home, player (controls revealed), settings; plus a design switch while
 * a stream is open, and the homes at 1280×720.
 *
 * Opt-in: -Dshots=true (and -Dlive=true for network). On aarch64 hosts run the test JVM under
 * x86_64 emulation: -PtestJvm=… (see docs/SCREENSHOTS.md). Video frames are never decoded here:
 * Robolectric has no media codecs, so the player shows no decoded video.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class DesignScreenshots {
    @get:Rule val compose = createEmptyComposeRule()
    private val out = File(System.getProperty("liveOut", "build/live-report.txt")).parentFile!!.resolve("shots")
    private var scenario: ActivityScenario<MainActivity>? = null
    private val log = StringBuilder()

    private fun note(s: String) { println("SHOT: $s"); log.appendLine(s) }

    @After fun tearDown() {
        scenario?.close()
        out.mkdirs(); File(out, "log-${System.nanoTime()}.txt").writeText(log.toString())
    }

    private fun launch(d: Design) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("twitchtv", Context.MODE_PRIVATE).edit().putString("design", d.name).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.mainClock.autoAdvance = false
        pump(1500)
    }

    /** Advances the Compose clock and the main looper while background IO completes in real time. */
    private fun pump(ms: Long) {
        var left = ms
        while (left > 0) {
            compose.mainClock.advanceTimeBy(80)
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(40); left -= 80
        }
    }

    private fun activity(): MainActivity { var a: MainActivity? = null; scenario!!.onActivity { a = it }; return a!! }

    private fun key(code: Int, times: Int = 1) = repeat(times) {
        scenario!!.onActivity { it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }
        pump(350)
    }

    private fun texts(): List<String> = runCatching {
        compose.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { n -> n.config.getOrElseNullable(SemanticsProperties.Text) { null }?.map { it.text } ?: emptyList() }
    }.getOrDefault(emptyList())

    private fun waitFor(desc: String, ms: Long = 40_000, cond: (List<String>) -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond(texts())) return true; pump(400) }
        note("TIMEOUT waiting for $desc; visible: " + texts().take(40).joinToString(" | ")); return false
    }

    private fun shot(name: String) {
        pump(900)
        scenario!!.onActivity { a ->
            val v = a.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            out.mkdirs(); File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        note("saved $name.png")
    }

    private fun liveData() = texts().any { it.contains("тыс") || it.contains("млн") }

    /** Opens the first popular stream through the shared AppState (same call every design's OK uses). */
    private fun openFirstPopular() {
        val list = StreamRepo.cached("POPULAR:") ?: StreamRepo.load(StreamRepo.Key.POPULAR, activity().app.prefs)
        val first = list.first { it.live }
        scenario!!.onActivity { it.app.open(first, list) }
        pump(2500)
        assertNotNull("session must exist", activity().app.session)
        note("player opened: ${first.login}")
    }

    private fun runDesign(d: Design, toHome: () -> Unit, revealPlayer: () -> Unit) {
        assumeTrue(System.getProperty("shots") == "true")
        launch(d)
        toHome()
        assertTrue("${d.name}: live list rendered", waitFor("live data") { liveData() })
        shot("${d.name.lowercase()}-1-home")

        // D-pad sanity: move around and make sure the activity is still responsive.
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_RIGHT, 2); key(KeyEvent.KEYCODE_DPAD_LEFT)
        note("${d.name}: d-pad moves ok, visible=" + texts().take(6))

        openFirstPopular()
        revealPlayer()
        shot("${d.name.lowercase()}-2-player")
        // Back closes controls, second Back leaves the player.
        key(KeyEvent.KEYCODE_BACK); key(KeyEvent.KEYCODE_BACK)
        pump(800)
        if (activity().app.session != null) { key(KeyEvent.KEYCODE_BACK); pump(800) }
        assertEquals("${d.name}: Back returns home", null, activity().app.session)
        note("${d.name}: back to home ok")

        // Settings: switch from another design into this one -> it opens on its own settings screen.
        val other = if (d == Design.CLASSIC) Design.GTV else Design.CLASSIC
        scenario!!.onActivity { it.app.applyDesign(other) }
        pump(1200)
        scenario!!.onActivity { it.app.applyDesign(d) }
        pump(2500)
        assertEquals(d, activity().app.prefs.design)
        waitFor("settings") { t -> t.any { it.contains("Дизайн интерфейса") || it.contains("Обход рекламы") || it.contains("Classic") } }
        shot("${d.name.lowercase()}-3-settings")
    }

    @Test fun classic() = runDesign(Design.CLASSIC, toHome = { key(KeyEvent.KEYCODE_DPAD_RIGHT) }) { key(KeyEvent.KEYCODE_DPAD_CENTER); pump(1000) }
    @Test fun glass() = runDesign(Design.GLASS, toHome = { key(KeyEvent.KEYCODE_DPAD_RIGHT) }) { key(KeyEvent.KEYCODE_DPAD_CENTER); pump(1200) }
    @Test fun googleTv() = runDesign(Design.GTV, toHome = {}) { key(KeyEvent.KEYCODE_DPAD_CENTER); pump(1200) }
    @Test fun vice() = runDesign(Design.VICE, toHome = { key(KeyEvent.KEYCODE_DPAD_DOWN, 1) }) { key(KeyEvent.KEYCODE_DPAD_CENTER); pump(1200) }

    /** Switching design while a stream is open keeps the same session and ExoPlayer instance. */
    @Test fun switchDesignDuringPlayback() {
        assumeTrue(System.getProperty("shots") == "true")
        launch(Design.CLASSIC)
        waitFor("live data") { liveData() }
        openFirstPopular()
        val session = activity().app.session
        val player = session!!.controller.player
        for (d in listOf(Design.GLASS, Design.GTV, Design.VICE, Design.CLASSIC, Design.VICE)) {
            scenario!!.onActivity { it.app.applyDesign(d) }
            pump(1200)
            assertSame("session survives switch to $d", session, activity().app.session)
            assertSame("player survives switch to $d", player, activity().app.session!!.controller.player)
            note("switch to $d during playback: same session/player")
        }
        key(KeyEvent.KEYCODE_DPAD_CENTER); pump(800)
        shot("vice-2b-player-after-switch")
        // Persisted: a fresh read of prefs returns the last choice.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("VICE", ctx.getSharedPreferences("twitchtv", Context.MODE_PRIVATE).getString("design", null))
    }

    /** «Сеть и прокси» in each design, then the Reyohoho consent modal (declined: mode stays Direct). */
    private fun runNetwork(d: Design) {
        assumeTrue(System.getProperty("shots") == "true")
        launch(if (d == Design.CLASSIC) Design.GTV else Design.CLASSIC)
        scenario!!.onActivity { it.app.openNetworkOnce = true; it.app.applyDesign(d) }
        pump(2500)
        var n = 0
        val netVisible = { t: List<String> -> t.any { it.contains("Проверить подключение", ignoreCase = true) || (d == Design.VICE && it == "СВОЙ ПРОКСИ") } }
        while (n < 30 && !netVisible(texts())) { key(KeyEvent.KEYCODE_DPAD_DOWN); n++ }
        // Lists that scroll (Classic, Glass): a few more steps so the whole section is on screen.
        if (d == Design.GLASS) key(KeyEvent.KEYCODE_DPAD_DOWN, 3)
        assertTrue("${d.name}: network section visible", waitFor("network section", 10_000, netVisible))
        note("${d.name}: network section after $n DOWN; visible=" + texts().take(12))
        shot("${d.name.lowercase()}-4-network")
        val m = activity().app.settings
        scenario!!.onActivity { m.chooseMode(su.expensive.twitchtv.net.ConnectionMode.REYOHOHO) }
        pump(1200)
        assertTrue("${d.name}: consent modal", m.consentAsk)
        waitFor("consent text", 10_000) { t -> t.any { it.contains("OAuth") } }
        shot("${d.name.lowercase()}-5-consent")
        scenario!!.onActivity { m.declineConsent() }
        pump(800)
        assertEquals(su.expensive.twitchtv.net.ConnectionMode.DIRECT, su.expensive.twitchtv.net.Connection.settings.mode)
    }

    @Test fun networkClassic() = runNetwork(Design.CLASSIC)
    @Test fun networkGlass() = runNetwork(Design.GLASS)
    @Test fun networkGoogleTv() = runNetwork(Design.GTV)
    @Test fun networkVice() = runNetwork(Design.VICE)

    /** Homes at 1280×720 (tvdpi) — the other common TV output resolution. */
    @Config(qualifiers = "w960dp-h540dp-land-television-tvdpi")
    @Test fun homesAt720p() {
        assumeTrue(System.getProperty("shots") == "true")
        for (d in Design.entries) {
            launch(d)
            if (d == Design.CLASSIC || d == Design.GLASS) key(KeyEvent.KEYCODE_DPAD_RIGHT)
            if (d == Design.VICE) key(KeyEvent.KEYCODE_DPAD_DOWN)
            waitFor("live data $d") { liveData() }
            shot("${d.name.lowercase()}-720p-home")
            scenario?.close()
        }
    }
}
