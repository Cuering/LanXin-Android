package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.domain.ChatLocalFallback
import com.lanxin.android.builtin.localinference.domain.ChatRouter
import com.lanxin.android.builtin.localinference.domain.InferenceRouteCoordinator
import com.lanxin.android.builtin.localinference.domain.InferenceRouteDecision
import com.lanxin.android.builtin.localinference.domain.InferenceRouteTarget
import com.lanxin.android.builtin.localinference.domain.RouteReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6.3 路由矩阵 + Chat fallback 辅助逻辑。
 */
class ChatLocalFallbackTest {

    @Test
    fun `shouldUseLocal only for LOCAL target`() {
        assertTrue(
            ChatLocalFallback.shouldUseLocal(
                InferenceRouteDecision(InferenceRouteTarget.LOCAL, RouteReason.OFFLINE_LOCAL)
            )
        )
        assertFalse(
            ChatLocalFallback.shouldUseLocal(
                InferenceRouteDecision(InferenceRouteTarget.CLOUD, RouteReason.DEFAULT_CLOUD)
            )
        )
        assertFalse(
            ChatLocalFallback.shouldUseLocal(
                InferenceRouteDecision(InferenceRouteTarget.UNAVAILABLE, RouteReason.NO_PROVIDER)
            )
        )
    }

    @Test
    fun `shouldEmitUnavailable only for UNAVAILABLE`() {
        assertTrue(
            ChatLocalFallback.shouldEmitUnavailable(
                InferenceRouteDecision(InferenceRouteTarget.UNAVAILABLE, RouteReason.NO_PROVIDER)
            )
        )
        assertFalse(
            ChatLocalFallback.shouldEmitUnavailable(
                InferenceRouteDecision(InferenceRouteTarget.LOCAL, RouteReason.OFFLINE_LOCAL)
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
            InferenceRouteDecision(
                InferenceRouteTarget.UNAVAILABLE,
                RouteReason.OFFLINE_LOCAL_UNAVAILABLE
            ),
            networkAvailable = false
        )
        assertEquals(InferenceRouteCoordinator.OFFLINE_LOCAL_UNAVAILABLE_MESSAGE, msg)
        assertTrue(msg.contains("本地推理"))
    }

    @Test
    fun `route matrix offline ready uses local`() {
        val d = ChatRouter.decide(
            preferLocal = false,
            localReady = true,
            cloudAvailable = false,
            networkAvailable = false
        )
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertTrue(ChatLocalFallback.shouldUseLocal(d))
        assertEquals(RouteReason.OFFLINE_LOCAL, d.reason)
    }

    @Test
    fun `route matrix offline not ready unavailable`() {
        val d = ChatRouter.decide(
            preferLocal = false,
            localReady = false,
            cloudAvailable = false,
            networkAvailable = false
        )
        assertEquals(InferenceRouteTarget.UNAVAILABLE, d.target)
        assertTrue(ChatLocalFallback.shouldEmitUnavailable(d))
        assertEquals(RouteReason.OFFLINE_LOCAL_UNAVAILABLE, d.reason)
    }

    @Test
    fun `route matrix online defaults cloud`() {
        val d = ChatRouter.decide(
            preferLocal = false,
            localReady = true,
            cloudAvailable = true,
            networkAvailable = true
        )
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
        assertFalse(ChatLocalFallback.shouldUseLocal(d))
        assertEquals(RouteReason.DEFAULT_CLOUD, d.reason)
    }

    @Test
    fun `route matrix preferLocal online uses local when ready`() {
        val d = ChatRouter.decide(
            preferLocal = true,
            localReady = true,
            cloudAvailable = true,
            networkAvailable = true
        )
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals(RouteReason.PREFER_LOCAL, d.reason)
    }

    @Test
    fun `route matrix enabled off means not ready never local`() {
        val offline = ChatRouter.decide(
            preferLocal = true,
            localReady = false,
            cloudAvailable = false,
            networkAvailable = false
        )
        assertEquals(InferenceRouteTarget.UNAVAILABLE, offline.target)

        val online = ChatRouter.decide(
            preferLocal = true,
            localReady = false,
            cloudAvailable = true,
            networkAvailable = true
        )
        assertEquals(InferenceRouteTarget.CLOUD, online.target)
    }

    @Test
    fun `isLocalGeneration matches LOCAL target`() {
        assertTrue(
            ChatLocalFallback.isLocalGeneration(
                InferenceRouteDecision(InferenceRouteTarget.LOCAL, RouteReason.OFFLINE_LOCAL)
            )
        )
    }

    @Test
    fun `needs tools cloud reason`() {
        val d = ChatRouter.decide(
            preferLocal = true,
            localReady = true,
            cloudAvailable = true,
            networkAvailable = true,
            needsTools = true
        )
        assertEquals(RouteReason.NEED_TOOLS_CLOUD, d.reason)
        assertFalse(ChatLocalFallback.shouldUseLocal(d))
    }
}
