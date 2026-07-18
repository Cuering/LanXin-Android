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

import com.lanxin.android.builtin.pet.domain.OverlayPosition
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
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
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
import com.lanxin.android.builtin.voice.domain.TtsConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7.5 端到端：VoiceSession 经 DeviceToolBridge 选工具 → Gate → 回传会话。
 */
class VoiceSessionToolBridgeTest {

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
        override suspend fun setCompanionBackground(presetId: String, customPath: String?) {
            config = config.copy(
                companionBgPresetId = presetId,
                companionBgCustomPath = customPath ?: config.companionBgCustomPath
            )
        }
    }

    private class OkAlarmClock : AlarmClockGateway {
        override fun canScheduleExactAlarms() = true
        override fun setAlarmClock(request: SetAlarmClockRequest) = AlarmClockResult.Ok(
            triggerAtEpochMs = request.triggerAtEpochMs,
            requestCode = 42,
            message = request.message
        )
    }

    private class OkLauncher : SystemToolsIntentLauncher {
        override fun launch(spec: IntentLaunchSpec) = IntentLaunchResult.Ok(
            action = spec.action,
            launched = true,
            description = "ok:${spec.action}"
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

    private fun registry(): DeviceToolRegistry {
        val gateway = StubCalendarGateway()
        val notes = StubNotesStore()
        val launcher = OkLauncher()
        val saf = NoopSaf()
        val catalog = InMemoryUserFileCatalog()
        val fileIo = NoopFileIo()
        return DeviceToolRegistry(
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
    }

    private fun bridge(config: SystemToolsConfig): DeviceToolBridge =
        DeviceToolBridge.forTest(registry = registry(), configProvider = { config })

    private fun coordinator(bridge: DeviceToolBridge): VoiceSessionCoordinator {
        val tts = StubTtsEngine()
        runBlocking { tts.load(TtsConfig(enabled = true)) }
        return VoiceSessionCoordinator(
            responder = StubPetChatResponder(),
            ttsEngine = tts,
            petSettings = FakePetSettings(PetConfig(enabled = true)),
            deviceToolBridge = bridge
        )
    }

    @Test
    fun `chatty round has no tool`() = runBlocking {
        val c = coordinator(
            bridge(
                SystemToolsConfig(masterEnabled = true, alarmEnabled = true)
            )
        )
        val r = c.runRound(VoiceSessionInput("兰心，你好呀", isStub = true))
        assertNull(r.toolName)
        assertNull(r.toolOutcome)
        assertEquals(VoiceSessionPhase.IDLE, r.phase)
        assertTrue(r.replyText.isNotBlank())
    }

    @Test
    fun `open alarm list goes through bridge and returns ok`() = runBlocking {
        val c = coordinator(
            bridge(
                SystemToolsConfig(masterEnabled = true, alarmEnabled = true)
            )
        )
        val r = c.runRound(VoiceSessionInput("帮我打开闹钟列表", isStub = true))
        assertEquals(DeviceToolIds.ALARM_SHOW, r.toolName)
        assertNotNull(r.toolOutcome)
        assertTrue(r.toolOutcome is DeviceToolOutcome.Ok)
        assertEquals(VoiceSessionPhase.IDLE, r.phase)
        assertTrue(r.replyText.contains(DeviceToolIds.ALARM_SHOW) || r.replyText.isNotBlank())
    }

    @Test
    fun `set alarm without confirm surfaces needs confirmation`() = runBlocking {
        val c = coordinator(
            bridge(
                SystemToolsConfig(
                    masterEnabled = true,
                    alarmEnabled = true,
                    requireConfirmOnWrite = true
                )
            )
        )
        val r = c.runRound(
            VoiceSessionInput("设个闹钟 7:30", isStub = true),
            toolConfirmed = false
        )
        assertEquals(DeviceToolIds.ALARM_SET, r.toolName)
        assertTrue(r.toolOutcome is DeviceToolOutcome.NeedsConfirmation)
        assertTrue(r.replyText.contains("确认"))
    }

    @Test
    fun `set alarm with confirm completes ok`() = runBlocking {
        val c = coordinator(
            bridge(
                SystemToolsConfig(
                    masterEnabled = true,
                    alarmEnabled = true,
                    requireConfirmOnWrite = true
                )
            )
        )
        val r = c.runRound(
            VoiceSessionInput("设个闹钟 7:30", isStub = true),
            toolConfirmed = true
        )
        assertEquals(DeviceToolIds.ALARM_SET, r.toolName)
        assertTrue(r.toolOutcome is DeviceToolOutcome.Ok)
        assertEquals(VoiceSessionPhase.IDLE, r.phase)
    }

    @Test
    fun `master off denies tool but session still completes`() = runBlocking {
        val c = coordinator(
            bridge(
                SystemToolsConfig(masterEnabled = false, alarmEnabled = true)
            )
        )
        val r = c.runRound(VoiceSessionInput("打开闹钟列表", isStub = true))
        assertEquals(DeviceToolIds.ALARM_SHOW, r.toolName)
        assertTrue(r.toolOutcome is DeviceToolOutcome.Denied)
        assertEquals(VoiceSessionPhase.IDLE, r.phase)
        assertTrue(r.replyText.contains("做不到") || r.replyText.contains("总开关"))
    }

    @Test
    fun `default config all off denies tool on intent hit`() = runBlocking {
        val c = coordinator(bridge(SystemToolsConfig()))
        val r = c.runRound(VoiceSessionInput("打开闹钟列表", isStub = true))
        assertEquals(DeviceToolIds.ALARM_SHOW, r.toolName)
        assertTrue(r.toolOutcome is DeviceToolOutcome.Denied)
        assertEquals(VoiceSessionPhase.IDLE, r.phase)
    }
}
