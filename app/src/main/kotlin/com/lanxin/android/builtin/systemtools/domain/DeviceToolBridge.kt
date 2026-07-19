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

package com.lanxin.android.builtin.systemtools.domain

import com.lanxin.android.builtin.systemtools.data.DeviceToolRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7.5 统一设备工具桥：Chat / VoiceSession / MCP 共用的发现与调用入口。
 *
 * ```
 * 用户意图 / tool_call
 *        │
 *        ▼
 *  DeviceToolBridge.resolve / invoke
 *        │
 *        ├─ DeviceToolRegistry（发现）
 *        └─ DeviceToolGate（总开关 + 分项 + 确认）
 *               └─ DeviceTool.invoke
 * ```
 *
 * 不绕过 [DeviceToolGate]；默认尊重 SystemTools 开关与确认策略。
 * 不下载模型、不接厂商深度协议。
 */
@Singleton
class DeviceToolBridge @Inject constructor(
    private val registry: DeviceToolRegistry,
    private val settings: SystemToolsSettings,
    private val intentResolver: DeviceToolIntentResolver
) {

    // named：避免 trailing lambda 绑到最后的 smartMasterProvider
    private val gate = DeviceToolGate(
        configProvider = { settings.getConfig() }
    )

    fun listToolNames(): Set<String> = registry.names()

    fun listTools(): List<DeviceTool> = registry.all()

    fun getTool(name: String): DeviceTool? = registry.get(name)

    /**
     * 从用户自然语言解析工具计划；未命中或工具未注册返回 null。
     */
    fun resolveIntent(userText: String): DeviceToolPlan? {
        val plan = intentResolver.resolve(userText, availableToolNames = registry.names())
            ?: return null
        if (registry.get(plan.toolName) == null) return null
        return plan
    }

    /**
     * 经 Gate 调用已注册工具。
     */
    suspend fun invoke(
        toolName: String,
        args: Map<String, Any?> = emptyMap(),
        confirmed: Boolean = false
    ): DeviceToolOutcome {
        val tool = registry.get(toolName)
            ?: return DeviceToolOutcome.Error(
                message = "unknown tool: $toolName",
                code = "unknown_tool"
            )
        return gate.invoke(tool, args, confirmed = confirmed)
    }

    /**
     * 解析意图并执行（一站式：意图 → 选 id → Gate → 结果）。
     *
     * @return null 表示无工具意图（调用方走纯闲聊）；非 null 为 Gate 后结果
     */
    suspend fun resolveAndInvoke(
        userText: String,
        confirmed: Boolean = false
    ): DeviceToolInvocation? {
        val plan = resolveIntent(userText) ?: return null
        val outcome = invoke(
            toolName = plan.toolName,
            args = plan.args,
            confirmed = confirmed || plan.confirmed
        )
        return DeviceToolInvocation(plan = plan, outcome = outcome)
    }

    /**
     * Chat 一轮：意图发现 +（可选）经 Gate 调用。
     *
     * 供 ChatRouter / ChatViewModel 注入桌宠/系统工具上下文；
     * `execute=false` 时仅返回意图与 needsTools，不落副作用。
     */
    suspend fun chatTurn(
        userText: String,
        confirmed: Boolean = false,
        execute: Boolean = true
    ): DeviceToolTurn {
        val plan = resolveIntent(userText)
        if (plan == null) {
            return DeviceToolTurn(
                channel = DeviceToolChannel.CHAT,
                plan = null,
                outcome = null,
                needsTools = false,
                summary = null
            )
        }
        if (!execute) {
            return DeviceToolTurn(
                channel = DeviceToolChannel.CHAT,
                plan = plan,
                outcome = null,
                needsTools = true,
                summary = null
            )
        }
        val outcome = invoke(
            toolName = plan.toolName,
            args = plan.args,
            confirmed = confirmed || plan.confirmed
        )
        return DeviceToolTurn(
            channel = DeviceToolChannel.CHAT,
            plan = plan,
            outcome = outcome,
            needsTools = true,
            summary = summarize(outcome, plan.toolName)
        )
    }

    /**
     * VoiceSession 一轮：与 [chatTurn] 同路径，channel 标记为 VOICE。
     *
     * [VoiceSessionCoordinator] 默认 `execute=true`；写确认仍走 Gate。
     */
    suspend fun voiceTurn(
        userText: String,
        confirmed: Boolean = false,
        execute: Boolean = true
    ): DeviceToolTurn {
        val plan = resolveIntent(userText)
        if (plan == null) {
            return DeviceToolTurn(
                channel = DeviceToolChannel.VOICE,
                plan = null,
                outcome = null,
                needsTools = false,
                summary = null
            )
        }
        if (!execute) {
            return DeviceToolTurn(
                channel = DeviceToolChannel.VOICE,
                plan = plan,
                outcome = null,
                needsTools = true,
                summary = null
            )
        }
        val outcome = invoke(
            toolName = plan.toolName,
            args = plan.args,
            confirmed = confirmed || plan.confirmed
        )
        return DeviceToolTurn(
            channel = DeviceToolChannel.VOICE,
            plan = plan,
            outcome = outcome,
            needsTools = true,
            summary = summarize(outcome, plan.toolName)
        )
    }

    /** 是否命中设备工具意图（供 ChatRouter `needsTools`）。 */
    fun detectsToolIntent(userText: String): Boolean = resolveIntent(userText) != null

    /**
     * 将 [DeviceToolOutcome] 压成可说/可聊的短文案（桌宠 TTS / Chat 回灌）。
     */
    fun summarize(outcome: DeviceToolOutcome, toolName: String? = null): String {
        val prefix = toolName?.let { "[$it] " }.orEmpty()
        return when (outcome) {
            is DeviceToolOutcome.Ok -> {
                val msg = outcome.message?.takeIf { it.isNotBlank() }
                val dataHint = outcome.data.entries
                    .take(4)
                    .joinToString(", ") { (k, v) -> "$k=$v" }
                    .takeIf { it.isNotBlank() }
                when {
                    msg != null && dataHint != null -> "$prefix$msg（$dataHint）"
                    msg != null -> "$prefix$msg"
                    dataHint != null -> "${prefix}完成：$dataHint"
                    else -> "${prefix}好了。"
                }
            }
            is DeviceToolOutcome.Denied ->
                "${prefix}做不到：${outcome.reason}"
            is DeviceToolOutcome.NeedsConfirmation ->
                "${prefix}需要你确认后再做：${outcome.summary}"
            is DeviceToolOutcome.Error ->
                "${prefix}出错了：${outcome.message}"
        }
    }

    companion object {
        /**
         * JVM 单测工厂（无 Hilt / 无 DataStore）。
         */
        fun forTest(
            registry: DeviceToolRegistry,
            configProvider: suspend () -> SystemToolsConfig,
            intentResolver: DeviceToolIntentResolver = DeviceToolIntentResolver()
        ): DeviceToolBridge {
            val settings = object : SystemToolsSettings {
                override suspend fun getConfig(): SystemToolsConfig = configProvider()
                override suspend fun setMasterEnabled(enabled: Boolean) = Unit
                override suspend fun setCalendarEnabled(enabled: Boolean) = Unit
                override suspend fun setAlarmEnabled(enabled: Boolean) = Unit
                override suspend fun setNotesEnabled(enabled: Boolean) = Unit
                override suspend fun setUserFileEnabled(enabled: Boolean) = Unit
                override suspend fun setRequireConfirmOnWrite(require: Boolean) = Unit
            }
            return DeviceToolBridge(registry, settings, intentResolver)
        }
    }
}

/**
 * 一次「意图 → 工具」调用快照。
 */
data class DeviceToolInvocation(
    val plan: DeviceToolPlan,
    val outcome: DeviceToolOutcome
)

/** 对话 / 桌宠通道。 */
enum class DeviceToolChannel {
    CHAT,
    VOICE
}

/**
 * 一轮 chat_turn / voice_turn 的上下文与结果。
 *
 * @property needsTools 是否命中工具意图（可喂 ChatRouter）
 * @property summary 可说/可聊短文案；未执行时为 null
 */
data class DeviceToolTurn(
    val channel: DeviceToolChannel,
    val plan: DeviceToolPlan?,
    val outcome: DeviceToolOutcome?,
    val needsTools: Boolean,
    val summary: String?
) {
    fun toInvocationOrNull(): DeviceToolInvocation? {
        val p = plan ?: return null
        val o = outcome ?: return null
        return DeviceToolInvocation(plan = p, outcome = o)
    }
}
