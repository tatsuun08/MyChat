package com.tman.mychat

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(tableName = "message")
data class MessageEntity(
    val text : String,
    val senderId : Int,
    val isMe : Boolean,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "message_id")
    val messageId : Int,
)

@Entity(tableName = "user")
data class UserEntity(
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
    @ColumnInfo(name = "room_id")
    val roomId : Int,
)

@Entity(
    tableName = "room_user",
    primaryKeys = ["room_id", "user_id"],
)
data class RoomUser(
    @ColumnInfo(name = "room_id")
    val roomId : Int,
    @ColumnInfo(name = "user_id")
    val userId : Int
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM message")
    suspend fun getMessages(): List<MessageEntity>

    @Upsert
    suspend fun upsertMessage(messages: List<MessageEntity>)
}

@Database(
    entities = [
        MessageEntity::class,
        UserEntity::class,
        RoomEntity::class,
        RoomUser::class
   ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chat_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
