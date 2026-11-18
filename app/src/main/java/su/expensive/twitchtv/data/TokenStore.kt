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
            .apply()
    }

    @Synchronized
    fun load(): TwitchAuth.Tokens? = runCatching {
        val iv = Base64.decode(sp.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val ct = Base64.decode(sp.getString("ct", null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        val j = JSONObject(String(c.doFinal(ct)))
        TwitchAuth.Tokens(j.getString("a"), j.optString("r").ifEmpty { null }, j.getLong("e"))
    }.getOrElse { clear(); null } // key invalidated / data corrupt → signed out, never crash

    val login: String? get() = sp.getString("login", null)

    @Synchronized
    fun clear() { sp.edit().clear().apply() }

    private companion object { const val ALIAS = "twitchtv_oauth_v1" }
}
