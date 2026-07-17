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

package com.lanxin.android.plugin.claw.domain

/**
 * Claw / 机器人动态插件宿主配置。
 *
 * 默认 **关闭**：不启动常驻前台服务、不向动态插件暴露真实 PlatformHost 能力。
 * 动态 .apk 的扫描/签名/enable 仍走既有 PluginCatalog 管线；本配置只控「常驻宿主」产品路径。
 */
data class ClawHostConfig(
    /** 总开关：开后允许启动常驻服务并启用 PlatformHost 能力 */
    val enabled: Boolean = false,
    /** 用户是否请求保持前台常驻（需 enabled=true 才真正 startService） */
    val residentRequested: Boolean = false
) {
    /** 是否应启动常驻前台服务 */
    fun shouldRunResident(): Boolean = enabled && residentRequested

    companion object {
        const val FEATURE_ID = "claw_host"
    }
}
