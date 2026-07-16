package com.lanxin.android.data.repository

import android.content.ContextWrapper
import com.lanxin.android.data.context.ContextBuilder
import com.lanxin.android.data.dto.ApiState
import com.lanxin.android.data.dto.anthropic.request.MessageRequest
import com.lanxin.android.data.dto.anthropic.response.MessageResponseChunk
import com.lanxin.android.data.dto.google.request.GenerateContentRequest
import com.lanxin.android.data.dto.google.response.Candidate
import com.lanxin.android.data.dto.google.response.GenerateContentResponse
import com.lanxin.android.data.dto.google.response.PromptFeedback
import com.lanxin.android.data.dto.groq.request.GroqChatCompletionRequest
import com.lanxin.android.data.dto.groq.response.GroqChatCompletionChunk
import com.lanxin.android.data.dto.groq.response.GroqChoice
import com.lanxin.android.data.dto.groq.response.GroqDelta
import com.lanxin.android.data.dto.openai.request.ChatCompletionRequest
import com.lanxin.android.data.dto.openai.request.ResponsesRequest
import com.lanxin.android.data.dto.openai.response.ChatCompletionChunk
import com.lanxin.android.data.dto.openai.response.ResponsesStreamEvent
import com.lanxin.android.data.model.ChatAttachment
import com.lanxin.android.data.model.ClientType
import com.lanxin.android.data.model.GeminiSafetySettings
import com.lanxin.android.data.network.AnthropicAPI
import com.lanxin.android.data.network.GoogleAPI
import com.lanxin.android.data.network.GroqAPI
import com.lanxin.android.data.network.LanXinAPI
import com.lanxin.android.data.network.OpenAIAPI
import com.lanxin.android.data.network.UploadedProviderFile
import com.lanxin.android.plugins.chat.data.AttachmentUploadCoordinator
import com.lanxin.android.plugins.chat.data.ChatRepositoryImpl
import com.lanxin.android.plugins.chat.data.entity.MessageV2
import com.lanxin.android.plugins.chat.data.entity.PlatformV2
import com.lanxin.android.builtin.localinference.domain.InferenceRouteCoordinator
import com.lanxin.android.builtin.localinference.domain.LocalEngineState
import com.lanxin.android.builtin.localinference.domain.LocalGenerateRequest
import com.lanxin.android.builtin.localinference.domain.LocalGenerateResult
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import com.lanxin.android.builtin.localinference.domain.LocalInferenceProvider
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
import com.lanxin.android.builtin.localinference.domain.NetworkStatusProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.lanxin.android.plugins.chat.data.streamPreparedApiState
import com.lanxin.android.plugins.chat.data.validateResponseInputPartsOrThrow
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryImplTest {

    @Test(expected = IllegalStateException::class)
    fun `blank response input without encodable parts throws`() {
        validateResponseInputPartsOrThrow("", 0, 42)
    }

    @Test
    fun `response input with text does not throw when image encoding fails`() {
        validateResponseInputPartsOrThrow("hello", 0, 42)
    }

    @Test
    fun `response input with encoded image parts does not throw when text is blank`() {
        validateResponseInputPartsOrThrow("", 1, 42)
    }

    @Test
    fun `loading is emitted before expensive request preparation finishes`() = runBlocking {
        val firstState = withTimeout(100) {
            streamPreparedApiState(
                prepare = {
                    Thread.sleep(200)
                },
                stream = {
                    flowOf(ApiState.Success("done"))
                }
            ).first()
        }

        assertEquals(ApiState.Loading, firstState)
    }

    @Test
    fun `groq path uses groq api and emits parsed reasoning`() = runBlocking {
        val groqAPI = FakeGroqAPI(
            flowOf(
                GroqChatCompletionChunk(
                    choices = listOf(
                        GroqChoice(
                            index = 0,
                            delta = GroqDelta(
                                reasoning = "Plan",
                                content = "Answer"
                            )
                        )
                    )
                )
            )
        )
        val openAIAPI = RecordingOpenAIAPI()
        val repository = createRepository(
            groqAPI = groqAPI,
            openAIAPI = openAIAPI
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = groqPlatform(reasoning = true, model = "qwen/qwen3-32b")
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Thinking("Plan"),
                ApiState.Success("Answer"),
                ApiState.Done
            ),
            states
        )
        assertEquals(1, groqAPI.streamCalls)
        assertEquals(0, openAIAPI.streamChatCompletionCalls)
    }

    @Test
    fun `groq raw think fallback populates thinking state`() = runBlocking {
        val groqAPI = FakeGroqAPI(
            flowOf(
                GroqChatCompletionChunk(
                    choices = listOf(
                        GroqChoice(
                            index = 0,
                            delta = GroqDelta(content = "<think>Secret</think>\nVisible")
                        )
                    )
                )
            )
        )
        val repository = createRepository(groqAPI = groqAPI)

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = groqPlatform(reasoning = true, model = "qwen/qwen3-32b")
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Thinking("Secret"),
                ApiState.Success("Visible"),
                ApiState.Done
            ),
            states
        )
    }

    @Test
    fun `groq reasoning disabled hides qwen reasoning`() = runBlocking {
        val groqAPI = FakeGroqAPI(emptyFlow())
        val repository = createRepository(groqAPI = groqAPI)

        repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = groqPlatform(reasoning = false, model = "qwen/qwen3-32b")
        ).toList()

        val request = groqAPI.lastRequest
        assertEquals("hidden", request?.reasoningFormat)
        assertNull(request?.includeReasoning)
        assertNull(request?.reasoningEffort)
    }

    @Test
    fun `groq reasoning disabled turns off gpt oss reasoning`() = runBlocking {
        val groqAPI = FakeGroqAPI(emptyFlow())
        val repository = createRepository(groqAPI = groqAPI)

        repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = groqPlatform(reasoning = false, model = "openai/gpt-oss-20b")
        ).toList()

        val request = groqAPI.lastRequest
        assertNull(request?.reasoningFormat)
        assertEquals(false, request?.includeReasoning)
        assertNull(request?.reasoningEffort)
    }

    @Test
    fun `google request includes configured safety settings`() = runBlocking {
        val googleAPI = FakeGoogleAPI()
        val repository = createRepository(googleAPI = googleAPI)

        repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = googlePlatform()
        ).toList()

        assertEquals(1, googleAPI.streamCalls)
        assertEquals(
            listOf(
                GeminiSafetySettings.HARM_CATEGORY_HARASSMENT to GeminiSafetySettings.BLOCK_LOW_AND_ABOVE,
                GeminiSafetySettings.HARM_CATEGORY_HATE_SPEECH to GeminiSafetySettings.BLOCK_MEDIUM_AND_ABOVE,
                GeminiSafetySettings.HARM_CATEGORY_SEXUALLY_EXPLICIT to GeminiSafetySettings.BLOCK_ONLY_HIGH,
                GeminiSafetySettings.HARM_CATEGORY_DANGEROUS_CONTENT to GeminiSafetySettings.BLOCK_NONE
            ),
            googleAPI.lastRequest?.safetySettings?.map { it.category to it.threshold }
        )
    }

    @Test
    fun `google prompt safety block emits error`() = runBlocking {
        val repository = createRepository(
            googleAPI = FakeGoogleAPI(
                flowOf(
                    GenerateContentResponse(
                        promptFeedback = PromptFeedback(blockReason = "SAFETY")
                    )
                )
            )
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = googlePlatform()
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Error("Gemini safety settings blocked the prompt: SAFETY"),
                ApiState.Done
            ),
            states
        )
    }

    @Test
    fun `google safety finish reason emits error`() = runBlocking {
        val repository = createRepository(
            googleAPI = FakeGoogleAPI(
                flowOf(
                    GenerateContentResponse(
                        candidates = listOf(Candidate(finishReason = "SAFETY"))
                    )
                )
            )
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = googlePlatform()
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Error("Gemini safety settings blocked the response."),
                ApiState.Done
            ),
            states
        )
    }

    @Test
    fun `failed historical turn is excluded from subsequent inline budget checks`() = runBlocking {
        val openAIAPI = RecordingOpenAIAPI()
        val repository = createRepository(openAIAPI = openAIAPI)
        val tempDir = kotlin.io.path.createTempDirectory("context-inline-budget").toFile().apply {
            deleteOnExit()
        }
        val missingAttachmentFile = File(tempDir, "oversized-${UUID.randomUUID()}.png")
        if (missingAttachmentFile.exists()) {
            missingAttachmentFile.delete()
        }
        assertFalse(missingAttachmentFile.exists())
        val failedTurnAttachment = ChatAttachment(
            localFilePath = missingAttachmentFile.absolutePath,
            preparedFilePath = missingAttachmentFile.absolutePath,
            displayName = "oversized.png",
            mimeType = "image/png",
            sizeBytes = 13L * 1024 * 1024
        )
        val customPlatform = customPlatform()

        val states = repository.completeChat(
            userMessages = listOf(
                MessageV2(
                    id = 1,
                    content = "",
                    platformType = null,
                    attachments = listOf(failedTurnAttachment)
                ),
                MessageV2(
                    id = 2,
                    content = "Try again with text only",
                    platformType = null
                )
            ),
            assistantMessages = listOf(
                listOf(
                    MessageV2(
                        id = 11,
                        content = "Error: These images are too large to upload safely on this provider.",
                        platformType = customPlatform.uid
                    )
                ),
                listOf(
                    MessageV2(
                        id = 12,
                        content = "",
                        platformType = customPlatform.uid
                    )
                )
            ),
            platform = customPlatform
        ).toList()

        assertEquals(listOf(ApiState.Loading, ApiState.Done), states)
        assertEquals(1, openAIAPI.streamChatCompletionCalls)
    }


    @Test
    fun `offline with local ready uses local provider and skips cloud`() = runBlocking {
        val openAIAPI = RecordingOpenAIAPI()
        val local = RecordingLocalProvider(listOf(ApiState.Loading, ApiState.Success("local-hi"), ApiState.Done))
        val repository = createRepository(
            openAIAPI = openAIAPI,
            localProvider = local,
            networkAvailable = false,
            localReady = true,
            preferLocal = false
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hello offline", platformType = null)),
            assistantMessages = emptyList(),
            platform = customPlatform()
        ).toList()

        assertEquals(
            listOf(ApiState.Loading, ApiState.Success("local-hi"), ApiState.Done),
            states
        )
        assertEquals(0, openAIAPI.streamChatCompletionCalls)
        assertEquals(1, local.calls)
        assertEquals(true, repository.lastUsedLocal)
        assertEquals("offline_fallback", repository.lastRouteReason)
    }

    @Test
    fun `offline without local ready emits guidance error`() = runBlocking {
        val openAIAPI = RecordingOpenAIAPI()
        val local = RecordingLocalProvider(emptyList())
        val repository = createRepository(
            openAIAPI = openAIAPI,
            localProvider = local,
            networkAvailable = false,
            localReady = false,
            preferLocal = false
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hello", platformType = null)),
            assistantMessages = emptyList(),
            platform = customPlatform()
        ).toList()

        assertEquals(ApiState.Loading, states.first())
        assertTrue(states[1] is ApiState.Error)
        val err = (states[1] as ApiState.Error).message
        assertTrue(err.contains("本地推理") || err.contains("无网络"))
        assertEquals(ApiState.Done, states.last())
        assertEquals(0, openAIAPI.streamChatCompletionCalls)
        assertEquals(0, local.calls)
        assertEquals(false, repository.lastUsedLocal)
    }

    @Test
    fun `online without preferLocal keeps cloud path`() = runBlocking {
        val openAIAPI = RecordingOpenAIAPI()
        val local = RecordingLocalProvider(listOf(ApiState.Success("should-not")))
        val repository = createRepository(
            openAIAPI = openAIAPI,
            localProvider = local,
            networkAvailable = true,
            localReady = true,
            preferLocal = false
        )

        repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = customPlatform()
        ).toList()

        assertEquals(1, openAIAPI.streamChatCompletionCalls)
        assertEquals(0, local.calls)
        assertEquals(false, repository.lastUsedLocal)
        assertEquals("cloud_preferred", repository.lastRouteReason)
    }

    @Test
    fun `preferLocal online uses local when ready`() = runBlocking {
        val openAIAPI = RecordingOpenAIAPI()
        val local = RecordingLocalProvider(listOf(ApiState.Loading, ApiState.Success("prefer"), ApiState.Done))
        val repository = createRepository(
            openAIAPI = openAIAPI,
            localProvider = local,
            networkAvailable = true,
            localReady = true,
            preferLocal = true
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = customPlatform()
        ).toList()

        assertEquals(ApiState.Success("prefer"), states[1])
        assertEquals(0, openAIAPI.streamChatCompletionCalls)
        assertEquals(1, local.calls)
        assertEquals("user_prefer_local", repository.lastRouteReason)
    }


    private fun createRepository(
        groqAPI: GroqAPI = FakeGroqAPI(emptyFlow()),
        openAIAPI: OpenAIAPI = RecordingOpenAIAPI(),
        googleAPI: GoogleAPI = FakeGoogleAPI(),
        lanXinAPI: LanXinAPI = FakeLanXinAPI(),
        localProvider: LocalInferenceProvider? = null,
        networkAvailable: Boolean = true,
        localReady: Boolean = false,
        preferLocal: Boolean = false
    ): ChatRepositoryImpl {
        val coordinator = if (localProvider != null) {
            InferenceRouteCoordinator(
                networkStatusProvider = NetworkStatusProvider { networkAvailable },
                settings = object : LocalInferenceSettings {
                    override suspend fun getConfig() = LocalInferenceConfig(
                        enabled = true,
                        modelPath = "stub://demo-model"
                    )
                    override suspend fun setEnabled(enabled: Boolean) = Unit
                    override suspend fun setModelPath(path: String?) = Unit
                    override suspend fun setMaxTokens(maxTokens: Int) = Unit
                    override suspend fun setTemperature(temperature: Float) = Unit
                    override suspend fun isPreferLocal() = preferLocal
                    override suspend fun setPreferLocal(prefer: Boolean) = Unit
                },
                engine = object : LocalLlmEngine {
                    private val st = MutableStateFlow(
                        if (localReady) LocalEngineState.READY else LocalEngineState.IDLE
                    )
                    override val state: StateFlow<LocalEngineState> = st
                    override val isReady: Boolean get() = localReady
                    override val isAvailable: Boolean get() = true
                    override val lastError: String? = null
                    override suspend fun load(config: LocalInferenceConfig) = localReady
                    override suspend fun unload() = Unit
                    override suspend fun generate(request: LocalGenerateRequest) =
                        LocalGenerateResult("x", isStub = true)
                    override fun stream(request: LocalGenerateRequest) = emptyFlow<String>()
                }
            )
        } else {
            null
        }
        return ChatRepositoryImpl(
            context = ContextWrapper(null),
            chatRoomDao = proxy(),
            messageDao = proxy(),
            chatRoomV2Dao = proxy(),
            messageV2Dao = proxy(),
            chatPlatformModelV2Dao = proxy(),
            settingRepository = proxy(),
            openAIAPI = openAIAPI,
            groqAPI = groqAPI,
            anthropicAPI = FakeAnthropicAPI(),
            googleAPI = googleAPI,
            lanXinAPI = lanXinAPI,
            attachmentUploadCoordinator = AttachmentUploadCoordinator(
                openAIAPI,
                FakeAnthropicAPI(),
                googleAPI
            ),
            contextBuilder = ContextBuilder(),
            localInferenceProvider = localProvider,
            inferenceRouteCoordinator = coordinator,
            networkStatusProvider = if (localProvider != null) {
                NetworkStatusProvider { networkAvailable }
            } else {
                null
            }
        )
    }

    private class RecordingLocalProvider(
        private val states: List<ApiState>
    ) : LocalInferenceProvider {
        var calls: Int = 0
            private set

        override fun canServe(): Boolean = true

        override fun completeAsApiState(prompt: String, systemPrompt: String?): Flow<ApiState> {
            calls += 1
            return flowOf(*states.toTypedArray())
        }
    }

    private fun groqPlatform(reasoning: Boolean, model: String) = PlatformV2(
        uid = "groq-platform",
        name = "Groq",
        compatibleType = ClientType.GROQ,
        apiUrl = "https://api.groq.com/openai/",
        model = model,
        reasoning = reasoning
    )

    private fun googlePlatform() = PlatformV2(
        uid = "google-platform",
        name = "Google",
        compatibleType = ClientType.GOOGLE,
        apiUrl = "https://generativelanguage.googleapis.com",
        model = "gemini-3-pro-preview",
        harassmentSafetyThreshold = GeminiSafetySettings.BLOCK_LOW_AND_ABOVE,
        hateSpeechSafetyThreshold = GeminiSafetySettings.BLOCK_MEDIUM_AND_ABOVE,
        sexuallyExplicitSafetyThreshold = GeminiSafetySettings.BLOCK_ONLY_HIGH,
        dangerousContentSafetyThreshold = GeminiSafetySettings.BLOCK_NONE
    )

    private fun customPlatform() = PlatformV2(
        uid = "custom-platform",
        name = "Custom",
        compatibleType = ClientType.CUSTOM,
        apiUrl = "https://example.com",
        model = "custom-model",
        stream = true
    )

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> proxy(): T {
        val handler = InvocationHandler { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                Unit::class.java -> Unit
                else -> null
            }
        }

        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
            handler
        ) as T
    }

    private class FakeGroqAPI(
        private val chunks: Flow<GroqChatCompletionChunk>
    ) : GroqAPI {
        var streamCalls = 0
        var lastRequest: GroqChatCompletionRequest? = null

        override fun streamChatCompletion(
            request: GroqChatCompletionRequest,
            timeoutSeconds: Int,
            token: String?,
            apiUrl: String
        ): Flow<GroqChatCompletionChunk> {
            streamCalls += 1
            lastRequest = request
            return chunks
        }
    }

    private class RecordingOpenAIAPI : OpenAIAPI {
        var streamChatCompletionCalls = 0

        override fun setToken(token: String?) = Unit

        override fun setAPIUrl(url: String) = Unit

        override fun streamChatCompletion(request: ChatCompletionRequest, timeoutSeconds: Int): Flow<ChatCompletionChunk> {
            streamChatCompletionCalls += 1
            return emptyFlow()
        }

        override fun streamResponses(request: ResponsesRequest, timeoutSeconds: Int): Flow<ResponsesStreamEvent> = emptyFlow()

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String
        ): UploadedProviderFile = UploadedProviderFile(id = "file-uploaded", mimeType = mimeType)

        override suspend fun isFileAvailable(fileId: String): Boolean = false
    }

    private class FakeAnthropicAPI : AnthropicAPI {
        override fun setToken(token: String?) = Unit

        override fun setAPIUrl(url: String) = Unit

        override fun streamChatMessage(messageRequest: MessageRequest, timeoutSeconds: Int): Flow<MessageResponseChunk> = emptyFlow()

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String
        ): UploadedProviderFile = UploadedProviderFile(id = "anthropic-file", mimeType = mimeType)

        override suspend fun isFileAvailable(fileId: String): Boolean = false
    }

    private class FakeGoogleAPI(
        private val chunks: Flow<GenerateContentResponse> = emptyFlow()
    ) : GoogleAPI {
        var streamCalls = 0
        var lastRequest: GenerateContentRequest? = null

        override fun setToken(token: String?) = Unit

        override fun setAPIUrl(url: String) = Unit

        override fun streamGenerateContent(
            request: GenerateContentRequest,
            model: String,
            timeoutSeconds: Int
        ): Flow<GenerateContentResponse> {
            streamCalls += 1
            lastRequest = request
            return chunks
        }

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String
        ): UploadedProviderFile = UploadedProviderFile(id = "google-file", mimeType = mimeType)

        override suspend fun isFileAvailable(fileName: String): Boolean = false
    }

    private class FakeLanXinAPI : LanXinAPI {
        override fun setToken(token: String) = Unit

        override fun setAPIUrl(apiUrl: String) = Unit

        override suspend fun streamChat(
            message: String,
            username: String,
            sessionId: String?,
            timeoutSeconds: Int
        ): Flow<ApiState> = emptyFlow()
    }
}
