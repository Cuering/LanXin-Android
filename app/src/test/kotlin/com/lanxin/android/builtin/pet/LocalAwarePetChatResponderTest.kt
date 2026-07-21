package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.localinference.domain.LocalEngineState
import com.lanxin.android.builtin.localinference.domain.LocalGenerateRequest
import com.lanxin.android.builtin.localinference.domain.LocalGenerateResult
import com.lanxin.android.builtin.localinference.domain.LocalInferenceBootstrap
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import com.lanxin.android.builtin.localinference.domain.LocalInferenceProvider
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
import com.lanxin.android.builtin.pet.domain.CompanionMemoryEnricher
import com.lanxin.android.builtin.pet.domain.LocalAwarePetChatResponder
import com.lanxin.android.builtin.pet.domain.NoOpCompanionMemoryEnricher
import com.lanxin.android.builtin.pet.domain.StubPetChatResponder
import com.lanxin.android.data.dto.ApiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAwarePetChatResponderTest {

    @Test
    fun `falls back to stub when no model path`() = runBlocking {
        val settings = FakeLocalSettings(LocalInferenceConfig(enabled = false, modelPath = ""))
        val engine = FakeEngine(ready = false)
        val bootstrap = LocalInferenceBootstrap(settings, engine)
        val provider = RecordingProvider(canServe = false)
        val responder = LocalAwarePetChatResponder(
            localProvider = provider,
            localSettings = settings,
            bootstrap = bootstrap,
            memoryEnricher = NoOpCompanionMemoryEnricher,
            stub = StubPetChatResponder()
        )
        val out = responder.respond("你好")
        assertTrue(out.contains("听到了") || out.contains("你好"))
        assertEquals(0, provider.calls)
    }

    @Test
    fun `uses local when ready and sanitizes`() = runBlocking {
        val settings = FakeLocalSettings(
            LocalInferenceConfig(enabled = true, modelPath = "/models/x")
        )
        val engine = FakeEngine(ready = true)
        val bootstrap = LocalInferenceBootstrap(settings, engine)
        val dirty = """
            让我分析一下这个问题：
            ## 回应建议
            你好呀哥哥～
            ---
            **分析：** 这是问候
        """.trimIndent()
        val provider = RecordingProvider(
            canServe = true,
            states = listOf(
                ApiState.Loading,
                ApiState.Success(dirty),
                ApiState.Done
            )
        )
        val responder = LocalAwarePetChatResponder(
            localProvider = provider,
            localSettings = settings,
            bootstrap = bootstrap,
            memoryEnricher = NoOpCompanionMemoryEnricher,
            stub = StubPetChatResponder()
        )
        val out = responder.respond("你好")
        assertEquals(1, provider.calls)
        assertEquals(LocalAwarePetChatResponder.COMPANION_MAX_TOKENS, provider.lastMaxTokens)
        assertTrue(out.contains("你好呀"))
        assertFalse(out.contains("让我分析"))
        assertFalse(out.contains("**分析**"))
        assertTrue(out.contains("[[mood="))
    }

    @Test
    fun `local error falls back to stub`() = runBlocking {
        val settings = FakeLocalSettings(
            LocalInferenceConfig(enabled = true, modelPath = "/models/x")
        )
        val engine = FakeEngine(ready = true)
        val bootstrap = LocalInferenceBootstrap(settings, engine)
        val provider = RecordingProvider(
            canServe = true,
            states = listOf(ApiState.Error("boom"), ApiState.Done)
        )
        val responder = LocalAwarePetChatResponder(
            localProvider = provider,
            localSettings = settings,
            bootstrap = bootstrap,
            memoryEnricher = NoOpCompanionMemoryEnricher,
            stub = StubPetChatResponder()
        )
        val out = responder.respond("在吗")
        assertTrue(out.contains("听到了") || out.contains("在吗"))
        assertFalse(out.contains("boom"))
    }

    @Test
    fun `enriches prompt with companion memory before local generate`() = runBlocking {
        val settings = FakeLocalSettings(
            LocalInferenceConfig(enabled = true, modelPath = "/models/x")
        )
        val engine = FakeEngine(ready = true)
        val bootstrap = LocalInferenceBootstrap(settings, engine)
        val provider = RecordingProvider(
            canServe = true,
            states = listOf(ApiState.Success("记得呀"), ApiState.Done)
        )
        val enricher = CompanionMemoryEnricher { text ->
            "[精简记忆]\n- 哥哥喜欢猫\n[记忆结束·勿朗读标签]\n\n$text"
        }
        val responder = LocalAwarePetChatResponder(
            localProvider = provider,
            localSettings = settings,
            bootstrap = bootstrap,
            memoryEnricher = enricher,
            stub = StubPetChatResponder()
        )
        val out = responder.respond("我喜欢什么")
        assertEquals(1, provider.calls)
        assertTrue(provider.lastPrompt!!.contains("精简记忆"))
        assertTrue(provider.lastPrompt!!.contains("我喜欢什么"))
        assertTrue(out.contains("记得呀"))
    }

    @Test
    fun `companion max tokens is short for latency`() {
        assertTrue(LocalAwarePetChatResponder.COMPANION_MAX_TOKENS <= 64)
        assertTrue(LocalAwarePetChatResponder.COMPANION_TIMEOUT_MS <= 20_000L)
    }

    private class FakeLocalSettings(
        private var config: LocalInferenceConfig
    ) : LocalInferenceSettings {
        override suspend fun getConfig(): LocalInferenceConfig = config
        override suspend fun setEnabled(enabled: Boolean) {
            config = config.copy(enabled = enabled)
        }
        override suspend fun setModelPath(path: String?) {
            config = config.copy(modelPath = path.orEmpty())
        }
        override suspend fun setMaxTokens(maxTokens: Int) {
            config = config.copy(maxTokens = maxTokens)
        }
        override suspend fun setTemperature(temperature: Float) {
            config = config.copy(temperature = temperature)
        }
        override suspend fun setShowThinking(show: Boolean) {
            config = config.copy(showThinking = show)
        }
        override suspend fun setContextWindowTokens(tokens: Int) {
            config = config.copy(contextWindowTokens = tokens)
        }
        override suspend fun isPreferLocal(): Boolean = false
        override suspend fun setPreferLocal(prefer: Boolean) = Unit
    }

    private class FakeEngine(
        ready: Boolean
    ) : LocalLlmEngine {
        private val _state = MutableStateFlow(
            if (ready) LocalEngineState.READY else LocalEngineState.IDLE
        )
        override val state: StateFlow<LocalEngineState> = _state.asStateFlow()
        override val isReady: Boolean get() = _state.value == LocalEngineState.READY
        override val isAvailable: Boolean = true
        override val lastError: String? = null
        override suspend fun load(config: LocalInferenceConfig): Boolean {
            _state.value = LocalEngineState.READY
            return true
        }
        override suspend fun unload() {
            _state.value = LocalEngineState.IDLE
        }
        override suspend fun generate(request: LocalGenerateRequest): LocalGenerateResult =
            LocalGenerateResult(text = "stub")
        override fun stream(request: LocalGenerateRequest): Flow<String> = flowOf("")
    }

    private class RecordingProvider(
        private val canServe: Boolean,
        private val states: List<ApiState> = emptyList()
    ) : LocalInferenceProvider {
        var calls: Int = 0
            private set
        var lastMaxTokens: Int? = null
            private set
        var lastPrompt: String? = null
            private set

        override fun canServe(): Boolean = canServe

        override fun completeAsApiState(
            prompt: String,
            systemPrompt: String?,
            maxTokens: Int?
        ): Flow<ApiState> {
            calls += 1
            lastMaxTokens = maxTokens
            lastPrompt = prompt
            return flow {
                for (s in states) emit(s)
            }
        }
    }
}
