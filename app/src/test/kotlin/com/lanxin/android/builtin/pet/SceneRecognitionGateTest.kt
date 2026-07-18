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

import com.lanxin.android.builtin.pet.domain.FrameStats
import com.lanxin.android.builtin.pet.domain.FrameStatsSampler
import com.lanxin.android.builtin.pet.domain.HeuristicSceneRecognizer
import com.lanxin.android.builtin.pet.domain.SceneCaptureCoordinator
import com.lanxin.android.builtin.pet.domain.SceneLabel
import com.lanxin.android.builtin.pet.domain.SceneRecognitionConfig
import com.lanxin.android.builtin.pet.domain.SceneRecognitionGate
import com.lanxin.android.builtin.pet.domain.SceneRecognitionSession
import com.lanxin.android.builtin.pet.domain.SceneRecognitionSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 场景识别：默认关、确认 Gate、关闭清理会话、启发式标签。
 */
class SceneRecognitionGateTest {

    @Test
    fun `default config is disabled without consent`() {
        val c = SceneRecognitionConfig()
        assertFalse(c.enabled)
        assertFalse(c.consentGranted)
        assertFalse(SceneRecognitionGate.isEnabled(c))
        assertFalse(SceneRecognitionGate.hasConsent(c))
        assertFalse(SceneRecognitionGate.canCapture(c))
    }

    @Test
    fun `tryEnable requires userConfirmed`() {
        val cur = SceneRecognitionConfig()
        assertNull(SceneRecognitionGate.tryEnable(cur, userConfirmed = false))
        val on = SceneRecognitionGate.tryEnable(cur, userConfirmed = true)
        assertNotNull(on)
        assertTrue(on!!.enabled)
        assertTrue(on.consentGranted)
        assertTrue(SceneRecognitionGate.canCapture(on))
    }

    @Test
    fun `enabled without consent cannot capture`() {
        val c = SceneRecognitionConfig(enabled = true, consentGranted = false)
        assertFalse(SceneRecognitionGate.canCapture(c))
        assertEquals(
            SceneRecognitionGate.NO_CONSENT_CODE,
            SceneRecognitionGate.denyIfCannotCapture(c)
        )
    }

    @Test
    fun `disabled denies even with consent`() {
        val c = SceneRecognitionConfig(enabled = false, consentGranted = true)
        assertEquals(
            SceneRecognitionGate.DENIED_CODE,
            SceneRecognitionGate.denyIfCannotCapture(c)
        )
    }

    @Test
    fun `disable revokes consent by default`() {
        val on = SceneRecognitionConfig(enabled = true, consentGranted = true)
        val off = SceneRecognitionGate.disable(on)
        assertFalse(off.enabled)
        assertFalse(off.consentGranted)
        assertTrue(SceneRecognitionGate.shouldClearSessionOnDisable())
    }

    @Test
    fun `privacy notice is non blank`() {
        assertTrue(SceneRecognitionGate.PRIVACY_NOTICE.length > 20)
    }

    @Test
    fun `heuristic night and warm desk`() {
        val r = HeuristicSceneRecognizer()
        val night = r.recognize(FrameStats(0.05f, 0.05f, 0.05f, 0.05f, 10), 1L)
        assertEquals(SceneLabel.NIGHT, night.label)
        val warm = r.recognize(FrameStats(0.55f, 0.7f, 0.5f, 0.35f, 10), 2L)
        assertEquals(SceneLabel.WARM_DESK, warm.label)
        assertTrue(warm.feedbackText.isNotBlank())
    }

    @Test
    fun `frame sampler averages`() {
        // 全白
        val white = IntArray(16) { 0xFFFFFFFF.toInt() }
        val s = FrameStatsSampler.fromArgbPixels(white, 4, 4, step = 1)
        assertEquals(16, s.sampleCount)
        assertTrue(s.meanLuma > 0.99f)
    }

    @Test
    fun `coordinator denies when off and clears on disable`() = runBlocking {
        val settings = FakeSceneSettings()
        val session = SceneRecognitionSession()
        val coord = SceneCaptureCoordinator(
            settings = settings,
            recognizer = HeuristicSceneRecognizer(),
            session = session
        )
        val denied = coord.captureWithStats(FrameStats(0.5f, 0.5f, 0.5f, 0.5f, 4))
        assertTrue(denied is SceneCaptureCoordinator.CaptureOutcome.Denied)

        assertNotNull(coord.enableWithConsent(true))
        val ok = coord.captureWithStats(FrameStats(0.55f, 0.62f, 0.5f, 0.4f, 8))
        assertTrue(ok is SceneCaptureCoordinator.CaptureOutcome.Success)
        assertNotNull(session.current())
        assertNotNull(session.feedbackLine())

        coord.disableAndClear()
        assertNull(session.current())
        assertFalse(settings.getConfig().enabled)
        assertFalse(settings.getConfig().consentGranted)
    }

    private class FakeSceneSettings : SceneRecognitionSettings {
        private var config = SceneRecognitionConfig()
        override suspend fun getConfig() = config
        override suspend fun setEnabled(enabled: Boolean) {
            config = config.copy(enabled = enabled)
        }
        override suspend fun setConsentGranted(granted: Boolean) {
            config = config.copy(consentGranted = granted)
        }
        override suspend fun update(config: SceneRecognitionConfig) {
            this.config = config
        }
    }
}
