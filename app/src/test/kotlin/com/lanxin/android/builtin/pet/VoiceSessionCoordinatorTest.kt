package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.OverlayPosition
import com.lanxin.android.builtin.pet.domain.PetChatResponder
import com.lanxin.android.builtin.pet.domain.PetConfig
import com.lanxin.android.builtin.pet.domain.PetSettings
import com.lanxin.android.builtin.pet.domain.StubPetChatResponder
import com.lanxin.android.builtin.pet.domain.VoiceSessionCoordinator
import com.lanxin.android.builtin.pet.domain.VoiceSessionInput
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import com.lanxin.android.builtin.systemtools.data.AlarmSetDeviceTool
import com.lanxin.android.builtin.systemtools.data.AlarmShowDeviceTool
import com.lanxin.android.builtin.systemtools.data.CalendarCreateEventDeviceTool
import com.lanxin.android.builtin.systemtools.data.CalendarListUpcomingDeviceTool
import com.lanxin.android.builtin.systemtools.data.DeviceToolRegistry
import com.lanxin.android.builtin.systemtools.data.NoteAppendDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteCreateDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteDeleteDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteExportDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteImportDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteListDeviceTool
import com.lanxin.android.builtin.systemtools.data.NoteUpdateDeviceTool
import com.lanxin.android.builtin.systemtools.data.StubCalendarGateway
import com.lanxin.android.builtin.systemtools.data.StubNotesStore
import com.lanxin.android.builtin.systemtools.data.files.FileDeleteDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FileListDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FilePickDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FileReadTextDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FileShareDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.FileWriteDeviceTool
import com.lanxin.android.builtin.systemtools.data.files.InMemoryUserFileCatalog
import com.lanxin.android.builtin.systemtools.domain.AlarmClockGateway
import com.lanxin.android.builtin.systemtools.domain.AlarmClockResult
import com.lanxin.android.builtin.systemtools.domain.DeviceToolBridge
import com.lanxin.android.builtin.systemtools.domain.IntentLaunchResult
import com.lanxin.android.builtin.systemtools.domain.IntentLaunchSpec
import com.lanxin.android.builtin.systemtools.domain.NotesIoResult
import com.lanxin.android.builtin.systemtools.domain.NotesSafGateway
import com.lanxin.android.builtin.systemtools.domain.SetAlarmClockRequest
import com.lanxin.android.builtin.systemtools.domain.SystemToolsConfig
import com.lanxin.android.builtin.systemtools.domain.SystemToolsIntentLauncher
import com.lanxin.android.builtin.systemtools.domain.UserFileEntry
import com.lanxin.android.builtin.systemtools.domain.UserFileIoGateway
import com.lanxin.android.builtin.systemtools.domain.UserFileIoResult
import com.lanxin.android.builtin.systemtools.domain.UserFileProbe
import com.lanxin.android.builtin.voice.data.StubTtsEngine
import com.lanxin.android.builtin.voice.domain.TtsEngine
import com.lanxin.android.builtin.voice.domain.TtsEngineState
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeRequest
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.lanxin.android.builtin.voice.domain.TtsConfig
import com.lanxin.android.builtin.voice.domain.TtsSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionCoordinatorTest {

    private class FakeTtsSettings(
        private var config: TtsConfig = TtsConfig(enabled = true)
    ) : TtsSettings {
        override suspend fun getConfig(): TtsConfig = config
        override suspend fun setEnabled(enabled: Boolean) {
            config = config.copy(enabled = enabled)
        }
        override suspend fun setModelPath(path: String?) {
            config = config.copy(modelPath = path.orEmpty())
        }
        override suspend fun setModelDir(path: String?) {
            config = config.copy(modelDir = path.orEmpty())
        }
        override suspend fun setReferenceAudio(path: String?) {
            config = config.copy(referenceAudio = path.orEmpty())
        }
        override suspend fun setVoiceId(voiceId: String) {
            config = config.copy(voiceId = voiceId)
        }
    }

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
        override suspend fun setOverlayPosition(x: Int, y: Int) {
            config = config.copy(overlayPosition = OverlayPosition(x, y))
        }
        override suspend fun setLanXinSafTreeUri(uri: String?) = Unit
        override suspend fun setCompanionBackground(presetId: String, customPath: String?) {
            config = config.copy(
                companionBgPresetId = presetId,
                companionBgCustomPath = customPath ?: config.companionBgCustomPath
            )
        }
    }

    private class FixedResponder(
        private val text: String
    ) : PetChatResponder {
        override suspend fun respond(userText: String): String = text
    }

    private class OkAlarmClock : AlarmClockGateway {
        override fun canScheduleExactAlarms() = true
        override fun setAlarmClock(request: SetAlarmClockRequest) = AlarmClockResult.Ok(
            triggerAtEpochMs = request.triggerAtEpochMs,
            requestCode = 1,
            message = request.message
        )
    }

    private class OkLauncher : SystemToolsIntentLauncher {
        override fun launch(spec: IntentLaunchSpec) = IntentLaunchResult.Ok(
            action = spec.action,
            launched = true,
            description = spec.description
        )
    }

    private class NoopSaf : NotesSafGateway {
        override fun writeText(uriString: String, text: String, mimeType: String) =
            NotesIoResult.Ok("ok", bytes = text.length, uri = uriString)

        override fun readText(uriString: String) = NotesIoResult.Ok("{}", bytes = 2, uri = uriString)

        override fun shareText(text: String, mimeType: String, chooserTitle: String) =
            NotesIoResult.Ok("shared", bytes = text.length)
    }

    private class NoopFileIo : UserFileIoGateway {
        override fun readText(uriOrPath: String, maxChars: Int) =
            UserFileIoResult.Ok("", uri = uriOrPath)

        override fun writeText(uriOrPath: String, text: String, mimeType: String) =
            UserFileIoResult.Ok("ok", bytes = text.length, uri = uriOrPath)

        override fun copyToAppPrivate(uriString: String, preferredName: String?) =
            UserFileIoResult.Ok("ok", uri = "/x", name = preferredName ?: "x")

        override fun writeAppPrivateText(name: String, text: String, mimeType: String) =
            UserFileIoResult.Ok("ok", bytes = text.length, uri = "/x/$name", name = name)

        override fun listAppPrivateFiles(): List<UserFileEntry> = emptyList()

        override fun deleteAppPrivate(pathOrName: String) =
            UserFileIoResult.Ok("deleted", uri = pathOrName)

        override fun shareUri(uriOrPath: String, mimeType: String?, chooserTitle: String) =
            UserFileIoResult.Ok("shared", uri = uriOrPath)

        override fun shareText(text: String, mimeType: String, chooserTitle: String) =
            UserFileIoResult.Ok("shared", bytes = text.length)

        override fun probe(uriString: String): UserFileProbe? = null
    }

    /** 默认全关：闲聊路径不触发工具执行（即使关键词命中也会 Denied，但 demo 句不命中）。 */
    private fun defaultBridge(): DeviceToolBridge {
        val gateway = StubCalendarGateway()
        val notes = StubNotesStore()
        val launcher = OkLauncher()
        val saf = NoopSaf()
        val catalog = InMemoryUserFileCatalog()
        val fileIo = NoopFileIo()
        val registry = DeviceToolRegistry(
            alarmSet = AlarmSetDeviceTool(OkAlarmClock(), launcher),
            alarmShow = AlarmShowDeviceTool(launcher),
            calendarList = CalendarListUpcomingDeviceTool(gateway),
            calendarCreate = CalendarCreateEventDeviceTool(gateway, gateway),
            noteCreate = NoteCreateDeviceTool(notes),
            noteList = NoteListDeviceTool(notes),
            noteAppend = NoteAppendDeviceTool(notes),
            noteUpdate = NoteUpdateDeviceTool(notes),
            noteDelete = NoteDeleteDeviceTool(notes),
            noteExport = NoteExportDeviceTool(notes, saf),
            noteImport = NoteImportDeviceTool(notes, saf),
            filePick = FilePickDeviceTool(catalog, fileIo),
            fileList = FileListDeviceTool(catalog, fileIo),
            fileReadText = FileReadTextDeviceTool(catalog, fileIo),
            fileWrite = FileWriteDeviceTool(catalog, fileIo),
            fileShare = FileShareDeviceTool(catalog, fileIo),
            fileDelete = FileDeleteDeviceTool(catalog, fileIo)
        )
        return DeviceToolBridge.forTest(
            registry = registry,
            configProvider = { SystemToolsConfig() }
        )
    }

    private fun coordinator(
        responder: PetChatResponder = StubPetChatResponder(),
        pet: PetConfig = PetConfig(enabled = true)
    ): VoiceSessionCoordinator {
        val tts = StubTtsEngine()
        runBlocking { tts.load(TtsConfig(enabled = true)) }
        return VoiceSessionCoordinator(
            responder = responder,
            ttsEngine = tts,
            ttsSettings = FakeTtsSettings(),
            petSettings = FakePetSettings(pet),
            deviceToolBridge = defaultBridge()
        )
    }

    @Test
    fun `runRound fails when pet disabled`() = runBlocking {
        val c = coordinator(pet = PetConfig(enabled = false))
        val r = c.runRound(VoiceSessionInput("你好"))
        assertNotNull(r.error)
        assertTrue(r.error!!.contains("pet_disabled"))
        assertEquals(VoiceSessionPhase.ERROR, r.phase)
    }

    @Test
    fun `runDemoRound happy path ends IDLE`() = runBlocking {
        val c = coordinator()
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
        val c = coordinator()
        val r = c.runRound(VoiceSessionInput("   "))
        assertTrue(r.error!!.contains("empty_asr"))
    }

    @Test
    fun `custom responder text appears in result`() = runBlocking {
        val c = coordinator(responder = FixedResponder("自定义回复"))
        val r = c.runRound(VoiceSessionInput("测试", isStub = true))
        assertEquals("自定义回复", r.replyText)
        assertEquals("自定义回复", r.subtitle)
    }

    @Test
    fun `mood tags stripped from TTS result and final snapshot`() = runBlocking {
        val c = coordinator(responder = FixedResponder("[[mood=joy]]\n今天真开心"))
        val r = c.runRound(VoiceSessionInput("测试", isStub = true))
        assertEquals("今天真开心", r.replyText)
        assertEquals("今天真开心", r.subtitle)
        assertFalse(r.replyText.contains("[["))
        assertFalse(c.current().replyText.contains("[["))
        assertFalse(c.current().subtitle.contains("[["))
    }

    @Test
    fun `skipTts returns text without synthesizing and ends IDLE`() = runBlocking {
        val tts = CountingTtsEngine()
        tts.load(TtsConfig(enabled = true))
        val c = VoiceSessionCoordinator(
            responder = FixedResponder("仅文字回复"),
            ttsEngine = tts,
            ttsSettings = FakeTtsSettings(),
            petSettings = FakePetSettings(PetConfig(enabled = true)),
            deviceToolBridge = defaultBridge()
        )
        val r = c.runRound(
            input = VoiceSessionInput("测试", isStub = true, source = "companion_text"),
            skipTts = true
        )
        assertEquals(null, r.error)
        assertEquals(VoiceSessionPhase.IDLE, r.phase)
        assertEquals("仅文字回复", r.replyText)
        assertEquals("仅文字回复", r.subtitle)
        assertEquals(0, tts.synthesizeCount)
    }

    @Test
    fun `tts failure still returns display text for caller soft-handle`() = runBlocking {
        val tts = FailingTtsEngine()
        tts.load(TtsConfig(enabled = true))
        val c = VoiceSessionCoordinator(
            responder = FixedResponder("有字可显示"),
            ttsEngine = tts,
            ttsSettings = FakeTtsSettings(),
            petSettings = FakePetSettings(PetConfig(enabled = true)),
            deviceToolBridge = defaultBridge()
        )
        val r = c.runRound(VoiceSessionInput("测试", isStub = true))
        assertTrue(r.error!!.startsWith("tts_failed"))
        assertEquals("有字可显示", r.replyText)
        assertEquals("有字可显示", r.subtitle)
    }

    /** 统计 synthesize 调用次数，验证 skipTts 短路。 */
    private class CountingTtsEngine : TtsEngine {
        private val _state = MutableStateFlow(TtsEngineState.DISABLED)
        var synthesizeCount: Int = 0
            private set
        override val state: StateFlow<TtsEngineState> = _state.asStateFlow()
        override val isReady: Boolean get() = _state.value == TtsEngineState.READY
        override val isAvailable: Boolean = true
        override val lastError: String? = null
        override suspend fun load(config: TtsConfig): Boolean {
            _state.value = TtsEngineState.READY
            return true
        }
        override suspend fun unload() {
            _state.value = TtsEngineState.DISABLED
        }
        override suspend fun synthesize(request: TtsSynthesizeRequest): TtsSynthesizeResult {
            synthesizeCount += 1
            return TtsSynthesizeResult(
                pcm16leMono = ByteArray(0),
                sampleRateHz = 22050,
                durationMs = 100L,
                isStub = true,
                subtitle = request.text
            )
        }
    }

    /** synthesize 必失败，模拟 TTS 未就绪路径。 */
    private class FailingTtsEngine : TtsEngine {
        private val _state = MutableStateFlow(TtsEngineState.READY)
        override val state: StateFlow<TtsEngineState> = _state.asStateFlow()
        override val isReady: Boolean = true
        override val isAvailable: Boolean = true
        override val lastError: String? = "tts_not_ready"
        override suspend fun load(config: TtsConfig): Boolean = true
        override suspend fun unload() = Unit
        override suspend fun synthesize(request: TtsSynthesizeRequest): TtsSynthesizeResult {
            error("tts_not_ready")
        }
    }
}
