package com.tman.mychat

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.tman.mychat.databinding.FragmentChatRoomBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.http.RetryAndFollowUpInterceptor


class ChatRoomFragment : Fragment(R.layout.fragment_chat_room) {

    // Safe Args から roomId を受け取るためのプロパティ
    private val args: ChatRoomFragmentArgs by navArgs()
    private var _binding : FragmentChatRoomBinding? = null
    private val binding get() = _binding!!

    // 画面上のパーツを保持する変数
    private lateinit var chatAdapter: ChatAdapter
    private val messageList = mutableListOf<Message>()

    private fun sendMessage(text: String, isMe: Boolean) {
        messageList.add(Message(text, isMe, null))
        chatAdapter.notifyItemInserted(messageList.size - 1)

        val recyclerview = binding.chatRecyclerView
        recyclerview.scrollToPosition(messageList.size - 1)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatRoomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //myID
        val sharedPref = requireActivity().getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE)
        val myUserId = sharedPref.getInt("myUserId", -1)
        // 受け取った roomId を確認
        val currentRoomId = args.roomId
        val currentRoomName = args.roomName
        binding.roomName.text = currentRoomName
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)

        // データベースとDaoのインスタンスを取得
        val db = AppDatabase.getDatabase(requireContext())
        val messageDao = db.messageDao()
        val userDao = db.userDao()

        //同期処理
        syncUserByRoom(userDao, currentRoomId)

        // Adapterの準備
        chatAdapter = ChatAdapter(messageList)
        val recyclerView = binding.chatRecyclerView
        recyclerView.adapter = chatAdapter //recycleViewとChatAdapterの接続
        recyclerView.layoutManager = LinearLayoutManager(requireContext())//?

        val sendButton = binding.sendButton
        val messageInput = binding.messageInput

        // 招待ボタンの処理
        binding.inviteButton.setOnClickListener {
            showInviteDialog(currentRoomId)
        }
        //キーボードの状態を監視
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val keyboardHeight = imeInsets.bottom
            val isVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val systembar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val homebutton = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            recyclerView.setPadding(0, systembar.top, 0, 0)
            if (isVisible) {
                view.setPadding(0, 0, 0, keyboardHeight)
            } else {
                view.setPadding(0, 0, 0, homebutton.bottom)
            }
            insets
        }
        //送信ボダンを押されたときの処理
        sendButton.setOnClickListener {
            val text = messageInput.text.toString()
            if (text.isNotEmpty()) {
                // リストにデータを追加
                messageList.add(Message(text, true, null))
                // Adapterに「データが増えたよ」と通知（これで画面が更新される）
                chatAdapter.notifyItemInserted(messageList.size - 1)
                // 入力欄を空にする
                messageInput.text.clear()

                // 2. 返信ロジック
                val replyText = when {
                    text.startsWith("/help") -> "/help コマンド詳細\n /hello こんにちは\n /echo 文字列"
                    text.startsWith("/hello") -> "こんにちは"
                    text.startsWith("/echo") -> "[$text]っていった？"
                    else -> null
                }

                if (replyText != null) {
                    sendMessage(replyText, false)
                }
                // コルーチン内でデータの保存・取得
                val currentRoomId = args.roomId

                // 💡 2. サーバーやDBに保存するために、ここで暗号化する！！
                // ユーザーの最新情報を取得
                syncUserByRoom(userDao, currentRoomId)
                // Roomから所属しているユーザーの公開鍵を取得
                val userList = mutableListOf<UserEntity>()
                lifecycleScope.launch {
                    userDao.getUsersByRoom(currentRoomId).forEach { user ->
                        userList.add(user)
                    }
                }
                //部屋にいるユーザーに各公開鍵で暗号化されたAES鍵と暗号文を送る
                lifecycleScope.launch {
                    try {
                        // 1. AESで本文を暗号化
                        val aesKey = CryptoManager.generateAESKey()
                        val encryptedText = AesCrypto.encrypt(text, aesKey)

                        // 2. 部屋のユーザー全員分、AES鍵をRSAで暗号化してリストを作る
                        val roomUsers = db.userDao().getUsersByRoom(currentRoomId)
                        val keyList = mutableListOf<KeyInfo>()

                        roomUsers.forEach { user ->
                            if (user.publicKey.isNotEmpty()) {
                                // 相手の公開鍵でAES鍵をロック
                                val rsaEncryptedKey = CryptoManager.encryptAESKeyWithRSA(aesKey, user.publicKey)

                                // リストに追加
                                keyList.add(KeyInfo(userId = user.userId, key = rsaEncryptedKey))
                            }
                        }

                        // 3. 全体をひとまとめにする
                        val payload = MessagePayload(
                            encryptedText = encryptedText,
                            encryptedKeys = keyList
                        )

                        // 4. GsonでJSON文字列に変換
                        val jsonString = Gson().toJson(payload)

                        // データの保存 (Upsert)
                        val newMessages = mutableListOf(
                            MessageEntity(text = text, senderId = myUserId, isMe = true, roomId = currentRoomId, messageId = 0),
                        )
                        if (replyText != null){
                            newMessages.add(MessageEntity(text = replyText, senderId = 0, isMe = false, roomId = currentRoomId, messageId = 0))
                        }
                        messageDao.upsertMessage(newMessages)

                        // 5. サーバーへ送信 jsonで鍵と内容をまとめて送信
                        RetrofitClient.api.createMessage(
                            MessageRequest(id = 0, text = jsonString, senderID = myUserId, roomID = currentRoomId)
                        )

                    } catch (e: Exception) {
                        Log.e("Crypto", "JSON作成・送信エラー", e)
                    }

                }
                //【TODO】UserEntityが公開鍵を持っていないので，データベース更新
                val publicKeyString = userList.firstOrNull()?.publicKey ?: ""

                recyclerView.scrollToPosition(messageList.size - 1)
            }
        }
        //ルームリストボタンを押されたときの処理 ルーム選択に戻る
        binding.roomListButton.setOnClickListener {
            // 一つ前の画面に戻る命令
            findNavController().navigateUp()
        }

        //同期 roomIDのメッセージを取得　【TODO】同期処理と画面更新を分ける
        lifecycleScope.launch {
            try {
                // 1. サーバーから最新のメッセージ一覧を取得して変数に入れる
                val remoteMessages = RetrofitClient.api.getMessages(currentRoomId)

                // 2. サーバーのデータをローカルDB用(MessageEntity)に変換する
                val messageEntities = remoteMessages.map { response ->
                    // 💡 あらかじめ平文を入れる変数を用意
                    var decryptedText = ""

                    try {
                        // 文字列をJSONオブジェクト（Mapのようなもの）に変換
                        val jsonObject = org.json.JSONObject(response.text)

                        // "encrypted_text" の値を取り出す
                        val encryptedContent = jsonObject.getString("encrypted_text")
                        // "encrypted_keys" の配列（リスト）を取り出す
                        val keysArray = jsonObject.getJSONArray("encrypted_keys")

                        // 自分のユーザーIDと一致する鍵を探す
                        var myEncryptedAesKey: String? = null
                        for (i in 0 until keysArray.length()) {
                            val keyInfo = keysArray.getJSONObject(i)
                            if (keyInfo.getInt("user_id") == myUserId) {
                                myEncryptedAesKey = keyInfo.getString("key")
                                break // 見つかったらループ終了
                            }
                        }

                        // 自分の鍵が見つかったら復号化を実行！
                        if (myEncryptedAesKey != null) {
                            val decryptedKey = CryptoManager.decryptAESKeyWithRSA(myEncryptedAesKey)
                            // AESで復号化
                            decryptedText = CryptoManager.decrypt(encryptedContent, decryptedKey)
                        } else {
                            decryptedText = "[このメッセージはあなたには暗号化されていません]"
                        }

                    } catch (e: Exception) {
                        decryptedText = "複合に失敗しました"
                    }

                    // 💡 最終的にDBに保存する Entity を作る
                    MessageEntity(
                        messageId = response.id,
                        roomId = currentRoomId,
                        senderId = response.senderID,
                        text = decryptedText, // ✨ ここにはすでに「復号済みの読める文字」が入る！
                        isMe = (response.senderID == myUserId)
                    )
                }
                // 3. ローカルデータベースにまとめて保存・上書き(Upsert)
                messageDao.upsertMessage(messageEntities)

            } catch(e: Exception) {
                Log.e("ChatApp", "通信エラー: ${e.message}")
            }


            val allMessages = messageDao.getMessagesByRoom(currentRoomId)

            messageList.clear()
            allMessages.forEach { message ->

                messageList.add(Message(message.text, message.isMe, null))
            }
            chatAdapter.notifyDataSetChanged()
            if (messageList.isNotEmpty()) {
                binding.chatRecyclerView.scrollToPosition(messageList.size - 1)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // メモリリーク防止
    }
    // 招待用のダイアログを表示する関数
    private fun showInviteDialog(roomId: Int) {
        val editText = EditText(requireContext())
        editText.hint = "招待したい人の名前を入力"

        AlertDialog.Builder(requireContext())
            .setTitle("友達を招待する")
            .setView(editText)
            .setPositiveButton("招待") { _, _ ->
                val inviteeName = editText.text.toString()
                if (inviteeName.isNotEmpty()) {
                    inviteUserToRoom(inviteeName, roomId)
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    // 実際に通信を行う関数
    private fun inviteUserToRoom(inviteeName: String, roomId: Int) {
        lifecycleScope.launch {
            try {
                // 1. まず、入力された名前からその人の「ユーザーID」を取得する
                // （既存の loginUser API を「検索用」として使い回す裏技！）
                val userResponse = RetrofitClient.api.searchUser(inviteeName)
                val inviteeId = userResponse.userId

                // 2. 取得したユーザーIDと、今の部屋のIDを紐付ける（中間テーブルに登録！）
                val request = RoomUserRequest(roomID = roomId, userID = inviteeId)
                RetrofitClient.api.createRoomUser(request)

                // 3. 成功したら画面にフワッとメッセージを出す
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "${inviteeName}さんを招待しました！",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.e("ChatApp", "招待に失敗しました", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "招待エラー: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun syncUserByRoom(userDao: UserDao, currentRoomId : Int) {
        lifecycleScope.launch {
            //サーバーのエンドポイントからルームのユーザーを取得
            val userList = RetrofitClient.api.getUsersByRoom(currentRoomId)
            userDao.setUserByRoom(userList.map { user ->
                UserEntity(user.name, "", user.id, user.publicKey)
            })
        }
    }
}

// 1つの鍵情報を表すクラス
data class KeyInfo(
    val userId: Int,
    val key: String
)

// 送信するメッセージ全体の構造
data class MessagePayload(
    val encryptedText: String,
    val encryptedKeys: List<KeyInfo> // 複数人分をリストで保持
)