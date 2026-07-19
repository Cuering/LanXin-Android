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
 * 导游 Guide（V1 规划）— 与导航 Navigate **独立**。
 *
 * 职责：景点/展品讲解、看世界识别讲解、历史文化与看点、附近景点介绍。
 * 看世界抓帧能力归属本模块侧（复用 companion vision），导航不强制开相机。
 *
 * 交付顺序：先 Navigate V1，再本模块 V1。
 */
object GuideConfig {
    /** 规划中：位置增强讲解（非工具注册名，文档用） */
    const val FEATURE_ID = "guide"

    /** 复用全屏陪伴「看世界」 */
    const val VISION_FEATURE = "companion_vision_explain"

    /** 可选：附近景点介绍（依赖 get_location + web_search，非导航 POI） */
    const val NEARBY_SIGHTS_HINT = "nearby_sights_explain"
}
