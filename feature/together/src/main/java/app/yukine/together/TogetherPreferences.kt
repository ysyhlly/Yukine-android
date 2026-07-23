package app.yukine.together

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class TogetherSavedSettings(
    val nickname: String = "",
    val relays: List<String> = TogetherConnectOptions.DEFAULT_RELAYS,
    val turnUrl: String = "",
    val turnUsername: String = "",
    val turnPassword: String = "",
    val rememberTurnPassword: Boolean = false
) {
    fun connectOptions(cacheDirectory: String = "") = TogetherConnectOptions(
        nickname = nickname,
        relays = relays,
        turnUrl = turnUrl,
        turnUsername = turnUsername,
        turnPassword = turnPassword,
        cacheDirectory = cacheDirectory
    )
}

class TogetherPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "together_connection_settings",
        Context.MODE_PRIVATE
    )

    fun load(): TogetherSavedSettings {
        val remember = prefs.getBoolean(KEY_REMEMBER, false)
        return TogetherSavedSettings(
            nickname = prefs.getString(KEY_NICK, "").orEmpty(),
            relays = prefs.getString(KEY_RELAYS, null)
                ?.lineSequence()
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?.toList()
                ?.takeIf(List<String>::isNotEmpty)
                ?: TogetherConnectOptions.DEFAULT_RELAYS,
            turnUrl = prefs.getString(KEY_TURN_URL, "").orEmpty(),
            turnUsername = prefs.getString(KEY_TURN_USER, "").orEmpty(),
            turnPassword = if (remember) decrypt(prefs.getString(KEY_TURN_PASS, "").orEmpty()) else "",
            rememberTurnPassword = remember
        )
    }

    fun save(settings: TogetherSavedSettings) {
        prefs.edit()
            .putString(KEY_NICK, settings.nickname.trim())
            .putString(KEY_RELAYS, settings.relays.joinToString("\n"))
            .putString(KEY_TURN_URL, settings.turnUrl.trim())
            .putString(KEY_TURN_USER, settings.turnUsername)
            .putBoolean(KEY_REMEMBER, settings.rememberTurnPassword)
            .putString(
                KEY_TURN_PASS,
                if (settings.rememberTurnPassword) encrypt(settings.turnPassword) else ""
            )
            .apply()
    }

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, payload.copyOfRange(0, IV_BYTES))
            )
            cipher.doFinal(payload.copyOfRange(IV_BYTES, payload.size)).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "echo_together_turn_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val KEY_NICK = "nickname"
        const val KEY_RELAYS = "relays"
        const val KEY_TURN_URL = "turn_url"
        const val KEY_TURN_USER = "turn_user"
        const val KEY_TURN_PASS = "turn_password"
        const val KEY_REMEMBER = "remember_turn_password"
    }
}
