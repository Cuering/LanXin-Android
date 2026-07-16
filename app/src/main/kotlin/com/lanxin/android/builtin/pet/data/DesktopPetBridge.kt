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

package com.lanxin.android.builtin.pet.data

import android.webkit.JavascriptInterface
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeMessage
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import com.lanxin.android.builtin.pet.domain.VoiceSessionSnapshot

/**
 * 桌宠 WebView JS Bridge（概念对齐妹居 DesktopPetBridge）。
 *
 * 注入名：`DesktopPetBridge`
 * Web → Native：close / ping / requestVoice
 * Native → Web：evaluateJavascript 推送编码消息（由 Service 调用 [lastOutbound] 观察或直接 eval）
 */
class DesktopPetBridge(
    private val onCommand: (PetBridgeMessage) -> Unit
) {

    @Volatile
    var lastOutbound: String = ""
        private set

    @Volatile
    var lastInbound: String = ""
        private set

    @JavascriptInterface
    fun postMessage(raw: String?) {
        val text = raw.orEmpty()
        lastInbound = text
        val msg = PetBridgeProtocol.decode(text)
        onCommand(msg)
    }

    @JavascriptInterface
    fun closePet() {
        onCommand(
            PetBridgeMessage(
                command = PetBridgeCommand.CLOSE_PET,
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    @JavascriptInterface
    fun ping(): String = "pong"

    @JavascriptInterface
    fun requestStartVoice() {
        onCommand(
            PetBridgeMessage(
                command = PetBridgeCommand.START_VOICE,
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    @JavascriptInterface
    fun requestStopVoice() {
        onCommand(
            PetBridgeMessage(
                command = PetBridgeCommand.STOP_VOICE,
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    /** Native 侧编码会话状态，供 evaluateJavascript 推送。 */
    fun encodeSession(snapshot: VoiceSessionSnapshot): String {
        val msg = PetBridgeProtocol.sessionStateMessage(snapshot)
        lastOutbound = PetBridgeProtocol.encode(msg)
        return lastOutbound
    }

    fun encodeBubble(text: String): String {
        val msg = PetBridgeProtocol.showBubbleMessage(text)
        lastOutbound = PetBridgeProtocol.encode(msg)
        return lastOutbound
    }

    companion object {
        const val JS_NAME = "DesktopPetBridge"
    }
}
