package dev.homesentinel.data.blink

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SessionVault {
    suspend fun read(): String?

    suspend fun write(value: String)

    suspend fun clear()
}

/**
 * AES-256-GCM with a non-exportable Keystore key. Tokens stay in noBackupFilesDir. Authentication
 * tags detect corruption; read errors require a new login, never plaintext fallback.
 */
class KeystoreSessionVault(context: Context) : SessionVault {
    private val file = AtomicFile(File(context.noBackupFilesDir, "blink-session.enc"))
    private val alias = "home-sentinel.session.v1"
    private val aad = "HomeSentinel/session/v1".toByteArray()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    override suspend fun read(): String? =
        withContext(Dispatchers.IO) {
            if (!file.baseFile.exists()) return@withContext null
            val bytes = file.openRead().use { it.readBytes() }
            require(bytes.size >= 30 && bytes[0] == 1.toByte()) { "Encrypted session unreadable" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            cipher.updateAAD(aad)
            cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8)
        }

    override suspend fun write(value: String) =
        withContext(Dispatchers.IO) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(aad)
            check(cipher.iv.size == 12)
            val bytes =
                byteArrayOf(1) + cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (e: Exception) {
                file.failWrite(stream)
                throw e
            }
        }

    override suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }
}
