package app.yukine.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的轻量字段级加密工具，用 AES/GCM/NoPadding 加密敏感字符串
 * （流媒体 cookie、WebDAV 密码等）。密钥由系统 Keystore 托管，不离开设备、不进入应用备份。
 *
 * 设计要点：
 * - 所有方法在失败时返回 null 而非抛异常。调用方按“无数据”处理即可（最坏情况是用户需要重新
 *   登录），从而避免 keystore 在系统升级 / 数据恢复后失效时直接让应用崩溃。
 * - 存储格式为 Base64(iv ‖ ciphertext)，IV 固定 12 字节，GCM 认证标签 128 位。
 * - [encryptOrPlain] / [decryptOrPlain] 提供向后兼容：读取旧的明文数据不会失败，
 *   方便从“明文存储”平滑迁移到“加密存储”。
 *
 * 注意：依赖 Android Keystore，纯 JVM 单元测试环境下 [encrypt] 会返回 null，
 * 此时 [encryptOrPlain] 退化为明文——调用方的降级路径需保证这一行为不破坏现有逻辑。
 */
object SecureSecretStore {

    private const val KEY_ALIAS = "echo_secure_secret_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    /** 加密成功返回 Base64 密文，任何失败返回 null。 */
    fun encrypt(plain: String?): String? {
        if (plain == null) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (t: Throwable) {
            null
        }
    }
    /** 解密成功返回明文，密文损坏 / keystore 失效等任何失败返回 null。 */
    fun decrypt(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH) return null
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * 加密 [plain]；若加密不可用（如无 Keystore 的测试环境）则原样返回明文，保证写入不丢数据。
     * 配套 [decryptOrPlain] 使用。
     */
    fun encryptOrPlain(plain: String?): String? = encrypt(plain) ?: plain

    /**
     * 解密 [stored]；若它不是本工具产生的密文（如历史遗留的明文），解密失败时原样返回，
     * 实现明文 → 密文的平滑迁移。
     */
    fun decryptOrPlain(stored: String?): String? {
        if (stored.isNullOrBlank()) return stored
        return decrypt(stored) ?: stored
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
