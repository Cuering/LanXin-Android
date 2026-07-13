package com.lanxin.android.data.network

import com.lanxin.android.data.dto.ApiState
import com.lanxin.android.util.applyPlatformStreamingTimeout
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject

@Singleton
class LanXinAPIImpl @Inject constructor(
    private val networkClient: NetworkClient
) : LanXinAPI {

    private var token: String = ""
    private var apiUrl: String = ""

    override fun setToken(token: String) {
        this.token = token
    }

    override fun setAPIUrl(apiUrl: String) {
        this.apiUrl = apiUrl.trimEnd('/')
    }

    override suspend fun streamChat(
        message: String,
        username: String,
        sessionId: String?,
        timeoutSeconds: Int
    ): Flow<ApiState> = flow {
        emit(ApiState.Loading)

        try {
            val url = "$apiUrl/api/v1/chat"
            val body = buildJsonMessage(message, username, sessionId)

            networkClient().preparePost(url) {
                applyPlatformStreamingTimeout(timeoutSeconds)
                setBody(body)
                contentType(ContentType.Application.Json)
                accept(ContentType.Text.EventStream)
                applyAuthHeader()
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    emit(ApiState.Error("HTTP ${response.status.value}: ${response.status.description}"))
                    emit(ApiState.Done)
                    return@execute
                }

                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank() || line.startsWith(":")) {
                        // SSE heartbeat / empty line
                        continue
                    }

                    val payload = when {
                        line.startsWith("data: ") -> line.removePrefix("data: ").trim()
                        line.startsWith("data:") -> line.removePrefix("data:").trim()
                        else -> continue
                    }

                    if (payload == "[DONE]") {
                        emit(ApiState.Done)
                        return@execute
                    }
                    if (payload.isBlank()) continue

                    handleSsePayload(payload)?.let { emit(it) }
                }
                emit(ApiState.Done)
            }
        } catch (e: HttpRequestTimeoutException) {
            emit(ApiState.Error("请求超时，请把提供商超时调到 180 秒以上后再试"))
            emit(ApiState.Done)
        } catch (e: java.net.SocketTimeoutException) {
            emit(ApiState.Error("等待回复超时，请把提供商超时调到 180 秒以上后再试"))
            emit(ApiState.Done)
        } catch (e: Exception) {
            emit(ApiState.Error(e.message ?: "Unknown error"))
            emit(ApiState.Done)
        }
    }.flowOn(Dispatchers.IO)

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuthHeader() {
        if (token.isBlank()) return
        // AstrBot API Key: abk_xxx → X-API-Key
        // Dashboard login JWT → Authorization: Bearer
        if (token.startsWith("abk_")) {
            header("X-API-Key", token)
        } else {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    /**
     * AstrBot SSE events look like:
     * {"type":"plain","data":"能","streaming":true}
     * {"type":"complete","data":"完整回复"}
     * {"type":"think","data":"思考过程",...}
     * {"type":"plain","data":"{...}","chain_type":"tool_call"}  → 工具调用，跳过
     * {"type":"plain","data":"{...}","chain_type":"tool_call_result"}  → 工具结果，跳过
     * {"type":"session_id", ...} / agent_stats / message_saved / end → ignore for chat text
     */
    private fun handleSsePayload(payload: String): ApiState? {
        return try {
            val json = JSONObject(payload)
            val type = json.optString("type")
            val chainType = json.optString("chain_type")

            // 工具调用/结果事件：type=plain 但 chain_type 标记为 tool_call，跳过避免显示原始 JSON
            if (chainType == "tool_call" || chainType == "tool_call_result") {
                return null
            }

            when (type) {
                "plain" -> {
                    val text = json.optString("data")
                    if (text.isNotEmpty()) ApiState.Success(text) else null
                }

                // Final full text is already streamed via plain deltas; skip to avoid duplication.
                "complete" -> null

                "error" -> {
                    val msg = json.optString("data").ifBlank {
                        json.optString("message").ifBlank { "服务端返回错误" }
                    }
                    ApiState.Error(msg)
                }

                // 思考过程：暂时隐藏（以后可用 Thinking 状态展示）
                "think" -> null

                // control / meta events
                "session_id",
                "user_message_saved",
                "message_saved",
                "agent_stats",
                "attachment_saved",
                "end" -> null

                else -> {
                    // Fallback for non-typed payloads / older formats
                    val content = json.optString("content").takeIf { it.isNotEmpty() }
                        ?: json.optString("message").takeIf { it.isNotEmpty() }
                        ?: json.optString("text").takeIf { it.isNotEmpty() }
                        ?: json.optString("data").takeIf { it.isNotEmpty() && type.isBlank() }
                    content?.let { ApiState.Success(it) }
                }
            }
        } catch (_: Exception) {
            // Non-JSON residual line: only accept plain text, never dump raw JSON-looking blobs
            if (payload.isNotBlank() && !payload.trimStart().startsWith("{")) {
                ApiState.Success(payload)
            } else {
                null
            }
        }
    }

    private fun buildJsonMessage(message: String, username: String, sessionId: String?): String =
        JSONObject().apply {
            put("username", username)
            put("message", message)
            sessionId?.let { put("session_id", it) }
            put(
                "platform",
                JSONObject().apply {
                    put("platform", "webchat")
                    put("user_id", "lanxin_app")
                    put("group_name", "default")
                }
            )
        }.toString()
}
