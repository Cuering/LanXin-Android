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

package com.lanxin.android.builtin.pet.data

import android.webkit.JavascriptInterface
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeMessage
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import com.lanxin.android.builtin.pet.domain.VoiceSessionSnapshot

/**
 * 语音会话 JS Bridge（概念对齐妹居 AndroidVoiceBridge）。
 *
 * 注入名：`AndroidVoiceBridge`
 * M1：查询状态 / 触发 demo 会话；不偷偷录音。
 */
class AndroidVoiceBridge(
    private val snapshotProvider: () -> VoiceSessionSnapshot,
    private val onStartVoice: () -> Unit,
    private val onStopVoice: () -> Unit
) {

    @Volatile
    var lastCommand: PetBridgeCommand = PetBridgeCommand.UNKNOWN
        private set

    @JavascriptInterface
    fun getPhase(): String = snapshotProvider().phase.name

    @JavascriptInterface
    fun getAsrText(): String = snapshotProvider().asrText

    @JavascriptInterface
    fun getReplyText(): String = snapshotProvider().replyText

    @JavascriptInterface
    fun getSubtitle(): String = snapshotProvider().subtitle

    @JavascriptInterface
    fun getSnapshotJson(): String {
        val s = snapshotProvider()
        // 手写最小 JSON，避免 WebView 侧依赖
        return buildString {
            append('{')
            append("\"phase\":\"").append(escape(s.phase.name)).append("\",")
            append("\"asrText\":\"").append(escape(s.asrText)).append("\",")
            append("\"replyText\":\"").append(escape(s.replyText)).append("\",")
            append("\"subtitle\":\"").append(escape(s.subtitle)).append("\",")
            append("\"roundId\":").append(s.roundId).append(',')
            append("\"error\":\"").append(escape(s.lastError.orEmpty())).append('"')
            append('}')
        }
    }

    @JavascriptInterface
    fun startVoiceChat() {
        lastCommand = PetBridgeCommand.START_VOICE
        onStartVoice()
    }

    @JavascriptInterface
    fun stopVoiceChat() {
        lastCommand = PetBridgeCommand.STOP_VOICE
        onStopVoice()
    }

    @JavascriptInterface
    fun isSpeaking(): Boolean = snapshotProvider().phase == VoiceSessionPhase.SPEAKING

    @JavascriptInterface
    fun isListening(): Boolean = snapshotProvider().phase == VoiceSessionPhase.LISTENING

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    companion object {
        const val JS_NAME = "AndroidVoiceBridge"
    }
}
