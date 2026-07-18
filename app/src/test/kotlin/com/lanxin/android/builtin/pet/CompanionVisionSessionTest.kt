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

import com.lanxin.android.builtin.pet.domain.CompanionVisionSession
import com.lanxin.android.builtin.pet.presentation.CompanionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 看世界：默认关 · Gate 复用 · 提问才抓帧。
 */
class CompanionVisionSessionTest {

    @Test
    fun `default ui state vision is off`() {
        val s = CompanionUiState()
        assertFalse(s.visionLooking)
        assertFalse(s.visionConsentGranted)
        assertFalse(s.cameraGranted)
        assertFalse(s.visionPreviewReady)
        assertFalse(s.showVisionConsentDialog)
    }

    @Test
    fun `canUseCamera requires looking consent and camera`() {
        assertFalse(
            CompanionVisionSession.canUseCamera(
                lookingEnabled = false,
                consentGranted = true,
                cameraGranted = true
            )
        )
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
    }

    @Test
    fun `needsConsentDialog when turning on without consent`() {
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
    fun `shouldCaptureOnAsk aligns with canUseCamera`() {
        assertFalse(
            CompanionVisionSession.shouldCaptureOnAsk(
                lookingEnabled = false,
                consentGranted = true,
                cameraGranted = true
            )
        )
        assertTrue(
            CompanionVisionSession.shouldCaptureOnAsk(
                lookingEnabled = true,
                consentGranted = true,
                cameraGranted = true
            )
        )
    }

    @Test
    fun `status labels`() {
        assertEquals(
            CompanionVisionSession.STATUS_OFF,
            CompanionVisionSession.statusLabel(lookingEnabled = false, previewReady = false)
        )
        assertEquals(
            CompanionVisionSession.STATUS_LOOKING,
            CompanionVisionSession.statusLabel(lookingEnabled = true, previewReady = true)
        )
        assertTrue(
            CompanionVisionSession.statusLabel(lookingEnabled = true, previewReady = false)
                .contains("准备")
        )
    }

    @Test
    fun `denyReason when off`() {
        assertEquals(
            "companion_vision_off",
            CompanionVisionSession.denyReason(
                lookingEnabled = false,
                consentGranted = true,
                cameraGranted = true
            )
        )
    }
}
