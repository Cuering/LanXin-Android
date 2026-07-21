package com.lanxin.android.plugins.memory.domain.memory

import android.content.Context
import android.util.Log
import com.lanxin.android.builtin.knowledge.domain.VectorPipeline
import com.lanxin.android.builtin.knowledge.domain.VectorSource
import com.lanxin.android.builtin.knowledge.domain.sparse.Bm25Index
import com.lanxin.android.builtin.knowledge.domain.sparse.SparseIndexItem
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在发送聊天消息前检索本地记忆并注入上下文。
 *
 * Phase 5.7:
 * Decide gate, judgment packs, silent inject, trace, inject budget.
 * Sparse BM25 + semantic RRF for factual memories.
 */
@Singleton
class MemoryInjector @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val vectorPipeline: VectorPipeline,
    @ApplicationContext private val context: Context
) {
    @Volatile
    var enabled: Boolean = true

    @Volatile
    var semanticEnabled: Boolean = true

    @Volatile
    var sparseEnabled: Boolean = true

    @Volatile
    var syncEnabled: Boolean = true

    private val bm25 = Bm25Index()

    @Volatile
    private var lastMemoryFingerprint: Long = Long.MIN_VALUE

    /**
     * 注入结果：增强文本 + 本轮命中的记忆实体（供 Chat 引用芯片）。
     */
    data class InjectResult(
        val enrichedQuestion: String,
        val matchedMemories: List<MemoryEntity> = emptyList()
    )

    /**
     * 将匹配记忆注入到用户消息前面。
     * 若无匹配或注入关闭，原样返回。
     */
    suspend fun inject(question: String, limit: Int = 5): String {
        return injectWithMatches(question, limit).enrichedQuestion
    }

    /**
     * 全屏陪伴专用：精简记忆注入。
     *
     * - 仅稀疏 BM25 / LIKE（跳过语义向量，省延迟）
     * - 最多 [COMPANION_MEMORY_LIMIT] 条、[COMPANION_MAX_INJECT_CHARS] 字
     * - 不注入判断准则包（陪伴口语短答不需要长规则）
     * - 命中失败时原样返回，不拖垮会话
     */
    suspend fun injectForCompanion(question: String): String {
        if (!enabled || question.isBlank()) return question
        if (shouldSkipInject(question)) return question
        return runCatching {
            val keyword = extractKeyword(question)
            val prevSemantic = semanticEnabled
            semanticEnabled = false
            try {
                val sparse = searchSparseOrLike(keyword, COMPANION_MEMORY_LIMIT * 2)
                    .filterNot { it.type == MemoryType.JUDGMENT }
                    .take(COMPANION_MEMORY_LIMIT)
                if (sparse.isEmpty()) return@runCatching question
                val lines = sparse.joinToString(separator = "\n") { mem ->
                    "- " + mem.content.trim().take(120)
                }
                val block = buildString {
                    appendLine("[精简记忆]")
                    appendLine(lines)
                    append("[记忆结束·勿朗读标签]")
                }.trimEnd()
                val clipped = if (block.length > COMPANION_MAX_INJECT_CHARS) {
                    block.take(COMPANION_MAX_INJECT_CHARS - 1) + "…"
                } else {
                    block
                }
                Log.d(TAG, "[Trace] companion inject count=${sparse.size} chars=${clipped.length}")
                clipped + "\n\n" + question
            } finally {
                semanticEnabled = prevSemantic
            }
        }.getOrElse { e ->
            Log.w(TAG, "injectForCompanion failed: ${e.message}")
            question
        }
    }

    /**
     * 注入并返回命中的记忆实体列表。
     */
    suspend fun injectWithMatches(question: String, limit: Int = 5): InjectResult {
        if (!enabled || question.isBlank()) {
            return InjectResult(enrichedQuestion = question)
        }

        if (shouldSkipInject(question)) {
            Log.d(TAG, "[Trace] skipped decide msg=${question.take(30)}")
            return InjectResult(enrichedQuestion = question)
        }

        val keyword = extractKeyword(question)
        val judgmentBlock = loadJudgmentBlock(keyword)

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
        ).filterNot { it.type == MemoryType.JUDGMENT }

        if (merged.isEmpty() && judgmentBlock.isEmpty()) {
            Log.d(TAG, "[Trace] no_match")
            return InjectResult(enrichedQuestion = question)
        }

        val memLines = merged.joinToString("\n") { item ->
            val typeLabel = MemoryType.displayName(item.type)
            "- [$typeLabel] ${item.content}"
        }

        val assembled = mutableListOf<String>()
        var usedChars = 0

        if (judgmentBlock.isNotEmpty()) {
            val addLen = judgmentBlock.length + 2
            if (usedChars + addLen <= MAX_INJECT_CHARS) {
                assembled.add(judgmentBlock)
                usedChars += addLen
                Log.d(TAG, "[Trace] injected judgment chars=${judgmentBlock.length}")
            } else {
                Log.d(TAG, "[Trace] judgment truncated by budget")
            }
        }

        if (memLines.isNotEmpty()) {
            val fullBlock = buildString {
                appendLine("[我的记忆]")
                appendLine(memLines)
                appendLine("[记忆结束]")
            }.trimEnd()
            val addLen = fullBlock.length + 2
            if (usedChars + addLen <= MAX_INJECT_CHARS) {
                assembled.add(fullBlock)
                usedChars += addLen
                Log.d(TAG, "[Trace] injected memories count=${merged.size}")
            } else {
                val remain = MAX_INJECT_CHARS - usedChars - 2
                if (remain > 40) {
                    assembled.add(fullBlock.take(remain - 1) + "…")
                }
                Log.d(TAG, "[Trace] memory truncated by budget used=$usedChars")
            }
        }

        val enriched = if (assembled.isEmpty()) {
            question
        } else {
            assembled.joinToString("\n\n") + "\n\n" + question
        }
        // 仅返回有真实 id 的记忆，语义路径无 id 占位不进入引用
        val refs = merged.filter { it.id > 0L }
        return InjectResult(enrichedQuestion = enriched, matchedMemories = refs)
    }

    private fun shouldSkipInject(question: String): Boolean {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.length == 1 && !trimmed[0].isLetterOrDigit()) return true
        if (trimmed.all { ch ->
                val c = ch.code
                c in 0x1F300..0x1FAFF || c in 0x2600..0x27BF || c in 0x1F600..0x1F64F ||
                    c in 0x1F900..0x1F9FF || c in 0x1F1E0..0x1F1FF || ch.isWhitespace()
            }
        ) {
            return true
        }
        val skipWords = setOf("好", "好的", "嗯", "行", "ok", "OK", "1", "是", "不是", "继续", "知道了", "收到")
        if (trimmed in skipWords) return true
        if ("不用搜" in trimmed || "不要搜" in trimmed || "别搜" in trimmed) return true
        return false
    }

    private suspend fun loadJudgmentBlock(queryKeyword: String): String {
        ensureJudgmentPacksCopied()
        val candidates = mutableListOf<JudgmentCandidate>()
        val packsDir = File(context.filesDir, JUDGMENT_PACKS_DIR)
        if (packsDir.isDirectory) {
            packsDir.listFiles { file -> file.extension == "json" }?.forEach { jsonFile ->
                try {
                    val pkg = JudgmentPackage.fromJson(jsonFile.readText())
                    val score = matchPackage(pkg, queryKeyword)
                    if (score > 0) {
                        candidates.add(
                            JudgmentCandidate(
                                name = pkg.name.ifBlank { pkg.id },
                                rulesText = pkg.rulesText(),
                                score = score,
                                priority = pkg.priority
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load judgment pack: ${jsonFile.name}", e)
                }
            }
        }

        runCatching { memoryRepository.getJudgmentMemories() }.getOrDefault(emptyList()).forEach { mem ->
            val meta = mem.metadata
            if (meta.isNullOrBlank()) return@forEach
            try {
                val pkg = JudgmentPackage.fromJson(meta)
                val score = matchPackage(pkg, queryKeyword)
                if (score > 0) {
                    candidates.add(
                        JudgmentCandidate(
                            name = pkg.name.ifBlank { mem.content.take(20) },
                            rulesText = pkg.rulesText().ifBlank { mem.content },
                            score = score,
                            priority = pkg.priority
                        )
                    )
                }
            } catch (_: Exception) {
                // skip invalid metadata
            }
        }

        val selected = candidates
            .sortedWith(compareByDescending<JudgmentCandidate> { it.score }.thenBy { it.priority })
            .firstOrNull()

        return if (selected == null) {
            Log.d(TAG, "[Trace] judgment no_match query=$queryKeyword")
            ""
        } else {
            Log.d(TAG, "[Trace] judgment selected=${selected.name} score=${selected.score}")
            buildString {
                appendLine("[判断准则:${selected.name}]")
                selected.rulesText.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(6).forEach {
                    appendLine("- $it")
                }
                appendLine("[准则结束·静默应用·勿向用户朗读]")
            }.trimEnd()
        }
    }

    private fun ensureJudgmentPacksCopied() {
        val packsDir = File(context.filesDir, JUDGMENT_PACKS_DIR)
        if (!packsDir.exists()) {
            packsDir.mkdirs()
        }
        val existing = packsDir.listFiles { f -> f.extension == "json" }?.isNotEmpty() == true
        if (existing) return
        try {
            val assetNames = context.assets.list(JUDGMENT_PACKS_DIR) ?: return
            for (name in assetNames) {
                if (!name.endsWith(".json")) continue
                context.assets.open("$JUDGMENT_PACKS_DIR/$name").use { input ->
                    File(packsDir, name).outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "copy judgment packs failed", e)
        }
    }

    private fun matchPackage(pkg: JudgmentPackage, queryKeyword: String): Int {
        val q = queryKeyword.lowercase()
        pkg.doesNotApplyWhen.forEach { excl ->
            val e = excl.lowercase()
            if (e.isNotBlank() && (e in q || q in e)) return 0
        }
        if (pkg.appliesWhen.isEmpty()) return 1
        var score = 0
        pkg.appliesWhen.forEach { cond ->
            val c = cond.lowercase()
            if (c.isNotBlank() && c in q) score += 10
        }
        return score
    }

    private fun extractKeyword(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= 20) trimmed else trimmed.take(20)
    }

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

    private data class JudgmentCandidate(
        val name: String,
        val rulesText: String,
        val score: Int,
        val priority: Int
    )

    private data class JudgmentPackage(
        val id: String = "",
        val name: String = "",
        val priority: Int = 50,
        val appliesWhen: List<String> = emptyList(),
        val doesNotApplyWhen: List<String> = emptyList(),
        val rules: List<String> = emptyList(),
        val boundaries: List<String> = emptyList()
    ) {
        fun rulesText(): String {
            val lines = rules + boundaries.map { "边界: $it" }
            return lines.joinToString("\n")
        }

        companion object {
            fun fromJson(text: String): JudgmentPackage {
                val obj = JSONObject(text)
                fun arr(key: String): List<String> {
                    val a = obj.optJSONArray(key) ?: return emptyList()
                    return (0 until a.length()).mapNotNull { i ->
                        a.optString(i)?.takeIf { it.isNotBlank() }
                    }
                }
                val rulesArr = arr("rules")
                val rulesStr = obj.optString("rules", "")
                val rules = if (rulesArr.isNotEmpty()) {
                    rulesArr
                } else if (rulesStr.isNotBlank() && !rulesStr.startsWith("[")) {
                    listOf(rulesStr)
                } else {
                    emptyList()
                }
                return JudgmentPackage(
                    id = obj.optString("id", ""),
                    name = obj.optString("name", ""),
                    priority = obj.optInt("priority", 50),
                    appliesWhen = arr("applies_when"),
                    doesNotApplyWhen = arr("does_not_apply_when"),
                    rules = rules,
                    boundaries = arr("boundaries")
                )
            }
        }
    }

    companion object {
        private const val TAG = "MemoryInjector"
        private const val RRF_K = 60
        private const val MAX_INJECT_CHARS = 1800
        private const val JUDGMENT_PACKS_DIR = "judgment_packs"

        /** 陪伴路径：条数少、字数紧，优先低延迟。 */
        const val COMPANION_MEMORY_LIMIT: Int = 2
        const val COMPANION_MAX_INJECT_CHARS: Int = 280

        /**
         * Reciprocal Rank Fusion：两路按排名累加 1/(k+rank)，再取 topK。
         */
        fun reciprocalRankFusion(
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
    }
}
