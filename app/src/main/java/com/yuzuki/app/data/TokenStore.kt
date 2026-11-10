package com.yuzuki.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.tokenStore: DataStore<Preferences> by preferencesDataStore(name = "secure_tokens")

/**
 * token 用 Android Keystore 中的 AES-GCM 密钥加密后写入 DataStore（方案 §4）。
 * 明文 token 不落盘、不进日志；密文格式 base64(iv).base64(ciphertext)。
 */
class TokenStore(context: Context) {
    private val appContext = context.applicationContext
    private val store: DataStore<Preferences> get() = appContext.tokenStore

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "yuzuki_token_aes"
        private const val GCM_TAG_BITS = 128
        private const val SEP = "."
        private val KEY_TOKEN = stringPreferencesKey("token_enc")
        private val KEY_USERNAME = stringPreferencesKey("username")
    }

    val username: Flow<String?> = store.data.map { it[KEY_USERNAME] }

    suspend fun save(token: String, username: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(token.isNotBlank()) { "token 不能为空" }
            val encrypted = encrypt(token)
            store.edit {
                it[KEY_TOKEN] = encrypted
                it[KEY_USERNAME] = username
            }
            Unit
        }
    }

    suspend fun getToken(): String? = withContext(Dispatchers.IO) {
        val raw = store.data.first()[KEY_TOKEN] ?: return@withContext null
        runCatching { decrypt(raw) }.getOrElse {
            // Keystore 失效（例如用户重置锁屏）时清掉不可用密文，回到登录流程
            store.edit {
                it.remove(KEY_TOKEN)
                it.remove(KEY_USERNAME)
            }
            null
        }
    }

    suspend fun getUsername(): String? = store.data.first()[KEY_USERNAME]

    suspend fun clear() {
        store.edit {
            it.remove(KEY_TOKEN)
            it.remove(KEY_USERNAME)
        }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(iv) + SEP + b64(ciphertext)
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(SEP)
        require(parts.size == 2) { "密文格式错误" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        val plain = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
        return String(plain, Charsets.UTF_8)
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
