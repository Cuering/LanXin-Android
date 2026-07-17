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

package com.lanxin.android.builtin.pet.domain

/**
 * Bridge 协议编解码（纯逻辑，对齐妹居 DesktopPetBridge / AndroidVoiceBridge 概念命名）。
 *
 * 线格式：简单 `key=value` 行或 JSON-like 手写序列化（避免强依赖 gson 于单测）。
 *
 * 示例：
 * ```
 * command=SESSION_STATE
 * phase=LISTENING
 * asrText=你好
 * ```
 */
object PetBridgeProtocol {

    const val KEY_COMMAND = "command"
    const val KEY_PHASE = "phase"
    const val KEY_ASR = "asrText"
    const val KEY_REPLY = "replyText"
    const val KEY_SUBTITLE = "subtitle"
    const val KEY_ERROR = "error"
    const val KEY_BUBBLE = "bubble"
    const val KEY_ROUND = "roundId"
    const val KEY_TS = "timestampMs"

    /** M2b Live2D */
    const val KEY_LIVE2D_PATH = "live2dPath"
    const val KEY_LIVE2D_FILE_URL = "live2dFileUrl"
    const val KEY_LIVE2D_DIR_URL = "live2dDirUrl"
    const val KEY_LIVE2D_MODE = "live2dMode"
    const val KEY_LIVE2D_REASON = "live2dReason"

    fun encode(message: PetBridgeMessage): String {
        val lines = mutableListOf<String>()
        lines += "$KEY_COMMAND=${message.command.name}"
        lines += "$KEY_TS=${message.timestampMs}"
        message.payload.forEach { (k, v) ->
            lines += "${escapeKey(k)}=${escapeValue(v)}"
        }
        return lines.joinToString("\n")
    }

    fun decode(raw: String): PetBridgeMessage {
        val map = linkedMapOf<String, String>()
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) return@forEach
                val k = unescapeKey(line.substring(0, idx))
                val v = unescapeValue(line.substring(idx + 1))
                map[k] = v
            }
        val command = PetBridgeMessage.parseCommand(map[KEY_COMMAND])
        val ts = map[KEY_TS]?.toLongOrNull() ?: 0L
        val payload = map.filterKeys { it != KEY_COMMAND && it != KEY_TS }
        return PetBridgeMessage(command = command, payload = payload, timestampMs = ts)
    }

    fun sessionStateMessage(snapshot: VoiceSessionSnapshot, timestampMs: Long = System.currentTimeMillis()): PetBridgeMessage {
        return PetBridgeMessage(
            command = PetBridgeCommand.SESSION_STATE,
            payload = mapOf(
                KEY_PHASE to snapshot.phase.name,
                KEY_ASR to snapshot.asrText,
                KEY_REPLY to snapshot.replyText,
                KEY_SUBTITLE to snapshot.subtitle,
                KEY_ERROR to (snapshot.lastError.orEmpty()),
                KEY_ROUND to snapshot.roundId.toString()
            ),
            timestampMs = timestampMs
        )
    }

    fun showBubbleMessage(text: String, timestampMs: Long = System.currentTimeMillis()): PetBridgeMessage {
        return PetBridgeMessage(
            command = PetBridgeCommand.SHOW_BUBBLE,
            payload = mapOf(KEY_BUBBLE to text, KEY_SUBTITLE to text),
            timestampMs = timestampMs
        )
    }

    fun setPetStateMessage(phase: VoiceSessionPhase, timestampMs: Long = System.currentTimeMillis()): PetBridgeMessage {
        return PetBridgeMessage(
            command = PetBridgeCommand.SET_PET_STATE,
            payload = mapOf(KEY_PHASE to phase.name),
            timestampMs = timestampMs
        )
    }

    /** Native → Web：推送 Live2D 加载决策。 */
    fun loadLive2dMessage(
        decision: Live2dDisplayController.Decision,
        timestampMs: Long = System.currentTimeMillis()
    ): PetBridgeMessage {
        return PetBridgeMessage(
            command = PetBridgeCommand.LOAD_LIVE2D,
            payload = mapOf(
                KEY_LIVE2D_PATH to decision.model3Path,
                KEY_LIVE2D_FILE_URL to decision.model3FileUrl,
                KEY_LIVE2D_DIR_URL to decision.modelDirFileUrl,
                KEY_LIVE2D_MODE to decision.mode.name,
                KEY_LIVE2D_REASON to decision.reason
            ),
            timestampMs = timestampMs
        )
    }

    /** Web → Native：显示状态回传。 */
    fun live2dStatusMessage(
        mode: String,
        reason: String = "",
        timestampMs: Long = System.currentTimeMillis()
    ): PetBridgeMessage {
        return PetBridgeMessage(
            command = PetBridgeCommand.LIVE2D_STATUS,
            payload = mapOf(
                KEY_LIVE2D_MODE to mode,
                KEY_LIVE2D_REASON to reason
            ),
            timestampMs = timestampMs
        )
    }

    private fun escapeKey(k: String): String = k.replace("=", "_").replace("\n", " ")
    private fun escapeValue(v: String): String = v.replace("\n", "\\n")
    private fun unescapeKey(k: String): String = k
    private fun unescapeValue(v: String): String = v.replace("\\n", "\n")
}
