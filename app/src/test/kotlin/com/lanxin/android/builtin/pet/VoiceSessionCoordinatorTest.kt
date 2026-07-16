package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.PetChatResponder
import com.lanxin.android.builtin.pet.domain.PetConfig
import com.lanxin.android.builtin.pet.domain.PetSettings
import com.lanxin.android.builtin.pet.domain.StubPetChatResponder
import com.lanxin.android.builtin.pet.domain.VoiceSessionCoordinator
import com.lanxin.android.builtin.pet.domain.VoiceSessionInput
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import com.lanxin.android.builtin.voice.data.StubTtsEngine
import com.lanxin.android.builtin.voice.domain.TtsConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionCoordinatorTest {

    private class FakePetSettings(
        var config: PetConfig = PetConfig(enabled = true)
    ) : PetSettings {
        override suspend fun getConfig(): PetConfig = config
        override suspend fun setEnabled(enabled: Boolean) {
            config = config.copy(enabled = enabled)
        }
        override suspend fun setOverlayRunning(running: Boolean) {
            config = config.copy(overlayRunning = running)
        }
        override suspend fun setAutoListen(autoListen: Boolean) {
            config = config.copy(autoListen = autoListen)
        }
        override suspend fun setLive2dModelPath(path: String?) {
            config = config.copy(live2dModelPath = path.orEmpty())
        }
    }

    private class FixedResponder(
        private val text: String
    ) : PetChatResponder {
        override suspend fun respond(userText: String): String = text
    }

    @Test
    fun `runRound fails when pet disabled`() = runBlocking {
        val tts = StubTtsEngine()
        tts.load(TtsConfig(enabled = true))
        val c = VoiceSessionCoordinator(
            responder = StubPetChatResponder(),
            ttsEngine = tts,
            petSettings = FakePetSettings(PetConfig(enabled = false))
        )
        val r = c.runRound(VoiceSessionInput("你好"))
        assertNotNull(r.error)
        assertTrue(r.error!!.contains("pet_disabled"))
        assertEquals(VoiceSessionPhase.ERROR, r.phase)
    }

    @Test
    fun `runDemoRound happy path ends IDLE`() = runBlocking {
        val tts = StubTtsEngine()
        tts.load(TtsConfig(enabled = true))
        val c = VoiceSessionCoordinator(
            responder = StubPetChatResponder(),
            ttsEngine = tts,
            petSettings = FakePetSettings(PetConfig(enabled = true))
        )
        val r = c.runDemoRound()
        assertEquals(null, r.error)
        assertEquals(VoiceSessionPhase.IDLE, r.phase)
        assertTrue(r.replyText.isNotBlank())
        assertTrue(r.subtitle.isNotBlank())
        assertTrue(r.isStub)
        assertEquals(VoiceSessionPhase.IDLE, c.current().phase)
    }

    @Test
    fun `empty asr fails`() = runBlocking {
        val tts = StubTtsEngine()
        tts.load(TtsConfig(enabled = true))
        val c = VoiceSessionCoordinator(
            responder = StubPetChatResponder(),
            ttsEngine = tts,
            petSettings = FakePetSettings()
        )
        val r = c.runRound(VoiceSessionInput("   "))
        assertTrue(r.error!!.contains("empty_asr"))
    }

    @Test
    fun `custom responder text appears in result`() = runBlocking {
        val tts = StubTtsEngine()
        tts.load(TtsConfig(enabled = true))
        val c = VoiceSessionCoordinator(
            responder = FixedResponder("自定义回复"),
            ttsEngine = tts,
            petSettings = FakePetSettings()
        )
        val r = c.runRound(VoiceSessionInput("测试", isStub = true))
        assertEquals("自定义回复", r.replyText)
        assertEquals("自定义回复", r.subtitle)
    }
}
