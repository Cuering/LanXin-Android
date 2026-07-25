package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.localinference.domain.LocalEngineState
import com.lanxin.android.builtin.localinference.domain.LocalGenerateRequest
import com.lanxin.android.builtin.localinference.domain.LocalGenerateResult
import com.lanxin.android.builtin.localinference.domain.LocalInferenceBootstrap
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import com.lanxin.android.builtin.localinference.domain.LocalChatMessage
import com.lanxin.android.builtin.localinference.domain.LocalInferenceProvider
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
import com.lanxin.android.builtin.pet.domain.LocalAwarePetChatResponder
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
            stub = StubPetChatResponder()
        )
        val out = responder.respond("你好")
        // stub 问候池：不回声用户原话，只出短答 + mood 标签
        assertTrue(out.contains("[[mood="))
        assertTrue(
            out.contains("你好") || out.contains("嗨") || out.contains("看到你") ||
                out.contains("我在") || out.contains("嗯嗯")
        )
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
            stub = StubPetChatResponder()
        )
        val out = responder.respond("你好")
        assertEquals(1, provider.calls)
        // 对齐 MNNChat：null system、skip 约束、更大 maxTokens、原文出口
        assertEquals(256, provider.lastMaxTokens)
        assertEquals(null, provider.lastSystem)
        assertTrue(provider.lastSkipConstraint)
        assertTrue(provider.lastReuseKv)
        assertEquals(0, provider.lastHistorySize)
        assertTrue(out.contains("你好呀"))
        // bare path 不再激进剥「让我分析」——Provider 侧 lightClean 只剥 think/tags
        // 本测试仍走 Fake Provider 直接 Success，Responder 只 lightClean
        assertTrue(out.contains("[[mood="))
    }

    @Test
    fun `quality gate rejects garbage score reply`() = runBlocking {
        val settings = FakeLocalSettings(
            LocalInferenceConfig(enabled = true, modelPath = "/models/x")
        )
        val engine = FakeEngine(ready = true)
        val bootstrap = LocalInferenceBootstrap(settings, engine)
        val provider = RecordingProvider(
            canServe = true,
            states = listOf(
                ApiState.Loading,
                ApiState.Success("（0-5 分） 4 你好！"),
                ApiState.Done
            )
        )
        val responder = LocalAwarePetChatResponder(
            localProvider = provider,
            localSettings = settings,
            bootstrap = bootstrap,
            stub = StubPetChatResponder()
        )
        val out = responder.respond("你喜欢什么？")
        // 垃圾回复被闸门丢弃，回 stub（非分数串）
        assertFalse(out.contains("0-5"))
        assertFalse(out.contains("分）"))
        assertTrue(out.contains("[[mood="))
    }

    @Test
    fun `quality gate rejects off-topic name answer`() = runBlocking {
        val settings = FakeLocalSettings(
            LocalInferenceConfig(enabled = true, modelPath = "/models/x")
        )
        val engine = FakeEngine(ready = true)
        val bootstrap = LocalInferenceBootstrap(settings, engine)
        val provider = RecordingProvider(
            canServe = true,
            states = listOf(
                ApiState.Loading,
                ApiState.Success("我帮你回答问题时要表现出耐心。"),
                ApiState.Done
            )
        )
        val responder = LocalAwarePetChatResponder(
            localProvider = provider,
            localSettings = settings,
            bootstrap = bootstrap,
            stub = StubPetChatResponder()
        )
        val out = responder.respond("你叫什么名字？")
        assertTrue(out.contains("兰心"))
        assertFalse(out.contains("表现出耐心"))
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
            stub = StubPetChatResponder()
        )
        val out = responder.respond("在吗")
        assertTrue(out.contains("[[mood="))
        assertTrue(out.isNotBlank())
        assertFalse(out.contains("boom"))
        // 不再回声用户原话
        assertFalse(out.contains("在吗") && out.contains("听到了"))
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

    @Test
    fun `second turn passes prior history and reuseKv`() = runBlocking {
        val settings = FakeLocalSettings(
            LocalInferenceConfig(enabled = true, modelPath = "stub://ok")
        )
        val engine = FakeEngine(ready = true)
        val bootstrap = LocalInferenceBootstrap(settings, engine)
        val provider = RecordingProvider(
            canServe = true,
            states = listOf(ApiState.Success("好呀～"))
        )
        val responder = LocalAwarePetChatResponder(
            localProvider = provider,
            localSettings = settings,
            bootstrap = bootstrap,
            stub = StubPetChatResponder()
        )
        responder.respond("你好")
        assertEquals(1, provider.calls)
        assertEquals(0, provider.lastHistorySize)
        responder.respond("你叫什么")
        assertEquals(2, provider.calls)
        // 第一轮成功后 history = user+assistant
        assertEquals(2, provider.lastHistorySize)
        assertTrue(provider.lastReuseKv)
        assertEquals(2, responder.historySizeForTest())
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
        var lastSystem: String? = "unset"
            private set
        var lastSkipConstraint: Boolean = false
            private set

        override fun canServe(): Boolean = canServe

        var lastReuseKv: Boolean = false
            private set
        var lastHistorySize: Int = -1
            private set

        override fun completeAsApiState(
            prompt: String,
            systemPrompt: String?,
            maxTokens: Int?,
            history: List<LocalChatMessage>,
            skipOutputConstraint: Boolean,
            reuseKv: Boolean
        ): Flow<ApiState> {
            calls += 1
            lastMaxTokens = maxTokens
            lastSystem = systemPrompt
            lastSkipConstraint = skipOutputConstraint
            lastReuseKv = reuseKv
            lastHistorySize = history.size
            return flow {
                for (s in states) emit(s)
            }
        }
    }
}
