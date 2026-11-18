package su.expensive.twitchtv.ui.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import su.expensive.twitchtv.chat.ChatClient
import su.expensive.twitchtv.data.Prefs
import su.expensive.twitchtv.data.StreamItem
import su.expensive.twitchtv.player.PlayStatus
import su.expensive.twitchtv.player.PlaybackController
import su.expensive.twitchtv.player.QualityChoice

/**
 * One open stream: the PlaybackController (ExoPlayer, ad bypass, reconnects, 1440p guard),
 * the chat connection and the player-screen state every design shows in its own way.
 * Created by [AppState.open], released by [AppState.close]; a recomposition or a design switch
 * never touches it.
 */
@UnstableApi
class PlayerSession(context: Context, val prefs: Prefs, start: StreamItem, val list: List<StreamItem>) {
    private val appContext = context.applicationContext
    val controller = PlaybackController(appContext, prefs)
    val chat = ChatClient(prefs.chatThirdPartyEmotes)

    var item by mutableStateOf(start); private set
    var chatOn by mutableStateOf(prefs.chatEnabled); private set
    var diagOn by mutableStateOf(prefs.showDiagnostics); private set
    var toast by mutableStateOf<String?>(null)
    /** Bumped when favorites change so labels recompose. */
    var favVersion by mutableIntStateOf(0); private set

    private var stopped = false
    private var released = false

    init {
        controller.open(start.login)
        if (chatOn) chat.connect(start.login)
    }

    val isFavorite: Boolean get() = favVersion >= 0 && prefs.isFavorite(item.login)
    val playing: Boolean get() = controller.status == PlayStatus.PLAYING

    /** Human label of the quality in use: "Авто · 1440p60 HEVC". */
    val qualityLabel: String get() {
        val cur = controller.current ?: return "—"
        return if (controller.userChoice == null) "Авто · ${cur.label} ${cur.codecLabel}" else "${cur.label} ${cur.codecLabel}"
    }

    fun switchTo(s: StreamItem) {
        if (released || s.login == item.login) return
        item = s
        prefs.addHistory(s.login)
        StreamRepo.invalidate(StreamRepo.Key.HISTORY)
        controller.open(s.login)
        if (chatOn) chat.connect(s.login)
        toast = s.displayName
    }

    fun step(delta: Int) {
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.login == item.login }
        val next = list[((if (idx < 0) 0 else idx) + delta + list.size) % list.size]
        switchTo(next)
    }

    fun toggleChat() {
        chatOn = !chatOn
        if (chatOn) chat.connect(item.login) else chat.disconnect()
    }

    fun toggleDiag() { diagOn = !diagOn }

    fun toggleFavorite() {
        val now = prefs.toggleFavorite(item.login)
        StreamRepo.invalidate(StreamRepo.Key.FAVORITES)
        favVersion++
        toast = if (now) "Добавлено в избранное" else "Убрано из избранного"
    }

    fun selectQuality(choice: QualityChoice?) = controller.selectQuality(choice)
    fun resumeLive() = controller.resumeLive()
    fun exportLog(ctx: Context) { toast = exportLogs(ctx, prefs, controller.diag.asText()) }

    /** Text shown in every design's diagnostics overlay. */
    fun diagText(): String = controller.diag.asText() +
        (controller.current?.let { "Вариант: ${it.label} ${it.codecLabel} (${it.bandwidth / 1000} кбит/с)\n" } ?: "") +
        (controller.autoCapLabel?.let { "Авто ограничило до: $it\n" } ?: "")

    /** Free decoders/network while the TV app is in the background; reopen at the live edge on return. */
    fun onStop() {
        if (released || stopped) return
        stopped = true; controller.player.stop(); chat.disconnect()
    }

    fun onStart() {
        if (released || !stopped) return
        stopped = false; controller.open(item.login); if (chatOn) chat.connect(item.login)
    }

    fun release() {
        if (released) return
        released = true
        chat.release(); controller.release()
    }
}
