package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.data.DefaultLocalInferenceProvider
import com.lanxin.android.builtin.localinference.data.MnnNativeBridge
import com.lanxin.android.builtin.localinference.data.StubLocalLlmEngine
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.data.dto.ApiState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地脑 Provider：开关默认关；打开且路径就绪时 auto-load；路径空有明确错误。
 */
class DefaultLocalInferenceProviderTest {

    private class FakeSettings(
        @Volatile var config: LocalInferenceConfig
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
        override suspend fun setContextWindowTokens(tokens: Int) {
            config = config.copy(contextWindowTokens = tokens)
        }
        override suspend fun setTemperature(temperature: Float) {
            config = config.copy(temperature = temperature)
        }
        override suspend fun setShowThinking(show: Boolean) {
            config = config.copy(showThinking = show)
        }
        override suspend fun isPreferLocal(): Boolean = false
        override suspend fun setPreferLocal(prefer: Boolean) = Unit
    }

    @Test
    fun `default config keeps local brain off`() {
        val c = LocalInferenceConfig()
        assertFalse("本地脑开关必须默认 OFF", c.enabled)
        assertTrue(c.modelPath.isEmpty())
    }

    @Test
    fun `completeAsApiState errors when disabled`() = runBlocking {
        val engine = StubLocalLlmEngine(MnnNativeBridge())
        val settings = FakeSettings(
            LocalInferenceConfig(enabled = false, modelPath = "stub://demo")
        )
        val provider = DefaultLocalInferenceProvider(engine, settings)
        val states = provider.completeAsApiState("hi").toList()
        val err = states.filterIsInstance<ApiState.Error>().first()
        assertTrue(err.message.contains("未启用"))
        assertFalse(engine.isReady)
    }

    @Test
    fun `completeAsApiState errors when path empty`() = runBlocking {
        val engine = StubLocalLlmEngine(MnnNativeBridge())
        val settings = FakeSettings(
            LocalInferenceConfig(enabled = true, modelPath = "")
        )
        val provider = DefaultLocalInferenceProvider(engine, settings)
        val states = provider.completeAsApiState("hi").toList()
        val err = states.filterIsInstance<ApiState.Error>().first()
        assertTrue(err.message.contains("路径为空"))
        assertFalse(engine.isReady)
    }

    @Test
    fun `completeAsApiState autoLoads when enabled with path`() = runBlocking {
        val engine = StubLocalLlmEngine(MnnNativeBridge())
        val settings = FakeSettings(
            LocalInferenceConfig(enabled = true, modelPath = "stub://demo-model")
        )
        assertFalse(engine.isReady)
        val provider = DefaultLocalInferenceProvider(engine, settings)
        val states = provider.completeAsApiState("hello").toList()
        assertTrue(engine.isReady)
        assertTrue(states.any { it is ApiState.Success })
        assertTrue(states.any { it is ApiState.Done })
        assertFalse(states.any { it is ApiState.Error })
    }

    @Test
    fun `showThinking defaults false and dirty prompt still yields clean success`() = runBlocking {
        val engine = StubLocalLlmEngine(MnnNativeBridge())
        val settings = FakeSettings(
            LocalInferenceConfig(
                enabled = true,
                modelPath = "stub://demo-model",
                showThinking = false
            )
        )
        val provider = DefaultLocalInferenceProvider(engine, settings)
        // stub 会 echo prompt；即使 prompt 含标签，出口清洗后不应把 [[ 泄漏到 Success
        val dirty = "你好[[mood=joy]]"
        val states = provider.completeAsApiState(dirty).toList()
        val success = states.filterIsInstance<ApiState.Success>()
        assertTrue(success.isNotEmpty())
        success.forEach { s ->
            assertFalse(s.textChunk.contains("[["))
            assertFalse(s.textChunk.contains("</think>", ignoreCase = true))
        }
        assertFalse(states.any { it is ApiState.Thinking })
    }
}
