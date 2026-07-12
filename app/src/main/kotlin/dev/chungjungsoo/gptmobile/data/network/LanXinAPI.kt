package com.lanxin.android.data.network

import com.lanxin.android.data.dto.ApiState
import kotlinx.coroutines.flow.Flow

interface LanXinAPI {
    fun setToken(token: String)
    fun setAPIUrl(apiUrl: String)
    suspend fun streamChat(
        message: String,
        username: String,
        sessionId: String?,
        timeoutSeconds: Int
    ): Flow<ApiState>
}
