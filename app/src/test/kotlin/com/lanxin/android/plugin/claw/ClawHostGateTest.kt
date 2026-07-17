/*
 * Copyright 2025 LanXin Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lanxin.android.plugin.claw

import com.lanxin.android.plugin.claw.domain.ClawHostConfig
import com.lanxin.android.plugin.claw.domain.ClawHostGate
import com.lanxin.android.plugin.claw.domain.NoOpPlatformHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Claw 宿主门闸纯逻辑：默认关、常驻条件、Host 能力。
 * 不碰真 Service / 动态 dex。
 */
class ClawHostGateTest {

    @Test
    fun `default config is disabled and not resident`() {
        val c = ClawHostConfig()
        assertFalse(c.enabled)
        assertFalse(c.residentRequested)
        assertFalse(c.shouldRunResident())
        assertFalse(ClawHostGate.isEnabled(c))
        assertFalse(ClawHostGate.shouldRunResident(c))
        assertFalse(ClawHostGate.isHostCapabilityOpen(c))
    }

    @Test
    fun `enabled alone does not start resident`() {
        val c = ClawHostConfig(enabled = true, residentRequested = false)
        assertTrue(ClawHostGate.isEnabled(c))
        assertTrue(ClawHostGate.isHostCapabilityOpen(c))
        assertFalse(ClawHostGate.shouldRunResident(c))
        val deny = ClawHostGate.denyResidentIfDisabled(c)
        assertNotNull(deny)
        assertTrue(deny!!.contains("claw_resident_not_requested"))
    }

    @Test
    fun `enabled and resident requested allows resident`() {
        val c = ClawHostConfig(enabled = true, residentRequested = true)
        assertTrue(ClawHostGate.shouldRunResident(c))
        assertNull(ClawHostGate.denyResidentIfDisabled(c))
    }

    @Test
    fun `resident requested but host off is denied`() {
        val c = ClawHostConfig(enabled = false, residentRequested = true)
        assertFalse(ClawHostGate.shouldRunResident(c))
        val deny = ClawHostGate.denyResidentIfDisabled(c)
        assertNotNull(deny)
        assertTrue(deny!!.contains("claw_host_disabled"))
    }

    @Test
    fun `NoOpPlatformHost rejects all capabilities`() {
        val host = NoOpPlatformHost
        assertFalse(host.isCapabilityOpen())
        assertFalse(host.isResidentRunning())
        assertFalse(host.requestKeepAlive("bot.wx", "login"))
        assertNull(host.postQrScanRequest("bot.wx"))
        host.showStatusNotification("bot.wx", "t", "x")
        host.cancelKeepAlive("bot.wx")
    }

    @Test
    fun `feature id constant stable`() {
        assertEquals("claw_host", ClawHostConfig.FEATURE_ID)
    }
}
