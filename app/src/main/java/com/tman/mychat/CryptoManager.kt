package com.tman.mychat

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    //AES暗号を採用
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    // 秘密の鍵（16文字）いったんハードコード
    private val SECRET_KEY = SecretKeySpec("MySuperSecretKey".toByteArray(), "AES")
    private val IV = IvParameterSpec("MySuperSecretIV1".toByteArray())

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