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

package com.lanxin.android.builtin.localinference.domain

/**
 * 网络连通性探测（有网 / 无网）。
 *
 * Phase 6.2 离线兜底依赖此接口；实现默认走 [android.net.ConnectivityManager]。
 * 单测可注入假实现。
 */
fun interface NetworkStatusProvider {

    /**
     * @return true 表示当前具备可用网络（通常含 INTERNET + VALIDATED）；
     *         false 表示明确无网或无法确认为可用。
     */
    fun isNetworkAvailable(): Boolean
}
