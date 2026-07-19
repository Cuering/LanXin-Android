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

package com.lanxin.android.builtin.capabilities.domain

/**
 * 位置能力配置。
 *
 * 默认 **prefs ON**（可被智能能力主开关与本开关关闭）；
 * **不**后台持续定位；tool 首次调用时申请运行时权限。
 */
data class LocationConfig(
    /** 分项开关；与 master 合成后才可调 get_location */
    val enabled: Boolean = DEFAULT_ENABLED
) {
    companion object {
        const val TOOL_NAME = "get_location"
        const val DEFAULT_ENABLED = true
    }
}
