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

package com.lanxin.android.builtin.voice.data

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Android 系统 TextToSpeech 回退。
 *
 * 当 [SherpaTtsEngine] native 不可用或 stub 降级时，作为后备输出语音。
 * 不依赖本地 ONNX 模型，权重在系统 ROM 中。
 *
 * ## 线程安全
 * - [speak] 挂起直到播完或失败；可并发（内部排队）
 * - 初始化在 IO 线程同步等待（最长 3s）
 */
@Singleton
class AndroidTtsFallback @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private val lock = Any()
    private var initOk = false

    /** 引擎是否可用（初始化成功）。 */
    val available: Boolean
        get() = synchronized(lock) { initOk && tts != null }

    /** 尝试初始化（幂等）。 */
    private fun ensureInit(): Boolean {
        synchronized(lock) {
            if (initOk && tts != null) return true
            if (tts == null) {
                val latch = CountDownLatch(1)
                val engine = TextToSpeech(context) { status ->
                    synchronized(lock) {
                        initOk = status == TextToSpeech.SUCCESS
                        if (!initOk) {
                            Log.w(TAG, "TextToSpeech init failed: status=$status")
                        }
                    }
                    latch.countDown()
                }
                tts = engine
                // 同步等初始化（最长 3s）
                if (!latch.await(3, TimeUnit.SECONDS)) {
                    Log.w(TAG, "TextToSpeech init timeout")
                    synchronized(lock) { initOk = false }
                }
                // 设置中文
                synchronized(lock) {
                    if (initOk) {
                        val lang = engine.setLanguage(Locale.CHINESE)
                        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                            // fallback: 英语
                            engine.setLanguage(Locale.ENGLISH)
                        }
                    }
                }
            }
            return initOk && tts != null
        }
    }

    /**
     * 合成并播放文字。
     *
     * @return true 正常播完；false 初始化失败 / 引擎不支持
     */
    suspend fun speak(
        text: String,
        utteranceId: String = UUID.randomUUID().toString()
    ): Boolean = withContext(Dispatchers.IO) {
        val engine = synchronized(lock) { tts }
        if (engine == null) {
            if (!ensureInit()) return@withContext false
        }
        val eng = synchronized(lock) { tts } ?: return@withContext false

        return@withContext suspendCancellableCoroutine { cont ->
            val spoken = AtomicBoolean(false)
            val listener = object : UtteranceProgressListener() {
                override fun onStart(uttId: String?) {}
                override fun onDone(uttId: String?) {
                    if (spoken.compareAndSet(false, true)) {
                        cont.resume(true) {}
                    }
                }
                override fun onError(uttId: String?, errorCode: Int) {
                    if (spoken.compareAndSet(false, true)) {
                        Log.w(TAG, "TextToSpeech onError: uttId=$uttId code=$errorCode")
                        cont.resume(false) {}
                    }
                }
                override fun onError(uttId: String?) {
                    if (spoken.compareAndSet(false, true)) {
                        cont.resume(false) {}
                    }
                }
            }
            eng.setOnUtteranceProgressListener(listener)

            val params = Bundle()
            // 正常语速
            val result = eng.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                if (spoken.compareAndSet(false, true)) {
                    cont.resume(false) {}
                }
            }
            cont.invokeOnCancellation {
                eng.stop()
            }
        }
    }

    /** 释放引擎资源。 */
    fun shutdown() {
        synchronized(lock) {
            tts?.stop()
            tts?.shutdown()
            tts = null
            initOk = false
        }
    }

    companion object {
        private const val TAG = "AndroidTtsFallback"
    }
}
