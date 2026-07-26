package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.domain.TtsPluginConfig
import com.lanxin.android.builtin.voice.domain.TtsSettings
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsPlugin @Inject constructor(
    private val ttsSettings: TtsSettings
) : LanXinPlugin {

    override val id = TtsPluginConfig.PLUGIN_ID
    override val name = "TTS 语音输出"
    override val version = "1.0.0"
    override val description =
        "朗读助手回复（默认安装启用；可在插件管理或桌宠页关闭）"

    override suspend fun onLoad(context: PluginContext) {
        runCatching { ttsSettings.setEnabled(true) }
    }

    override suspend fun onUnload() {
        runCatching { ttsSettings.setEnabled(false) }
    }
}
