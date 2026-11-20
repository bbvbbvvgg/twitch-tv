package su.expensive.twitchtv.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * OAuth tokens at rest: AES-256-GCM with a non-exportable key in the Android Keystore.
 * Only ciphertext reaches SharedPreferences; the app has allowBackup=false, so it never leaves the
 * device. Tokens are never logged (DiagLog also redacts them defensively) and never exported.
 */
class TokenStore(context: Context) {
    private val sp = context.getSharedPreferences("twitchtv_auth", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return gen.generateKey()
    }

    @Synchronized
    fun save(t: TwitchAuth.Tokens, login: String) {
        val plain = JSONObject().put("a", t.access).put("r", t.refresh ?: "").put("e", t.expiresAtMs).toString()
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = c.doFinal(plain.toByteArray())
        sp.edit()
            .putString("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            .putString("ct", Base64.encodeToString(ct, Base64.NO_WRAP))
            .putString("login", login)
            .commit() // synchronous: the app is often killed right after sign-in (Home, power off)
    }

    @Synchronized
    fun load(): TwitchAuth.Tokens? = runCatching {
        val iv = Base64.decode(sp.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val ct = Base64.decode(sp.getString("ct", null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        val j = JSONObject(String(c.doFinal(ct)))
        TwitchAuth.Tokens(j.getString("a"), j.optString("r").ifEmpty { null }, j.getLong("e"))
    }.getOrElse { e ->
        // Never wipe here: a Keystore hiccup at cold start (seen on TCL firmware) used to sign the
        // viewer out on every launch. Keep the ciphertext; the next launch tries again.
        note("не удалось прочитать токен: ${e.javaClass.simpleName}"); null
    }

    /** Last auth event, persisted so the diagnostics screen can say why a session ended. */
    fun note(msg: String) { sp.edit().putString("last_event", msg).putLong("last_event_at", System.currentTimeMillis()).commit() }
    val lastEvent: String? get() = sp.getString("last_event", null)

    val login: String? get() = sp.getString("login", null)

    @Synchronized
    fun clear() { sp.edit().remove("iv").remove("ct").remove("login").commit() }

    private companion object { const val ALIAS = "twitchtv_oauth_v1" }
}
