package com.tman.mychat

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


object CryptoManager {
    //【TODO】送信相手の公開鍵の取得
    //AES鍵でテキストメッセージを暗号化
    //AES鍵を送信相手の公開鍵で暗号化　O(N)
    //暗号分と暗号化された鍵を送信
    //復号　AES鍵を復号
    //復号された鍵で暗号文を復元

    // メッセージを暗号化する
    fun encrypt(plainText: String, publicKeyString: String): EncryptResult {
        return try {
            val secretKey = generateAESKey()
            EncryptResult(
                encryptedText = AesCrypto.encrypt(plainText, secretKey), // Base64(String)前提
                encryptedKey = encryptAESKeyWithRSA(secretKey, publicKeyString)
            )
        } catch (e: Exception) {
            Log.e("Crypto", "暗号化に失敗しました: ${e.message}", e)
            EncryptResult(
                encryptedText = "暗号化に失敗しました", // 本文の代わりにエラーメッセージを入れる
                encryptedKey = "" // 鍵はないので空文字にする
            )
        }
    }

    //メッセージの複合
    fun decrypt(encryptedText: String, encryptedKey: SecretKey): String {
        return try {
            val decryptedKey = decryptAESKeyWithRSA(encryptedKey.encoded.toString())
            AesCrypto.decrypt(encryptedText, decryptedKey)
        } catch (e: Exception) {
            encryptedText
        }
    }

    //ランダムなAES鍵を生成
    fun generateAESKey() : SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")

        keyGen.init(256, SecureRandom())
        return keyGen.generateKey()
    }


    //AES鍵をRSAによる暗号化
    fun encryptAESKeyWithRSA(aesKey: SecretKey, publicKeyString: String): String {
        // 1. String形式の公開鍵をオブジェクトに変換
        val publicBytes = Base64Utils.decode(publicKeyString)
        val keySpec = X509EncodedKeySpec(publicBytes)
        val kf = KeyFactory.getInstance("RSA")
        val publicKey = kf.generatePublic(keySpec)

        // 2. RSA Cipherの準備 (OAEPパディングが現代の標準)
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)

        // 3. AES鍵の本体（バイト列）を暗号化
        val encryptedAesKeyBytes = cipher.doFinal(aesKey.encoded)

        // 4. Base64にして返す
        return Base64Utils.encode(encryptedAesKeyBytes)
    }

    //RSAによる暗号化されたAES鍵を復号化
    fun decryptAESKeyWithRSA(encryptedAesKeyBase64: String): SecretKey {
        // 1. Android Keystoreから秘密鍵を取得
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = keyStore.getKey("MyChatKey", null) as java.security.PrivateKey

        // 2. 受信したデータをデコード
        val encryptedBytes = Base64Utils.decode(encryptedAesKeyBase64)

        // 3. RSA Cipherの準備 (送信時と同じアルゴリズムを指定)
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)

        // 4. AES鍵を復号
        val decryptedAesKeyBytes = cipher.doFinal(encryptedBytes)

        // 5. バイト列からAES鍵オブジェクトを再構築
        return SecretKeySpec(decryptedAesKeyBytes, "AES")
    }

    // 💡 アプリ起動時やログイン時に呼び出して、鍵を作る関数
    fun generateRSAKeyPairIfNeeded() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        // すでに "myChatKey" という名前の鍵が金庫にあるかチェック（あれば何もしない）
        if (!keyStore.containsAlias("myChatKey")) {

            // RSA鍵を作るための工場（キージェネレーター）を準備
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore" // 保存先をAndroidの金庫に指定
            )

            // 鍵の仕様書（オーダーメイドの設定）を作成
            val parameterSpec = KeyGenParameterSpec.Builder(
                "myChatKey", // 金庫から出し入れする時の名前（エイリアス）
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT // 暗号化と復号化に使う
            )
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP) // OAEPパディングを使用
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setKeySize(2048) // セキュリティ的に十分な2048ビットを指定
                .build()

            // 工場に仕様書を渡して、鍵を生成（自動的に金庫に保存されます！）
            keyPairGenerator.initialize(parameterSpec)
            keyPairGenerator.generateKeyPair()
        }
    }

    //鍵の削除
    fun deleteRsaKey(alias: String) {
        // 1. AndroidKeyStoreのインスタンスを取得
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

        // 2. 指定したエイリアス（名前）の鍵が存在するか確認して削除
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
    fun getMyPublicKeyString() : String? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getCertificate("myChatKey")
        val publicKey = keyStore.getCertificate("myChatKey").publicKey
        return Base64Utils.encode(publicKey.encoded)
    }
}

//Base64エンコーダーとデコーダー
object Base64Utils {
    fun encode(input: ByteArray): String {
        return Base64.encodeToString(input, Base64.NO_WRAP)//改行を入れると予期せぬエラーになる
    }
    fun decode(base64String : String ): ByteArray {
        return Base64.decode(base64String, Base64.NO_WRAP)
    }
}

data class EncryptResult (
    val encryptedText : String,
    val encryptedKey : String
)



object AesCrypto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128 // GCMの認証タグの長さ
    private const val IV_SIZE = 12     // GCM推奨のIVサイズ

    /**
     * メッセージを暗号化する
     */
    fun encrypt(plainText: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(ALGORITHM)

        // 1. 毎回ランダムなIV（使い捨ての数値）を生成
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)

        // 2. 暗号化モードで初期化
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        // 3. 暗号化実行
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // 4. [IV (12byte)] + [暗号文] の形に結合する
        val combined = iv + encryptedBytes

        // 5. Base64にして返す
        return Base64Utils.encode(combined)
    }

    /**
     * メッセージを復号化する
     */
    fun decrypt(encryptedBase64: String, secretKey: SecretKey): String {
        // 1. Base64をバイト配列に戻す
        val combined = Base64Utils.decode(encryptedBase64)

        // 2. 先頭12バイト（IV）と、それ以降（暗号文）に切り分ける
        val iv = combined.sliceArray(0 until IV_SIZE)
        val encryptedBytes = combined.sliceArray(IV_SIZE until combined.size)

        val cipher = Cipher.getInstance(ALGORITHM)

        // 3. 復号モードで初期化（取り出したIVを使う）
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        // 4. 復号実行して文字列に戻す
        val decodedBytes = cipher.doFinal(encryptedBytes)
        return String(decodedBytes, Charsets.UTF_8)
    }
}
