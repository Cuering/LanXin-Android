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

package com.lanxin.android.builtin.voice.domain

/**
 * 主聊天对话框麦克风听写阶段。
 *
 * 产品：点麦开录 → 再点停录 → 转写填入输入框（可改再发），不自动发送。
 */
enum class ChatMicPhase {
    /** 空闲，可点麦。 */
    IDLE,

    /** 正在录音。 */
    RECORDING,

    /** 停麦后转写中。 */
    TRANSCRIBING
}

/**
 * Chat 输入区麦克风 UI 状态。
 *
 * 与全屏陪伴底栏对齐的「语音聊天模式」语义：
 * - [voiceChatEnabled]=false：关，不占麦（MicOff）
 * - true：开，可听写轮次（Mic）
 *
 * @property phase 阶段
 * @property voiceChatEnabled 语音聊天模式开关（会话态；关后不占麦）
 * @property snackbarMessage 一次性提示（未就绪 / 权限 / 错误）；消费后清空
 * @property needRequestPermission 为 true 时 UI 应拉起 RECORD_AUDIO 申请
 * @property lastFilledText 最近一次成功填入输入框的文本（便于调试 / 单测）
 */
data class ChatMicUiState(
    val phase: ChatMicPhase = ChatMicPhase.IDLE,
    val voiceChatEnabled: Boolean = false,
    val snackbarMessage: String? = null,
    val needRequestPermission: Boolean = false,
    val lastFilledText: String? = null
) {
    val isRecording: Boolean get() = phase == ChatMicPhase.RECORDING
    val isBusy: Boolean get() = phase == ChatMicPhase.TRANSCRIBING
    val micToggleEnabled: Boolean get() = phase != ChatMicPhase.TRANSCRIBING
}
