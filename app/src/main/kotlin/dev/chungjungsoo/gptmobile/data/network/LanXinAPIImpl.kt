package com.lanxin.android.data.network

import com.lanxin.android.data.dto.ApiState
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
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

            val response = networkClient().post(url) {
                setBody(body)
                contentType(ContentType.Application.Json)
            }

            if (!response.status.isSuccess()) {
                emit(ApiState.Error("HTTP ${response.status.value}: ${response.status.description}"))
                emit(ApiState.Done)
                return@flow
            }

            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        emit(ApiState.Done)
                        return@flow
                    }
                    if (data.isBlank()) continue

                    try {
                        val json = JSONObject(data)
                        val content = json.optString("content").takeIf { it.isNotEmpty() }
                            ?: json.optString("message").takeIf { it.isNotEmpty() }
                            ?: json.optString("text").takeIf { it.isNotEmpty() }
                            ?: data
                        if (content.isNotBlank()) {
                            emit(ApiState.Success(content))
                        }
                    } catch (_: Exception) {
                        if (data.isNotBlank()) {
                            emit(ApiState.Success(data))
                        }
                    }
                }
            }
            emit(ApiState.Done)
        } catch (e: Exception) {
            emit(ApiState.Error(e.message ?: "Unknown error"))
            emit(ApiState.Done)
        }
    }.flowOn(Dispatchers.IO)

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
