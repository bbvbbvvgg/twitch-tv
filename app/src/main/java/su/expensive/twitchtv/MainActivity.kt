package su.expensive.twitchtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import su.expensive.twitchtv.data.StreamItem
import su.expensive.twitchtv.ui.HomeScreen
import su.expensive.twitchtv.ui.Palette
import su.expensive.twitchtv.ui.PlayerScreen
import su.expensive.twitchtv.ui.TvTheme

/** Simple two-level navigation: Home ⇄ full-screen Player. Back from the player returns to the same tab/focus. */
@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = (application as App).prefs
        setContent {
            TvTheme {
                var playing by remember { mutableStateOf<Pair<StreamItem, List<StreamItem>>?>(null) }
                var tab by rememberSaveable { mutableStateOf(if (prefs.favorites().isNotEmpty()) 0 else 1) }
                Box(Modifier.fillMaxSize().background(Palette.bg)) {
                    val p = playing
                    if (p == null) {
                        HomeScreen(
                            prefs = prefs,
                            initialTab = tab,
                            onTabChanged = { tab = it },
                            onOpen = { item, list -> prefs.addHistory(item.login); playing = item to list },
                        )
                    } else {
                        BackHandler { playing = null }
                        PlayerScreen(
                            prefs = prefs,
                            start = p.first,
                            list = p.second,
                            onExit = { playing = null },
                        )
                    }
                }
            }
        }
    }
}
