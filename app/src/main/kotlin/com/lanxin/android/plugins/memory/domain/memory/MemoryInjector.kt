package com.lanxin.android.plugins.memory.domain.memory

import android.util.Log
import com.lanxin.android.builtin.knowledge.domain.VectorPipeline
import com.lanxin.android.builtin.knowledge.domain.VectorSource
import com.lanxin.android.builtin.knowledge.domain.sparse.Bm25Index
import com.lanxin.android.builtin.knowledge.domain.sparse.SparseIndexItem
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在发送聊天消息前检索本地记忆并注入上下文。
 *
 * Phase 5.7 增强：
 * - Decide 门控：跳过极短确认/纯表情/不用搜
 * - 判断包加载：扫描 filesDir/judgment_packs/*.json
 * - applies_when / does_not_apply_when 场景匹配
 * - 单主判断包（0~1），静默注入
 * - Trace 日志
 * - 注入预算 MAX_INJECT_CHARS=1800，优先判断包再高分记忆
 */
@Singleton
class MemoryInjector @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val vectorPipeline: VectorPipeline,
    private val context: android.content.Context
) {
    @Volatile
    var enabled: Boolean = true

    /** 语义向量检索开关，默认开启；关闭后仅走稀疏/关键词路。 */
    @Volatile
    var semanticEnabled: Boolean = true

    /** 稀疏 BM25 开关；关闭后直接 LIKE。 */
    @Volatile
    var sparseEnabled: Boolean = true

    /** 同步总开关，决定是否启用云端同步能力。 */
    @Volatile
    var syncEnabled: Boolean = true

    private val bm25 = Bm25Index()

    @Volatile
    private var lastMemoryFingerprint: Long = Long.MIN_VALUE

    companion object {
        private const val TAG = "MemoryInjector"
        private const val RRF_K = 60
        private const val MAX_INJECT_CHARS = 1800
        private const val JUDGMENT_PACKS_DIR = "judgment_packs"
    }

    /**
     * 将匹配记忆注入到用户消息前面。
     * 若无匹配或注入关闭，原样返回。
     */
    suspend fun inject(question: String, limit: Int = 5): String {
        if (!enabled || question.isBlank()) return question

        // === Decide 门控 ===
        val decideResult = decide(question)
        when (decideResult) {
            DecideResult.SKIP -> {
                Log.d(TAG, "[Trace] decided=skip question=${question.take(30)}")
                return question
            }
            DecideResult.CONTINUE -> { /* 继续注入流程 */ }
        }

        val keyword = extractKeyword(question)

        // === 1. 加载判断包 ===
        val judgmentBlock = loadJudgmentBlocks(keyword)

        // === 2. 记忆检索 ===
        val sparseResults = searchSparseOrLike(keyword, limit * 2)
        val semanticResults = if (semanticEnabled) {
            try {
                vectorPipeline.search(
                    query = keyword,
                    topK = limit * 2,
                    source = VectorSource.MEMORY
                )
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val merged = reciprocalRankFusion(
            keywordResults = sparseResults,
            semanticTexts = semanticResults.map { it.textPreview },
            k = RRF_K,
            topK = limit
        ).filterNot { it.type == MemoryType.JUDGMENT } // judgment 已由上面处理

        if (merged.isEmpty() && judgmentBlock.isEmpty()) return question

        // === 3. 记忆文本 ===
        val memLines = merged.joinToString("\n") { item ->
            val typeLabel = MemoryType.displayName(item.type)
            "- [$typeLabel] ${item.content}"
        }

        // === 4. 统一拼装 + 注入预算 ===
        val assembled = mutableListOf<String>()
        var usedChars = 0

        // 判断包优先
        if (judgmentBlock.isNotEmpty()) {
            val addLen = judgmentBlock.length + 2
            if (usedChars + addLen <= MAX_INJECT_CHARS) {
                assembled.add(judgmentBlock)
                usedChars += addLen
            } else {
                val remain = MAX_INJECT_CHARS - usedChars
                if (remain > 40) {
                    assembled.add("[判断准则]...(已裁剪)")
                }
                Log.d(TAG, "[Trace] judgment truncated by budget")
            }
        }

        // 记忆块
        if (memLines.isNotEmpty()) {
            val fullBlock = buildString {
                appendLine("[我的记忆]")
                appendLine(memLines)
                appendLine("[记忆结束]")
                appendLine()
            }
            val addLen = fullBlock.length + 2
            if (usedChars + addLen <= MAX_INJECT_CHARS) {
                assembled.add(fullBlock.trimEnd())
                usedChars += addLen
                Log.d(TAG, "[Trace] injected memories count=${merged.size}")
            } else {
                val remain = MAX_INJECT_CHARS - usedChars - 2
                if (remain > 40) {
                    assembled.add(fullBlock.take(remain - 1) + "...")
                }
                Log.d(TAG, "[Trace] memory truncated by budget, used=$usedChars")
            }
        }

        return if (assembled.isNotEmpty()) {
            buildString {
                append(assembled.joinToString("\n\n"))
                appendLine()
                append(question)
            }
        } else {
            question
        }
    }

    /**
     * Decide 门控：判断是否跳过注入。
     */
    private fun decide(question: String): DecideResult {
        val trimmed = question.trim()
        if (trimmed.length <= 1) return DecideResult.SKIP // 单字符

        // 纯表情/emoji
        if (trimmed.all { it.code in 0x1F000..0x1FFFF || it.code in 0x2600..0x27BF }) {
            return DecideResult.SKIP
        }

        // 常见跳过词
        val skipWords = setOf("好", "好的", "嗯", "行", "ok", "1", "是", "不是", "继续", "知道了", "收到")
        if (trimmed in skipWords) return DecideResult.SKIP

        // 含"不用搜"
        if ("不用搜" in trimmed || "不要搜" in trimmed) return DecideResult.SKIP

        return DecideResult.CONTINUE
    }

    /**
     * 加载判断包块：扫描 judgment_packs/*.json + Room judgment 记忆。
     */
    private suspend fun loadJudgmentBlocks(queryKeyword: String): String {
        val candidates = mutableListOf<JudgmentCandidate>()

        // 1. 扫描 filesDir/judgment_packs/*.json
        val packsDir = File(context.filesDir, JUDGMENT_PACKS_DIR)
        if (packsDir.exists() && packsDir.isDirectory) {
            packsDir.listFiles { file -> file.extension == "json" }?.forEach { jsonFile ->
                try {
                    val pkg = JudgmentPackage.fromJson(jsonFile.readText())
                    val matchScore = matchPackage(pkg, queryKeyword)
                    if (matchScore > 0) {
                        candidates.add(JudgmentCandidate(pkg.name, pkg.rules, matchScore))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load judgment pack: ${jsonFile.name}", e)
                }
            }
        }

        // 2. 从 Room 读取 judgment 类型记忆
        val judgmentMems = runCatching { memoryRepository.getJudgmentMemories() }.getOrDefault(emptyList())
        for (mem in judgmentMems) {
            val pkg = try {
                JudgmentPackage.fromJson(mem.metadata ?: "{}")
            } catch (_: Exception) {
                continue
            }
            val matchScore = matchPackage(pkg, queryKeyword)
            if (matchScore > 0) {
                candidates.add(JudgmentCandidate(mem.content, pkg.rules ?: "", matchScore))
            }
        }

        // 3. 选择 0~1 个主包（优先级最高）
        val sorted = candidates.sortedByDescending { it.score }
        val selected = sorted.firstOrNull { it.score > 0 }

        return if (selected != null) {
            Log.d(TAG, "[Trace] judgment selected: ${selected.name} score=${selected.score}")
            buildString {
                appendLine("[判断准则:${selected.name}]")
                selected.rules.lines().forEach { line ->
                    if (line.isNotBlank()) appendLine(line.trim())
                }
                appendLine("[准则结束·静默应用·勿向用户朗读]")
            }
        } else {
            Log.d(TAG, "[Trace] judgment no_match query=$queryKeyword")
            ""
        }
    }

    /**
     * 匹配判断包适用条件。
     */
    private fun matchPackage(pkg: JudgmentPackage, queryKeyword: String): Int {
        // does_not_apply_when 排除
        pkg.doesNotApplyWhen?.let { excludes ->
            for (excl in excludes) {
                if (excl.contains(queryKeyword) || queryKeyword.contains(excl)) return 0
            }
        }

        // applies_when 匹配
        var score = 0
        pkg.appliesWhen?.forEach { cond ->
            if (queryKeyword.contains(cond)) score += 10
        }

        // 无 applies_when 时弱兜底（低分）
        if (pkg.appliesWhen.isNullOrEmpty()) {
            score = 1
        }

        return score
    }

    /**
     * 提取检索关键词：取前 20 个字或整句。
     */
    private fun extractKeyword(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= 20) trimmed else trimmed.take(20)
    }

    /**
     * BM25 稀疏检索；索引不可用或异常时 fallback LIKE。
     */
    private suspend fun searchSparseOrLike(keyword: String, limit: Int): List<MemoryEntity> {
        if (!sparseEnabled) {
            return memoryRepository.searchForInject(keyword, limit)
        }
        return try {
            val all = memoryRepository.getAllMemoriesOnce()
            if (all.isEmpty()) return emptyList()

            val items = all.map { entity ->
                SparseIndexItem(
                    documentId = entity.id,
                    source = VectorSource.MEMORY,
                    text = entity.content,
                    payload = entity.type
                )
            }
            val fp = Bm25Index.fingerprintOf(items)
            if (fp != lastMemoryFingerprint || bm25.isEmpty) {
                bm25.index(items)
                lastMemoryFingerprint = fp
            }

            val hits = bm25.search(keyword, limit)
            if (hits.isEmpty()) {
                return memoryRepository.searchForInject(keyword, limit)
            }

            val byId = all.associateBy { it.id }
            hits.mapNotNull { hit -> byId[hit.documentId] }
        } catch (e: Exception) {
            Log.w(TAG, "BM25 sparse failed, fallback LIKE: ${e.message}")
            memoryRepository.searchForInject(keyword, limit)
        }
    }

    /**
     * Reciprocal Rank Fusion：两路按排名累加 1/(k+rank)，再取 topK。
     */
    private fun reciprocalRankFusion(
        keywordResults: List<MemoryEntity>,
        semanticTexts: List<String>,
        k: Int = RRF_K,
        topK: Int = 5
    ): List<MemoryEntity> {
        val scores = mutableMapOf<String, Double>()
        val entityByContent = LinkedHashMap<String, MemoryEntity>()

        keywordResults.forEachIndexed { index, entity ->
            val key = entity.content
            scores[key] = scores.getOrDefault(key, 0.0) + 1.0 / (k + index + 1)
            entityByContent.putIfAbsent(key, entity)
        }

        semanticTexts.forEachIndexed { index, text ->
            if (text.isBlank()) return@forEachIndexed
            scores[text] = scores.getOrDefault(text, 0.0) + 1.0 / (k + index + 1)
            entityByContent.putIfAbsent(
                text,
                MemoryEntity(content = text, type = MemoryType.CHAT)
            )
        }

        return scores.entries
            .sortedByDescending { it.value }
            .take(topK)
            .mapNotNull { entityByContent[it.key] }
    }

    /** 判断包候选 */
    private data class JudgmentCandidate(
        val name: String,
        val rules: String,
        val score: Int
    )

    /** 判断包 JSON 结构 */
    private data class JudgmentPackage(
        val id: String = "",
        val name: String = "",
        val priority: Int = 50,
        val appliesWhen: List<String>? = null,
        val doesNotApplyWhen: List<String>? = null,
        val rules: String? = null,
        val boundaries: String? = null
    ) {
        companion object {
            fun fromJson(text: String): JudgmentPackage {
                val obj = JSONObject(text)
                val appliesWhen = obj.optJSONArray("applies_when")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
                val doesNotApplyWhen = obj.optJSONArray("does_not_apply_when")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
                return JudgmentPackage(
                    id = obj.optString("id", ""),
                    name = obj.optString("name", ""),
                    priority = obj.optInt("priority", 50),
                    appliesWhen = appliesWhen,
                    doesNotApplyWhen = doesNotApplyWhen,
                    rules = obj.optString("rules", null).takeIf { it.isNotEmpty() },
                    boundaries = obj.optString("boundaries", null).takeIf { it.isNotEmpty() }
                )
            }
        }
    }

    private enum class DecideResult { SKIP, CONTINUE }
}
