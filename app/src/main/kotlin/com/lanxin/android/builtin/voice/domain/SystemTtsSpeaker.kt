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

package com.lanxin.android.builtin.voice.domain

/**
 * 系统级 TTS 回退接口（Android TextToSpeech）。
 *
 * 与 [TtsEngine]（离线 ONNX）分离，便于 JVM 单测 mock。
 */
interface SystemTtsSpeaker {
    /** 引擎是否可用。 */
    val available: Boolean

    /**
     * 合成并播放文字。
     * @return true 正常播完；false 初始化失败 / 引擎不支持
     */
    suspend fun speak(text: String): Boolean
}
