package com.tman.mychat

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    //使い捨てのAES
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    // 秘密の鍵（16文字）いったんハードコード
    private val SECRET_KEY = SecretKeySpec("MySuperSecretKey".toByteArray(), "AES")
    private val IV = IvParameterSpec("MySuperSecretIV1".toByteArray())

    //【TODO】送信相手の公開鍵の取得
    //AES鍵でテキストメッセージを暗号化
    //AES鍵を送信相手の公開鍵で暗号化　O(N)
    //暗号分と暗号化された鍵を送信
    //復号　AES鍵を復号
    //復号された鍵で暗号文を復元

    // メッセージを暗号化する
    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, IV)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray())
            // サーバーに送りやすいように、Base64という文字の形式に変換します
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT).trim()
        } catch (e: Exception) {
            plainText //失敗したらそのまま返す
        }
    }

    // 暗号を元のメッセージに戻す
    fun decrypt(encryptedText: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, IV)
            val decodedBytes = Base64.decode(encryptedText, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes)
        } catch (e: Exception) {
            encryptedText
        }
    }
}