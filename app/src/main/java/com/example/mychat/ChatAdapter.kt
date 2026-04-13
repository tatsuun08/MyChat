package com.example.mychat

import android.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Adapterは RecyclerView.Adapter を継承して作成
class ChatAdapter(private val messages: List<Message>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    // 1. 1行分のレイアウト（ViewHolder）の定義
    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.text1) // 簡易的に標準のIDを使用
    }

    // 2. 1行分の見た目（XML）を生成する
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.simple_list_item_1, parent, false)
        return MessageViewHolder(view)
    }

    // 3. 表示するデータの総数を返す
    override fun getItemCount(): Int = messages.size

    // 4. 指定された位置（position）のデータを、見た目（ViewHolder）にセットする
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.text = message.text
    }
}