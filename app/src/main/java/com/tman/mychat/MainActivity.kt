package com.tman.mychat

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tman.mychat.R.id
import com.tman.mychat.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding

    // 画面上のパーツを保持する変数
    private lateinit var chatAdapter: ChatAdapter
    private val messageList = mutableListOf<Message>()

    private fun sendMessage(text: String, isMe: Boolean) {
        messageList.add(Message(text, isMe))
        chatAdapter.notifyItemInserted(messageList.size - 1)

        val recycleview = findViewById<RecyclerView>(R.id.chatRecyclerView)
        recycleview.scrollToPosition(messageList.size - 1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {//Bundleは入力途中の一時データ ?はNullの場合があるかもしれない
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) //Layoutディレクトリのactivity_mainを呼び出す
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Adapterの準備
        chatAdapter = ChatAdapter(messageList)
        val recyclerView = findViewById<RecyclerView>(id.chatRecyclerView)
        recyclerView.adapter = chatAdapter //recycleViewとChatAdapterの接続
        recyclerView.layoutManager = LinearLayoutManager(this)//?

        // 2. 送信ボタンの処理
        val sendButton = findViewById<Button>(id.sendButton)
        val messageInput = findViewById<EditText>(id.messageInput)

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
                val split_text = text.split(" ")
                var replyText : String = ""
                if (split_text[0] == "/help"){
                    replyText = "/help コマンド詳細"
                }
                else if (split_text[0] == "/hello"){
                    replyText = "こんにちは"
                    if (split_text.size > 1){
                        replyText += split_text[1]
                    }
                }
                else {
                    replyText = "[${text}って言った？]"
                }

                sendMessage(replyText, false)
                recyclerView.scrollToPosition(messageList.size - 1)
            }
        }
    }
}

