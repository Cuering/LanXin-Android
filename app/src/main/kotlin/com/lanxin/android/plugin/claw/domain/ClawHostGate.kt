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
 * Claw 宿主门闸：纯逻辑，便于 JVM 单测。
 *
 * - 关：PlatformHost 一律 NoOp；不启动常驻
 * - 开但未请求常驻：Host 能力可用，但不拉起 FGS
 * - 开且请求常驻：允许 startForegroundService
 */
object ClawHostGate {

    fun isEnabled(config: ClawHostConfig): Boolean = config.enabled

    fun shouldRunResident(config: ClawHostConfig): Boolean = config.shouldRunResident()

    /**
     * 宿主能力是否对动态插件开放（真实实现 vs NoOp）。
     * 仅 [ClawHostConfig.enabled]；与 residentRequested 解耦。
     */
    fun isHostCapabilityOpen(config: ClawHostConfig): Boolean = config.enabled

    /**
     * 关时拒绝常驻相关操作的可读原因；开则 null。
     */
    fun denyResidentIfDisabled(config: ClawHostConfig): String? {
        if (!config.enabled) {
            return "claw_host_disabled：机器人常驻宿主已关闭（设置 → 机器人 / Claw 宿主）"
        }
        if (!config.residentRequested) {
            return "claw_resident_not_requested：请先开启「保持前台常驻」"
        }
        return null
    }
}
