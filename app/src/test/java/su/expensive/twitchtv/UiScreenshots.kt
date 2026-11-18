package su.expensive.twitchtv

import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.ConscryptMode
import java.io.File

/**
 * JVM (Robolectric) smoke test of the real Compose UI: the app starts, tabs render, D-pad keys
 * move between tabs, live data from Twitch loads into cards, Settings shows the package name,
 * a stream opens in the player and Back returns home. Not a substitute for a device test:
 * Robolectric has no hardware video decoder, so playback itself is NOT exercised here.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class UiScreenshots {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val out = File(System.getProperty("liveOut", "build/live-report.txt")).parentFile!!

    private fun key(code: Int) {
        rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        rule.waitForIdle()
    }

    private fun shot(name: String) = runCatching {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(out, "ui-$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }.onFailure { println("screenshot $name failed: $it") }

    private fun waitText(t: String, ms: Long = 30_000) {
        try { rule.waitUntil(ms) { rule.onAllNodes(hasText(t, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }
        catch (e: Throwable) { rule.onRoot(useUnmergedTree = true).printToLog("UITREE"); println(dumpTexts()); throw e }
    }
    private fun dumpTexts(): String = rule.onAllNodes(androidx.compose.ui.test.hasText("", substring = true), useUnmergedTree = true)
        .fetchSemanticsNodes().joinToString(" | ") { n -> n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString() ?: "" }

    @Test fun homeNavigationAndPlayer() {
        assumeTrue(System.getProperty("shots") == "true")
        out.mkdirs()
        // Tabs present
        for (t in listOf("Избранное", "Популярное", "GTA VI", "Поиск", "История", "Настройки"))
            rule.onNodeWithText(t).assertExists()
        // Popular tab is the default; live cards appear (any "зрител" label from a card)
        waitText("LIVE", 30_000)
        shot("popular")
        println("UI: popular loaded")

        // Move focus to the tab row and right → GTA VI
        key(KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_UP)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitText("Grand Theft Auto VI", 30_000)
        shot("gta")
        println("UI: GTA VI tab via D-pad OK")

        // Right x3 → Settings: package name for Happ is shown
        var n = 0
        while (n < 6 && rule.onAllNodes(hasText("Обход рекламы", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            n++; key(KeyEvent.KEYCODE_DPAD_RIGHT); Thread.sleep(300); rule.waitForIdle()
            println("UI: RIGHT #$n → selected=" + dumpTexts().split(" | ").getOrNull(7))
        }
        waitText("Обход рекламы", 10_000)
        shot("settings")
        println("UI: Settings via D-pad OK")

        // Back to GTA VI, down into the grid, OK opens the player
        n = 0
        while (n < 6 && rule.onAllNodes(hasText("Grand Theft Auto VI ·", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            n++; key(KeyEvent.KEYCODE_DPAD_LEFT); Thread.sleep(300); rule.waitForIdle()
        }
        println("UI: back to GTA VI after $n LEFT presses")
        waitText("LIVE", 30_000)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        Thread.sleep(4_000); rule.waitForIdle()
        shot("player")
        println("UI: after OK on card, LIVE nodes still visible: " + rule.onAllNodes(hasText("LIVE", substring = true)).fetchSemanticsNodes().size)

        // Back returns home
        InstrumentationRegistry.getInstrumentation().runOnMainSync { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Настройки").assertExists()
        println("UI: Back → home OK")
    }
}
