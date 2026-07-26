package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.domain.AsrPluginConfig
import com.lanxin.android.builtin.voice.domain.AsrSettings
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AsrPlugin @Inject constructor(
    private val asrSettings: AsrSettings
) : LanXinPlugin {

    override val id = AsrPluginConfig.PLUGIN_ID
    override val name = "离线 ASR"
    override val version = "1.0.0"
    override val description =
        "端侧语音识别（默认不启用；开启后才允许麦输入/听写）"

    override suspend fun onLoad(context: PluginContext) {
        runCatching { asrSettings.setEnabled(true) }
    }

    override suspend fun onUnload() {
        runCatching { asrSettings.setEnabled(false) }
    }
}
