package com.tman.mychat
import android.widget.ImageView

data class Message(
    val text: String,
    val isMe: Boolean,
    val image: ImageView?
)