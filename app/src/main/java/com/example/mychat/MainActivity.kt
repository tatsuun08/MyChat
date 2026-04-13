package com.example.mychat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mychat.R.id
import com.example.mychat.R.layout

class MainActivity : AppCompatActivity() {

    // 画面上のパーツを保持する変数
    private lateinit var chatAdapter: ChatAdapter
    private val messageList = mutableListOf<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {//Bundleは入力途中の一時データ ?はNullの場合があるかもしれない
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_main) //Layoutディレクトリのactivity_mainを呼び出す

        // 1. Adapterの準備
        chatAdapter = ChatAdapter(messageList)
        val recyclerView = findViewById<RecyclerView>(id.chatRecyclerView)
        recyclerView.adapter = chatAdapter //recycleViewとChatAdapterの接続
        recyclerView.layoutManager = LinearLayoutManager(this)//?

        // 2. 送信ボタンの処理
        val sendButton = findViewById<Button>(id.sendButton)
        val messageInput = findViewById<EditText>(id.messageInput)

        //送信ボダンを起こされたときの処理
        sendButton.setOnClickListener {
            val text = messageInput.text.toString()
            if (text.isNotEmpty()) {
                // リストにデータを追加
                messageList.add(Message(text, true))
                // Adapterに「データが増えたよ」と通知（これで画面が更新される）
                chatAdapter.notifyItemInserted(messageList.size - 1)
                // 入力欄を空にする
                messageInput.text.clear()
                // 最新のメッセージまでスクロール
                recyclerView.scrollToPosition(messageList.size - 1)
            }
        }
    }
}
