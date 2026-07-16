package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.domain.ChatRouteContext
import com.lanxin.android.builtin.localinference.domain.ChatRouter
import com.lanxin.android.builtin.localinference.domain.InferenceRouteTarget
import com.lanxin.android.builtin.localinference.domain.RouteReason
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 6.3 ChatRouter 纯函数矩阵：
 * network × ready × preferLocal × needsTools
 */
class ChatRouterTest {

    @Test
    fun `preferLocal ready no tools selects LOCAL`() {
        val d = ChatRouter.decide(
            ChatRouteContext(
                preferLocal = true,
                localReady = true,
                networkAvailable = true,
                needsTools = false
            )
        )
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals(RouteReason.PREFER_LOCAL, d.reason)
    }

    @Test
    fun `preferLocal ready with tools selects CLOUD need_tools`() {
        val d = ChatRouter.decide(
            ChatRouteContext(
                preferLocal = true,
                localReady = true,
                networkAvailable = true,
                needsTools = true
            )
        )
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
        assertEquals(RouteReason.NEED_TOOLS_CLOUD, d.reason)
    }

    @Test
    fun `offline ready selects offline_local`() {
        val d = ChatRouter.decide(
            ChatRouteContext(
                preferLocal = false,
                localReady = true,
                networkAvailable = false,
                needsTools = false,
                cloudAvailable = false
            )
        )
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals(RouteReason.OFFLINE_LOCAL, d.reason)
    }

    @Test
    fun `offline not ready selects offline_local_unavailable`() {
        val d = ChatRouter.decide(
            ChatRouteContext(
                preferLocal = false,
                localReady = false,
                networkAvailable = false,
                needsTools = false,
                cloudAvailable = false
            )
        )
        assertEquals(InferenceRouteTarget.UNAVAILABLE, d.target)
        assertEquals(RouteReason.OFFLINE_LOCAL_UNAVAILABLE, d.reason)
    }

    @Test
    fun `online default cloud`() {
        val d = ChatRouter.decide(
            ChatRouteContext(
                preferLocal = false,
                localReady = true,
                networkAvailable = true,
                needsTools = false
            )
        )
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
        assertEquals(RouteReason.DEFAULT_CLOUD, d.reason)
    }

    @Test
    fun `needsTools offline with ready still prefers cloud only if cloud available`() {
        // 无网时 cloudAvailable=false，needsTools 无法走云端，回退本地
        val d = ChatRouter.decide(
            ChatRouteContext(
                preferLocal = false,
                localReady = true,
                networkAvailable = false,
                needsTools = true,
                cloudAvailable = false
            )
        )
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals(RouteReason.OFFLINE_LOCAL, d.reason)
    }

    @Test
    fun `needsTools online forces cloud even when preferLocal`() {
        val d = ChatRouter.decide(
            preferLocal = true,
            localReady = true,
            cloudAvailable = true,
            networkAvailable = true,
            needsTools = true
        )
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
        assertEquals(RouteReason.NEED_TOOLS_CLOUD, d.reason)
    }

    @Test
    fun `null network does not force offline`() {
        val d = ChatRouter.decide(
            ChatRouteContext(
                preferLocal = false,
                localReady = true,
                networkAvailable = null,
                needsTools = false
            )
        )
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
        assertEquals(RouteReason.DEFAULT_CLOUD, d.reason)
    }

    @Test
    fun `local only when cloud unavailable but ready`() {
        val d = ChatRouter.decide(
            ChatRouteContext(
                preferLocal = false,
                localReady = true,
                networkAvailable = true,
                needsTools = false,
                cloudAvailable = false
            )
        )
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals(RouteReason.LOCAL_ONLY, d.reason)
    }

    @Test
    fun `no provider when nothing available online`() {
        val d = ChatRouter.decide(
            ChatRouteContext(
                preferLocal = false,
                localReady = false,
                networkAvailable = true,
                needsTools = false,
                cloudAvailable = false
            )
        )
        assertEquals(InferenceRouteTarget.UNAVAILABLE, d.target)
        assertEquals(RouteReason.NO_PROVIDER, d.reason)
    }

    @Test
    fun `matrix not ready never local even preferLocal online`() {
        val d = ChatRouter.decide(
            ChatRouteContext(
                preferLocal = true,
                localReady = false,
                networkAvailable = true,
                needsTools = false
            )
        )
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
        assertEquals(RouteReason.DEFAULT_CLOUD, d.reason)
    }

    @Test
    fun `matrix offline preferLocal ready is local`() {
        val d = ChatRouter.decide(
            ChatRouteContext(
                preferLocal = true,
                localReady = true,
                networkAvailable = false,
                needsTools = false,
                cloudAvailable = false
            )
        )
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals(RouteReason.PREFER_LOCAL, d.reason)
    }
}
