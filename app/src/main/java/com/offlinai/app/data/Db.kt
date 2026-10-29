package com.offlinai.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val role: String, // "user" or "assistant"
    val content: String,
    val createdAt: Long
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY createdAt DESC")
    fun chats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id")
    fun messages(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id")
    suspend fun messagesOnce(chatId: Long): List<MessageEntity>

    @Insert suspend fun insertChat(chat: ChatEntity): Long
    @Insert suspend fun insertMessage(message: MessageEntity): Long
    @Update suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM chats WHERE id = :id") suspend fun deleteChat(id: Long)
    @Query("DELETE FROM messages WHERE chatId = :id") suspend fun deleteMessages(id: Long)
}

@Database(entities = [ChatEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ChatDao
}
