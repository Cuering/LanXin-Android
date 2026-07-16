package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.domain.InferenceRouteCoordinator
import com.lanxin.android.builtin.localinference.domain.InferenceRouteTarget
import com.lanxin.android.builtin.localinference.domain.LocalEngineState
import com.lanxin.android.builtin.localinference.domain.LocalGenerateRequest
import com.lanxin.android.builtin.localinference.domain.LocalGenerateResult
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
import com.lanxin.android.builtin.localinference.domain.NetworkStatusProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceRouteCoordinatorTest {

    @Test
    fun `offline and ready selects LOCAL`() = runBlocking {
        val c = coordinator(
            network = false,
            ready = true,
            preferLocal = false,
            enabled = true
        )
        val d = c.decide()
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals("offline_fallback", d.reason)
    }

    @Test
    fun `offline and not ready selects UNAVAILABLE`() = runBlocking {
        val c = coordinator(
            network = false,
            ready = false,
            preferLocal = false,
            enabled = true
        )
        val d = c.decide()
        assertEquals(InferenceRouteTarget.UNAVAILABLE, d.target)
    }

    @Test
    fun `online defaults CLOUD even if ready`() = runBlocking {
        val c = coordinator(
            network = true,
            ready = true,
            preferLocal = false,
            enabled = true
        )
        val d = c.decide()
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
        assertEquals("cloud_preferred", d.reason)
    }

    @Test
    fun `preferLocal and ready selects LOCAL online`() = runBlocking {
        val c = coordinator(
            network = true,
            ready = true,
            preferLocal = true,
            enabled = true
        )
        val d = c.decide()
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals("user_prefer_local", d.reason)
    }

    @Test
    fun `disabled engine never local even preferLocal`() = runBlocking {
        val c = coordinator(
            network = true,
            ready = false,
            preferLocal = true,
            enabled = false
        )
        val d = c.decide()
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
    }

    @Test
    fun `previewLabel includes network and route`() = runBlocking {
        val c = coordinator(
            network = false,
            ready = true,
            preferLocal = false,
            enabled = true
        )
        val label = c.previewLabel()
        assertTrue(label.contains("无网"))
        assertTrue(label.contains("本地"))
    }

    private fun coordinator(
        network: Boolean,
        ready: Boolean,
        preferLocal: Boolean,
        enabled: Boolean
    ): InferenceRouteCoordinator {
        val net = NetworkStatusProvider { network }
        val settings = object : LocalInferenceSettings {
            override suspend fun getConfig() = LocalInferenceConfig(
                enabled = enabled,
                modelPath = if (enabled) "stub://demo-model" else ""
            )

            override suspend fun setEnabled(enabled: Boolean) = Unit
            override suspend fun setModelPath(path: String?) = Unit
            override suspend fun setMaxTokens(maxTokens: Int) = Unit
            override suspend fun setTemperature(temperature: Float) = Unit
            override suspend fun isPreferLocal() = preferLocal
            override suspend fun setPreferLocal(prefer: Boolean) = Unit
        }
        val engine = FakeEngine(ready = ready, enabled = enabled)
        return InferenceRouteCoordinator(net, settings, engine)
    }

    private class FakeEngine(
        private val ready: Boolean,
        private val enabled: Boolean
    ) : LocalLlmEngine {
        private val _state = MutableStateFlow(
            when {
                ready -> LocalEngineState.READY
                enabled -> LocalEngineState.IDLE
                else -> LocalEngineState.DISABLED
            }
        )
        override val state: StateFlow<LocalEngineState> = _state
        override val isReady: Boolean get() = ready
        override val isAvailable: Boolean get() = enabled
        override val lastError: String? = null
        override suspend fun load(config: LocalInferenceConfig): Boolean = ready
        override suspend fun unload() = Unit
        override suspend fun generate(request: LocalGenerateRequest): LocalGenerateResult =
            LocalGenerateResult("x", isStub = true)
        override fun stream(request: LocalGenerateRequest): Flow<String> = emptyFlow()
    }
}
