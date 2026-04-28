package com.tman.mychat

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "message")
data class MessageEntitiy(
    val text : String,
    val senderId : Int,
    val isMe : Boolean,
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId : Int,
)

@Entity(tableName = "user")
data class UserEntitiy(
    val name : String,
    val icon : String,
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId : Int,
)

@Entity(tableName = "room")
data class RoomEntity(
    val name : String,
    val icon : String,
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId : Int,
)

@Entity(
    tableName = "room_user",
    primaryKeys = ["room_id", "user_id"],
)
data class RoomUser(
    val roomId : Int,
    val userId : Int
)

