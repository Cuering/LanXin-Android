package com.lanxin.android.plugin.dynamic

import java.io.File

/**
 * 为插件 APK 创建 ClassLoader。
 *
 * 真机默认实现使用 `dalvik.system.PathClassLoader`；
 * 单测可注入假实现，避免依赖 Android runtime / dex。
 */
fun interface DynamicPluginClassLoaderFactory {
    /**
     * @param apkFile 插件包
     * @param parent 宿主 ClassLoader（需能解析 LanXinPlugin 等 API）
     * @return ClassLoader；失败返回 null（不抛到宿主）
     */
    fun create(apkFile: File, parent: ClassLoader): ClassLoader?
}

/**
 * Android 运行时 PathClassLoader 工厂。
 *
 * 通过反射创建，以便在纯 JVM 单测 classpath 上编译通过；
 * 真机 dalvik 存在时正常工作。
 */
object AndroidPathClassLoaderFactory : DynamicPluginClassLoaderFactory {
    override fun create(apkFile: File, parent: ClassLoader): ClassLoader? {
        return try {
            val clazz = Class.forName("dalvik.system.PathClassLoader")
            val ctor = clazz.getConstructor(String::class.java, ClassLoader::class.java)
            ctor.newInstance(apkFile.absolutePath, parent) as ClassLoader
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * 从 ClassLoader 实例化 [com.lanxin.android.plugin.LanXinPlugin]。
 */
object DynamicPluginInstantiator {

    fun instantiate(
        classLoader: ClassLoader,
        entryClass: String
    ): Result<com.lanxin.android.plugin.LanXinPlugin> {
        return try {
            val clazz = Class.forName(entryClass, true, classLoader)
            val instance = clazz.getDeclaredConstructor().newInstance()
            val plugin = instance as? com.lanxin.android.plugin.LanXinPlugin
                ?: return Result.failure(
                    IllegalStateException("entryClass 未实现 LanXinPlugin: $entryClass")
                )
            // 校验 id 非空
            if (plugin.id.isBlank()) {
                return Result.failure(IllegalStateException("插件 id 为空: $entryClass"))
            }
            Result.success(plugin)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
