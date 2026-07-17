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

package com.lanxin.android.builtin.platform.domain

/**
 * 设备感知（system_info）配置。
 *
 * 默认 **关闭**，避免 Agent 未授权读取设备/网络/电量等上下文；
 * 开启后 Agent 才可见/可调 [TOOL_NAME]。
 *
 * 能力边界（只读、无危险权限）：
 * - 型号 / 厂商 / Build 信息
 * - Android 版本 / SDK
 * - 本 App 包名与版本、ANDROID_ID
 * - 屏幕尺寸
 * - 网络类型（wifi/cellular…，非精确定位）
 * - 电量（粘性广播，无需额外权限）
 *
 * **不**包含：位置、通讯录、短信、麦克风、摄像头、无障碍、UsageStats。
 * **不**改 ChatRouter / needsTools：有工具 ≠ 首轮强制云端。
 */
data class DeviceSensingConfig(
    /** 总开关；关则不向模型暴露工具，调用亦拒绝 */
    val enabled: Boolean = false
) {
    companion object {
        const val TOOL_NAME = "system_info"
    }
}
