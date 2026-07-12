package com.lanxin.android.data.network

import com.lanxin.android.data.dto.ApiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class LanXinAPIImpl @Inject constructor() : LanXinAPI {

    private var token: String = ""
    private var apiUrl: String = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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
            val requestBody = buildJsonMessage(message, username, sessionId)
            val jsonMediaType = "application/json".toMediaType()

            val httpRequest = Request.Builder()
                .url("${apiUrl}/api/v1/chat")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody(jsonMediaType))
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(httpRequest).execute()
            }

            if (!response.isSuccessful) {
                emit(ApiState.Error("HTTP ${response.code}: ${response.message}"))
                emit(ApiState.Done)
                return@flow
            }

            val body = response.body ?: run {
                emit(ApiState.Error("Empty response body"))
                emit(ApiState.Done)
                return@flow
            }

            withContext(Dispatchers.IO) {
                val reader = BufferedReader(InputStreamReader(body.byteStream()))
                reader.use { r ->
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        if (l.startsWith("data: ")) {
                            val data = l.removePrefix("data: ").trim()
                            if (data == "[DONE]") {
                                emit(ApiState.Done)
                                return@use
                            }
                            if (data.isBlank()) continue

                            // Try to parse as JSON
                            try {
                                val json = JSONObject(data)
                                val content = json.optString("content")
                                    ?: json.optString("message")
                                    ?: json.optString("text")
                                    ?: data
                                if (content.isNotBlank()) {
                                    emit(ApiState.Success(content))
                                }
                            } catch (e: Exception) {
                                // Plain text content
                                if (data.isNotBlank()) {
                                    emit(ApiState.Success(data))
                                }
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
