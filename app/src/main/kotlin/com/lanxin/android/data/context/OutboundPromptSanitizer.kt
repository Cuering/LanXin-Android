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

package com.lanxin.android.data.context

import com.lanxin.android.plugins.chat.data.entity.ACTIVE_REVISION_LATEST
import com.lanxin.android.plugins.chat.data.entity.MessageV2
import com.lanxin.android.plugins.chat.data.entity.effectiveContent

/**
 * 出站 Prompt 清洗：在发给中转/云端模型前剥离易触发敏感词网关 500 的词，
 * 并规范化 system 前缀，提高 Prompt Cache 命中率。
 *
 * 仅作用于 API 请求体，不改本地 DB / UI 展示原文。
 */
object OutboundPromptSanitizer {

    /**
     * 内置替换表：敏感词 → 同义/谐音/遮蔽替代表达。
     * 按词长降序匹配，避免短词抢先截断长词。
     *
     * 列表刻意保守：覆盖中转网关常见触发类（政治人物全名、露骨色情、暴力违禁），
     * 日常聊天用词尽量不误伤。可通过 [extraReplacements] 扩展。
     */
    private val BUILTIN_REPLACEMENTS: List<Pair<String, String>> = listOf(
        // —— 政治人物 / 高频触发（中转常见）——
        "习近平" to "某领导",
        "江泽民" to "某领导",
        "胡锦涛" to "某领导",
        "邓小平" to "某领导",
        "毛泽东" to "某领导",
        "温家宝" to "某领导",
        "李克强" to "某领导",
        "李鹏" to "某领导",
        "周恩来" to "某领导",
        "刘少奇" to "某领导",
        "彭丽媛" to "某家属",
        "六四事件" to "某历史事件",
        "六四" to "某事件",
        "天安门事件" to "某历史事件",
        "法轮功" to "某组织",
        "法轮大法" to "某组织",
        "新疆集中营" to "某地区议题",
        "台独" to "某政治议题",
        "港独" to "某政治议题",
        "藏独" to "某政治议题",
        "疆独" to "某政治议题",
        "反共" to "某政治观点",
        "推翻政权" to "某政治议题",
        "共产党下台" to "某政治议题",
        // —— 露骨色情（网关常见整句拒）——
        "口交" to "**",
        "肛交" to "**",
        "强奸" to "侵害",
        "轮奸" to "侵害",
        "迷奸" to "侵害",
        "幼女" to "未成年人",
        "萝莉" to "少女",
        "正太" to "少年",
        "援交" to "不当交易",
        "约炮" to "约会",
        "一夜情" to "短暂关系",
        "色情" to "敏感内容",
        "黄色网站" to "不当网站",
        "成人视频" to "影片",
        "裸聊" to "视频聊天",
        "自慰" to "私密行为",
        "手淫" to "私密行为",
        "射精" to "**",
        "阴道" to "私密部位",
        "阴茎" to "私密部位",
        "鸡巴" to "**",
        "操逼" to "**",
        "草泥马" to "生气",
        "妈的" to "哎",
        "他妈的" to "哎",
        "傻逼" to "笨蛋",
        "傻B" to "笨蛋",
        " ent" to "笨蛋",
        "去死" to "别这样",
        "找死" to "别这样",
        // —— 暴力 / 违禁行为关键词 ——
        "制作炸弹" to "危险物品相关",
        "怎么造炸弹" to "危险物品相关",
        "冰毒制作" to "违禁品相关",
        "制造冰毒" to "违禁品相关",
        "海洛因" to "违禁品",
        "甲基苯丙胺" to "违禁品",
        "购买枪支" to "危险物品",
        "贩卖枪支" to "危险物品",
        "杀人教程" to "暴力内容",
        "如何杀人" to "暴力内容"
    ).sortedByDescending { it.first.length }

    /** 运行时可追加的自定义替换（词 → 替换），默认空。 */
    @Volatile
    var extraReplacements: List<Pair<String, String>> = emptyList()

    /**
     * 清洗单段文本。空串直接返回。
     */
    fun sanitizeText(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        val all = if (extraReplacements.isEmpty()) {
            BUILTIN_REPLACEMENTS
        } else {
            (BUILTIN_REPLACEMENTS + extraReplacements).sortedByDescending { it.first.length }
        }
        for ((raw, repl) in all) {
            if (raw.isEmpty()) continue
            if (out.contains(raw)) {
                out = out.replace(raw, repl)
            }
        }
        return out
    }

    /**
     * 规范化 system prompt：统一换行、去首尾空白、折叠多余空行。
     * 保证跨请求前缀字节级稳定，利于 Prompt Cache 命中。
     */
    fun normalizeSystemPrompt(prompt: String?): String? {
        if (prompt == null) return null
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return null
        // \r\n / \r → \n，连续空行压成单空行
        val normalized = trimmed
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("\n{3,}"), "\n\n")
        return sanitizeText(normalized)
    }

    /**
     * 清洗消息正文（含 revision 生效内容）；attachments 路径不改。
     */
    fun sanitizeMessage(message: MessageV2): MessageV2 {
        val original = message.effectiveContent()
        val cleaned = sanitizeText(original)
        if (cleaned == message.content && message.activeRevisionIndex == ACTIVE_REVISION_LATEST) {
            return message
        }
        // 出站用最新正文，避免历史 revision 仍带敏感词
        return message.copy(
            content = cleaned,
            activeRevisionIndex = ACTIVE_REVISION_LATEST
        )
    }

    /**
     * 清洗整轮上下文（user + assistant）。
     */
    fun sanitizeTurns(turns: List<ConversationTurn>): List<ConversationTurn> {
        if (turns.isEmpty()) return turns
        return turns.map { turn ->
            turn.copy(
                userMessage = sanitizeMessage(turn.userMessage),
                assistantMessage = turn.assistantMessage?.let { sanitizeMessage(it) }
            )
        }
    }

    /**
     * 对附件列表做稳定排序：按 localFilePath 字典序，避免同一请求因列表顺序抖动丢 cache。
     */
    fun stabilizeAttachments(message: MessageV2): MessageV2 {
        if (message.attachments.size <= 1) return message
        val sorted = message.attachments.sortedWith(
            compareBy(
                { it.localFilePath },
                { it.preparedFilePath },
                { it.mimeType }
            )
        )
        return if (sorted == message.attachments) message else message.copy(attachments = sorted)
    }

    /**
     * 出站轮次最终处理：敏感词清洗 + 附件稳定排序。
     */
    fun prepareTurnsForOutbound(turns: List<ConversationTurn>): List<ConversationTurn> {
        return sanitizeTurns(turns).map { turn ->
            turn.copy(
                userMessage = stabilizeAttachments(turn.userMessage),
                assistantMessage = turn.assistantMessage?.let { stabilizeAttachments(it) }
            )
        }
    }

    /**
     * 统计命中次数（调试用）：返回被替换的敏感词列表。
     */
    fun findHits(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val hits = mutableListOf<String>()
        val all = if (extraReplacements.isEmpty()) {
            BUILTIN_REPLACEMENTS
        } else {
            (BUILTIN_REPLACEMENTS + extraReplacements).sortedByDescending { it.first.length }
        }
        for ((raw, _) in all) {
            if (raw.isNotEmpty() && text.contains(raw)) {
                hits.add(raw)
            }
        }
        return hits
    }
}
