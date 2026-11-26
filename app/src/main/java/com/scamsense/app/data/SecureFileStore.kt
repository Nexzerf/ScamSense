package com.scamsense.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Minimal blob store abstraction so repositories can be unit-tested with an in-memory fake. */
interface BlobStore {
    fun read(): String?
    fun write(value: String)
    fun delete()
}

/**
 * Stores one UTF-8 string per file, encrypted with AES-256-GCM using a key that lives in
 * the Android Keystore (hardware-backed where available, never exportable).
 *
 * File format: [version:1][ivLen:1][iv][ciphertext+tag]
 *
 * If decryption ever fails (key invalidated after a factory reset of the lock screen,
 * corrupted file …) the file is discarded instead of crashing the app — the data is
 * only a convenience history, never the source of truth.
 */
class SecureFileStore(context: Context, fileName: String) : BlobStore {

    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private val lock = Any()

    override fun read(): String? = synchronized(lock) {
        val bytes = try {
            file.readFully()
        } catch (_: Exception) {
            return null
        }
        return try {
            decrypt(bytes)
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    override fun write(value: String) = synchronized(lock) {
        val encrypted = encrypt(value)
        var out: FileOutputStream? = null
        try {
            out = file.startWrite()
            out.write(encrypted)
            file.finishWrite(out)
        } catch (e: Exception) {
            if (out != null) file.failWrite(out)
            throw e
        }
    }

    override fun delete() = synchronized(lock) { file.delete() }

    private fun encrypt(plain: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return ByteBuffer.allocate(2 + iv.size + ct.size)
            .put(FORMAT_VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ct)
            .array()
    }

    private fun decrypt(data: ByteArray): String {
        require(data.size > 2 && data[0] == FORMAT_VERSION) { "unknown format" }
        val ivLen = data[1].toInt()
        require(ivLen in 12..16 && data.size > 2 + ivLen) { "bad iv" }
        val iv = data.copyOfRange(2, 2 + ivLen)
        val ct = data.copyOfRange(2 + ivLen, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "scamsense_store_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
    }
}

/** Used in tests and as a fallback if the Keystore is unavailable on a broken device. */
class InMemoryBlobStore : BlobStore {
    @Volatile private var value: String? = null
    override fun read(): String? = value
    override fun write(value: String) { this.value = value }
    override fun delete() { value = null }
}
