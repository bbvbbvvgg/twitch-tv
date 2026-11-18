package su.expensive.twitchtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import su.expensive.twitchtv.ui.classic.ClassicApp
import su.expensive.twitchtv.ui.core.AppState
import su.expensive.twitchtv.ui.core.Design
import su.expensive.twitchtv.ui.glass.GlassApp
import su.expensive.twitchtv.ui.gtv.GtvApp
import su.expensive.twitchtv.ui.vice.ViceApp

/**
 * Hosts exactly one design tree at a time. The shared [AppState] (player session, settings,
 * caches) outlives a design switch, so the stream keeps playing and nothing is reset; the old
 * design's composition is disposed as a whole (key(design)), which releases its views and state.
 */
@UnstableApi
class MainActivity : ComponentActivity() {
    lateinit var app: AppState
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = AppState(applicationContext, (application as App).prefs)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { app.onStart() }
            override fun onStop(owner: LifecycleOwner) { app.onStop() }
        })
        setContent {
            key(app.design) {
                when (app.design) {
                    Design.CLASSIC -> ClassicApp(app)
                    Design.GLASS -> GlassApp(app)
                    Design.GTV -> GtvApp(app)
                    Design.VICE -> ViceApp(app)
                }
            }
        }
    }

    override fun onDestroy() {
        app.release()
        super.onDestroy()
    }
}
