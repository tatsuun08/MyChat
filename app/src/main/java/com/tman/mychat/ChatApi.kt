package com.tman.mychat
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// 1. サーバーから受け取るデータの形（GoのJSONに合わせる）
data class RoomResponse(
    val id: Int,
    val name: String
)

// 2. サーバーに送るデータの形（POSTで新しい部屋を作る用）
data class RoomRequest(
    val name: String
)

data class UserResponse(
    val id: Int,
    val name: String,
    @SerializedName("public_key") val publicKey : String?
)
data class UserRequest(
    val name: String,
    val publicKey : String
)


data class RoomUserRequest(
    @SerializedName("room_id") val roomID: Int,
    @SerializedName("user_id") val userID: Int
)

data class RoomUserResponse(
    @SerializedName("room_id") val roomID: Int,
    @SerializedName("user_id") val userID: Int
)


data class MessageRequest(
    val id: Int,
    val text: String,
    @SerializedName("sender_id") val senderID: Int,
    @SerializedName("room_id") val roomID: Int,

)
data class MessageResponse(
    val id: Int,
    val text: String,
    @SerializedName("sender_id") val senderID: Int,
)



// 3. 通信のルールブック（インターフェース）
interface ChatApi {
    // ★ログイン（ユーザー登録）用のAPI
    @POST("users")
    suspend fun loginUser(@Body request: UserRequest): UserResponse

    @GET("room_users/list")
    suspend fun getUsersByRoom(@Query("room_id") roomId: Int): List<UserResponse>

    @POST("users/public_key")
    suspend fun updatePublicKey(@Query("user_id") userId : Int, @Body publicKey: okhttp3.RequestBody) //【TODO】認証がないと実行できない仕組み

    // GETリクエストで /rooms にアクセスし、部屋のリストを受け取る
    @GET("rooms")
    suspend fun getRooms(@Query("user_id") userId: Int): List<RoomResponse>

    // POSTリクエストで /rooms にアクセスし、新しい部屋を作成して結果を受け取る
    @POST("rooms")
    suspend fun createRoom(@Body request: RoomRequest): RoomResponse

    //RoomEntityを作成して登録
    @POST("room_user")
    suspend fun createRoomUser(@Body request: RoomUserRequest): RoomUserResponse

    //RoomIDに一致するメッセージを取得
    @GET("messages")
    suspend fun getMessages(@Query("room_id") roomId: Int): List<MessageResponse>//Query("room_id") http://localhost:PORT/messages?room_id={$roomId}

    @POST("messages")
    suspend fun createMessage(@Body request: MessageRequest): MessageResponse

    @GET("users/search")
    suspend fun searchUser(@Query("name") name: String): UserResponse
}