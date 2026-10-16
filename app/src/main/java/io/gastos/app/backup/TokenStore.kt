package io.gastos.app.backup

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the token at rest. Injected so unit tests can run without the Android Keystore. */
interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/** AES/GCM with a non-exportable key in the Android Keystore. Output is IV followed by ciphertext. */
class KeystoreTokenCipher(private val alias: String = "gastos_github_token") : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size <= IV_BYTES) throw GeneralSecurityException("Ciphertext too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * Holds the GitHub personal access token, encrypted, in private SharedPreferences.
 * The Keystore is only touched on save/load, never at construction. Never log the token.
 */
class TokenStore(private val prefs: SharedPreferences, private val cipher: TokenCipher) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("gastos_secure", Context.MODE_PRIVATE),
        KeystoreTokenCipher(),
    )

    fun save(token: String) {
        val blob = cipher.encrypt(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    /**
     * The saved token, or null if none is saved or it can no longer be decrypted (key lost, Keystore
     * ProviderException, I/O error, corrupt Base64...). Never throws, so callers such as Settings can't crash.
     */
    fun load(): String? {
        val stored = prefs.getString(KEY, null) ?: return null
        return try {
            String(cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "github_token"
    }
}
