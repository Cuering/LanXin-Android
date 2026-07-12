package com.lanxin.android.data.network

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class LoginResult(
    val success: Boolean,
    val token: String? = null,
    val message: String = ""
)

@Singleton
class LanXinAuthClient @Inject constructor(
    private val networkClient: NetworkClient
) {

    private val client get() = networkClient()

    suspend fun login(baseUrl: String, username: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            try {
                val url = "${baseUrl.trimEnd('/')}/api/v1/auth/login"
                val json = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }

                val response = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(json.toString())
                }

                val responseBody = response.bodyAsText()

                if (response.status.isSuccess()) {
                    val responseJson = JSONObject(responseBody)
                    val token = responseJson.optString("token")
                    if (token.isNotBlank()) {
                        LoginResult(success = true, token = token, message = "登录成功")
                    } else {
                        LoginResult(success = false, message = "响应中未找到 token")
                    }
                } else {
                    val errorMsg = try {
                        JSONObject(responseBody).optString("detail", response.status.description)
                    } catch (_: Exception) {
                        response.status.description
                    }
                    LoginResult(success = false, message = "登录失败: $errorMsg")
                }
            } catch (e: Exception) {
                LoginResult(success = false, message = "网络错误: ${e.localizedMessage ?: "未知错误"}")
            }
        }
}
