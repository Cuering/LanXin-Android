package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.domain.InferenceRouteCoordinator
import com.lanxin.android.builtin.localinference.domain.InferenceRouteTarget
import com.lanxin.android.builtin.localinference.domain.LocalEngineState
import com.lanxin.android.builtin.localinference.domain.LocalGenerateRequest
import com.lanxin.android.builtin.localinference.domain.LocalGenerateResult
import com.lanxin.android.builtin.localinference.domain.LocalInferenceBootstrap
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
import com.lanxin.android.builtin.localinference.domain.NetworkStatusProvider
import com.lanxin.android.builtin.localinference.domain.RouteReason
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
        assertEquals(RouteReason.OFFLINE_LOCAL, d.reason)
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
        assertEquals(RouteReason.OFFLINE_LOCAL_UNAVAILABLE, d.reason)
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
        assertEquals(RouteReason.DEFAULT_CLOUD, d.reason)
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
        assertEquals(RouteReason.PREFER_LOCAL, d.reason)
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
    fun `needsTools forces cloud when online`() = runBlocking {
        val c = coordinator(
            network = true,
            ready = true,
            preferLocal = true,
            enabled = true
        )
        val d = c.decide(needsTools = true)
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
        assertEquals(RouteReason.NEED_TOOLS_CLOUD, d.reason)
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
        assertTrue(label.contains(RouteReason.OFFLINE_LOCAL) || label.contains("路由="))
    }

    @Test
    fun `forceLocal with path lazy-loads then selects LOCAL`() = runBlocking {
        val engine = LazyFakeEngine(startReady = false, loadSucceeds = true)
        val settings = MutableSettings(
            LocalInferenceConfig(enabled = false, modelPath = "stub://demo-model")
        )
        val c = InferenceRouteCoordinator(
            networkStatusProvider = NetworkStatusProvider { true },
            settings = settings,
            engine = engine,
            bootstrap = LocalInferenceBootstrap(settings, engine)
        )
        val d = c.decide(forceLocal = true)
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals(RouteReason.FORCE_LOCAL, d.reason)
        assertTrue(engine.isReady)
        assertTrue(settings.config.enabled)
    }

    @Test
    fun `forceLocal without path stays UNAVAILABLE`() = runBlocking {
        val engine = LazyFakeEngine(startReady = false, loadSucceeds = true)
        val settings = MutableSettings(
            LocalInferenceConfig(enabled = false, modelPath = "")
        )
        val c = InferenceRouteCoordinator(
            networkStatusProvider = NetworkStatusProvider { true },
            settings = settings,
            engine = engine,
            bootstrap = LocalInferenceBootstrap(settings, engine)
        )
        val d = c.decide(forceLocal = true)
        assertEquals(InferenceRouteTarget.UNAVAILABLE, d.target)
        assertEquals(RouteReason.FORCE_LOCAL_UNAVAILABLE, d.reason)
        assertTrue(!engine.isReady)
    }

    private fun coordinator(
        network: Boolean,
        ready: Boolean,
        preferLocal: Boolean,
        enabled: Boolean
    ): InferenceRouteCoordinator {
        val net = NetworkStatusProvider { network }
        val settings = MutableSettings(
            LocalInferenceConfig(
                enabled = enabled,
                modelPath = if (enabled) "stub://demo-model" else ""
            ),
            preferLocal = preferLocal
        )
        val engine = LazyFakeEngine(startReady = ready, loadSucceeds = ready)
        return InferenceRouteCoordinator(
            networkStatusProvider = net,
            settings = settings,
            engine = engine,
            bootstrap = LocalInferenceBootstrap(settings, engine)
        )
    }

    private class MutableSettings(
        @Volatile var config: LocalInferenceConfig,
        private val preferLocal: Boolean = false
    ) : LocalInferenceSettings {
        override suspend fun getConfig() = config
        override suspend fun setEnabled(enabled: Boolean) {
            config = config.copy(enabled = enabled)
        }
        override suspend fun setModelPath(path: String?) {
            config = config.copy(modelPath = path.orEmpty())
        }
        override suspend fun setMaxTokens(maxTokens: Int) = Unit
        override suspend fun setContextWindowTokens(tokens: Int) = Unit
        override suspend fun setTemperature(temperature: Float) = Unit
        override suspend fun setShowThinking(show: Boolean) = Unit
        override suspend fun isPreferLocal() = preferLocal
        override suspend fun setPreferLocal(prefer: Boolean) = Unit
    }

    private class LazyFakeEngine(
        startReady: Boolean,
        private val loadSucceeds: Boolean
    ) : LocalLlmEngine {
        private val _state = MutableStateFlow(
            if (startReady) LocalEngineState.READY else LocalEngineState.IDLE
        )
        override val state: StateFlow<LocalEngineState> = _state
        override val isReady: Boolean get() = _state.value == LocalEngineState.READY
        override val isAvailable: Boolean get() = true
        override val lastError: String? = null
        override suspend fun load(config: LocalInferenceConfig): Boolean {
            if (!config.enabled || config.modelPath.isBlank()) {
                _state.value = LocalEngineState.ERROR
                return false
            }
            return if (loadSucceeds) {
                _state.value = LocalEngineState.READY
                true
            } else {
                _state.value = LocalEngineState.ERROR
                false
            }
        }
        override suspend fun unload() {
            _state.value = LocalEngineState.IDLE
        }
        override suspend fun generate(request: LocalGenerateRequest): LocalGenerateResult =
            LocalGenerateResult("x", isStub = true)
        override fun stream(request: LocalGenerateRequest): Flow<String> = emptyFlow()
    }
}
