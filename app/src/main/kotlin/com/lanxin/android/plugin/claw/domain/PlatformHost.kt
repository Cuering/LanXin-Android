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
 * 宿主向动态机器人插件暴露的扩展点（Claw 式）。
 *
 * 真实现仅在 Claw 宿主总开关开启时注入；否则为 [NoOpPlatformHost]。
 * 不替代 PluginSignatureVerifier / 市场完整性校验。
 */
interface PlatformHost {

    /** 当前宿主能力是否开放（总开关）。 */
    fun isCapabilityOpen(): Boolean

    /** 常驻前台服务是否在跑（或应在跑）。 */
    fun isResidentRunning(): Boolean

    /**
     * 插件请求保持存活（登记 keep-alive 引用）。
     * @return true 表示宿主已接受（能力开且常驻已请求）
     */
    fun requestKeepAlive(pluginId: String, reason: String = ""): Boolean

    /** 取消 keep-alive 引用。 */
    fun cancelKeepAlive(pluginId: String)

    /** 更新状态通知文案（常驻通知内容；关时 no-op）。 */
    fun showStatusNotification(pluginId: String, title: String, text: String)

    /**
     * 请求二维码扫描 UI（扩展点桩）。
     * MVP 不拉起真实扫码页，返回 requestId 或 null（能力关）。
     */
    fun postQrScanRequest(pluginId: String, title: String = "扫码登录"): String?
}

/**
 * 动态插件可选实现：常驻生命周期钩子。
 * 未实现则仅走 LanXinPlugin 工具注册。
 */
interface ResidentCapablePlugin {
    suspend fun onResidentStart(host: PlatformHost)
    suspend fun onResidentStop()
}

/** 能力关闭时的安全空实现。 */
object NoOpPlatformHost : PlatformHost {
    override fun isCapabilityOpen(): Boolean = false
    override fun isResidentRunning(): Boolean = false
    override fun requestKeepAlive(pluginId: String, reason: String): Boolean = false
    override fun cancelKeepAlive(pluginId: String) = Unit
    override fun showStatusNotification(pluginId: String, title: String, text: String) = Unit
    override fun postQrScanRequest(pluginId: String, title: String): String? = null
}
