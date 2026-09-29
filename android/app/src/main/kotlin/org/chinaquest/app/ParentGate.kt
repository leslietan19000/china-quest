package org.chinaquest.app

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

class ParentGate(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences("parent_gate", Context.MODE_PRIVATE)
    val isConfigured get() = prefs.contains("hash")
    fun remainingLockSeconds() = ((prefs.getLong("locked_until", 0) - now() + 999) / 1000).coerceAtLeast(0)
    fun setPin(pin: String) {
        require(pin.matches(Regex("[0-9]{6,12}"))) { "请设置 6–12 位数字 PIN" }
        check(!isConfigured) { "PIN already configured" }
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("salt", encode(salt)).putString("hash", encode(derive(pin, salt)))
            .putInt("attempts", 0).putLong("locked_until", 0).commit()
    }
    fun verify(pin: String): Boolean {
        if (!isConfigured || remainingLockSeconds() > 0) return false
        val expected = decode(prefs.getString("hash", "")!!)
        val salt = decode(prefs.getString("salt", "")!!)
        val valid = MessageDigest.isEqual(expected, derive(pin, salt))
        val attempts = if (valid) 0 else prefs.getInt("attempts", 0) + 1
        prefs.edit().putInt("attempts", attempts)
            .putLong("locked_until", if (!valid && attempts >= 5) now() + 60_000 else 0).commit()
        return valid
    }
    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 160_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun encode(data: ByteArray) = Base64.encodeToString(data, Base64.NO_WRAP)
    private fun decode(data: String) = Base64.decode(data, Base64.NO_WRAP)
}
