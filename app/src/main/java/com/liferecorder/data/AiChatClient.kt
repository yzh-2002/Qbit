package com.liferecorder.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * AI 聊天客户端，支持 OpenAI 兼容 API 的 SSE 流式输出
 */
class AiChatClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    data class Message(val role: String, val content: String)

    /**
     * 流式发送消息，返回 Flow<String>，每次 emit 一个增量 token
     */
    fun streamChat(messages: List<Message>): Flow<String> = callbackFlow {
        val trimmedBase = baseUrl.trimEnd('/')
        val url = if (trimmedBase.endsWith("/v1")) {
            "$trimmedBase/chat/completions"
        } else {
            "$trimmedBase/v1/chat/completions"
        }

        val body = gson.toJson(mapOf(
            "model" to model,
            "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
            "stream" to true
        ))

        Log.d("AiChatClient", "Request URL: $url")
        Log.d("AiChatClient", "Model: $model")
        Log.d("AiChatClient", "BaseUrl: $baseUrl")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("AiChatClient", "Response code: ${response.code}")
                Log.d("AiChatClient", "Response URL: ${response.request.url}")
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e("AiChatClient", "API error ${response.code}: $errorBody")
                    close(IOException("API error ${response.code}: $errorBody"))
                    return
                }

                try {
                    val reader = BufferedReader(
                        InputStreamReader(response.body!!.byteStream())
                    )
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val data = line ?: continue
                        if (!data.startsWith("data: ")) continue
                        val json = data.removePrefix("data: ").trim()
                        if (json == "[DONE]") break

                        try {
                            val parsed = JsonParser.parseString(json).asJsonObject
                            val choices = parsed.getAsJsonArray("choices")
                            if (choices != null && choices.size() > 0) {
                                val delta = choices[0].asJsonObject
                                    .getAsJsonObject("delta")
                                val content = delta?.get("content")?.asString
                                if (!content.isNullOrEmpty()) {
                                    trySend(content)
                                }
                            }
                        } catch (_: Exception) {
                            // 跳过解析失败的行
                        }
                    }
                    reader.close()
                    close()
                } catch (e: Exception) {
                    close(e)
                }
            }
        })

        awaitClose { call.cancel() }
    }
}
