package com.liferecorder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    /** 获取指定会话的所有消息（按时间正序） */
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesByConversation(conversationId: Long): Flow<List<ChatMessage>>

    /** 获取所有会话 ID 及其最新消息（用于会话列表） */
    @Query("SELECT * FROM chat_messages WHERE id IN (SELECT MAX(id) FROM chat_messages GROUP BY conversationId) ORDER BY timestamp DESC")
    fun getLatestMessagePerConversation(): Flow<List<ChatMessage>>

    /** 插入消息 */
    @Insert
    suspend fun insert(message: ChatMessage): Long

    /** 更新消息（用于流式追加内容） */
    @Update
    suspend fun update(message: ChatMessage)

    /** 删除指定会话的所有消息 */
    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: Long)

    /** 获取最大会话 ID */
    @Query("SELECT MAX(conversationId) FROM chat_messages")
    suspend fun getMaxConversationId(): Long?
}
