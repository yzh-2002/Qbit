package com.liferecorder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liferecorder.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val chatDao = db.chatMessageDao()
    private val recordDao = db.lifeRecordDao()
    private val settings = SettingsManager.getInstance(application)

    // 当前会话 ID
    private val _currentConversationId = MutableStateFlow(0L)
    val currentConversationId: StateFlow<Long> = _currentConversationId.asStateFlow()

    // 当前会话的消息列表
    val messages: StateFlow<List<ChatMessage>> = _currentConversationId
        .flatMapLatest { id ->
            if (id > 0) chatDao.getMessagesByConversation(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 会话列表（每个会话的最新消息）
    val conversations: StateFlow<List<ChatMessage>> = chatDao.getLatestMessagePerConversation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 是否正在生成回复
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // 错误信息
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // 启动时加载或创建新会话
        viewModelScope.launch {
            val maxId = chatDao.getMaxConversationId() ?: 0L
            _currentConversationId.value = if (maxId > 0) maxId else 1L
        }
    }

    /** 创建新会话 */
    fun newConversation() {
        viewModelScope.launch {
            val maxId = chatDao.getMaxConversationId() ?: 0L
            _currentConversationId.value = maxId + 1
        }
    }

    /** 切换到指定会话 */
    fun switchConversation(conversationId: Long) {
        _currentConversationId.value = conversationId
    }

    /** 删除会话 */
    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            chatDao.deleteConversation(conversationId)
            if (_currentConversationId.value == conversationId) {
                val maxId = chatDao.getMaxConversationId() ?: 0L
                _currentConversationId.value = if (maxId > 0) maxId else 1L
            }
        }
    }

    /** 发送消息并获取 AI 回复 */
    /** 是否使用周总结提示词 */
    fun sendMessage(content: String, isWeeklySummary: Boolean = false) {
        if (content.isBlank() || _isGenerating.value) return

        val conversationId = _currentConversationId.value
        viewModelScope.launch {
            // 保存用户消息
            chatDao.insert(ChatMessage(
                role = "user",
                content = content.trim(),
                timestamp = System.currentTimeMillis(),
                conversationId = conversationId
            ))

            // 开始生成
            _isGenerating.value = true
            _error.value = null

            try {
                val apiKey = settings.aiApiKey.value
                if (apiKey.isBlank()) {
                    _error.value = "请先在设置中配置 AI API Key"
                    _isGenerating.value = false
                    return@launch
                }

                // 周总结模式需要检查生活记录数据
                val lifeContext = if (isWeeklySummary) buildLifeRecordContext() else ""
                if (isWeeklySummary && lifeContext.isBlank()) {
                    // 没有记录时直接回复，不调用 API
                    val reply = "当前还没有生活记录数据哦～请先在首页记录你的日常活动，积累一些数据后我再帮你总结分析。"
                    chatDao.insert(ChatMessage(
                        role = "assistant",
                        content = reply,
                        timestamp = System.currentTimeMillis(),
                        conversationId = conversationId
                    ))
                    _isGenerating.value = false
                    return@launch
                }

                val client = AiChatClient(
                    baseUrl = settings.aiBaseUrl.value,
                    apiKey = apiKey,
                    model = settings.aiModel.value
                )

                // 构建上下文
                val contextMessages = buildContextMessages(conversationId, content, lifeContext, isWeeklySummary)

                // 插入一条空的 assistant 消息，后续流式更新
                val assistantMsg = ChatMessage(
                    role = "assistant",
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    conversationId = conversationId
                )
                val msgId = chatDao.insert(assistantMsg)

                // 流式接收
                val buffer = StringBuilder()
                client.streamChat(contextMessages).collect { token ->
                    buffer.append(token)
                    chatDao.update(assistantMsg.copy(id = msgId, content = buffer.toString()))
                }

                // 如果没有收到任何内容
                if (buffer.isEmpty()) {
                    chatDao.update(assistantMsg.copy(id = msgId, content = "（AI 未返回内容）"))
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "请求失败"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    /**
     * 构建发送给 AI 的消息列表，包含系统提示词和生活记录上下文
     */
    private suspend fun buildContextMessages(
        conversationId: Long,
        latestUserMessage: String,
        lifeContext: String,
        isWeeklySummary: Boolean
    ): List<AiChatClient.Message> {
        val result = mutableListOf<AiChatClient.Message>()

        // 根据模式构建系统提示词
        if (isWeeklySummary && lifeContext.isNotEmpty()) {
            val systemPrompt = buildString {
                append("你是一个生活记录分析助手，用户会提供每天的时间记录数据（格式包含日期、时间段、活动内容）。\n")
                append("你的任务是：\n")
                append("1. 将活动按类型分类：\n")
                append("   - 工作/学习（如：写代码、开会、看书、学习课程）\n")
                append("   - 娱乐（如：打游戏、看电影、刷短视频/社交媒体，包括小红书、抖音、B站等）\n")
                append("   - 生活事务（如：吃饭、睡觉、散步、洗漱、家务）\n")
                append("2. 特别关注娱乐中的短视频/社交媒体时间，指出其占比和可能的影响。\n")
                append("3. 计算每天各类别的时间占比（小时数和百分比），并展示分布情况。\n")
                append("输出格式建议：\n")
                append("- 分类统计表（按天）\n")
                append("- 占比分析\n\n")
                append("以下是用户最近的生活记录数据：\n")
                append(lifeContext)
            }
            result.add(AiChatClient.Message("system", systemPrompt))
        }

        // 历史对话（最近 20 条）
        val history = messages.value.takeLast(20)
        for (msg in history) {
            if (msg.content.isNotBlank()) {
                result.add(AiChatClient.Message(msg.role, msg.content))
            }
        }

        // 当前用户消息（如果历史里还没有）
        if (history.lastOrNull()?.content != latestUserMessage) {
            result.add(AiChatClient.Message("user", latestUserMessage))
        }

        return result
    }

    /**
     * 构建最近 7 天的生活记录文本作为上下文
     */
    private suspend fun buildLifeRecordContext(): String {
        val dateFormat = SimpleDateFormat("M月d日(E)", Locale.CHINESE)
        val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val holidayDao = db.holidayCacheDao()
        val sb = StringBuilder()

        // 最近 7 天
        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayStart = dayCal.timeInMillis
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L

            val records = recordDao.getRecordsByDaySync(dayStart, dayEnd)
            if (records.isEmpty()) continue

            // 查询当天的日期类型
            val dateKey = dateKeyFormat.format(Date(dayStart))
            val holiday = holidayDao.getHoliday(dateKey, "CN")
            val dayTypeLabel = when {
                holiday != null -> when (holiday.type) {
                    0 -> "工作日"
                    1 -> "周末"
                    2 -> "节日·${holiday.name}"
                    3 -> "调休补班"
                    else -> ""
                }
                else -> {
                    // 没有缓存时根据星期几判断
                    val dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK)
                    if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) "周末" else "工作日"
                }
            }

            sb.append("【${dateFormat.format(Date(dayStart))} $dayTypeLabel】\n")
            for (record in records.sortedBy { it.startTime }) {
                sb.append("  ${record.hourLabel} ${record.content}\n")
            }
            sb.append("\n")
        }

        return sb.toString()
    }
}
