package com.tman.mychat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.tman.mychat.databinding.FragmentChatRoomBinding
import kotlinx.coroutines.launch

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

        // 受け取った roomId を確認
        val currentRoomId = args.roomId
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)

        // データベースとDaoのインスタンスを取得
        val db = AppDatabase.getDatabase(requireContext())
        val messageDao = db.messageDao()

        // Adapterの準備
        chatAdapter = ChatAdapter(messageList)
        val recyclerView = binding.chatRecyclerView
        recyclerView.adapter = chatAdapter //recycleViewとChatAdapterの接続
        recyclerView.layoutManager = LinearLayoutManager(requireContext())//?

        // データの取得 (Select)
        lifecycleScope.launch {
            val allMessages = messageDao.getMessages()
            allMessages.forEach { message ->
                messageList.add(Message(message.text, message.isMe, null))
            }
            chatAdapter.notifyDataSetChanged()
        }

        // 送信ボタンの処理
        val sendButton = binding.sendButton
        val messageInput = binding.messageInput

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
                messageList.add(Message(text, true, null))
                // Adapterに「データが増えたよ」と通知（これで画面が更新される）
                chatAdapter.notifyItemInserted(messageList.size - 1)
                // 入力欄を空にする
                messageInput.text.clear()

                // 2. 返信ロジック
                val replyText = when {
                    text.startsWith("/help") -> "/help コマンド詳細"
                    text.startsWith("/hello") -> "こんにちは"
                    else -> "[$text って言った？]"
                }
                // コルーチン内でデータの保存・取得を実行
                lifecycleScope.launch {
                    // データの保存 (Upsert)
                    val newMessages = mutableListOf(
                        MessageEntity(text = text, senderId = 1, isMe = true, messageId = 0),
                    )
                    if (replyText != ""){
                        newMessages.add(MessageEntity(text = replyText, senderId = 2, isMe = false, messageId = 0))
                    }
                    messageDao.upsertMessage(newMessages)
                }
                sendMessage(replyText, false)
                recyclerView.scrollToPosition(messageList.size - 1)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // メモリリーク防止
    }
}