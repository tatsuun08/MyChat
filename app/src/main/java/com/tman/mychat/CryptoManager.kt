package com.tman.mychat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val AES_LOCAL_ALIAS = "myChatLocalAesKey"
    private const val KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private val SALT = "my_chat_app_salt_12345".toByteArray()

    private const val PREF_NAME = "ChatAppPrefs"
    private const val KEY_LOCAL_PRIV = "local_encrypted_private_key"
    private const val KEY_LOCAL_PUB = "local_public_key"

    // ==========================================
    // 🛡️ 1. KeyStoreの「安全なAES鍵」の管理（証明書は不要！）
    // ==========================================
    private fun getOrCreateLocalAESKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(AES_LOCAL_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGenerator.init(
                KeyGenParameterSpec.Builder(AES_LOCAL_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            keyGenerator.generateKey()
        }
        return keyStore.getKey(AES_LOCAL_ALIAS, null) as SecretKey
    }

    // ==========================================
    // 🔑 2. パスワードベースのKDF（サーバーバックアップ用）
    // ==========================================
    private fun deriveAESKeyFromPassword(password: String): SecretKey {
        val iterationCount = 10000
        val keyLength = 256
        val spec = PBEKeySpec(password.toCharArray(), SALT, iterationCount, keyLength)
        val factory = SecretKeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
        val primeKey = factory.generateSecret(spec).encoded
        return SecretKeySpec(primeKey, "AES")
    }

    fun encryptPrivateKeyWithPassword(privateKeyBytes: ByteArray, password: String): String {
        val aesKey = deriveAESKeyFromPassword(password)
        return AesCrypto.encrypt(Base64Utils.encode(privateKeyBytes), aesKey)
    }

    // サーバーから復元した秘密鍵を解凍し、ローカルに暗号化保存する
    fun decryptAndImportPrivateKey(context: Context, encryptedPrivateKeyBase64: String, password: String) {
        val aesKey = deriveAESKeyFromPassword(password)
        val decryptedBase64 = AesCrypto.decrypt(encryptedPrivateKeyBase64, aesKey)
        val privateKeyBytes = Base64Utils.decode(decryptedBase64)

        // 💡 【超高度な暗号復元】秘密鍵（PKCS8）の内部データから、ペアとなる公開鍵を数学的に逆算する
        try {
            val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
            val kf = KeyFactory.getInstance("RSA")
            val privateKey = kf.generatePrivate(keySpec)

            // RSA秘密鍵の構造体キャストから、公開エキスポネント等を抽出
            val rsaPrivKey = privateKey as? java.security.interfaces.RSAPrivateCrtKey
            if (rsaPrivKey != null) {
                val publicKeySpec = java.security.spec.RSAPublicKeySpec(rsaPrivKey.modulus, rsaPrivKey.publicExponent)
                val publicKey = kf.generatePublic(publicKeySpec)
                val publicKeyString = Base64Utils.encode(publicKey.encoded)

                // 復元した正しい公開鍵をローカルに保存
                val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                sharedPref.edit().putString(KEY_LOCAL_PUB, publicKeyString).apply()
            }
        } catch (e: Exception) {
            Log.e("Crypto", "秘密鍵からの公開鍵の逆算に失敗しました", e)
        }

        // 秘密鍵をローカル専用AESでパックして保存
        savePrivateKeyToLocal(context, privateKeyBytes)
    }

    // 新規登録時に、作った初期鍵を自分の端末にも即座に保存するための関数
    fun saveKeyPairToLocal(context: Context, publicKeyString: String, privateKeyBytes: ByteArray) {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().putString(KEY_LOCAL_PUB, publicKeyString).apply()
        savePrivateKeyToLocal(context, privateKeyBytes)
    }

    // 秘密鍵の生バイトをローカル専用AES鍵で暗号化してSharedPrefに保存
    private fun savePrivateKeyToLocal(context: Context, privateKeyBytes: ByteArray) {
        val localAesKey = getOrCreateLocalAESKey()
        val encryptedPrivKeyStr = AesCrypto.encrypt(Base64Utils.encode(privateKeyBytes), localAesKey)

        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().putString(KEY_LOCAL_PRIV, encryptedPrivKeyStr).apply()
    }

    // ==========================================
    // 📦 3. 復号時に呼び出すローカル秘密鍵の取得
    // ==========================================
    fun getLocalPrivateKey(context: Context): PrivateKey? {
        return try {
            val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val encryptedPrivKeyStr = sharedPref.getString(KEY_LOCAL_PRIV, null) ?: return null

            val localAesKey = getOrCreateLocalAESKey()
            val decryptedBase64 = AesCrypto.decrypt(encryptedPrivKeyStr, localAesKey)
            val privateKeyBytes = Base64Utils.decode(decryptedBase64)

            val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
            KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        } catch (e: Exception) {
            Log.e("Crypto", "ローカル秘密鍵の復元に失敗しました", e)
            null
        }
    }

    // ==========================================
    // 🔒 4. 既存のメッセージ暗号化・復号（連動用）
    // ==========================================
    fun encrypt(plainText: String, publicKeyString: String): EncryptResult {
        return try {
            val secretKey = generateAESKey()
            EncryptResult(
                encryptedText = AesCrypto.encrypt(plainText, secretKey),
                encryptedKey = encryptAESKeyWithRSA(secretKey, publicKeyString)
            )
        } catch (e: Exception) {
            Log.e("Crypto", "暗号化に失敗しました", e)
            EncryptResult("暗号化に失敗しました", "")
        }
    }

    fun decrypt(encryptedText: String, encryptedKey: SecretKey): String {
        return try {
            AesCrypto.decrypt(encryptedText, encryptedKey)
        } catch (e: Exception) {
            encryptedText
        }
    }

    fun generateAESKey(): SecretKey {
        return KeyGenerator.getInstance("AES").apply {
            init(256, SecureRandom())
        }.generateKey()
    }

    fun encryptAESKeyWithRSA(aesKey: SecretKey, publicKeyString: String): String {
        val publicBytes = Base64Utils.decode(publicKeyString)
        val keySpec = X509EncodedKeySpec(publicBytes)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)

        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val spec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, spec)

        return Base64Utils.encode(cipher.doFinal(aesKey.encoded))
    }

    fun decryptAESKeyWithRSA(context: Context, encryptedAesKeyBase64: String): SecretKey? {
        return try {
            val privateKey = getLocalPrivateKey(context) ?: return null
            val encryptedBytes = Base64Utils.decode(encryptedAesKeyBase64)

            val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
            val spec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
            cipher.init(Cipher.DECRYPT_MODE, privateKey, spec)

            SecretKeySpec(cipher.doFinal(encryptedBytes), "AES")
        } catch (e: Exception) {
            Log.e("Crypto", "復号エラー: ${e.message}")
            null
        }
    }

    // ==========================================
    // 📦 5. 鍵ペア生成・削除
    // ==========================================
    fun generateKeyForBackup(): Pair<String, ByteArray> {
        val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val keyPair = kpg.generateKeyPair()
        val publicKeyString = Base64Utils.encode(keyPair.public.encoded)
        return Pair(publicKeyString, keyPair.private.encoded)
    }

    fun generateRSAKeyPairIfNeeded(context: Context) {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!sharedPref.contains(KEY_LOCAL_PRIV)) {
            val (publicKeyString, privateKeyBytes) = generateKeyForBackup()

            sharedPref.edit().putString(KEY_LOCAL_PUB, publicKeyString).apply()
            savePrivateKeyToLocal(context, privateKeyBytes)
        }
    }

    fun deleteRsaKey(context: Context) {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            remove(KEY_LOCAL_PRIV)
            remove(KEY_LOCAL_PUB)
        }.apply()
    }

    fun getMyPublicKeyString(context: Context): String? {
        val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(KEY_LOCAL_PUB, null)
    }
}

object Base64Utils {
    fun encode(input: ByteArray): String = Base64.encodeToString(input, Base64.NO_WRAP)
    fun decode(base64String: String): ByteArray = Base64.decode(base64String, Base64.NO_WRAP)
}

data class EncryptResult(val encryptedText: String, val encryptedKey: String)

object AesCrypto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12

    /**
     * メッセージや秘密鍵を暗号化する
     */
    fun encrypt(plainText: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(ALGORITHM)

        // 💡【修正のポイント】
        // GCMParameterSpec（自分で作ったIV）を渡さずに、鍵だけで初期化します。
        // これにより、KeyStoreのハードウェアが安全なIVを自動生成してくれます。
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        // 💡システムが自動生成したIV（12バイト）を後ろから回収する
        val iv = cipher.iv ?: ByteArray(IV_SIZE)

        // 暗号化を実行
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // [IV (12byte)] + [暗号文] の形に結合する
        val combined = iv + encryptedBytes

        // Base64にして返す
        return Base64Utils.encode(combined)
    }

    /**
     * メッセージや秘密鍵を復号化する（※復号はIVを指定する必要があるのでこのままでOK）
     */
    fun decrypt(encryptedBase64: String, secretKey: SecretKey): String {
        val combined = Base64Utils.decode(encryptedBase64)

        val iv = combined.sliceArray(0 until IV_SIZE)
        val encryptedBytes = combined.sliceArray(IV_SIZE until combined.size)

        val cipher = Cipher.getInstance(ALGORITHM)

        // 復号の時は、暗号化されたデータにくっついていたIVを正確に教える必要があります（128bitタグ指定）
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decodedBytes = cipher.doFinal(encryptedBytes)
        return String(decodedBytes, Charsets.UTF_8)
    }
}