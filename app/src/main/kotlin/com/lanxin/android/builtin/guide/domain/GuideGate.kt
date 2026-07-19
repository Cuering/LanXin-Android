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

package com.lanxin.android.builtin.guide.domain

/**
 * 导游 Guide 门闸（纯逻辑）。
 *
 * - 视觉讲解：依赖场景视觉 consent + 运行时 CAMERA（由 Companion 侧执行）
 * - 位置增强：智能能力主开关 + 位置 prefs；运行时再读 last known，不持续定位
 * - 与导航互跳：仅文案/提示，不要求导航工具在本 Gate 内注册
 */
object GuideGate {

    /**
     * 是否允许把 last known 位置注入讲解提示。
     * 视觉本身仍由相机 consent 决定；位置关闭时讲解可继续，只是无坐标上下文。
     */
    fun canAugmentWithLocation(
        masterEnabled: Boolean,
        locationPrefsOpen: Boolean
    ): Boolean = masterEnabled && locationPrefsOpen

    /**
     * 视觉讲解语义：用户已 consent 且会话「看世界」开、有相机权限。
     * （与 CompanionVisionSession 条件对齐，便于单测）
     */
    fun canExplainWithVision(
        visionLooking: Boolean,
        consentGranted: Boolean,
        cameraGranted: Boolean
    ): Boolean = visionLooking && consentGranted && cameraGranted
}
