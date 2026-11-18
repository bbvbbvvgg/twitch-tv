package su.expensive.twitchtv.chat

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import su.expensive.twitchtv.data.Net
import su.expensive.twitchtv.data.TwitchApi
import su.expensive.twitchtv.player.DiagLog
import kotlin.random.Random

/** A piece of a chat line: plain text or an emote image. */
sealed interface Part {
    data class Text(val text: String) : Part
    data class Emote(val name: String, val url: String) : Part
}

data class ChatLine(val id: Long, val user: String, val color: Int?, val parts: List<Part>)

/**
 * Read-only anonymous Twitch chat (IRC over WebSocket, "justinfan" login — no account, no token).
 * Optional 7TV / BTTV / FFZ emotes via their public APIs (only the channel id is sent there).
 * Keeps at most [MAX] lines in memory.
 */
class ChatClient(private val thirdPartyEmotes: Boolean) {
    companion object { const val MAX = 60 }

    val lines = mutableStateListOf<ChatLine>()
    val state = mutableStateOf("")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ws: WebSocket? = null
    private var channel = ""
    private var nextId = 0L
    private var job: Job? = null
    @Volatile private var emotes: Map<String, String> = emptyMap()
    @Volatile private var closed = false

    fun connect(login: String) {
        disconnect()
        closed = false
        channel = login.lowercase()
        lines.clear()
        state.value = "Подключение к чату…"
        job = scope.launch {
            if (thirdPartyEmotes) {
                emotes = withContext(Dispatchers.IO) { runCatching { loadThirdPartyEmotes(channel) }.getOrDefault(emptyMap()) }
            }
            open()
        }
    }

    private fun open() {
        val req = Request.Builder().url("wss://irc-ws.chat.twitch.tv:443").build()
        ws = Net.http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("CAP REQ :twitch.tv/tags")
                webSocket.send("PASS SCHMOOPIIE")
                webSocket.send("NICK justinfan${Random.nextInt(10000, 99999)}")
                webSocket.send("JOIN #$channel")
                scope.launch { state.value = "" }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                text.split("\r\n").filter { it.isNotEmpty() }.forEach { raw ->
                    if (raw.startsWith("PING")) { webSocket.send("PONG :tmi.twitch.tv"); return@forEach }
                    parse(raw)?.let { line -> scope.launch { add(line) } }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (closed) return
                DiagLog.log("Чат: обрыв (${t.javaClass.simpleName}), переподключение")
                scope.launch { state.value = "Чат переподключается…"; delay(3_000); if (!closed) open() }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed) scope.launch { delay(3_000); if (!closed) open() }
            }
        })
    }

    private fun add(line: ChatLine) {
        lines.add(line)
        while (lines.size > MAX) lines.removeAt(0)
    }

    private fun parse(raw: String): ChatLine? {
        if (!raw.contains(" PRIVMSG #")) return null
        var rest = raw
        val tags = HashMap<String, String>()
        if (rest.startsWith("@")) {
            val sp = rest.indexOf(' ')
            rest.substring(1, sp).split(';').forEach { kv ->
                val i = kv.indexOf('='); if (i > 0) tags[kv.substring(0, i)] = kv.substring(i + 1)
            }
            rest = rest.substring(sp + 1)
        }
        val nick = rest.substringAfter(':').substringBefore('!')
        val msg = rest.substringAfter(" PRIVMSG #").substringAfter(" :", "")
        val user = tags["display-name"]?.takeIf { it.isNotEmpty() }?.replace("\\s", " ") ?: nick
        val color = tags["color"]?.takeIf { it.length == 7 }?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
        return ChatLine(nextId++, user, color, buildParts(msg, tags["emotes"].orEmpty()))
    }

    /** Twitch emote ranges are code-point indices. */
    internal fun buildParts(msg: String, emoteTag: String): List<Part> {
        val cps = msg.codePoints().toArray()
        val ranges = ArrayList<Triple<Int, Int, String>>()
        if (emoteTag.isNotEmpty()) emoteTag.split('/').forEach { e ->
            val id = e.substringBefore(':')
            e.substringAfter(':').split(',').forEach { r ->
                val a = r.substringBefore('-').toIntOrNull(); val b = r.substringAfter('-').toIntOrNull()
                if (a != null && b != null && a <= b && b < cps.size) ranges += Triple(a, b, id)
            }
        }
        ranges.sortBy { it.first }
        val out = ArrayList<Part>()
        var i = 0
        fun text(a: Int, b: Int) = String(cps, a, b - a)
        for ((a, b, id) in ranges) {
            if (a < i) continue
            if (a > i) out += splitThirdParty(text(i, a))
            out += Part.Emote(text(a, b + 1), "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/1.0")
            i = b + 1
        }
        if (i < cps.size) out += splitThirdParty(text(i, cps.size))
        return out
    }

    private fun splitThirdParty(s: String): List<Part> {
        val em = emotes
        if (em.isEmpty()) return listOf(Part.Text(s))
        val out = ArrayList<Part>(); val sb = StringBuilder()
        val words = s.split(' ')
        for ((idx, w) in words.withIndex()) {
            val url = if (w.isNotEmpty()) em[w] else null
            if (url != null) {
                if (sb.isNotEmpty()) { out += Part.Text(sb.toString()); sb.clear() }
                out += Part.Emote(w, url)
            } else sb.append(w)
            if (idx < words.lastIndex) sb.append(' ')
        }
        if (sb.isNotEmpty()) out += Part.Text(sb.toString())
        return out
    }

    private fun loadThirdPartyEmotes(login: String): Map<String, String> {
        val id = TwitchApi.channelId(login) ?: return emptyMap()
        val map = HashMap<String, String>()
        fun json(url: String) = runCatching { Net.getText(url).takeIf { it.ok }?.body }.getOrNull()
        // BTTV
        json("https://api.betterttv.net/3/cached/emotes/global")?.let { t ->
            val a = JSONArray(t); for (i in 0 until a.length()) a.getJSONObject(i).let { map[it.getString("code")] = "https://cdn.betterttv.net/emote/${it.getString("id")}/1x" }
        }
        json("https://api.betterttv.net/3/cached/users/twitch/$id")?.let { t ->
            val o = JSONObject(t)
            listOf("channelEmotes", "sharedEmotes").forEach { k ->
                o.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { map[it.getString("code")] = "https://cdn.betterttv.net/emote/${it.getString("id")}/1x" } }
            }
        }
        // FFZ (via BTTV's cache)
        json("https://api.betterttv.net/3/cached/frankerfacez/users/twitch/$id")?.let { t ->
            val a = JSONArray(t)
            for (i in 0 until a.length()) a.getJSONObject(i).let { e -> e.optJSONObject("images")?.optString("1x")?.takeIf { it.isNotEmpty() }?.let { map[e.getString("code")] = it } }
        }
        // 7TV
        fun seven(set: JSONObject?) {
            val a = set?.optJSONArray("emotes") ?: return
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                val host = e.optJSONObject("data")?.optJSONObject("host") ?: continue
                map[e.getString("name")] = "https:" + host.getString("url") + "/1x.webp"
            }
        }
        json("https://7tv.io/v3/emote-sets/global")?.let { seven(JSONObject(it)) }
        json("https://7tv.io/v3/users/twitch/$id")?.let { seven(JSONObject(it).optJSONObject("emote_set")) }
        DiagLog.log("Чат: загружено ${map.size} эмоутов 7TV/BTTV/FFZ")
        return map
    }

    fun disconnect() {
        closed = true
        job?.cancel()
        ws?.close(1000, null); ws = null
    }

    fun release() { disconnect(); scope.cancel() }
}
