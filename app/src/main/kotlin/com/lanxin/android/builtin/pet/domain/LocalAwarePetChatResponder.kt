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

import com.lanxin.android.builtin.localinference.domain.LocalChatMessage
import com.lanxin.android.builtin.localinference.domain.LocalInferenceBootstrap
import com.lanxin.android.builtin.localinference.domain.LocalInferenceDiagnostics
import com.lanxin.android.builtin.localinference.domain.LocalInferenceProvider
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalReplySanitizer
import com.lanxin.android.data.dto.ApiState
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 全屏陪伴 / 桌宠「想」阶段：本地脑就绪则走本地，否则 stub 短答。
 *
 * 对齐 MNNChat 会话面：
 * - 进程内多轮 history（user/assistant 交替）
 * - reuseKv=true：不每轮 reset，生成后 syncPromptCache
 * - system 默认空；skip 输出约束；轻清洗出口
 */
@Singleton
class LocalAwarePetChatResponder @Inject constructor(
    private val localProvider: LocalInferenceProvider,
    private val localSettings: LocalInferenceSettings,
    private val bootstrap: LocalInferenceBootstrap,
    private val stub: StubPetChatResponder,
    private val diagnostics: LocalInferenceDiagnostics
) : PetChatResponder {

    private val mutex = Mutex()
    /** 不含 system；仅 user/assistant 成对。 */
    private val turnHistory = CopyOnWriteArrayList<LocalChatMessage>()

    override suspend fun respond(userText: String): String = mutex.withLock {
        val text = userText.trim()
        if (text.isEmpty()) {
            return@withLock stub.respond(text)
        }
        if (!ensureLocalReady()) {
            diagnostics.log("companion", "not_ready → stub user=${text.take(40)}")
            return@withLock stub.respond(text)
        }

        // 超长上下文时裁掉最旧轮次，避免 prefill 爆炸（仍 keep 最近几轮）
        trimHistoryIfNeeded()

        val historySnapshot = turnHistory.toList()
        diagnostics.log(
            "companion",
            "round begin hist=${historySnapshot.size} reuseKv=true max=$COMPANION_MAX_TOKENS user=${text.take(40)}"
        )
        val t0 = System.currentTimeMillis()
        val states = withTimeoutOrNull(COMPANION_TIMEOUT_MS) {
            localProvider.completeAsApiState(
                prompt = text,
                systemPrompt = null,
                maxTokens = COMPANION_MAX_TOKENS,
                history = historySnapshot,
                skipOutputConstraint = true,
                reuseKv = true
            ).toList()
        }
        if (states == null) {
            diagnostics.log("companion", "timeout ${COMPANION_TIMEOUT_MS}ms → stub")
            return@withLock stub.respond(text)
        }

        val success = states
            .filterIsInstance<ApiState.Success>()
            .joinToString("") { it.textChunk }
            .trim()
        if (success.isBlank()) {
            val err = states.filterIsInstance<ApiState.Error>().lastOrNull()?.message
            diagnostics.log("companion", "blank success err=$err → stub durMs=${System.currentTimeMillis()-t0}")
            return@withLock stub.respond(text)
        }
        val cleaned = LocalReplySanitizer.lightCleanForBareChat(success)
            .ifBlank { success }
            .trim()
        if (!isAcceptableReply(userText = text, reply = cleaned)) {
            diagnostics.log(
                "companion",
                "rejected by gate preview=${cleaned.take(50).replace('\n',' ')} → stub"
            )
            return@withLock stub.respond(text)
        }

        // 成功才写入 history（对齐 MNNChat：可见对话与引擎 cache 同步）
        turnHistory.add(LocalChatMessage(role = "user", content = text))
        turnHistory.add(LocalChatMessage(role = "assistant", content = cleaned))
        diagnostics.log(
            "companion",
            "ok durMs=${System.currentTimeMillis()-t0} histNow=${turnHistory.size} " +
                "reply=${cleaned.take(60).replace('\n',' ')}"
        )

        val mood = guessMood(text, cleaned)
        return@withLock "[[mood=$mood]]\n$cleaned"
    }

    /** 新开话题：清进程内 history（调用方可选）。 */
    fun clearHistory() {
        turnHistory.clear()
    }

    fun historySizeForTest(): Int = turnHistory.size

    private suspend fun ensureLocalReady(): Boolean {
        val config = localSettings.getConfig()
        if (config.modelPath.isBlank()) return false
        val result = bootstrap.ensureReady(enableIfNeeded = true)
        return result.isReady && localProvider.canServe()
    }

    private fun trimHistoryIfNeeded() {
        // 每轮 2 条；保留最近 MAX_TURNS 轮
        val maxMsgs = MAX_HISTORY_TURNS * 2
        while (turnHistory.size > maxMsgs) {
            turnHistory.removeAt(0)
        }
    }

    private fun guessMood(user: String, reply: String): String {
        val lower = (user + reply).lowercase()
        return when {
            listOf("音乐", "听歌", "放歌", "bgm").any { user.contains(it) } -> "music"
            listOf("抱歉", "对不起", "难过").any { user.contains(it) } -> "sorry"
            listOf("哈哈", "开心", "喜欢", "耶").any { user.contains(it) } -> "joy"
            listOf("你好", "哈喽", "hello", "hi").any { lower.contains(it) } -> "smile"
            else -> "speak"
        }
    }

    companion object {
        const val COMPANION_SYSTEM_PROMPT: String =
            "你是兰心。用一两句自然中文直接回答。"

        /** 陪伴生成上限。 */
        const val COMPANION_MAX_TOKENS: Int = 256

        /** 单轮本地推理超时。 */
        const val COMPANION_TIMEOUT_MS: Long = 45_000L

        /** 进程内保留的最近轮数（user+assistant 为一轮）。 */
        const val MAX_HISTORY_TURNS: Int = 6

        private val GARBAGE_PATTERNS = listOf(
            Regex("""[（(]\s*0\s*[-~～到至]\s*\d+\s*分\s*[）)]"""),
            Regex("""\d+\s*分[）)\s，,]"""),
            Regex("""系统已明确"""),
            Regex("""输出约束"""),
            Regex("""要表现出"""),
            Regex("""总是能给出"""),
            Regex("""简洁而有洞见"""),
            Regex("""chain of thought""", RegexOption.IGNORE_CASE),
            Regex("""thinking process""", RegexOption.IGNORE_CASE),
            Regex("""assistant\s*:""", RegexOption.IGNORE_CASE),
            Regex("""^\d+[\.、．]?$"""),
            Regex("""(?is)^thinking\s*process.*$"""),
            Regex("""^[\s!！?？.。,，、;；:：…~～]+$""")
        )

        fun isAcceptableReply(userText: String, reply: String): Boolean {
            val r = reply.trim()
            if (r.length < 2) return false
            val hasContent = r.any {
                it.isLetterOrDigit() || it.code in 0x4E00..0x9FFF
            }
            if (!hasContent) return false
            if (GARBAGE_PATTERNS.any { it.containsMatchIn(r) }) return false
            return true
        }
    }
}
