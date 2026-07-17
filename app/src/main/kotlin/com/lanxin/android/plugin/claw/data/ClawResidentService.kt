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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lanxin.android.R
import com.lanxin.android.plugin.PluginManager
import com.lanxin.android.plugin.claw.domain.ClawHostGate
import com.lanxin.android.plugin.claw.domain.ClawHostSettings
import com.lanxin.android.plugin.claw.domain.ResidentCapablePlugin
import com.lanxin.android.presentation.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 机器人 / Claw 动态插件常驻前台服务（默认不启动）。
 *
 * - 类型 dataSync：保持进程存活，不录音、不截屏、不挂 VPN
 * - 仅当设置总开关 + 常驻请求均为 true 时由 [ClawResidentController] 拉起
 * - 对已加载且实现 [ResidentCapablePlugin] 的插件派发 onResidentStart/Stop
 */
@AndroidEntryPoint
class ClawResidentService : Service() {

    @Inject
    lateinit var settings: ClawHostSettings

    @Inject
    lateinit var platformHost: DefaultPlatformHost

    @Inject
    lateinit var pluginManager: PluginManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var residentPluginsNotified = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("机器人宿主常驻中", "动态插件可保持存活"))
        platformHost.setResidentRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { notifyResidentStop() }
                platformHost.setResidentRunning(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                scope.launch {
                    val config = settings.getConfig()
                    if (!ClawHostGate.shouldRunResident(config)) {
                        notifyResidentStop()
                        platformHost.setResidentRunning(false)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@launch
                    }
                    if (!residentPluginsNotified) {
                        notifyResidentStart()
                        residentPluginsNotified = true
                    }
                    val status = platformHost.lastStatusLine()
                    if (status != null) {
                        val nm = getSystemService(NotificationManager::class.java)
                        nm?.notify(
                            NOTIFICATION_ID,
                            buildNotification(status.title, status.text)
                        )
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.launch { notifyResidentStop() }
        platformHost.setResidentRunning(false)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun notifyResidentStart() {
        val host = platformHost
        for (plugin in pluginManager.getPlugins()) {
            if (plugin is ResidentCapablePlugin) {
                runCatching { plugin.onResidentStart(host) }
            }
        }
    }

    private suspend fun notifyResidentStop() {
        for (plugin in pluginManager.getPlugins()) {
            if (plugin is ResidentCapablePlugin) {
                runCatching { plugin.onResidentStop() }
            }
        }
        residentPluginsNotified = false
    }

    private fun buildNotification(title: String, text: String): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ClawResidentService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_rounded_chat)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "停止常驻", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "机器人 / Claw 常驻",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "动态机器人插件宿主前台服务（用户显式开启）"
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "lanxin_claw_resident"
        const val NOTIFICATION_ID = 7101
        const val ACTION_START = "com.lanxin.android.claw.ACTION_START"
        const val ACTION_STOP = "com.lanxin.android.claw.ACTION_STOP"
    }
}
