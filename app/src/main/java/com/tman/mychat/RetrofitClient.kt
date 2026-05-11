package com.tman.mychat // ご自身のパッケージ名に合わせてください

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private val BASE_URL = BuildConfig.BASE_URL
    // lazyブロックを使うことで、最初にアクセスされた時に1回だけ初期化
    val api: ChatApi by lazy {

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // JSONを自動変換する設定
            .build()
            .create(ChatApi::class.java)
    }
}