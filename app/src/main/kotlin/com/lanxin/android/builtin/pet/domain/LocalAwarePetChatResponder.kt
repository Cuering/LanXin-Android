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

import com.lanxin.android.builtin.knowledge.domain.VectorPipeline
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
 * - reuseKv=false：每轮 reset + 清洗后 history 重建（弱模型必需）
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
    private val engine: LocalLlmEngine,
    private val contextEnricher: CompanionContextEnricher
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
        // 弱模型：禁止 reuseKv。
        // 原因：generate 返回的是 64 token 原始胡喷，syncPromptCache 会把「再见/角色串戏」
        // 整段写进 native KV，而 history 只存清洗后短句 → 下一轮被脏缓存带偏。
        // 每轮 reset + 用清洗后的 history 重建 ChatMessages 更稳。
        diagnostics.log(
            "companion",
            "round begin hist=${historySnapshot.size} reuseKv=false max=$COMPANION_MAX_TOKENS user=${text.take(40)}"
        )
        val t0 = System.currentTimeMillis()
        val systemPrompt = buildCompanionSystemPrompt(text)
        // 生成前强制 reset，避免上轮 raw 输出残留
        runCatching { engine.reset() }
        val states = withTimeoutOrNull(COMPANION_TIMEOUT_MS) {
            localProvider.completeAsApiState(
                prompt = text,
                systemPrompt = systemPrompt,
                maxTokens = COMPANION_MAX_TOKENS,
                history = historySnapshot,
                // 约束已写进 system，避免再叠长「【输出约束】」干扰弱模型
                skipOutputConstraint = true,
                reuseKv = false
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
        val cleaned = pickCompanionUtterance(
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


    private suspend fun buildCompanionSystemPrompt(userText: String): String {
        return contextEnricher.enrich(COMPANION_SYSTEM_PROMPT, userText)
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
        /**
         * 对齐 MNNChat：默认 system 极短（官方默认 "You are a helpful assistant."）。
         * 过长禁止项会让 0.8B 复读约束、走「答案：」模板。时间由 [CompanionContextEnricher] 注入。
         */
        const val COMPANION_SYSTEM_PROMPT: String =
            "你是兰心，一个友好的中文桌宠助手。用自然口语简体中文回复，用「我」自称。"

        /**
         * 对齐 MNNChat：默认 max_new_tokens 可达 2048。
         * 陪伴仍限 256，避免弱模型 phrase-loop；48 会截断正常多句回答。
         */
        const val COMPANION_MAX_TOKENS: Int = 256

        /** 单轮本地推理超时。 */
        const val COMPANION_TIMEOUT_MS: Long = 45_000L

        /** 进程内保留的最近轮数（user+assistant 为一轮）。 */
        const val MAX_HISTORY_TURNS: Int = 3

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
            Regex("""^[\d\s,，、;；:：.。\-~/|]+$"""),
            Regex("""答案[：:]"""),
            Regex("""请回答[：:]"""),
            Regex("""注意：回答请只"""),
            Regex("""感觉挺好的。你也觉得"""),
            // 英文 CoT / 解题串（弱模型或非中文权重常见）
            Regex("""(?i)we need to analyze"""),
            Regex("""(?i)okay,? the user"""),
            Regex("""(?i)looking back at the conversation"""),
            Regex("""(?i)the answer is one of"""),
            Regex("""(?i)multiple[- ]choice"""),
            Regex("""(?i)let me think"""),
            Regex("""(?i)first,? i (need|will|should)"""),
            Regex("""(?i)the user's (query|question|message)""")
        )


        /** 把用户叫作兰心 / 向兰心道别的串戏句。 */
        private val ROLE_FLIP_PATTERNS = listOf(
            Regex("""早上好兰心"""),
            Regex("""兰心早上好"""),
            Regex("""^兰心[，,！!]"""),
            Regex("""兰心[，,].*(再见|晚安|好梦|愉快)"""),
            Regex("""(再见|晚安|好梦).{0,6}兰心"""),
            Regex("""祝你有个好梦"""),
            Regex("""很高兴认识你""") // 用户已认识桌宠时仍「初见」通常是串戏
        )

        /**
         * 从模型胡喷里挑一句能用的陪伴口语。
         * 注意：含「兰心」不一定好——弱模型常把用户叫作兰心。
         */
        fun pickCompanionUtterance(raw: String): String {
            // 对齐 MNNChat GenerateResultProcessor：只剥 think/隐藏标签，尽量保留自然多句。
            val light0 = LocalReplySanitizer.lightCleanForBareChat(raw)
            val light = light0
                .replace(Regex("""^(?:答案|请回答)\s*[：:]\s*"""), "")
                .trim()
            if (light.isEmpty()) return ""
            // 整段无明显垃圾则直接用（对齐 MNNChat：不硬截一句）
            val looksGarbage = GARBAGE_PATTERNS.any { it.containsMatchIn(light) } ||
                ROLE_FLIP_PATTERNS.any { it.containsMatchIn(light) } ||
                light.contains("答案：") || light.contains("答案:") || light.contains("请回答：")
            if (!looksGarbage && scoreUtterance(light) >= 0 && light.length in 2..480) {
                return light.trim()
            }
            val parts = light
                .split(Regex("""(?<=[。！？!?…])|(?<=[。！？!?…] )"""))
                .map { it.trim().trimStart('，', ',', '、', '！', '!', '？', '?', '。', ' ') }
                .filter { it.length >= 2 }
            if (parts.isEmpty()) {
                return light.take(240).trim()
            }
            val ranked = parts.sortedByDescending { s -> scoreUtterance(s) }
            val best = ranked.first()
            // 最高分仍很差则返回空，交给闸门拒答
            if (scoreUtterance(best) < 0) return ""
            // 拼接高分句（最多 3 句），贴近 MNN 自然多句
            val good = ranked.filter { scoreUtterance(it) >= 0 }.take(3)
            return good.joinToString("").take(480).trim().ifEmpty { best }
        }

        fun scoreUtterance(s: String): Int {
            var score = 0
            if (listOf("我是", "我会", "我可以", "我能", "我在", "我挺", "我很", "我呀").any { s.contains(it) }) score += 6
            if (s.startsWith("我") || s.startsWith("嗯") || s.startsWith("好") || s.startsWith("嗨") || s.startsWith("嘿")) score += 2
            if (listOf("开心", "早上好", "你好", "在的", "陪你", "当然").any { s.contains(it) }) score += 3
            // 把用户叫作兰心 / 无故道别 → 重罚
            if (ROLE_FLIP_PATTERNS.any { it.containsMatchIn(s) }) score -= 12
            if (s.contains("答案") || s.contains("请回答")) score -= 12
            if (s.contains("一份水果和一些坚果")) score -= 15
            if (s.contains("再见") || s.contains("晚安")) score -= 8
            if (s.endsWith("。") || s.endsWith("！") || s.endsWith("!")) score += 1
            if (s.endsWith("？") || s.endsWith("?")) score -= 1
            if (s.length in 4..36) score += 2
            if (s.length > 48) score -= 2
            if (s.matches(Regex("""^[吗呢吧啊哦嗯呀。！？!?,，、…]+$"""))) score -= 10
            val cjk = s.count { it.code in 0x4E00..0x9FFF }
            val latin = s.count { it in 'A'..'Z' || it in 'a'..'z' }
            if (cjk == 0 && latin >= 4) score -= 15
            if (latin > cjk * 2 && latin >= 8) score -= 10
            return score
        }

        fun isAcceptableReply(userText: String, reply: String): Boolean {
            val r = reply.trim()
            if (r.length < 2) return false
            val cjk = r.count { it.code in 0x4E00..0x9FFF }
            val letters = r.count { it.isLetter() }
            val digits = r.count { it.isDigit() }
            if (cjk < 1 && letters + digits < 2) return false
            if (cjk == 0 && digits >= 6) return false
            if (cjk > 0 && digits > cjk * 3 && digits >= 8) return false
            if (GARBAGE_PATTERNS.any { it.containsMatchIn(r) }) return false
            if (r.contains("答案：") || r.contains("答案:") || r.contains("请回答：")) return false
            if (r.contains("注意：回答请只")) return false
            // 用户说中文时，拒纯英文/英主中辅的「思考体」
            val userCjk = userText.count { it.code in 0x4E00..0x9FFF }
            val latin = r.count { it in 'A'..'Z' || it in 'a'..'z' }
            if (userCjk >= 1) {
                if (cjk == 0 && latin >= 4) return false
                if (latin >= 12 && latin > cjk * 2) return false
            }
            if (r.length >= 80) {
                val punct = r.count { it in "。！？…!?" }
                if (punct == 0 && cjk < r.length / 4) return false
            }
            if (r.length <= 4 && r.matches(Regex("""^[吗呢吧啊哦嗯呀。！？!?,，、…]+$"""))) {
                return false
            }
            // 角色串戏：把用户叫作兰心 / 无故道别
            if (ROLE_FLIP_PATTERNS.any { it.containsMatchIn(r) }) return false
            val userBye = listOf("再见", "拜拜", "晚安", "回头聊").any { userText.contains(it) }
            if (!userBye && (r.contains("再见") || r.contains("晚安") || r.contains("好梦"))) {
                return false
            }
            // 身份类问题必须自称
            val userAsksIdentity = listOf("你是谁", "你叫什么", "你的名字", "你会做什么", "你能做什么", "你能回答", "你是兰心")
                .any { userText.contains(it) }
            if (userAsksIdentity) {
                val mentionsSelf = listOf("我是", "我呀", "就是我", "我会", "我可以", "我能").any { r.contains(it) }
                val asksBack = listOf("你叫什么", "你是谁", "你有什么技能", "你有什么能力", "你喜欢", "你快乐吗")
                    .any { r.contains(it) }
                if (asksBack && !mentionsSelf) return false
                if (!mentionsSelf && r.length <= 20 && !r.contains("兰心")) {
                    // 允许「对呀，我就是兰心」类；纯「嗯」不够
                    if (!r.contains("对") && !r.contains("嗯")) return false
                }
            }
            val qParts = Regex("""[^？?]+[？?]""").findAll(r).map { it.value.trim() }.toList()
            if (qParts.size >= 2 && qParts.distinct().size == 1) return false
            return true
        }
    }
}


/**
 * 陪伴 system 上下文增强：系统时间、知识库摘录等。
 * 失败必须静默，不能阻断对话。
 */
fun interface CompanionContextEnricher {
    suspend fun enrich(baseSystemPrompt: String, userText: String): String
}

/** 单测 / 无知识库时使用。 */
class NoOpCompanionContextEnricher : CompanionContextEnricher {
    override suspend fun enrich(baseSystemPrompt: String, userText: String): String = baseSystemPrompt
}

/**
 * 注入当前本地时间 + 可选知识库短摘录。
 */
@Singleton
class DefaultCompanionContextEnricher @Inject constructor(
    private val vectorPipeline: VectorPipeline
) : CompanionContextEnricher {

    override suspend fun enrich(baseSystemPrompt: String, userText: String): String {
        val now = formatNow()
        val kb = retrieveKnowledgeSnippet(userText)
        return buildString {
            append(baseSystemPrompt)
            append(" 现在是").append(now).append("。")
            if (kb != null) {
                append(" 可参考资料：").append(kb)
            }
        }
    }

    private fun formatNow(): String {
        val z = ZonedDateTime.now(ZoneId.systemDefault())
        val fmt = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm", Locale.CHINA)
        return z.format(fmt)
    }

    private suspend fun retrieveKnowledgeSnippet(query: String): String? {
        val q = query.trim()
        if (q.length < 2) return null
        val greet = listOf("你好", "早上好", "晚安", "在吗", "嗨", "哈喽", "hello", "hi")
        if (greet.any { q.equals(it, ignoreCase = true) || q == "$it！" || q == "$it~" }) {
            return null
        }
        return try {
            val hits = vectorPipeline.searchHybrid(q, topK = 2, source = null)
            if (hits.isEmpty()) return null
            val parts = hits
                .map { it.textPreview.replace('\n', ' ').trim() }
                .filter { it.length >= 8 }
                .take(2)
            if (parts.isEmpty()) null
            else parts.joinToString("；") { it.take(120) }.take(220)
        } catch (_: Exception) {
            null
        }
    }
}

