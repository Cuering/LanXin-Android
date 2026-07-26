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
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
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
 * 对齐 MNNChat 会话面，并补上小模型必需的护栏：
 * - 进程内多轮 history（user/assistant 交替）
 * - reuseKv=true：不每轮 reset，生成后 syncPromptCache
 * - 注入短 system（人设 + 输出约束），不再裸跑
 * - 较短 maxTokens，降低 phrase-loop / 胡言数字串概率
 * - 质量闸门拒绝后清 history + reset 引擎，避免脏 KV 回灌
 */
@Singleton
class LocalAwarePetChatResponder @Inject constructor(
    private val localProvider: LocalInferenceProvider,
    private val localSettings: LocalInferenceSettings,
    private val bootstrap: LocalInferenceBootstrap,
    private val stub: StubPetChatResponder,
    private val diagnostics: LocalInferenceDiagnostics,
    private val engine: LocalLlmEngine
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
            "round begin hist=${historySnapshot.size} reuseKv=${historySnapshot.isNotEmpty()} max=$COMPANION_MAX_TOKENS user=${text.take(40)}"
        )
        val t0 = System.currentTimeMillis()
        val states = withTimeoutOrNull(COMPANION_TIMEOUT_MS) {
            localProvider.completeAsApiState(
                prompt = text,
                systemPrompt = COMPANION_SYSTEM_PROMPT,
                maxTokens = COMPANION_MAX_TOKENS,
                history = historySnapshot,
                // 小模型必须带输出约束；裸跑易出数字串/乱码
                skipOutputConstraint = false,
                reuseKv = historySnapshot.isNotEmpty()
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
        // Provider 已 clean；再轻量兜底 + 单句硬截（陪伴口语）
        val cleaned = LocalReplySanitizer.limitToOneSentence(
            LocalReplySanitizer.forDisplay(success, showThinking = false)
                .ifBlank { LocalReplySanitizer.lightCleanForBareChat(success) }
        ).trim()
        if (!isAcceptableReply(userText = text, reply = cleaned)) {
            diagnostics.log(
                "companion",
                "rejected by gate preview=${cleaned.take(50).replace('\n',' ')} → stub + reset"
            )
            // 拒答：清进程 history，并 reset native KV，避免脏上下文污染下一轮
            turnHistory.clear()
            runCatching { engine.reset() }
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
        /**
         * 短人设置顶（小模型对前置事实更稳）。
         * 细节约束交给 LocalReplySanitizer.appendOutputConstraint。
         */
        const val COMPANION_SYSTEM_PROMPT: String =
            "你是兰心，用户身边的桌宠陪伴。用一两句自然中文直接回答对方。" +
                "不要输出思考过程、评分、数字区间清单、英文推理或复读同一句。"

        /** 陪伴生成上限：宁短勿爆；256 易 phrase-loop / 胡言数字串。 */
        const val COMPANION_MAX_TOKENS: Int = 64

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
            Regex("""^[\s!！?？.。,，、;；:：…~～]+$"""),
            // 常见胡言：反问身份/时间周期复读
            Regex("""你不是我爷爷"""),
            Regex("""时间的周期或时间段"""),
            Regex("""你是指时间"""),
            // 胡言数字区间串：24-26, 32-34, 36-38 …
            Regex("""(?:\d+\s*[-~～—]\s*\d+\s*[,，、|/]\s*){2,}"""),
            Regex("""(?:\d+\s*[-~～—]\s*\d+.*){3,}"""),
            // 西里尔等乱码起手（如 рассу）
            Regex("""[\u0400-\u04FF]{2,}"""),
            // 纯数字/标点拼盘
            Regex("""^[\d\s,，、;；:：.。\-~/|]+$""")
        )

        fun isAcceptableReply(userText: String, reply: String): Boolean {
            val r = reply.trim()
            if (r.length < 2) return false
            val cjk = r.count { it.code in 0x4E00..0x9FFF }
            val letters = r.count { it.isLetter() }
            val digits = r.count { it.isDigit() }
            // 至少要有一点中文可读内容；纯拉丁/数字乱喷拒掉
            if (cjk < 1 && letters + digits < 2) return false
            if (cjk == 0 && digits >= 6) return false
            if (cjk > 0 && digits > cjk * 3 && digits >= 8) return false
            if (GARBAGE_PATTERNS.any { it.containsMatchIn(r) }) return false
            // 过长且几乎无句读的复读/胡言
            if (r.length >= 80) {
                val punct = r.count { it in "。！？…!?" }
                if (punct == 0 && cjk < r.length / 4) return false
            }
            return true
        }
    }
}
