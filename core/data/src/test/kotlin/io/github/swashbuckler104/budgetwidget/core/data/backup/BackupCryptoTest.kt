package io.github.swashbuckler104.budgetwidget.core.data.backup

import com.google.common.truth.Truth.assertThat
import java.util.Base64
import org.junit.Test

class BackupCryptoTest {
    private val plain = """{"format":"budget-widget-backup","note":"₹1,234 at DMart"}""".toByteArray()
    private val pass = "correct horse".toCharArray()

    /** Lowest count the reader accepts: keeps the tests fast without a test-only code path. */
    private fun seal(bytes: ByteArray = plain) = BackupCrypto.encrypt(bytes, pass, iterations = 100_000)

    private fun open(file: EncryptedBackup, passphrase: String = "correct horse") =
        BackupCrypto.decrypt(file, passphrase.toCharArray())

    @Test
    fun `round trip gives back the exact bytes, and nothing readable is left in the file`() {
        val file = seal()
        assertThat((open(file) as Decrypted.Ok).plain).isEqualTo(plain)
        assertThat(file.ciphertext).doesNotContain("DMart")
        assertThat(Base64.getDecoder().decode(file.ciphertext).decodeToString()).doesNotContain("DMart")
        assertThat(Base64.getDecoder().decode(file.salt)).hasLength(16)
        assertThat(Base64.getDecoder().decode(file.nonce)).hasLength(12)
    }

    @Test
    fun `two backups of the same data never share salt, nonce or ciphertext`() {
        val a = seal()
        val b = seal()
        assertThat(a.salt).isNotEqualTo(b.salt)
        assertThat(a.nonce).isNotEqualTo(b.nonce)
        assertThat(a.ciphertext).isNotEqualTo(b.ciphertext)
    }

    @Test
    fun `a wrong password, a changed byte or a cut-short file are refused`() {
        val file = seal()
        assertThat(open(file, "correct horsE")).isEqualTo(Decrypted.WrongPassphrase)
        assertThat(open(file, "")).isEqualTo(Decrypted.WrongPassphrase)

        val sealed = Base64.getDecoder().decode(file.ciphertext)
        val flipped = sealed.copyOf().also { it[3] = (it[3].toInt() xor 1).toByte() }
        val cut = sealed.copyOf(sealed.size - 5)
        listOf(flipped, cut).forEach { bytes ->
            assertThat(open(file.copy(ciphertext = Base64.getEncoder().encodeToString(bytes))))
                .isEqualTo(Decrypted.WrongPassphrase)
        }
    }

    @Test
    fun `header fields are authenticated, so they can't be swapped for weaker ones`() {
        val file = seal()
        // Same key derivation inputs would be needed to decrypt; any header change breaks the tag.
        assertThat(open(file.copy(iterations = 100_001))).isEqualTo(Decrypted.WrongPassphrase)
        val otherNonce = Base64.getEncoder().encodeToString(ByteArray(12))
        assertThat(open(file.copy(nonce = otherNonce))).isEqualTo(Decrypted.WrongPassphrase)
    }

    @Test
    fun `unknown algorithms, newer versions and absurd iteration counts are not attempted`() {
        val file = seal()
        assertThat(open(file.copy(kdf = "PBKDF2WithHmacSHA1"))).isEqualTo(Decrypted.Unsupported)
        assertThat(open(file.copy(cipher = "AES/CBC/PKCS5Padding"))).isEqualTo(Decrypted.Unsupported)
        assertThat(open(file.copy(version = 2))).isEqualTo(Decrypted.Unsupported)
        assertThat(open(file.copy(iterations = 1))).isEqualTo(Decrypted.Unsupported)
        assertThat(open(file.copy(iterations = Int.MAX_VALUE))).isEqualTo(Decrypted.Unsupported)
        assertThat(open(file.copy(salt = "not base64!"))).isEqualTo(Decrypted.Unsupported)
        assertThat(open(file.copy(salt = Base64.getEncoder().encodeToString(ByteArray(4)))))
            .isEqualTo(Decrypted.Unsupported)
    }
}
