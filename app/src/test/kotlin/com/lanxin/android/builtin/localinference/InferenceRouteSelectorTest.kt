package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.domain.InferenceRouteSelector
import com.lanxin.android.builtin.localinference.domain.InferenceRouteTarget
import com.lanxin.android.builtin.localinference.domain.RouteReason
import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceRouteSelectorTest {

    @Test
    fun `preferLocal with local available selects LOCAL`() {
        val d = InferenceRouteSelector.select(
            preferLocal = true,
            localAvailable = true,
            cloudAvailable = true,
            networkAvailable = true
        )
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals(RouteReason.PREFER_LOCAL, d.reason)
    }

    @Test
    fun `offline with local available selects LOCAL`() {
        val d = InferenceRouteSelector.select(
            preferLocal = false,
            localAvailable = true,
            cloudAvailable = true,
            networkAvailable = false
        )
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals(RouteReason.OFFLINE_LOCAL, d.reason)
    }

    @Test
    fun `online prefers cloud when not preferLocal`() {
        val d = InferenceRouteSelector.select(
            preferLocal = false,
            localAvailable = true,
            cloudAvailable = true,
            networkAvailable = true
        )
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
        assertEquals(RouteReason.DEFAULT_CLOUD, d.reason)
    }

    @Test
    fun `cloud only when local unavailable`() {
        val d = InferenceRouteSelector.select(
            preferLocal = true,
            localAvailable = false,
            cloudAvailable = true,
            networkAvailable = true
        )
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
    }

    @Test
    fun `local only when cloud unavailable`() {
        val d = InferenceRouteSelector.select(
            preferLocal = false,
            localAvailable = true,
            cloudAvailable = false,
            networkAvailable = true
        )
        assertEquals(InferenceRouteTarget.LOCAL, d.target)
        assertEquals(RouteReason.LOCAL_ONLY, d.reason)
    }

    @Test
    fun `unavailable when nothing available`() {
        val d = InferenceRouteSelector.select(
            preferLocal = false,
            localAvailable = false,
            cloudAvailable = false,
            networkAvailable = false
        )
        assertEquals(InferenceRouteTarget.UNAVAILABLE, d.target)
        assertEquals(RouteReason.OFFLINE_LOCAL_UNAVAILABLE, d.reason)
    }

    @Test
    fun `null network does not force offline fallback`() {
        val d = InferenceRouteSelector.select(
            preferLocal = false,
            localAvailable = true,
            cloudAvailable = true,
            networkAvailable = null
        )
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
        assertEquals(RouteReason.DEFAULT_CLOUD, d.reason)
    }

    @Test
    fun `needsTools delegates to cloud`() {
        val d = InferenceRouteSelector.select(
            preferLocal = true,
            localAvailable = true,
            cloudAvailable = true,
            networkAvailable = true,
            needsTools = true
        )
        assertEquals(InferenceRouteTarget.CLOUD, d.target)
        assertEquals(RouteReason.NEED_TOOLS_CLOUD, d.reason)
    }
}
