package com.lanxin.android.presentation.ui.chat

/**
 * Chat UX 生成阶段：可变状态文案，不是完整任务进度条。
 *
 * 本地推理无 tool_call；检索注入在 App 侧。流式正文开始后通常收起为 Done。
 */
enum class ChatGenerationPhase {
    IDLE,
    PREPARING,
    SEARCHING_MEMORY,
    SEARCHING_KNOWLEDGE,
    GENERATING,

    /** 本地离线/偏好本地生成中（Phase 6.2）。 */
    GENERATING_LOCAL,
    CALLING_TOOLS,
    DONE;

    val isActive: Boolean
        get() = this != IDLE && this != DONE
}

/**
 * 本轮注入/命中的记忆或知识库引用，可点击跳转原文。
 */
enum class ChatRefType {
    MEMORY,
    KNOWLEDGE
}

data class ChatRef(
    val type: ChatRefType,
    /** 记忆为 memory id；知识库为 vector externalId 或 hit key。 */
    val id: String,
    val title: String,
    val snippet: String = ""
)

/**
 * 当前会话内某一 turn 的生成态与引用（P0 仅会话内存，不写 Room）。
 */
data class ChatTurnUxState(
    val phase: ChatGenerationPhase = ChatGenerationPhase.IDLE,
    val refs: List<ChatRef> = emptyList()
)

/**
 * 状态文案与阶段切换的纯逻辑（单测覆盖）。
 */
object ChatGenerationStatusLogic {

    fun label(phase: ChatGenerationPhase): String = when (phase) {
        ChatGenerationPhase.IDLE -> ""
        ChatGenerationPhase.PREPARING -> "准备中…"
        ChatGenerationPhase.SEARCHING_MEMORY -> "检索记忆…"
        ChatGenerationPhase.SEARCHING_KNOWLEDGE -> "检索知识库…"
        ChatGenerationPhase.GENERATING -> "生成中…"
        ChatGenerationPhase.GENERATING_LOCAL -> "本地离线生成中…"
        ChatGenerationPhase.CALLING_TOOLS -> "调用工具…"
        ChatGenerationPhase.DONE -> "已完成"
    }

    /**
     * 流式正文开始后：活动态收起为 Done；已 Idle/Done 保持不变。
     */
    fun onStreamContentStarted(phase: ChatGenerationPhase): ChatGenerationPhase {
        return if (phase.isActive) ChatGenerationPhase.DONE else phase
    }

    /**
     * 生成链路结束后：活动态 → Done；Idle 保持。
     */
    fun onGenerationFinished(phase: ChatGenerationPhase): ChatGenerationPhase {
        return when (phase) {
            ChatGenerationPhase.IDLE -> ChatGenerationPhase.IDLE
            ChatGenerationPhase.DONE -> ChatGenerationPhase.DONE
            else -> ChatGenerationPhase.DONE
        }
    }

    /**
     * 从统一检索 hit key 解析可点引用（仅 memory / knowledge）。
     */
    fun refsFromUnifiedKeys(
        keys: List<String>,
        texts: List<String>,
        subtitles: List<String>
    ): List<ChatRef> {
        require(keys.size == texts.size && texts.size == subtitles.size) {
            "keys/texts/subtitles size mismatch"
        }
        val out = ArrayList<ChatRef>(keys.size)
        keys.forEachIndexed { index, key ->
            when {
                key.startsWith("memory:") -> {
                    val id = key.removePrefix("memory:")
                    if (id.isNotBlank()) {
                        val text = texts[index]
                        val subtitle = subtitles[index]
                        out += ChatRef(
                            type = ChatRefType.MEMORY,
                            id = id,
                            title = subtitle.ifBlank { "记忆" },
                            snippet = text.take(120)
                        )
                    }
                }
                key.startsWith("knowledge:") -> {
                    // knowledge:{externalId}:{vectorId}
                    val rest = key.removePrefix("knowledge:")
                    val externalId = rest.substringBefore(':').ifBlank { rest }
                    if (externalId.isNotBlank()) {
                        val text = texts[index]
                        out += ChatRef(
                            type = ChatRefType.KNOWLEDGE,
                            id = externalId,
                            title = "知识",
                            snippet = text.take(120)
                        )
                    }
                }
            }
        }
        return out.distinctBy { "${it.type}:${it.id}" }
    }

    fun memoryRefs(ids: List<Long>, titles: List<String>, snippets: List<String>): List<ChatRef> {
        require(ids.size == titles.size && titles.size == snippets.size)
        return ids.mapIndexed { index, id ->
            ChatRef(
                type = ChatRefType.MEMORY,
                id = id.toString(),
                title = titles[index].ifBlank { "记忆" },
                snippet = snippets[index].take(120)
            )
        }.distinctBy { it.id }
    }
}
