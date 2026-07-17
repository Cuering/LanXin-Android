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

import com.lanxin.android.plugin.claw.domain.ClawHostGate
import com.lanxin.android.plugin.claw.domain.ClawHostSettings
import com.lanxin.android.plugin.claw.domain.PlatformHost
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * 默认 PlatformHost：读 Claw 配置门闸 + 内存 keep-alive / 通知状态。
 *
 * 常驻服务通过 [setResidentRunning] 同步运行态；不直接启动 Service（由 Controller）。
 */
@Singleton
class DefaultPlatformHost @Inject constructor(
    private val settings: ClawHostSettings
) : PlatformHost {

    private val keepAlive = ConcurrentHashMap<String, String>()
    private val residentRunning = AtomicBoolean(false)
    private val lastStatus = AtomicReference<StatusLine?>(null)
    private val qrRequests = ConcurrentHashMap<String, QrRequest>()

    data class StatusLine(
        val pluginId: String,
        val title: String,
        val text: String,
        val atMs: Long = System.currentTimeMillis()
    )

    data class QrRequest(
        val requestId: String,
        val pluginId: String,
        val title: String,
        val atMs: Long = System.currentTimeMillis()
    )

    private fun currentOpen(): Boolean = runBlocking {
        ClawHostGate.isHostCapabilityOpen(settings.getConfig())
    }

    fun setResidentRunning(running: Boolean) {
        residentRunning.set(running)
    }

    fun keepAliveSnapshot(): Map<String, String> = keepAlive.toMap()

    fun lastStatusLine(): StatusLine? = lastStatus.get()

    fun pendingQrRequests(): List<QrRequest> = qrRequests.values.toList()

    fun clearQrRequest(requestId: String) {
        qrRequests.remove(requestId)
    }

    override fun isCapabilityOpen(): Boolean = currentOpen()

    override fun isResidentRunning(): Boolean = residentRunning.get()

    override fun requestKeepAlive(pluginId: String, reason: String): Boolean {
        if (!currentOpen()) return false
        val id = pluginId.trim()
        if (id.isEmpty()) return false
        keepAlive[id] = reason
        return true
    }

    override fun cancelKeepAlive(pluginId: String) {
        keepAlive.remove(pluginId.trim())
    }

    override fun showStatusNotification(pluginId: String, title: String, text: String) {
        if (!currentOpen()) return
        lastStatus.set(
            StatusLine(
                pluginId = pluginId.trim(),
                title = title.take(80),
                text = text.take(200)
            )
        )
    }

    override fun postQrScanRequest(pluginId: String, title: String): String? {
        if (!currentOpen()) return null
        val id = pluginId.trim()
        if (id.isEmpty()) return null
        val requestId = UUID.randomUUID().toString()
        qrRequests[requestId] = QrRequest(
            requestId = requestId,
            pluginId = id,
            title = title.take(80)
        )
        return requestId
    }
}
