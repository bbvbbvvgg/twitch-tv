package su.expensive.twitchtv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import su.expensive.twitchtv.player.DiagLog
import java.io.IOException

/**
 * The signed-in Twitch viewer, if any. Hands out a currently valid access token for the primary
 * PlaybackAccessToken request only; everything else (search, backups for ad bypass) stays anonymous.
 */
object Account {
    private lateinit var store: TokenStore
    var login by mutableStateOf<String?>(null); private set
    @Volatile private var cached: TwitchAuth.Tokens? = null
    @Volatile private var lastValidatedAt = 0L

    fun init(context: Context) {
        store = TokenStore(context)
        cached = store.load()
        login = if (cached != null) store.login else null
    }

    val signedIn: Boolean get() = cached != null

    fun onSignedIn(t: TwitchAuth.Tokens, userLogin: String) {
        store.save(t, userLogin); cached = t; login = userLogin
        lastValidatedAt = System.currentTimeMillis()
        DiagLog.log("Вход в Twitch выполнен: $userLogin")
        su.expensive.twitchtv.net.Connection.onAccountChanged()
    }

    fun signOut(revoke: Boolean = true) {
        val t = cached
        cached = null; login = null; store.clear()
        if (revoke && t != null) Thread { TwitchAuth.revoke(t.access) }.start()
        DiagLog.log("Выход из Twitch")
        // The old token is gone from memory and disk; nothing (proxy included) can send it now.
        su.expensive.twitchtv.net.Connection.onAccountChanged()
    }

    /**
     * Blocking (call on IO). Returns a usable access token, refreshing or re-validating as Twitch
     * requires; null when signed out or the session can no longer be renewed (then signs out).
     */
    @Synchronized
    fun accessToken(forceRefresh: Boolean = false): String? {
        var t = cached ?: return null
        val now = System.currentTimeMillis()
        if (forceRefresh || now > t.expiresAtMs - 5 * 60_000) {
            val r = t.refresh ?: run { expire("нет refresh-токена"); return null }
            val nt = try { TwitchAuth.refresh(r) } catch (e: IOException) { return t.access.takeIf { now < t.expiresAtMs } }
            if (nt == null) { expire("refresh отклонён"); return null }
            store.save(nt, login ?: ""); cached = nt; t = nt; lastValidatedAt = now
            DiagLog.log("Токен Twitch обновлён")
        } else if (now - lastValidatedAt > 3_600_000) {
            val v = try { TwitchAuth.validate(t.access) } catch (e: IOException) { return t.access }
            if (v == null) return accessToken(forceRefresh = true)
            lastValidatedAt = now
        }
        return t.access
    }

    private fun expire(why: String) {
        DiagLog.log("Сессия Twitch завершена ($why) — продолжаю анонимно")
        cached = null; login = null; store.clear()
    }
}
