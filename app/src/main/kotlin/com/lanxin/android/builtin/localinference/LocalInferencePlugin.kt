package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.domain.LocalInferencePluginConfig
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地脑（端侧 LLM）编译期可选插件。
 * **默认不启用** — 主程序对话走云端/MNNChat；需在插件管理开启。
 */
@Singleton
class LocalInferencePlugin @Inject constructor(
    private val localInferenceSettings: LocalInferenceSettings
) : LanXinPlugin {

    override val id = LocalInferencePluginConfig.PLUGIN_ID
    override val name = "本地脑"
    override val version = "1.0.0"
    override val description =
        "端侧本地 LLM（默认不启用；插件管理开启后才参与路由。日常请用 MNNChat/云端 API）"

    override suspend fun onLoad(context: PluginContext) {
        runCatching { localInferenceSettings.setEnabled(true) }
    }

    override suspend fun onUnload() {
        runCatching {
            localInferenceSettings.setPreferLocal(false)
            localInferenceSettings.setEnabled(false)
        }
    }
}
