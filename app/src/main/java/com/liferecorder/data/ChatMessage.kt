package com.liferecorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 对话消息实体
 */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String,           // "user" 或 "assistant"
    val content: String,        // 消息内容
    val timestamp: Long,        // 发送时间（毫秒时间戳）
    val conversationId: Long    // 会话 ID，用于区分不同对话
)
