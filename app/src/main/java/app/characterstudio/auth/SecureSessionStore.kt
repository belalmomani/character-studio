package app.characterstudio.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** يحفظ الجلسة مشفّرة (AES-GCM بمفتاح في Android Keystore). لا تُكتب الرموز نصًّا صريحًا ولا تدخل السجلات. */
class SecureSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("auth_secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun save(session: Session) {
        val plain = JSONObject().put("a", session.accessToken).put("r", session.refreshToken)
            .put("e", session.expiresAtSec).put("u", session.userId).put("m", session.email).toString()
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(PREF, Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)).apply()
    }

    override fun load(): Session? = runCatching {
        val raw = prefs.getString(PREF, null) ?: return null
        val (iv, ct) = raw.split(":").let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        val o = JSONObject(String(c.doFinal(ct), Charsets.UTF_8))
        Session(o.getString("a"), o.getString("r"), o.getLong("e"), o.getString("u"), o.getString("m"))
    }.getOrElse { clear(); null } // تالف أو مفتاح مفقود: نبدأ بلا جلسة بدل التعطل

    override fun clear() { prefs.edit().remove(PREF).apply() }

    private companion object {
        const val ALIAS = "character_studio_session_key"
        const val PREF = "session"
    }
}
