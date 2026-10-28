package ir.tarazino.ai

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed interface VerifyResult {
    data object Ok : VerifyResult
    data object Wrong : VerifyResult
    data class Locked(val seconds: Int) : VerifyResult
}

/**
 * ورود محلی: رمز هرگز ذخیره نمی‌شود؛ فقط PBKDF2-SHA256 با salt تصادفی و ۱۲۰٬۰۰۰ تکرار، رمزنگاری‌شده با Android Keystore.
 * پس از چند تلاش ناموفق، قفل زمانی اعمال می‌شود.
 */
class AuthStore(context: Context, private val box: SecretBox = SecretBox("tarazino_auth_v1")) {
    private val prefs = context.getSharedPreferences("tarazino_auth", Context.MODE_PRIVATE)

    fun hasAccount() = prefs.contains("cred")
    var biometricEnabled: Boolean
        get() = prefs.getBoolean("bio", false)
        set(v) { prefs.edit().putBoolean("bio", v).apply() }

    fun identifier(): String = prefs.getString("id", null)?.let { box.decrypt(it) } ?: ""

    fun register(identifier: String, password: String) {
        require(identifier.isNotBlank()) { "شناسه الزامی است" }
        require(password.length >= 6) { "رمز حداقل ۶ نویسه" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val blob = b64(salt) + ":" + b64(hash(password, salt))
        prefs.edit().putString("cred", box.encrypt(blob)).putString("id", box.encrypt(identifier.trim()))
            .putInt("fails", 0).putLong("lock", 0).putBoolean("bio", false).apply()
    }

    fun verify(password: String, now: Long = System.currentTimeMillis()): VerifyResult {
        val lock = prefs.getLong("lock", 0)
        if (now < lock) return VerifyResult.Locked(((lock - now) / 1000).toInt() + 1)
        val blob = prefs.getString("cred", null)?.let { box.decrypt(it) } ?: return VerifyResult.Wrong
        val (s, h) = blob.split(":").let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
        return if (MessageDigest.isEqual(hash(password, s), h)) {
            prefs.edit().putInt("fails", 0).putLong("lock", 0).apply(); VerifyResult.Ok
        } else {
            val fails = prefs.getInt("fails", 0) + 1
            val e = prefs.edit().putInt("fails", fails)
            if (fails >= 5) e.putLong("lock", now + minOf(30L * (fails - 4), 600L) * 1000)
            e.apply(); VerifyResult.Wrong
        }
    }

    fun clear() { prefs.edit().clear().apply() }

    private fun hash(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
}
