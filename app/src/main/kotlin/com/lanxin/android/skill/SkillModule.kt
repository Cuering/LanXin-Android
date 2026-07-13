package com.lanxin.android.skill

import com.lanxin.android.plugin.PluginManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Skill 子系统 DI。
 *
 * - [SkillLoader] / [SkillEngine] 使用 @Inject constructor
 * - 通过 [provideSkillEngineRegistration] 把 SkillEngine 注册进 [PluginManager]
 */
@Module
@InstallIn(SingletonComponent::class)
object SkillModule {

    @Provides
    @Singleton
    fun provideSkillEngineRegistration(
        pluginManager: PluginManager,
        skillEngine: SkillEngine
    ): SkillEngineRegistration {
        pluginManager.register(skillEngine)
        return SkillEngineRegistration(skillEngine)
    }
}

/** 注册副作用载体，触发 [SkillModule.provideSkillEngineRegistration]。 */
data class SkillEngineRegistration(val engine: SkillEngine)
