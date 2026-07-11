package com.lanxin.android.data.network

import com.lanxin.android.data.dto.groq.request.GroqChatCompletionRequest
import com.lanxin.android.data.dto.groq.response.GroqChatCompletionChunk
import kotlinx.coroutines.flow.Flow

interface GroqAPI {
    fun streamChatCompletion(
        request: GroqChatCompletionRequest,
        timeoutSeconds: Int,
        token: String?,
        apiUrl: String
    ): Flow<GroqChatCompletionChunk>
}
