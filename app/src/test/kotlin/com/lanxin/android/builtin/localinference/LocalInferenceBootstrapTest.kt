package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.data.MnnNativeBridge
import com.lanxin.android.builtin.localinference.data.StubLocalLlmEngine
import com.lanxin.android.builtin.localinference.domain.LocalInferenceBootstrap
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #120 一键开启 / 懒加载：有路径自动就绪；无路径友好失败；重试逻辑。
 */
class LocalInferenceBootstrapTest {

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
    fun `with path auto enables and loads to READY`() = runBlocking {
        val engine = StubLocalLlmEngine(MnnNativeBridge())
        val settings = FakeSettings(
            LocalInferenceConfig(enabled = false, modelPath = "stub://demo-model")
        )
        assertFalse(engine.isReady)
        val bootstrap = LocalInferenceBootstrap(settings, engine)
        val result = bootstrap.ensureReady(enableIfNeeded = true)
        assertEquals(LocalInferenceBootstrap.Status.READY, result.status)
        assertTrue(result.isReady)
        assertTrue(engine.isReady)
        assertTrue(settings.config.enabled)
    }

    @Test
    fun `no path returns NEED_MODEL_PATH without loading`() = runBlocking {
        val engine = StubLocalLlmEngine(MnnNativeBridge())
        val settings = FakeSettings(
            LocalInferenceConfig(enabled = false, modelPath = "")
        )
        val bootstrap = LocalInferenceBootstrap(settings, engine)
        val result = bootstrap.ensureReady(enableIfNeeded = true)
        assertEquals(LocalInferenceBootstrap.Status.NEED_MODEL_PATH, result.status)
        assertFalse(result.isReady)
        assertFalse(engine.isReady)
        assertFalse(settings.config.enabled)
        assertTrue(result.message.contains("文件夹") || result.message.contains("模型"))
    }

    @Test
    fun `retry after path set becomes READY`() = runBlocking {
        val engine = StubLocalLlmEngine(MnnNativeBridge())
        val settings = FakeSettings(
            LocalInferenceConfig(enabled = false, modelPath = "")
        )
        val bootstrap = LocalInferenceBootstrap(settings, engine)

        val first = bootstrap.ensureReady()
        assertEquals(LocalInferenceBootstrap.Status.NEED_MODEL_PATH, first.status)

        settings.setModelPath("stub://demo-model")
        val second = bootstrap.ensureReady()
        assertEquals(LocalInferenceBootstrap.Status.READY, second.status)
        assertTrue(engine.isReady)
        assertTrue(settings.config.enabled)
    }

    @Test
    fun `already ready short-circuits`() = runBlocking {
        val engine = StubLocalLlmEngine(MnnNativeBridge())
        val settings = FakeSettings(
            LocalInferenceConfig(enabled = true, modelPath = "stub://demo-model")
        )
        assertTrue(engine.load(settings.getConfig()))
        val bootstrap = LocalInferenceBootstrap(settings, engine)
        val result = bootstrap.ensureReady()
        assertEquals(LocalInferenceBootstrap.Status.READY, result.status)
        assertEquals(LocalInferenceBootstrap.MSG_READY, result.message)
    }

    @Test
    fun `enableIfNeeded false with disabled does not force enable`() = runBlocking {
        val engine = StubLocalLlmEngine(MnnNativeBridge())
        val settings = FakeSettings(
            LocalInferenceConfig(enabled = false, modelPath = "stub://demo-model")
        )
        val bootstrap = LocalInferenceBootstrap(settings, engine)
        val result = bootstrap.ensureReady(enableIfNeeded = false)
        assertEquals(LocalInferenceBootstrap.Status.LOAD_FAILED, result.status)
        assertFalse(engine.isReady)
        assertFalse(settings.config.enabled)
    }

    @Test
    fun `forceLocal unavailable message is short and mentions retry`() {
        val msg = LocalInferenceBootstrap.FORCE_LOCAL_UNAVAILABLE_SHORT
        assertTrue(msg.length < 80)
        assertTrue(msg.contains("重试") || msg.contains("导入"))
        assertFalse(msg.contains("智能能力 → 本地推理"))
    }
}
