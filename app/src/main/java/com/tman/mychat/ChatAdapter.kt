package com.tman.mychat

import com.tman.mychat.R
import com.tman.mychat.R.id
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
        val textPartner: TextView = view.findViewById(id.textPartner)
        val textMe: TextView = view.findViewById(id.textMe)
    }

    // 2. 1行分の見た目（XML）を生成する
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    // 3. 表示するデータの総数を返す
    override fun getItemCount(): Int = messages.size

    // 4. 指定された位置（position）のデータを、見た目（ViewHolder）にセットする
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        if (message.isMe) {
            // 自分の場合：右を表示、左を隠す
            holder.textMe.text = message.text
            holder.textMe.visibility = View.VISIBLE
            holder.textPartner.visibility = View.GONE
        } else {
            // 相手の場合：左を表示、右を隠す
            holder.textPartner.text = message.text
            holder.textPartner.visibility = View.VISIBLE
            holder.textMe.visibility = View.GONE
        }
    }
}

