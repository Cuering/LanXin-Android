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

package com.lanxin.android.plugin.claw.data

import android.content.Context
import android.content.Intent
import android.os.Build
import com.lanxin.android.plugin.claw.domain.ClawHostGate
import com.lanxin.android.plugin.claw.domain.ClawHostSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按配置启动 / 停止 [ClawResidentService]。
 *
 * 默认关：不调 start。签名 / 动态加载不在此层。
 */
@Singleton
class ClawResidentController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: ClawHostSettings,
    private val platformHost: DefaultPlatformHost
) {

    /**
     * 读取最新配置并同步服务状态。
     * @return 是否要求常驻运行（shouldRun）
     */
    suspend fun syncFromSettings(): Boolean {
        val config = settings.getConfig()
        val should = ClawHostGate.shouldRunResident(config)
        if (should) {
            startService()
        } else {
            stopService()
        }
        return should
    }

    fun startService() {
        val intent = Intent(appContext, ClawResidentService::class.java).apply {
            action = ClawResidentService.ACTION_START
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }.onFailure {
            android.util.Log.w("ClawResidentController", "startForegroundService denied", it)
        }
    }

    fun stopService() {
        val intent = Intent(appContext, ClawResidentService::class.java).apply {
            action = ClawResidentService.ACTION_STOP
        }
        // 先发 STOP，再 stopService，避免粘性重启
        runCatching { appContext.startService(intent) }
        runCatching { appContext.stopService(intent) }
        platformHost.setResidentRunning(false)
    }
}
