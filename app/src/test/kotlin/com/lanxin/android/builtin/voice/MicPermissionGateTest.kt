package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.domain.MicPermissionGate
import com.lanxin.android.builtin.voice.domain.MicPermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicPermissionGateTest {

    @Test
    fun `canRecord only when granted`() {
        assertTrue(MicPermissionGate.canRecord(MicPermissionState.GRANTED))
        assertFalse(MicPermissionGate.canRecord(MicPermissionState.DENIED))
        assertFalse(MicPermissionGate.canRecord(MicPermissionState.PERMANENTLY_DENIED))
        assertFalse(MicPermissionGate.canRecord(MicPermissionState.UNKNOWN))
    }

    @Test
    fun `deniedMessage is gentle and actionable`() {
        val denied = MicPermissionGate.deniedMessage(MicPermissionState.DENIED)
        assertTrue(denied.contains("麦克风"))
        val permanent = MicPermissionGate.deniedMessage(MicPermissionState.PERMANENTLY_DENIED)
        assertTrue(permanent.contains("系统设置"))
        assertEquals("", MicPermissionGate.deniedMessage(MicPermissionState.GRANTED))
    }

    @Test
    fun `blockReason disabled first`() {
        val msg = MicPermissionGate.blockReason(
            permission = MicPermissionState.GRANTED,
            engineReady = true,
            enabled = false
        )
        assertNotNull(msg)
        assertTrue(msg!!.contains("未启用"))
    }

    @Test
    fun `blockReason engine not ready`() {
        val msg = MicPermissionGate.blockReason(
            permission = MicPermissionState.GRANTED,
            engineReady = false,
            enabled = true
        )
        assertNotNull(msg)
        assertTrue(msg!!.contains("未就绪"))
    }

    @Test
    fun `blockReason permission denied when requireMic`() {
        val msg = MicPermissionGate.blockReason(
            permission = MicPermissionState.DENIED,
            engineReady = true,
            enabled = true,
            requireMic = true
        )
        assertNotNull(msg)
        assertTrue(msg!!.contains("麦克风"))
    }

    @Test
    fun `blockReason null when all ok`() {
        assertNull(
            MicPermissionGate.blockReason(
                permission = MicPermissionState.GRANTED,
                engineReady = true,
                enabled = true
            )
        )
    }

    @Test
    fun `blockReason skips mic when not required`() {
        assertNull(
            MicPermissionGate.blockReason(
                permission = MicPermissionState.DENIED,
                engineReady = true,
                enabled = true,
                requireMic = false
            )
        )
    }
}
