package su.expensive.twitchtv.ui.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import su.expensive.twitchtv.data.Prefs
import su.expensive.twitchtv.data.StreamItem

/**
 * The four interface designs. Each one is a separate Compose tree (see ui/classic, ui/glass,
 * ui/gtv, ui/vice); only the selected one is ever composed. They share everything below the UI:
 * TwitchApi, PlaybackController/Media3, Prefs (favorites, history, settings) and Account.
 */
enum class Design(val title: String, val tagline: String) {
    CLASSIC("Classic", "Вкладки сверху и сетка карточек — оригинальный интерфейс"),
    GLASS("Liquid Glass", "Плавающий док, большое превью, стеклянные панели"),
    GTV("Google TV", "Большой баннер и карусели «Для вас»"),
    VICE("GTA VI", "Меню Vice City: список-миссии и постер, неон заката"),
}

/**
 * App-level state shared by every design. Lives for the Activity; designs read and drive it.
 * The player session is owned here, not by a design, so switching design never recreates the
 * ExoPlayer or reconnects the stream.
 */
@UnstableApi
class AppState(private val context: Context, val prefs: Prefs) {
    var design by mutableStateOf(prefs.design)
        private set

    /** Non-null while a stream is open. */
    var session by mutableStateOf<PlayerSession?>(null)
        private set

    /** Set when the design was just changed from Settings: the new design opens on its Settings screen. */
    var openSettingsOnce by mutableStateOf(false)

    val settings = SettingsModel(context, prefs, this)

    fun applyDesign(d: Design) {
        if (d == design) return
        prefs.design = d
        openSettingsOnce = true
        design = d
    }

    fun open(item: StreamItem, list: List<StreamItem>) {
        if (!item.live) return
        prefs.addHistory(item.login)
        StreamRepo.invalidate(StreamRepo.Key.HISTORY)
        session?.release()
        session = PlayerSession(context, prefs, item, list.filter { it.live }.ifEmpty { listOf(item) })
    }

    fun close() {
        val s = session ?: return
        session = null
        s.release()
    }

    fun onStop() = session?.onStop()
    fun onStart() = session?.onStart()
    fun release() = close()
}
