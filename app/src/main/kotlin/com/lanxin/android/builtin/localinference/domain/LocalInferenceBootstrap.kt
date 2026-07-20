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

package com.lanxin.android.builtin.localinference.domain

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 本地推理一键开启 / 冷启动懒加载。
 *
 * 场景（Issue #120）：
 * - 会话 `forceLocal` 且已有 `modelPath`：自动 `setEnabled(true)` + `engine.load`
 * - 设置页「一键开启本地对话」：有路径则就绪；无路径引导选完整模型文件夹
 * - 失败后用户点重试：再次 [ensureReady]
 *
 * 线程安全：串行化 load，避免多路 decide 并发 load。
 */
@Singleton
class LocalInferenceBootstrap @Inject constructor(
    private val settings: LocalInferenceSettings,
    private val engine: LocalLlmEngine
) {

    private val mutex = Mutex()

    /**
     * 确保本地引擎可用于对话。
     *
     * @param enableIfNeeded 为 true 时，路径非空则自动打开开关再 load（forceLocal / 一键开启）
     */
    suspend fun ensureReady(enableIfNeeded: Boolean = true): Result = mutex.withLock {
        if (engine.isReady) {
            return@withLock Result(status = Status.READY, message = MSG_READY)
        }

        val config = settings.getConfig()
        val path = config.modelPath.trim()
        if (path.isEmpty()) {
            return@withLock Result(
                status = Status.NEED_MODEL_PATH,
                message = MSG_NEED_MODEL
            )
        }

        if (enableIfNeeded && !config.enabled) {
            settings.setEnabled(true)
        }

        val effective = settings.getConfig().let { c ->
            if (enableIfNeeded && !c.enabled) c.copy(enabled = true) else c
        }
        if (!effective.enabled) {
            return@withLock Result(
                status = Status.LOAD_FAILED,
                message = MSG_DISABLED
            )
        }

        val ok = engine.load(effective)
        if (ok && engine.isReady) {
            Result(status = Status.READY, message = MSG_READY)
        } else {
            val err = engine.lastError
            Result(
                status = Status.LOAD_FAILED,
                message = formatLoadFailed(err),
                lastError = err
            )
        }
    }

    enum class Status {
        /** 引擎已 load，可本地对话。 */
        READY,

        /** 无保存的模型路径，需导入完整包。 */
        NEED_MODEL_PATH,

        /** 有路径但 load 失败，可重试。 */
        LOAD_FAILED
    }

    data class Result(
        val status: Status,
        val message: String,
        val lastError: String? = null
    ) {
        val isReady: Boolean get() = status == Status.READY
    }

    companion object {
        const val MSG_READY = "本地模型已就绪"
        const val MSG_NEED_MODEL =
            "尚未配置模型。请选择完整模型文件夹（config.json + *.mnn）。"
        const val MSG_DISABLED = "本地推理未启用"
        const val MSG_RETRY_HINT = "可点「重试加载」"

        /** 会话 forceLocal 未就绪（缩短，可重试）。 */
        const val FORCE_LOCAL_UNAVAILABLE_SHORT =
            "本地模型未就绪。有路径可点重试加载；无模型请导入完整文件夹。"

        /** 无网且本地不可用（缩短）。 */
        const val OFFLINE_LOCAL_UNAVAILABLE_SHORT =
            "无网络且本地模型未就绪。请开启本地对话或导入模型后重试。"

        fun formatLoadFailed(err: String?): String {
            val detail = err?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
            return "本地模型加载失败$detail。$MSG_RETRY_HINT"
        }
    }
}
