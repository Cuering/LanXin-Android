package com.lanxin.android.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class LoginResult(
    val success: Boolean,
    val token: String? = null,
    val message: String = ""
)

@Singleton
class LanXinAuthClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun login(baseUrl: String, username: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            try {
                val url = "${baseUrl.trimEnd('/')}/api/v1/auth/login"
                val json = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val responseJson = JSONObject(responseBody)
                    val token = responseJson.optString("token")
                    if (token.isNotBlank()) {
                        LoginResult(success = true, token = token, message = "登录成功")
                    } else {
                        LoginResult(success = false, message = "响应中未找到 token")
                    }
                } else {
                    val errorMsg = try {
                        JSONObject(responseBody).optString("detail", response.message)
                    } catch (_: Exception) {
                        response.message
                    }
                    LoginResult(success = false, message = "登录失败: $errorMsg")
                }
            } catch (e: Exception) {
                LoginResult(success = false, message = "网络错误: ${e.localizedMessage ?: "未知错误"}")
            }
        }
}
