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

package com.lanxin.android.builtin.localinference.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.lanxin.android.builtin.localinference.domain.NetworkStatusProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 [ConnectivityManager] 的网络状态实现。
 *
 * 对齐 [com.lanxin.android.builtin.platform.tools.SystemInfoTool] 的判定：
 * activeNetwork + NET_CAPABILITY_INTERNET + NET_CAPABILITY_VALIDATED。
 */
@Singleton
class ConnectivityNetworkStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkStatusProvider {

    override fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
