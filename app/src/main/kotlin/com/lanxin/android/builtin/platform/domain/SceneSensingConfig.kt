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
 * 摄像头 → 场景识别配置。
 *
 * 默认 **全关**：不申请相机、不拍、不识别、不改陪伴背景。
 * 首次开启必须用户明确同意（[consentGranted]）+ 系统 CAMERA 权限。
 *
 * 能力边界：
 * - 仅用户显式点击「识别当前场景」时走系统预览快照（TakePicturePreview）
 * - **本地**启发式分类，不上传原图
 * - 结果只映射到**现有**陪伴能力（背景预设 / 状态文案 / 可选 mood 提示）
 * - 禁止后台偷拍、禁止持续预览流、禁止发明 Live2D 资源
 */
data class SceneSensingConfig(
    /** 总开关；关则 Gate 拒绝任何识别 */
    val enabled: Boolean = false,
    /** 用户已读隐私说明并同意；首次开必须 true */
    val consentGranted: Boolean = false,
    /** 最近一次场景 id（可清）；空 = 无缓存 */
    val lastSceneId: String = "",
    /** 最近一次状态文案（可清） */
    val lastStatusText: String = ""
) {
    companion object {
        const val FEATURE_NAME = "camera_scene"
    }
}
