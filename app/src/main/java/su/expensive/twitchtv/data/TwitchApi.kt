package su.expensive.twitchtv.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import kotlin.random.Random

data class StreamItem(
    val login: String,
    val displayName: String,
    val title: String,
    val viewers: Int,
    val game: String,
    val thumbnail: String,
    val avatar: String,
    val live: Boolean = true,
)

data class PlaybackToken(val value: String, val signature: String)

/**
 * Anonymous Twitch GQL access, the same public web client id every Twitch web/TV client
 * (and SmartTwitchTV / TwitchAdSolutions) uses. No user token is ever sent anywhere.
 */
object TwitchApi {
    const val CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
    const val GTA6_NAME = "Grand Theft Auto VI"
    private const val GQL = "https://gql.twitch.tv/gql"
    private const val TOKEN_HASH = "ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9"
    private val JSON = "application/json".toMediaType()

    /** Random per-install-session device id (not tied to the user). */
    val deviceId: String = newDeviceId()

    fun newDeviceId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    @Throws(IOException::class)
    private fun gql(body: String, devId: String = deviceId): JSONObject {
        val req = Request.Builder().url(GQL)
            .header("Client-ID", CLIENT_ID)
            .header("X-Device-Id", devId)
            .post(body.toRequestBody(JSON))
            .build()
        Net.http.newCall(req).execute().use { r ->
            val txt = r.body?.string() ?: ""
            if (!r.isSuccessful) throw IOException("GQL HTTP ${r.code}")
            val obj = JSONObject(txt)
            obj.optJSONArray("errors")?.let { errs ->
                if (obj.optJSONObject("data") == null) throw IOException("GQL: " + errs.optJSONObject(0)?.optString("message"))
            }
            return obj
        }
    }

    private fun q(query: String): String = JSONObject().put("query", query).toString()
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private const val STREAM_FIELDS =
        "title viewersCount previewImageURL(width:640,height:360) " +
            "broadcaster{login displayName profileImageURL(width:70)} game{displayName}"

    private fun parseStream(node: JSONObject?): StreamItem? {
        node ?: return null
        val b = node.optJSONObject("broadcaster") ?: return null
        return StreamItem(
            login = b.optString("login"),
            displayName = b.optString("displayName").ifEmpty { b.optString("login") },
            title = node.optString("title"),
            viewers = node.optInt("viewersCount"),
            game = node.optJSONObject("game")?.optString("displayName") ?: "",
            // Twitch caches previews for ~5 min; a time bucket keeps them fresh without refetch storms.
            thumbnail = node.optString("previewImageURL").let { if (it.isEmpty()) it else "$it?t=${System.currentTimeMillis() / 300_000}" },
            avatar = b.optString("profileImageURL"),
        )
    }

    private fun edges(arr: JSONArray?): List<StreamItem> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parseStream(arr.optJSONObject(it)?.optJSONObject("node")) }
    }

    @Throws(IOException::class)
    fun topStreams(count: Int = 30): List<StreamItem> {
        val d = gql(q("query{streams(first:${count.coerceIn(1, 30)}){edges{node{$STREAM_FIELDS}}}}")).getJSONObject("data")
        return edges(d.optJSONObject("streams")?.optJSONArray("edges"))
    }

    data class Category(val id: String, val name: String, val viewers: Int)

    /** Resolves the category by its official name, falling back to category search. */
    @Throws(IOException::class)
    fun findCategory(name: String = GTA6_NAME): Category? {
        val d = gql(q("query{game(name:\"${esc(name)}\"){id displayName viewersCount}}")).optJSONObject("data")
        d?.optJSONObject("game")?.let { return Category(it.optString("id"), it.optString("displayName"), it.optInt("viewersCount")) }
        val s = gql(q("query{searchCategories(query:\"${esc(name)}\",first:5){edges{node{id displayName viewersCount}}}}"))
            .optJSONObject("data")?.optJSONObject("searchCategories")?.optJSONArray("edges")
        val nodes = (0 until (s?.length() ?: 0)).mapNotNull { s?.optJSONObject(it)?.optJSONObject("node") }
        val n = nodes.firstOrNull { it.optString("displayName").equals(name, true) }
            ?: nodes.firstOrNull { it.optString("displayName").endsWith(" VI") } ?: nodes.firstOrNull() ?: return null
        return Category(n.optString("id"), n.optString("displayName"), n.optInt("viewersCount"))
    }

    @Throws(IOException::class)
    fun categoryStreams(categoryId: String, count: Int = 30): List<StreamItem> {
        val d = gql(q("query{game(id:\"${esc(categoryId)}\"){streams(first:${count.coerceIn(1, 30)}){edges{node{$STREAM_FIELDS}}}}}"))
            .getJSONObject("data")
        return edges(d.optJSONObject("game")?.optJSONObject("streams")?.optJSONArray("edges"))
    }

    /** Channels matching the query; live ones first. */
    @Throws(IOException::class)
    fun search(query: String): List<StreamItem> {
        val gq = "query{searchFor(userQuery:\"${esc(query)}\",platform:\"web\",options:{targets:[{index:CHANNEL}]})" +
            "{channels{items{login displayName profileImageURL(width:70) stream{$STREAM_FIELDS}}}}}"
        val items = gql(q(gq)).optJSONObject("data")?.optJSONObject("searchFor")
            ?.optJSONObject("channels")?.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<StreamItem>()
        for (i in 0 until items.length()) {
            val u = items.optJSONObject(i) ?: continue
            val s = u.optJSONObject("stream")
            out += parseStream(s) ?: StreamItem(
                login = u.optString("login"), displayName = u.optString("displayName"), title = "",
                viewers = 0, game = "", thumbnail = "", avatar = u.optString("profileImageURL"), live = false,
            )
        }
        return out.sortedByDescending { if (it.live) it.viewers + 1 else 0 }
    }

    /** Status for a set of logins (favorites / history). Offline channels are returned with live=false. */
    @Throws(IOException::class)
    fun channels(logins: List<String>): List<StreamItem> {
        if (logins.isEmpty()) return emptyList()
        val out = ArrayList<StreamItem>()
        logins.distinct().chunked(30).forEach { chunk ->
            val list = chunk.joinToString(",") { "\"${esc(it)}\"" }
            val arr = gql(q("query{users(logins:[$list]){login displayName profileImageURL(width:70) stream{$STREAM_FIELDS}}}"))
                .optJSONObject("data")?.optJSONArray("users") ?: return@forEach
            for (i in 0 until arr.length()) {
                val u = arr.optJSONObject(i) ?: continue
                out += parseStream(u.optJSONObject("stream")) ?: StreamItem(
                    login = u.optString("login"), displayName = u.optString("displayName"), title = "",
                    viewers = 0, game = "", thumbnail = "", avatar = u.optString("profileImageURL"), live = false,
                )
            }
        }
        return out
    }

    @Throws(IOException::class)
    fun channelId(login: String): String? =
        gql(q("query{user(login:\"${esc(login)}\"){id}}")).optJSONObject("data")?.optJSONObject("user")?.optString("id")

    @Throws(IOException::class)
    fun streamInfo(login: String): StreamItem? =
        parseStream(gql(q("query{user(login:\"${esc(login)}\"){stream{$STREAM_FIELDS}}}"))
            .optJSONObject("data")?.optJSONObject("user")?.optJSONObject("stream"))

    /**
     * Playback access token. playerType matters for ads: "popout"/"embed" currently do not carry
     * preroll ads for anonymous viewers, "autoplay" (android) is a 360p-only clean fallback.
     */
    @Throws(IOException::class)
    fun playbackToken(login: String, playerType: String, devId: String = deviceId): PlaybackToken {
        val platform = if (playerType == "autoplay") "android" else "web"
        val body = JSONObject()
            .put("operationName", "PlaybackAccessToken")
            .put("variables", JSONObject()
                .put("isLive", true).put("login", login).put("isVod", false).put("vodID", "")
                .put("playerType", playerType).put("platform", platform))
            .put("extensions", JSONObject().put("persistedQuery",
                JSONObject().put("version", 1).put("sha256Hash", TOKEN_HASH)))
        val t = gql(body.toString(), devId).optJSONObject("data")?.optJSONObject("streamPlaybackAccessToken")
            ?: throw IOException("Нет токена воспроизведения (канал не найден?)")
        return PlaybackToken(t.getString("value"), t.getString("signature"))
    }

    fun usherUrl(login: String, token: PlaybackToken, codecs: String): String {
        fun e(s: String) = URLEncoder.encode(s, "UTF-8")
        return "https://usher.ttvnw.net/api/channel/hls/${e(login.lowercase())}.m3u8" +
            "?allow_source=true&allow_audio_only=true&fast_bread=true&playlist_include_framerate=true" +
            "&reassignments_supported=true&player_backend=mediaplayer&cdm=wv" +
            "&supported_codecs=${e(codecs)}&p=${Random.nextInt(1_000_000, 9_999_999)}" +
            "&sig=${e(token.signature)}&token=${e(token.value)}"
    }
}
