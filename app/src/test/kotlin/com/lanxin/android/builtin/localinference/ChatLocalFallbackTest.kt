package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.domain.ChatLocalFallback
import com.lanxin.android.builtin.localinference.domain.InferenceRouteCoordinator
import com.lanxin.android.builtin.localinference.domain.InferenceRouteDecision
import com.lanxin.android.builtin.localinference.domain.InferenceRouteSelector
import com.lanxin.android.builtin.localinference.domain.InferenceRouteTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6.2 路由矩阵 + Chat fallback 辅助逻辑。
 */
class ChatLocalFallbackTest {

    @Test
    fun `shouldUseLocal only for LOCAL target`() {
        assertTrue(
            ChatLocalFallback.shouldUseLocal(
                InferenceRouteDecision(InferenceRouteTarget.LOCAL, "offline_fallback")
            )
        )
        assertFalse(
            ChatLocalFallback.shouldUseLocal(
                InferenceRouteDecision(InferenceRouteTarget.CLOUD, "cloud_preferred")
            )
        )
        assertFalse(
            ChatLocalFallback.shouldUseLocal(
                InferenceRouteDecision(InferenceRouteTarget.UNAVAILABLE, "no_provider")
            )
        )
    }

    @Test
    fun `shouldEmitUnavailable only for UNAVAILABLE`() {
        assertTrue(
            ChatLocalFallback.shouldEmitUnavailable(
                InferenceRouteDecision(InferenceRouteTarget.UNAVAILABLE, "no_provider")
            )
        )
        assertFalse(
            ChatLocalFallback.shouldEmitUnavailable(
                InferenceRouteDecision(InferenceRouteTarget.LOCAL, "offline_fallback")
            )
        )
    }

    @Test
    fun `extractPrompt takes last non-blank user text`() {
        assertEquals(
            "hi",
            ChatLocalFallback.extractPrompt(listOf("a", "", "hi"))
        )
        assertEquals("", ChatLocalFallback.extractPrompt(emptyList()))
    }

    @Test
    fun `unavailableMessage guides offline when no network`() {
        val msg = ChatLocalFallback.unavailableMessage(
            InferenceRouteDecision(InferenceRouteTarget.UNAVAILABLE, "no_provider"),
            networkAvailable = false
        )
        assertEquals(InferenceRouteCoordinator.OFFLINE_LOCAL_UNAVAILABLE_MESSAGE, msg)
        assertTrue(msg.contains("本地推理"))
    }

    @Test
    fun `route matrix offline ready uses local`() {
        val d = InferenceRouteSelector.select(
            preferLocal = false,
            localAvailable = true,
            cloudAvailable = false,
            networkAvailable = false
        )
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertTrue(ChatLocalFallback.shouldUseLocal(d))
    }

    @Test
    fun `route matrix offline not ready unavailable`() {
        val d = InferenceRouteSelector.select(
            preferLocal = false,
            localAvailable = false,
            cloudAvailable = false,
            networkAvailable = false
        )
        assertEquals(InferenceRouteTarget.UNAVAILABLE, d.target)
        assertTrue(ChatLocalFallback.shouldEmitUnavailable(d))
    }

    @Test
    fun `route matrix online defaults cloud`() {
        val d = InferenceRouteSelector.select(
            preferLocal = false,
            localAvailable = true,
            cloudAvailable = true,
            networkAvailable = true
        )
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
        assertFalse(ChatLocalFallback.shouldUseLocal(d))
    }

    @Test
    fun `route matrix preferLocal online uses local when ready`() {
        val d = InferenceRouteSelector.select(
            preferLocal = true,
            localAvailable = true,
            cloudAvailable = true,
            networkAvailable = true
        )
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals("user_prefer_local", d.reason)
    }

    @Test
    fun `route matrix enabled off means not ready never local`() {
        // localAvailable=false 模拟开关关 / 未 load
        val offline = InferenceRouteSelector.select(
            preferLocal = true,
            localAvailable = false,
            cloudAvailable = false,
            networkAvailable = false
        )
        assertEquals(InferenceRouteTarget.UNAVAILABLE, offline.target)

        val online = InferenceRouteSelector.select(
            preferLocal = true,
            localAvailable = false,
            cloudAvailable = true,
            networkAvailable = true
        )
        assertEquals(InferenceRouteTarget.CLOUD, online.target)
    }

    @Test
    fun `isLocalGeneration matches LOCAL target`() {
        assertTrue(
            ChatLocalFallback.isLocalGeneration(
                InferenceRouteDecision(InferenceRouteTarget.LOCAL, "offline_fallback")
            )
        )
    }
}
