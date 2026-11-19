package su.expensive.twitchtv.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import su.expensive.twitchtv.data.Net

/**
 * Process-wide entry point to the connection settings and the one [ConnectionEngine].
 * All four designs read and change the same instance; none of them talks to the network itself.
 */
object Connection {
    lateinit var settings: NetSettings; private set
    lateinit var engine: ConnectionEngine; private set
    /** Bumped on every settings change so Compose screens recompose. */
    var version by mutableIntStateOf(0); private set

    fun init(context: Context) = init(NetSettings(context))

    fun init(s: NetSettings, get: ((String, Map<String, String>) -> su.expensive.twitchtv.data.HttpText)? = null) {
        settings = s
        engine = ConnectionEngine(
            settings = { ConnectionEngine.NetSnapshot(s.mode, s.reyohohoServer, s.reyohohoConsented, s.reyohohoSendToken) },
            get = get ?: { url, headers -> Net.getText(url, headers, Net.proxyApi) },
        )
    }

    val isInitialized: Boolean get() = ::settings.isInitialized

    fun changed() { version++ }

    /** Called on sign-in and sign-out: a new account gets a fresh chance with every server. */
    fun onAccountChanged() { if (isInitialized) engine.resetTokenMemory(); changed() }
}
