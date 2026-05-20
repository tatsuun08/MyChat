package com.tman.mychat // ご自身のパッケージ名に合わせてください

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private val BASE_URL = BuildConfig.BASE_URL

    // SharedPreferencesを読み込むためにContextが必要なので、アプリ起動時に1回だけセットしてもらいます
    lateinit var appContext: Context

    // lazyブロックを使うことで、最初にアクセスされた時に1回だけ初期化（元の構造をキープ！）
    val api: ChatApi by lazy {

        // 1. 保存してあるJWTを取り出すための準備
        val sharedPref = appContext.getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE)

        // 2. 通信の直前にJWTをヘッダーに自動で挟み込むインターセプターを作成
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val token = sharedPref.getString("jwt_token", null)

            // トークンがあれば Authorization ヘッダーを追加、なければそのまま
            val newRequest = if (!token.isNullOrEmpty()) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }
            chain.proceed(newRequest)
        }

        // 3. インターセプターを組み込んだ OkHttpClient を作成
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        // 4. Retrofitのビルド（okHttpClientをセット！）
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()) // JSONを自動変換する設定
            .build()
            .create(ChatApi::class.java)
    }
}