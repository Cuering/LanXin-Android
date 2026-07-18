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

package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.CompanionVisionFrame
import com.lanxin.android.builtin.pet.domain.CompanionVisionSession
import com.lanxin.android.builtin.platform.domain.SceneSensingGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全屏陪伴「看世界」会话门闸：默认关、复用 #99 consent/camera、仅提问抓帧。
 */
class CompanionVisionSessionTest {

    @Test
    fun `default looking off cannot use camera`() {
        assertFalse(
            CompanionVisionSession.canUseCamera(
                lookingEnabled = false,
                consentGranted = true,
                cameraGranted = true
            )
        )
        assertFalse(
            CompanionVisionSession.shouldCaptureOnAsk(
                lookingEnabled = false,
                consentGranted = true,
                cameraGranted = true
            )
        )
        assertEquals(
            "companion_vision_off",
            CompanionVisionSession.denyReason(
                lookingEnabled = false,
                consentGranted = true,
                cameraGranted = true
            )
        )
    }

    @Test
    fun `looking on still requires consent and camera like SceneSensingGate`() {
        assertFalse(
            CompanionVisionSession.canUseCamera(
                lookingEnabled = true,
                consentGranted = false,
                cameraGranted = true
            )
        )
        assertFalse(
            CompanionVisionSession.canUseCamera(
                lookingEnabled = true,
                consentGranted = true,
                cameraGranted = false
            )
        )
        assertTrue(
            CompanionVisionSession.canUseCamera(
                lookingEnabled = true,
                consentGranted = true,
                cameraGranted = true
            )
        )
        assertEquals(
            SceneSensingGate.DENIED_NO_CONSENT,
            CompanionVisionSession.denyReason(
                lookingEnabled = true,
                consentGranted = false,
                cameraGranted = true
            )
        )
        assertEquals(
            SceneSensingGate.DENIED_NO_CAMERA,
            CompanionVisionSession.denyReason(
                lookingEnabled = true,
                consentGranted = true,
                cameraGranted = false
            )
        )
        assertNull(
            CompanionVisionSession.denyReason(
                lookingEnabled = true,
                consentGranted = true,
                cameraGranted = true
            )
        )
    }

    @Test
    fun `shouldCaptureOnAsk only when fully allowed`() {
        assertTrue(
            CompanionVisionSession.shouldCaptureOnAsk(
                lookingEnabled = true,
                consentGranted = true,
                cameraGranted = true
            )
        )
        assertFalse(
            CompanionVisionSession.shouldCaptureOnAsk(
                lookingEnabled = true,
                consentGranted = true,
                cameraGranted = false
            )
        )
    }

    @Test
    fun `needsConsentDialog delegates to SceneSensingGate`() {
        assertTrue(
            CompanionVisionSession.needsConsentDialog(
                consentGranted = false,
                turningOn = true
            )
        )
        assertFalse(
            CompanionVisionSession.needsConsentDialog(
                consentGranted = true,
                turningOn = true
            )
        )
        assertFalse(
            CompanionVisionSession.needsConsentDialog(
                consentGranted = false,
                turningOn = false
            )
        )
    }

    @Test
    fun `statusLabel reflects looking and preview ready`() {
        assertEquals(
            CompanionVisionSession.STATUS_OFF,
            CompanionVisionSession.statusLabel(lookingEnabled = false, previewReady = true)
        )
        assertEquals(
            CompanionVisionSession.STATUS_LOOKING,
            CompanionVisionSession.statusLabel(lookingEnabled = true, previewReady = true)
        )
        assertEquals(
            "看世界·准备中",
            CompanionVisionSession.statusLabel(lookingEnabled = true, previewReady = false)
        )
    }

    @Test
    fun `frame dataUri has jpeg mime and no disk path`() {
        val frame = CompanionVisionFrame(
            jpegBase64 = "abc123",
            mimeType = "image/jpeg",
            width = 64,
            height = 48,
            capturedAtMs = 1L
        )
        assertEquals("data:image/jpeg;base64,abc123", frame.dataUri())
        assertEquals(CompanionVisionFrame.MAX_EDGE_PX, 768)
        assertEquals(CompanionVisionFrame.JPEG_QUALITY, 85)
    }
}
