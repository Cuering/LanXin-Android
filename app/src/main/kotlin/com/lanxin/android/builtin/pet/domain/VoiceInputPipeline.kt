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

import com.lanxin.android.builtin.systemtools.domain.DeviceToolBridge
import com.lanxin.android.builtin.systemtools.domain.DeviceToolChannel
import com.lanxin.android.builtin.systemtools.domain.DeviceToolTurn
import com.lanxin.android.core.log.LogManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 输入流水线：文本 → LLM → 回复文字。
 *
 * 这一层只负责「想」，不涉及 ASR/TTS/播放。
 * 各环节附带耗时/报错监控（tag=`VoiceInputPLC`）。
 *
 * ## 环节
 * | 环节           | 耗时字段         | 报错字段         |
 * |---------------|-----------------|-----------------|
 * | deviceTool    | toolDurMs       | toolError        |
 * | responder     | llmDurMs        | llmError        |
 *
 * @see VoiceOutputPipeline 配套输出流水线
 */
@Singleton
class VoiceInputPipeline @Inject constructor(
    private val responder: PetChatResponder,
    private val deviceToolBridge: DeviceToolBridge,
    private val logManager: LogManager? = null
) {
    private val log = logManager?.getLogger(TAG)

    /**
     * 处理一轮文字输入，走工具（可选）+ LLM，返回回复文字。
     *
     * @param text 用户文字（ASR 识别结果或键盘输入）
     * @param toolConfirmed 工具写操作是否默认确认
     * @return [VoiceInputResult] 包含回复文字与各环节耗时/报错
     */
    suspend fun process(
        text: String,
        toolConfirmed: Boolean = false,
        precomputedTurn: DeviceToolTurn? = null
    ): VoiceInputResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return VoiceInputResult(replyText = "")
        }

        // === 环节 1：DeviceTool ===
        val t0 = System.currentTimeMillis()
        val toolTurn = if (precomputedTurn != null) {
            precomputedTurn
        } else {
            runCatching {
                deviceToolBridge.voiceTurn(trimmed, confirmed = toolConfirmed)
            }.getOrElse { e ->
                log?.w("toolTurn failed, continue chat-only: ${e.message}")
                DeviceToolTurn(
                    channel = DeviceToolChannel.VOICE,
                    plan = null,
                    outcome = null,
                    needsTools = false,
                    summary = null
                )
            }
        }
        val toolDurMs = System.currentTimeMillis() - t0

        // === 环节 2：LLM ===
        val t1 = System.currentTimeMillis()
        val replyText = runCatching { responder.respond(trimmed) }
            .getOrElse { e ->
                log?.e("respond failed: ${e.message}", e)
                ""
            }
        val llmDurMs = System.currentTimeMillis() - t1
        val llmError = if (replyText.isEmpty()) "empty_reply" else null

        log?.i("process: text=${trimmed.take(48)} tool=${toolTurn.plan?.toolName} toolDur=${toolDurMs}ms llmDur=${llmDurMs}ms replyLen=${replyText.length}")

        return VoiceInputResult(
            replyText = replyText,
            toolName = toolTurn.plan?.toolName,
            toolOutcome = toolTurn.outcome,
            toolDurMs = toolDurMs,
            llmDurMs = llmDurMs,
            toolError = null,
            llmError = llmError
        )
    }

    companion object {
        private const val TAG = "VoiceInputPLC"
    }
}

/**
 * 输入流水线结果。
 *
 * @property toolDurMs 工具环节耗时（ms），0=未触发
 * @property llmDurMs LLM 环节耗时（ms）
 * @property toolError 工具环节报错文本，null=正常
 * @property llmError LLM 环节报错文本，null=正常
 */
data class VoiceInputResult(
    val replyText: String,
    val toolName: String? = null,
    val toolOutcome: com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome? = null,
    val toolDurMs: Long = 0L,
    val llmDurMs: Long = 0L,
    val toolError: String? = null,
    val llmError: String? = null
)
