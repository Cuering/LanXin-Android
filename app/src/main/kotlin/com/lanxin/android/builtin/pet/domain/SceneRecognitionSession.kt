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

package com.lanxin.android.builtin.pet.domain

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 场景识别会话缓存（仅内存，不落盘图像）。
 *
 * 关闭功能或用户主动清理时 [clear]；供陪伴页轻量文案反馈。
 */
@Singleton
class SceneRecognitionSession @Inject constructor() {

    private val _lastResult = MutableStateFlow<SceneRecognitionResult?>(null)
    val lastResult: StateFlow<SceneRecognitionResult?> = _lastResult.asStateFlow()

    fun publish(result: SceneRecognitionResult) {
        _lastResult.value = result
    }

    fun clear() {
        _lastResult.value = null
    }

    fun current(): SceneRecognitionResult? = _lastResult.value

    /** 陪伴页/状态栏用短文案；无缓存返回 null。 */
    fun feedbackLine(): String? {
        val r = _lastResult.value ?: return null
        return "场景：${r.label.displayName} · ${r.label.feedback}"
    }
}
