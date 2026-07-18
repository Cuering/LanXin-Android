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

package com.lanxin.android.builtin.platform

import com.lanxin.android.builtin.platform.domain.SceneSensingConfig
import com.lanxin.android.builtin.platform.domain.SceneSensingGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 场景识别门闸：默认关、确认 Gate、相机权限、钳制开关。
 */
class SceneSensingGateTest {

    @Test
    fun `default config is fully disabled`() {
        val c = SceneSensingConfig()
        assertFalse(c.enabled)
        assertFalse(c.consentGranted)
        assertEquals("", c.lastSceneId)
        assertEquals(SceneSensingConfig.FEATURE_NAME, "camera_scene")
    }

    @Test
    fun `canCapture requires enabled consent and camera`() {
        val off = SceneSensingConfig(enabled = false, consentGranted = true)
        assertFalse(SceneSensingGate.canCapture(off, cameraGranted = true))

        val noConsent = SceneSensingConfig(enabled = true, consentGranted = false)
        assertFalse(SceneSensingGate.canCapture(noConsent, cameraGranted = true))

        val noCam = SceneSensingConfig(enabled = true, consentGranted = true)
        assertFalse(SceneSensingGate.canCapture(noCam, cameraGranted = false))

        val ok = SceneSensingConfig(enabled = true, consentGranted = true)
        assertTrue(SceneSensingGate.canCapture(ok, cameraGranted = true))
    }

    @Test
    fun `needsConsentDialog only when turning on without consent`() {
        val bare = SceneSensingConfig()
        assertTrue(SceneSensingGate.needsConsentDialog(bare, turningOn = true))
        assertFalse(SceneSensingGate.needsConsentDialog(bare, turningOn = false))

        val consented = SceneSensingConfig(consentGranted = true)
        assertFalse(SceneSensingGate.needsConsentDialog(consented, turningOn = true))
    }

    @Test
    fun `denyReason codes stable`() {
        assertEquals(
            SceneSensingGate.DENIED_DISABLED,
            SceneSensingGate.denyReason(
                SceneSensingConfig(enabled = false, consentGranted = true),
                cameraGranted = true
            )
        )
        assertEquals(
            SceneSensingGate.DENIED_NO_CONSENT,
            SceneSensingGate.denyReason(
                SceneSensingConfig(enabled = true, consentGranted = false),
                cameraGranted = true
            )
        )
        assertEquals(
            SceneSensingGate.DENIED_NO_CAMERA,
            SceneSensingGate.denyReason(
                SceneSensingConfig(enabled = true, consentGranted = true),
                cameraGranted = false
            )
        )
        assertNull(
            SceneSensingGate.denyReason(
                SceneSensingConfig(enabled = true, consentGranted = true),
                cameraGranted = true
            )
        )
    }

    @Test
    fun `blockMessage covers known codes`() {
        assertNotNull(SceneSensingGate.blockMessage(SceneSensingGate.DENIED_DISABLED))
        assertNotNull(SceneSensingGate.blockMessage(SceneSensingGate.DENIED_NO_CONSENT))
        assertNotNull(SceneSensingGate.blockMessage(SceneSensingGate.DENIED_NO_CAMERA))
        assertNull(SceneSensingGate.blockMessage(null))
    }

    @Test
    fun `clampEnabled blocks on without consent`() {
        assertFalse(SceneSensingGate.clampEnabled(requestedEnabled = true, consentGranted = false))
        assertTrue(SceneSensingGate.clampEnabled(requestedEnabled = true, consentGranted = true))
        assertFalse(SceneSensingGate.clampEnabled(requestedEnabled = false, consentGranted = true))
    }
}
