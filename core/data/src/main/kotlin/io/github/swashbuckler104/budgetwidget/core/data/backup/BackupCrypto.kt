package io.github.swashbuckler104.budgetwidget.core.data.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable

/**
 * A password-protected backup (ADR-0044, audit F2): the plain backup JSON encrypted with
 * AES-256-GCM under a key from PBKDF2-HMAC-SHA256. `javax.crypto` only, no new library.
 * Every header field is authenticated (GCM associated data), so changing one fails like a
 * wrong password instead of decrypting with weaker settings.
 */
@Serializable
internal data class EncryptedBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val kdf: String = KDF,
    val iterations: Int,
    /** Base64. */
    val salt: String,
    val cipher: String = CIPHER,
    /** Base64. */
    val nonce: String,
    /** Base64: ciphertext followed by the 16-byte GCM tag. */
    val ciphertext: String,
) {
    fun associatedData(): ByteArray = listOf(format, version, kdf, iterations, salt, cipher, nonce)
        .joinToString("|")
        .toByteArray(Charsets.UTF_8)

    companion object {
        const val FORMAT = "budget-widget-backup-encrypted"
        const val VERSION = 1
        const val KDF = "PBKDF2WithHmacSHA256"
        const val CIPHER = "AES/GCM/NoPadding"
    }
}

/** What [BackupCrypto.decrypt] found. */
internal sealed interface Decrypted {
    class Ok(val plain: ByteArray) : Decrypted

    data object WrongPassphrase : Decrypted

    /** A header this version can't use (unknown algorithm, newer version, silly iteration count). */
    data object Unsupported : Decrypted
}

internal object BackupCrypto {
    /** OWASP 2023 guidance for PBKDF2-HMAC-SHA256. About a second on a 2023 phone. */
    const val ITERATIONS = 600_000

    /** Bounds for a file's own count: a crafted file must not stall the app for minutes. */
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 10_000_000

    const val MIN_PASSPHRASE_LENGTH = 8
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    fun encrypt(
        plain: ByteArray,
        passphrase: CharArray,
        iterations: Int = ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): EncryptedBackup {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = EncryptedBackup(
            iterations = iterations,
            salt = b64(salt),
            nonce = b64(nonce),
            ciphertext = "",
        )
        val cipher = Cipher.getInstance(EncryptedBackup.CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header.associatedData())
        return header.copy(ciphertext = b64(cipher.doFinal(plain)))
    }

    fun decrypt(file: EncryptedBackup, passphrase: CharArray): Decrypted {
        if (file.version > EncryptedBackup.VERSION ||
            file.kdf != EncryptedBackup.KDF ||
            file.cipher != EncryptedBackup.CIPHER ||
            file.iterations !in MIN_ITERATIONS..MAX_ITERATIONS
        ) {
            return Decrypted.Unsupported
        }
        val decoder = Base64.getDecoder()
        val (salt, nonce, sealed) = try {
            Triple(decoder.decode(file.salt), decoder.decode(file.nonce), decoder.decode(file.ciphertext))
        } catch (_: IllegalArgumentException) {
            return Decrypted.Unsupported
        }
        if (salt.size != SALT_BYTES || nonce.size != NONCE_BYTES) return Decrypted.Unsupported
        return try {
            val cipher = Cipher.getInstance(EncryptedBackup.CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt, file.iterations), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(file.associatedData())
            Decrypted.Ok(cipher.doFinal(sealed))
        } catch (_: AEADBadTagException) {
            // Wrong password, or the file was changed or cut short: GCM can't tell them apart.
            Decrypted.WrongPassphrase
        } catch (_: GeneralSecurityException) {
            Decrypted.Unsupported
        }
    }

    private fun key(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance(EncryptedBackup.KDF).generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
