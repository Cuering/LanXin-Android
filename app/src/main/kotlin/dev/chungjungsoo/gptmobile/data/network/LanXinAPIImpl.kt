package com.lanxin.android.data.network

import com.lanxin.android.data.dto.ApiState
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanXinAPIImpl @Inject constructor(
    private val networkClient: NetworkClient
) : LanXinAPI {

    private var token: String = ""
    private var apiUrl: String = ""

    private val client get() = networkClient()

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
            val url = "${apiUrl}/api/v1/chat"
            val body = buildJsonMessage(message, username, sessionId)

            val response = withContext(Dispatchers.IO) {
                client.preparePost(url) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.execute()
            }

            if (!response.status.isSuccess()) {
                emit(ApiState.Error("HTTP ${response.status.value}: ${response.status.description}"))
                emit(ApiState.Done)
                return@flow
            }

            withContext(Dispatchers.IO) {
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") {
                            emit(ApiState.Done)
                            return@withContext
                        }
                        if (data.isBlank()) continue

                        try {
                            val json = JSONObject(data)
                            val content = json.optString("content")
                                ?: json.optString("message")
                                ?: json.optString("text")
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
            }
        } catch (e: Exception) {
            emit(ApiState.Error(e.message ?: "Unknown error"))
            emit(ApiState.Done)
        }
    }.flowOn(Dispatchers.IO)

    private fun buildJsonMessage(message: String, username: String, sessionId: String?): String {
        return JSONObject().apply {
            put("username", username)
            put("message", message)
            sessionId?.let { put("session_id", it) }
            put("platform", JSONObject().apply {
                put("platform", "webchat")
                put("user_id", "lanxin_app")
                put("group_name", "default")
            })
        }.toString()
    }
}
