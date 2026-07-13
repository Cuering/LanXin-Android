package com.lanxin.android.plugin

/**
 * 所有插件的基接口。
 *
 * 每个插件是一个独立的能力单元，拥有自己的生命周期。
 */
interface LanXinPlugin {

    /** 插件唯一标识 */
    val id: String

    /** 插件可读名称 */
    val name: String

    /** 插件版本号 */
    val version: String

    /**
     * 插件被加载时调用。
     * 在此注册工具、初始化资源。
     */
    suspend fun onLoad(context: PluginContext)

    /**
     * 插件被卸载时调用。
     * 在此释放资源。
     */
    suspend fun onUnload() {}
}
