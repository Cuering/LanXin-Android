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
 * 桌宠语音会话状态机。
 *
 * ```
 * IDLE → LISTENING → THINKING → SPEAKING → IDLE
 * ```
 *
 * 错误可从任意活跃态回到 IDLE / ERROR。
 */
enum class VoiceSessionPhase {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

/**
 * 会话快照（UI / Bridge 可观测）。
 */
data class VoiceSessionSnapshot(
    val phase: VoiceSessionPhase = VoiceSessionPhase.IDLE,
    val asrText: String = "",
    val replyText: String = "",
    val subtitle: String = "",
    val lastError: String? = null,
    val isStubRound: Boolean = false,
    val roundId: Long = 0L
)

/**
 * 一轮会话输入（ASR 文本或 stub 演示文本）。
 */
data class VoiceSessionInput(
    val asrText: String,
    val isStub: Boolean = false,
    val source: String = "user"
)

/**
 * 一轮会话结果。
 *
 * @property toolName Phase 7.5：本轮若经 [com.lanxin.android.builtin.systemtools.domain.DeviceToolBridge]
 *   命中工具则为工具 id，否则 null
 * @property toolOutcome 对应 Gate 后结果（类型擦除为 Any? 避免 pet 模型硬依赖 sealed 细节时可扩展）
 */
data class VoiceSessionResult(
    val asrText: String,
    val replyText: String,
    val subtitle: String,
    val phase: VoiceSessionPhase,
    val isStub: Boolean = false,
    val durationMs: Long = 0L,
    val error: String? = null,
    val toolName: String? = null,
    val toolOutcome: com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome? = null
)

/**
 * 桌宠总配置。
 *
 * @property enabled 总开关（默认关）
 * @property overlayRunning 悬浮层是否应运行（需 SYSTEM_ALERT_WINDOW）
 * @property autoListen 启动后是否自动进入听（M1 默认 false）
 * @property live2dModelPath Live2D model3.json 路径（DataStore `live2d_model_path`）；
 *   空则 debug 可旁路 meiju-ref，release 用占位
 */
data class PetConfig(
    val enabled: Boolean = false,
    val overlayRunning: Boolean = false,
    val autoListen: Boolean = false,
    val live2dModelPath: String = "",
    /** 悬浮窗位置（px，Gravity.TOP|START）；未保存时 [OverlayPosition.UNSET]。 */
    val overlayPosition: OverlayPosition = OverlayPosition(),
    /**
     * 陪伴页背景预设 ID（[CompanionBackgrounds.PRESETS]）或
     * [CompanionBackgrounds.CUSTOM_ID]（配合 [companionBgCustomPath]）。
     */
    val companionBgPresetId: String = CompanionBackgrounds.DEFAULT_ID,
    /**
     * 自定义背景图路径（仅 custom 模式使用）。
     * 优先存相对 `LanXin/` 键（如 `backgrounds/a.jpg`），兼容历史绝对路径。
     */
    val companionBgCustomPath: String = "",
    /**
     * SAF 公共 `LanXin/` 树 Uri（OpenDocumentTree 持久化）。
     * 空 = 未授权；引擎写盘仍优先 File，SAF 用于公共目录可写/镜像。
     */
    val lanXinSafTreeUri: String = ""
)

/**
 * 桌宠设置门面。
 */
interface PetSettings {
    suspend fun getConfig(): PetConfig
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setOverlayRunning(running: Boolean)
    suspend fun setAutoListen(autoListen: Boolean)
    suspend fun setLive2dModelPath(path: String?)

    /** 持久化悬浮窗位置（拖拽松手后写入）。 */
    suspend fun setOverlayPosition(x: Int, y: Int)

    /** 陪伴页背景：预设 ID 或 custom + 可选自定义图路径。 */
    suspend fun setCompanionBackground(presetId: String, customPath: String? = null)

    /**
     * 保存/清除公共 `LanXin/` SAF 树 Uri。
     * @param uri content:// tree Uri；null 或空串清除
     */
    suspend fun setLanXinSafTreeUri(uri: String?)
}

/**
 * 对话回复生成器（思考阶段）。
 *
 * M1：可 stub；后续接 ChatRouter / 云端 / 本地 LLM。
 * **不**把结果塞进文本 Chat 输入框。
 */
interface PetChatResponder {
    suspend fun respond(userText: String): String
}

/**
 * Bridge 命令（原生 → WebView 或反向）。
 */
enum class PetBridgeCommand {
    /** 更新会话状态。 */
    SESSION_STATE,

    /** 显示气泡字幕。 */
    SHOW_BUBBLE,

    /** 隐藏气泡。 */
    HIDE_BUBBLE,

    /** 设置角色状态（听/想/说/闲）。 */
    SET_PET_STATE,

    /** Native → Web：加载 Live2D model3（M2b）。 */
    LOAD_LIVE2D,

    /** Web → Native：Live2D 显示模式回传。 */
    LIVE2D_STATUS,

    /** Native → Web：表情 / 口型姿态（M2b 打磨，随会话相位）。 */
    SET_EXPRESSION,

    /** Native → Web：播放官方 motion 组（Idle / TapBody）。 */
    PLAY_MOTION,

    /** Web → Native：模型被点触（hit area / 点击）。 */
    MODEL_TAPPED,

    /** WebView 请求开始语音会话。 */
    START_VOICE,

    /** WebView 请求停止。 */
    STOP_VOICE,

    /** 关闭桌宠。 */
    CLOSE_PET,

    /** 心跳 / ping。 */
    PING,

    /** 未知。 */
    UNKNOWN
}

/**
 * Bridge 消息（JSON 可序列化结构的纯数据）。
 */
data class PetBridgeMessage(
    val command: PetBridgeCommand,
    val payload: Map<String, String> = emptyMap(),
    val timestampMs: Long = 0L
) {
    fun toWireMap(): Map<String, Any?> = mapOf(
        "command" to command.name,
        "payload" to payload,
        "timestampMs" to timestampMs
    )

    companion object {
        fun parseCommand(raw: String?): PetBridgeCommand {
            if (raw.isNullOrBlank()) return PetBridgeCommand.UNKNOWN
            return runCatching { PetBridgeCommand.valueOf(raw.trim().uppercase()) }
                .getOrDefault(PetBridgeCommand.UNKNOWN)
        }

        fun fromWire(command: String?, payload: Map<String, String>?, timestampMs: Long = 0L): PetBridgeMessage {
            return PetBridgeMessage(
                command = parseCommand(command),
                payload = payload.orEmpty(),
                timestampMs = timestampMs
            )
        }
    }
}
