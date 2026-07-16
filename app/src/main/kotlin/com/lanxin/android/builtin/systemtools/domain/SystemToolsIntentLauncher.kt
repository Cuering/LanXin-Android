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

/**
 * 系统工具 Intent 启动抽象（闹钟 / 日历创建等）。
 *
 * 真机：[com.lanxin.android.builtin.systemtools.data.AndroidSystemToolsIntentLauncher]
 * 单测：记录调用的 Fake，不触碰 Android Runtime。
 */
interface SystemToolsIntentLauncher {
    fun launch(spec: IntentLaunchSpec): IntentLaunchResult
}

/**
 * 与 Android Intent 解耦的启动规格。
 *
 * @param action Intent action（如 SET_ALARM / SHOW_ALARMS / INSERT）
 * @param dataUri 可选 data URI（如 content://com.android.calendar/events）
 * @param mimeType 可选 MIME
 * @param extras 扁平 extras（Int/Long/Boolean/String/List&lt;Int&gt;）
 * @param description 人类可读摘要
 */
data class IntentLaunchSpec(
    val action: String,
    val dataUri: String? = null,
    val mimeType: String? = null,
    val extras: Map<String, Any?> = emptyMap(),
    val description: String = ""
)

sealed class IntentLaunchResult {
    data class Ok(
        val action: String,
        val launched: Boolean = true,
        val resolvedActivity: String? = null,
        val description: String = ""
    ) : IntentLaunchResult()

    data class ActivityNotFound(
        val message: String,
        val action: String
    ) : IntentLaunchResult()

    data class Error(
        val message: String,
        val code: String = "intent_error"
    ) : IntentLaunchResult()
}
