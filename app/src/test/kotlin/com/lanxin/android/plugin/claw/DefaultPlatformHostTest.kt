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

import com.lanxin.android.plugin.claw.data.DefaultPlatformHost
import com.lanxin.android.plugin.claw.domain.ClawHostConfig
import com.lanxin.android.plugin.claw.domain.ClawHostSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DefaultPlatformHost 门闸行为（内存 Fake settings，无 Android Service）。
 */
class DefaultPlatformHostTest {

    private class FakeSettings(
        @Volatile var config: ClawHostConfig = ClawHostConfig()
    ) : ClawHostSettings {
        override suspend fun getConfig(): ClawHostConfig = config
        override suspend fun setEnabled(enabled: Boolean) {
            config = config.copy(enabled = enabled)
        }
        override suspend fun setResidentRequested(requested: Boolean) {
            config = config.copy(residentRequested = requested)
        }
    }

    @Test
    fun `when disabled keepAlive and qr return false or null`() {
        val settings = FakeSettings(ClawHostConfig(enabled = false))
        val host = DefaultPlatformHost(settings)
        assertFalse(host.isCapabilityOpen())
        assertFalse(host.requestKeepAlive("p1", "r"))
        assertTrue(host.keepAliveSnapshot().isEmpty())
        assertNull(host.postQrScanRequest("p1"))
        host.showStatusNotification("p1", "t", "x")
        assertNull(host.lastStatusLine())
    }

    @Test
    fun `when enabled keepAlive qr and status work`() {
        val settings = FakeSettings(ClawHostConfig(enabled = true))
        val host = DefaultPlatformHost(settings)
        assertTrue(host.isCapabilityOpen())
        assertTrue(host.requestKeepAlive("bot.wx", "session"))
        assertEquals("session", host.keepAliveSnapshot()["bot.wx"])
        host.showStatusNotification("bot.wx", "微信机器人", "在线")
        assertEquals("微信机器人", host.lastStatusLine()?.title)
        val reqId = host.postQrScanRequest("bot.wx", "扫码")
        assertNotNull(reqId)
        assertEquals(1, host.pendingQrRequests().size)
        host.cancelKeepAlive("bot.wx")
        assertTrue(host.keepAliveSnapshot().isEmpty())
        host.clearQrRequest(reqId!!)
        assertTrue(host.pendingQrRequests().isEmpty())
    }

    @Test
    fun `resident running flag independent of settings`() {
        val settings = FakeSettings(ClawHostConfig(enabled = true, residentRequested = true))
        val host = DefaultPlatformHost(settings)
        assertFalse(host.isResidentRunning())
        host.setResidentRunning(true)
        assertTrue(host.isResidentRunning())
        host.setResidentRunning(false)
        assertFalse(host.isResidentRunning())
    }

    @Test
    fun `empty pluginId rejected`() = runBlocking {
        val settings = FakeSettings(ClawHostConfig(enabled = true))
        val host = DefaultPlatformHost(settings)
        assertFalse(host.requestKeepAlive("  ", "x"))
        assertNull(host.postQrScanRequest(""))
    }
}
