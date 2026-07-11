package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.response.GenerateContentResponse
import kotlinx.coroutines.flow.Flow

interface GoogleAPI {
    fun setToken(token: String?)
    fun setAPIUrl(url: String)
    fun streamGenerateContent(request: GenerateContentRequest, model: String, timeoutSeconds: Int): Flow<GenerateContentResponse>
    suspend fun uploadFile(filePath: String, fileName: String, mimeType: String): UploadedProviderFile
    suspend fun isFileAvailable(fileName: String): Boolean
}
