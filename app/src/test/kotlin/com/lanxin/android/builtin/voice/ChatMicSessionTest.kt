package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.data.PcmAudioRecorder
import com.lanxin.android.builtin.voice.data.SherpaOnnxBridge
import com.lanxin.android.builtin.voice.data.StubAsrEngine
import com.lanxin.android.builtin.voice.domain.AsrConfig
import com.lanxin.android.builtin.voice.domain.AsrSettings
import com.lanxin.android.builtin.voice.domain.ChatMicPhase
import com.lanxin.android.builtin.voice.domain.ChatMicSession
import com.lanxin.android.builtin.voice.domain.MicPermissionChecker
import com.lanxin.android.builtin.voice.domain.MicPermissionState
import com.lanxin.android.builtin.voice.domain.VoiceInputCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 主聊天麦克风：权限 / 未就绪 / 开关状态 / 转写填入。
 */
class ChatMicSessionTest {

    private class FakeSettings(
        var config: AsrConfig = AsrConfig()
    ) : AsrSettings {
        override suspend fun getConfig(): AsrConfig = config
        override suspend fun setEnabled(enabled: Boolean) {
            config = config.copy(enabled = enabled)
        }

        override suspend fun setModelPath(path: String?) {
            config = config.copy(modelPath = path.orEmpty())
        }

        override suspend fun setLanguage(language: String) {
            config = config.copy(language = language)
        }

        override suspend fun setSampleRateHz(sampleRateHz: Int) {
            config = config.copy(sampleRateHz = sampleRateHz)
        }
    }

    private lateinit var engine: StubAsrEngine
    private lateinit var settings: FakeSettings
    private lateinit var recorder: PcmAudioRecorder
    private lateinit var transcripts: MutableList<String>

    @Before
    fun setUp() {
        engine = StubAsrEngine(SherpaOnnxBridge())
        settings = FakeSettings()
        recorder = PcmAudioRecorder().apply {
            forceStubHardware = true
        }
        transcripts = mutableListOf()
    }

    private fun session(
        perm: MicPermissionState = MicPermissionState.GRANTED
    ): ChatMicSession {
        val coordinator = VoiceInputCoordinator(
            engine,
            settings,
            MicPermissionChecker { perm }
        )
        return ChatMicSession(
            coordinator = coordinator,
            recorder = recorder,
            settings = settings,
            engine = engine,
            permissionChecker = MicPermissionChecker { perm }
        )
    }

    @Test
    fun `mic blocked when asr disabled shows clear snackbar`() = runBlocking {
        settings.config = AsrConfig(enabled = false, modelPath = "stub://demo")
        val s = session()
        s.onMicClick { transcripts += it }
        assertEquals(ChatMicPhase.IDLE, s.uiState.value.phase)
        assertNotNull(s.uiState.value.snackbarMessage)
        assertTrue(s.uiState.value.snackbarMessage!!.contains("未启用"))
        assertTrue(transcripts.isEmpty())
        assertFalse(recorder.isRecording())
    }

    @Test
    fun `mic blocked when model path empty`() = runBlocking {
        settings.config = AsrConfig(enabled = true, modelPath = "")
        val s = session()
        s.onMicClick { transcripts += it }
        assertEquals(ChatMicPhase.IDLE, s.uiState.value.phase)
        val msg = s.uiState.value.snackbarMessage
        assertNotNull(msg)
        assertTrue(
            msg!!.contains("下载") || msg.contains("离线语音") || msg.contains("路径")
        )
        assertTrue(transcripts.isEmpty())
    }

    @Test
    fun `mic requests permission when denied`() = runBlocking {
        settings.config = AsrConfig(enabled = true, modelPath = "stub://demo")
        engine.load(settings.config)
        val s = session(MicPermissionState.DENIED)
        s.onMicClick { transcripts += it }
        assertEquals(ChatMicPhase.IDLE, s.uiState.value.phase)
        assertTrue(s.uiState.value.needRequestPermission)
        assertNull(s.uiState.value.snackbarMessage)
        assertTrue(transcripts.isEmpty())
    }

    @Test
    fun `permission permanently denied shows message`() = runBlocking {
        settings.config = AsrConfig(enabled = true, modelPath = "stub://demo")
        engine.load(settings.config)
        val s = session(MicPermissionState.PERMANENTLY_DENIED)
        s.onMicClick { transcripts += it }
        assertFalse(s.uiState.value.needRequestPermission)
        assertNotNull(s.uiState.value.snackbarMessage)
        assertTrue(s.uiState.value.snackbarMessage!!.contains("麦克风"))
    }

    @Test
    fun `hold dictation does not enable voice chat flag`() = runBlocking {
        settings.config = AsrConfig(enabled = true, modelPath = "stub://demo", language = "zh")
        val s = session(MicPermissionState.GRANTED)

        s.startHoldDictation()
        assertEquals(ChatMicPhase.RECORDING, s.uiState.value.phase)
        assertFalse(s.uiState.value.voiceChatEnabled)
        assertTrue(recorder.isRecording())

        delay(250)
        s.stopHoldDictation { transcripts += it }
        assertEquals(ChatMicPhase.IDLE, s.uiState.value.phase)
        assertFalse(s.uiState.value.voiceChatEnabled)
        assertEquals(1, transcripts.size)
    }

    @Test
    fun `toggle record then stop fills transcript`() = runBlocking {
        settings.config = AsrConfig(enabled = true, modelPath = "stub://demo", language = "zh")
        val s = session(MicPermissionState.GRANTED)

        s.onMicClick { transcripts += it }
        assertEquals(ChatMicPhase.RECORDING, s.uiState.value.phase)
        assertTrue(s.uiState.value.voiceChatEnabled)
        assertTrue(recorder.isRecording())

        // stub 硬件路径：等一小段再停，保证 duration ≥ MIN_USEFUL_MS
        delay(250)

        s.onMicClick { transcripts += it }
        assertEquals(ChatMicPhase.IDLE, s.uiState.value.phase)
        // 听写结束后模式保持开，便于连续语音
        assertTrue(s.uiState.value.voiceChatEnabled)
        assertFalse(recorder.isRecording())
        assertEquals(1, transcripts.size)
        assertTrue(transcripts[0].contains("[asr-stub]") || transcripts[0].isNotBlank())
        assertEquals(transcripts[0], s.uiState.value.lastFilledText)
        assertNull(s.uiState.value.snackbarMessage)
    }

    @Test
    fun `permission grant starts recording`() = runBlocking {
        settings.config = AsrConfig(enabled = true, modelPath = "stub://demo")
        engine.load(settings.config)
        // 先用 DENIED 会话触发 needRequestPermission 路径后，用 GRANTED checker 模拟授权
        val deniedSession = session(MicPermissionState.DENIED)
        deniedSession.onMicClick { transcripts += it }
        assertTrue(deniedSession.uiState.value.needRequestPermission)

        // 授权结果走 onPermissionResult（内部 skipPermissionCheck）
        deniedSession.onPermissionResult(granted = true) { transcripts += it }
        assertEquals(ChatMicPhase.RECORDING, deniedSession.uiState.value.phase)
        assertFalse(deniedSession.uiState.value.needRequestPermission)
    }

    @Test
    fun `permission deny shows snackbar`() = runBlocking {
        settings.config = AsrConfig(enabled = true, modelPath = "stub://demo")
        engine.load(settings.config)
        val s = session(MicPermissionState.DENIED)
        s.onPermissionResult(granted = false, permanentlyDenied = false)
        assertEquals(ChatMicPhase.IDLE, s.uiState.value.phase)
        assertNotNull(s.uiState.value.snackbarMessage)
        assertTrue(s.uiState.value.snackbarMessage!!.contains("麦克风"))
    }

    @Test
    fun `cancel stops recording without transcript`() = runBlocking {
        settings.config = AsrConfig(enabled = true, modelPath = "stub://demo")
        val s = session()
        s.onMicClick { transcripts += it }
        assertEquals(ChatMicPhase.RECORDING, s.uiState.value.phase)
        s.cancel()
        assertEquals(ChatMicPhase.IDLE, s.uiState.value.phase)
        assertFalse(s.uiState.value.voiceChatEnabled)
        assertFalse(recorder.isRecording())
        assertTrue(transcripts.isEmpty())
    }

    @Test
    fun `idle click while voice on turns mode off without recording`() = runBlocking {
        settings.config = AsrConfig(enabled = true, modelPath = "stub://demo")
        val s = session()
        // 开并录音
        s.onMicClick { transcripts += it }
        assertTrue(s.uiState.value.voiceChatEnabled)
        assertEquals(ChatMicPhase.RECORDING, s.uiState.value.phase)
        delay(250)
        // 停录，模式保持开
        s.onMicClick { transcripts += it }
        assertEquals(ChatMicPhase.IDLE, s.uiState.value.phase)
        assertTrue(s.uiState.value.voiceChatEnabled)
        // 再点：关模式、不占麦
        s.onMicClick { transcripts += it }
        assertEquals(ChatMicPhase.IDLE, s.uiState.value.phase)
        assertFalse(s.uiState.value.voiceChatEnabled)
        assertFalse(recorder.isRecording())
        assertEquals(1, transcripts.size)
        assertTrue(s.uiState.value.snackbarMessage!!.contains("语音聊天已关"))
    }

    @Test
    fun `failed open rolls back voiceChatEnabled`() = runBlocking {
        settings.config = AsrConfig(enabled = false, modelPath = "stub://demo")
        val s = session()
        s.onMicClick { transcripts += it }
        assertFalse(s.uiState.value.voiceChatEnabled)
        assertEquals(ChatMicPhase.IDLE, s.uiState.value.phase)
    }

    @Test
    fun `micToggleEnabled false while transcribing phase via ui state helpers`() {
        val idle = com.lanxin.android.builtin.voice.domain.ChatMicUiState()
        assertTrue(idle.micToggleEnabled)
        assertFalse(idle.isRecording)
        val recording = idle.copy(phase = ChatMicPhase.RECORDING)
        assertTrue(recording.isRecording)
        assertTrue(recording.micToggleEnabled)
        val busy = idle.copy(phase = ChatMicPhase.TRANSCRIBING)
        assertTrue(busy.isBusy)
        assertFalse(busy.micToggleEnabled)
    }

    @Test
    fun `auto load when enabled but engine not ready then record`() = runBlocking {
        settings.config = AsrConfig(enabled = true, modelPath = "stub://demo")
        assertFalse(engine.isReady)
        val s = session()
        s.onMicClick { transcripts += it }
        assertEquals(ChatMicPhase.RECORDING, s.uiState.value.phase)
        assertTrue(engine.isReady)
        delay(250)
        s.onMicClick { transcripts += it }
        assertEquals(1, transcripts.size)
    }
}
