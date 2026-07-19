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
 * 麦克风权限检查（可注入，便于 JVM 单测）。
 *
 * 产品约束：不在后台偷偷录音；权限拒绝时返回温柔文案。
 */
fun interface MicPermissionChecker {

    /** 当前 RECORD_AUDIO 权限状态。 */
    fun check(): MicPermissionState
}

/**
 * 权限门控与文案（纯逻辑）。
 */
object MicPermissionGate {

    /** 是否允许开始录音（仅 GRANTED）。 */
    fun canRecord(state: MicPermissionState): Boolean =
        state == MicPermissionState.GRANTED

    /**
     * 权限拒绝时的用户可见文案（温柔、可操作）。
     */
    fun deniedMessage(state: MicPermissionState): String = when (state) {
        MicPermissionState.GRANTED -> ""
        MicPermissionState.DENIED ->
            "需要麦克风权限才能语音输入。请在系统弹窗中允许，或到系统设置中开启。"
        MicPermissionState.PERMANENTLY_DENIED ->
            "麦克风权限已被关闭。请到系统设置 → 应用 → 兰心 → 权限中手动开启麦克风。"
        MicPermissionState.UNKNOWN ->
            "暂时无法确认麦克风权限，请稍后重试或到系统设置中检查。"
    }

    /**
     * 综合引擎与权限，判断「试转写 / 语音输入」是否可走通。
     *
     * @return null 表示可继续；非 null 为阻断文案
     */
    fun blockReason(
        permission: MicPermissionState,
        engineReady: Boolean,
        enabled: Boolean,
        requireMic: Boolean = true
    ): String? {
        if (!enabled) {
            return "离线语音识别未启用。请到「设置 → 离线语音识别」打开开关。"
        }
        if (!engineReady) {
            return "语音识别模型未就绪。请确认：① 开关已开；② 已下载/导入 ASR 模型；" +
                "③ 路径有效。可到桌宠设置「一键下载 ASR」后重开开关自动加载。"
        }
        if (requireMic && !canRecord(permission)) {
            return deniedMessage(permission)
        }
        return null
    }
}
