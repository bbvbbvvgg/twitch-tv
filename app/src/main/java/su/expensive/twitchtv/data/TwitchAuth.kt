package su.expensive.twitchtv.data

import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Twitch sign-in via the OAuth 2.0 Device Authorization Grant (RFC 8628), as documented at
 * dev.twitch.tv/docs/authentication/getting-tokens-oauth/#device-code-grant-flow.
 *
 * The TV shows a short code; the user confirms it on twitch.tv/activate from a phone. The app never
 * sees the password and never touches browser cookies.
 *
 * Client id: Twitch's playback GraphQL (gql.twitch.tv) only accepts Twitch's own first-party client
 * ids, and an OAuth token is only accepted together with the client id it was issued to. A token
 * from a third-party app registered on dev.twitch.tv is therefore rejected for PlaybackAccessToken
 * (verified: GQL answers 400 "Client-ID header is invalid" for non-first-party ids). The only
 * client id that both supports the device flow and is accepted by GQL is the one Twitch's own
 * Android TV app uses — so that is what the device flow here is started with.
 */
object TwitchAuth {
    const val TV_CLIENT_ID = "ue6666qo983tsx6so1t0vnawi233wa"
    private const val DEVICE_URL = "https://id.twitch.tv/oauth2/device"
    private const val TOKEN_URL = "https://id.twitch.tv/oauth2/token"
    private const val VALIDATE_URL = "https://id.twitch.tv/oauth2/validate"
    private const val REVOKE_URL = "https://id.twitch.tv/oauth2/revoke"
    private const val GRANT_DEVICE = "urn:ietf:params:oauth:grant-type:device_code"
    /** Playback needs no Helix scopes; the token only identifies the viewer. */
    private const val SCOPES = ""

    data class DeviceCode(
        val deviceCode: String, val userCode: String, val verificationUri: String,
        val expiresInSec: Int, val intervalSec: Int,
    )

    data class Tokens(val access: String, val refresh: String?, val expiresAtMs: Long)

    sealed interface PollResult {
        data object Pending : PollResult
        data object SlowDown : PollResult
        data object Denied : PollResult
        data object Expired : PollResult
        data class Done(val tokens: Tokens) : PollResult
        data class Error(val message: String) : PollResult
    }

    data class Validation(val login: String, val userId: String, val clientId: String, val expiresInSec: Long)

    private fun post(url: String, form: Map<String, String>): Pair<Int, JSONObject> {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        Net.http.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            return r.code to runCatching { JSONObject(txt) }.getOrDefault(JSONObject())
        }
    }

    @Throws(IOException::class)
    fun startDevice(): DeviceCode {
        val (code, j) = post(DEVICE_URL, mapOf("client_id" to TV_CLIENT_ID, "scopes" to SCOPES))
        if (code != 200 || !j.has("device_code")) throw IOException("Twitch не выдал код входа (HTTP $code: ${j.optString("message")})")
        return parseDevice(j)
    }

    internal fun parseDevice(j: JSONObject) = DeviceCode(
        deviceCode = j.getString("device_code"),
        userCode = j.getString("user_code"),
        verificationUri = j.optString("verification_uri", "https://www.twitch.tv/activate"),
        expiresInSec = j.optInt("expires_in", 1800),
        intervalSec = j.optInt("interval", 5).coerceAtLeast(1),
    )

    fun poll(deviceCode: String, now: Long = System.currentTimeMillis()): PollResult = try {
        val (code, j) = post(TOKEN_URL, mapOf(
            "client_id" to TV_CLIENT_ID, "device_code" to deviceCode,
            "grant_type" to GRANT_DEVICE, "scopes" to SCOPES))
        parsePoll(code, j, now)
    } catch (e: IOException) { PollResult.Pending } // network blip: keep polling until expiry

    internal fun parsePoll(code: Int, j: JSONObject, now: Long): PollResult {
        if (code == 200 && j.has("access_token")) return PollResult.Done(parseTokens(j, now))
        return when (j.optString("message").lowercase()) {
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown
            "access_denied" -> PollResult.Denied
            "expired_token", "invalid device code" -> PollResult.Expired
            else -> PollResult.Error("HTTP $code ${j.optString("message")}")
        }
    }

    internal fun parseTokens(j: JSONObject, now: Long) = Tokens(
        access = j.getString("access_token"),
        refresh = j.optString("refresh_token").ifEmpty { null },
        expiresAtMs = now + j.optLong("expires_in", 4 * 3600L) * 1000,
    )

    /** Public client: refresh needs no secret. Returns null when the refresh token is no longer valid. */
    @Throws(IOException::class)
    fun refresh(refreshToken: String, now: Long = System.currentTimeMillis()): Tokens? {
        val (code, j) = post(TOKEN_URL, mapOf(
            "client_id" to TV_CLIENT_ID, "grant_type" to "refresh_token", "refresh_token" to refreshToken))
        return when {
            code == 200 && j.has("access_token") -> parseTokens(j, now)
            code in 400..401 -> null
            else -> throw IOException("refresh HTTP $code")
        }
    }

    /** null = token invalid. Twitch requires validating stored tokens on start and hourly. */
    @Throws(IOException::class)
    fun validate(access: String): Validation? {
        val req = Request.Builder().url(VALIDATE_URL).header("Authorization", "OAuth $access").build()
        Net.http.newCall(req).execute().use { r ->
            if (r.code == 401) return null
            if (!r.isSuccessful) throw IOException("validate HTTP ${r.code}")
            val j = JSONObject(r.body?.string().orEmpty())
            return Validation(j.optString("login"), j.optString("user_id"), j.optString("client_id"), j.optLong("expires_in"))
        }
    }

    fun revoke(access: String) {
        runCatching { post(REVOKE_URL, mapOf("client_id" to TV_CLIENT_ID, "token" to access)) }
    }
}
