package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.localinference.domain.LocalEngineState
import com.lanxin.android.builtin.localinference.domain.LocalGenerateRequest
import com.lanxin.android.builtin.localinference.domain.LocalGenerateResult
import com.lanxin.android.builtin.localinference.domain.LocalInferenceBootstrap
import com.lanxin.android.builtin.localinference.domain.LocalInferenceDiagnostics
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
            stub = StubPetChatResponder(),
            diagnostics = LocalInferenceDiagnostics(),
            engine = engine
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
            stub = StubPetChatResponder(),
            diagnostics = LocalInferenceDiagnostics(),
            engine = engine
        )
        val out = responder.respond("你好")
        assertEquals(1, provider.calls)
        // 小模型护栏：短 system + 48 maxTokens；约束已内嵌 system，skip 外层叠加
        assertEquals(48, provider.lastMaxTokens)
        assertTrue(provider.lastSystem!!.contains("兰心"))
        assertTrue(provider.lastSkipConstraint)
        // 弱模型全程 reuseKv=false
        assertFalse(provider.lastReuseKv)
        assertEquals(0, provider.lastHistorySize)
        assertTrue(out.contains("你好呀"))
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
            stub = StubPetChatResponder(),
            diagnostics = LocalInferenceDiagnostics(),
            engine = engine
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
            stub = StubPetChatResponder(),
            diagnostics = LocalInferenceDiagnostics(),
            engine = engine
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
            stub = StubPetChatResponder(),
            diagnostics = LocalInferenceDiagnostics(),
            engine = engine
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
    fun `second turn passes prior history without reuseKv`() = runBlocking {
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
            stub = StubPetChatResponder(),
            diagnostics = LocalInferenceDiagnostics(),
            engine = engine
        )
        val out1 = responder.respond("你好")
        assertTrue("first turn should use local: $out1", out1.contains("好呀") || out1.contains("好"))
        assertEquals(1, provider.calls)
        assertEquals(0, provider.lastHistorySize)
        assertFalse(provider.lastReuseKv)
        // 第二轮用非身份问句，避免质量闸门因「你叫什么」拒掉短答「好呀」
        val out2 = responder.respond("今天开心吗")
        assertEquals(2, provider.calls)
        // 第二轮仍带清洗后 history，但 reuseKv 恒 false
        assertEquals(2, provider.lastHistorySize)
        assertFalse(provider.lastReuseKv)
        assertEquals(4, responder.historySizeForTest())
        assertTrue(out2.isNotBlank())
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
        override suspend fun reset() {}
    }

    
    @Test
    fun `gate rejects numeric range gibberish and cyrillic`() {
        assertFalse(
            LocalAwarePetChatResponder.isAcceptableReply(
                "你好",
                "рассу, 24-26, 32-34, 36-38, 44-46, 56-68"
            )
        )
        assertFalse(
            LocalAwarePetChatResponder.isAcceptableReply(
                "你是谁",
                "时间可以是24-26，28-30，32-34，36-38，44-46"
            )
        )
        assertTrue(
            LocalAwarePetChatResponder.isAcceptableReply("你好", "你好呀，我是兰心。")
        )
    }

    
    @Test
    fun `gate rejects reverse-question garbage`() {
        assertFalse(
            LocalAwarePetChatResponder.isAcceptableReply("你是谁？", "你叫什么名字？")
        )
        assertFalse(
            LocalAwarePetChatResponder.isAcceptableReply("你能回答我吗？", "你有什么技能？")
        )
        assertFalse(
            LocalAwarePetChatResponder.isAcceptableReply("你好", "吗？")
        )
        assertTrue(
            LocalAwarePetChatResponder.isAcceptableReply("你叫什么名字？", "你好，我是你的桌宠，兰心。")
        )
    }

    
    @Test
    fun `gate rejects role-flip goodbye`() {
        assertFalse(
            LocalAwarePetChatResponder.isAcceptableReply("今天开心吗？", "兰心，再见！")
        )
        assertFalse(
            LocalAwarePetChatResponder.isAcceptableReply("兰心早上好", "早上好兰心！")
        )
        assertFalse(
            LocalAwarePetChatResponder.isAcceptableReply("你是兰心啊", "兰心，祝你有个好梦。")
        )
        assertTrue(
            LocalAwarePetChatResponder.isAcceptableReply("今天开心吗？", "开心呀，有你在就很好。")
        )
    }

    @Test
    fun `pick prefers first-person over role-flip`() {
        val raw = "开心哦，你呢？兰心！ 今天有什么计划吗？兰心，再见！再见！"
        val picked = LocalAwarePetChatResponder.pickCompanionUtterance(raw)
        assertFalse(picked.contains("再见"))
        assertFalse(picked.startsWith("兰心"))
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
