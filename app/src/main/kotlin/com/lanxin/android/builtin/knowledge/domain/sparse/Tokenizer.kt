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

package com.lanxin.android.builtin.knowledge.domain.sparse

/**
 * 轻量中英混合分词器。
 *
 * - 中文（CJK）：unigram + bigram
 * - 英文/数字：按空格与标点切分并小写
 * - 过滤精简停用词
 */
object Tokenizer {

    /** 精简中文停用词 + 英文冠词/介词。 */
    private val STOPWORDS: Set<String> = setOf(
        // 中文
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
        "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
        "你", "会", "着", "没有", "看", "好", "自己", "这", "他", "她",
        "它", "们", "与", "及", "或", "而", "被", "把", "让", "给",
        "从", "对", "于", "以", "等", "其", "中", "为", "之",
        // 英文
        "a", "an", "the", "and", "or", "but", "if", "in", "on", "at",
        "to", "for", "of", "as", "by", "with", "from", "is", "are",
        "was", "were", "be", "been", "being", "this", "that", "it",
        "its", "we", "you", "he", "she", "they", "them", "our", "your"
    )

    /**
     * 将文本分词为 token 列表（已去停用词，保留顺序，可重复）。
     */
    fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val tokens = ArrayList<String>(text.length / 2 + 4)
        val latin = StringBuilder()

        fun flushLatin() {
            if (latin.isEmpty()) return
            val word = latin.toString().lowercase()
            latin.clear()
            if (word.isNotEmpty() && word !in STOPWORDS) {
                tokens.add(word)
            }
        }

        var i = 0
        val chars = text
        while (i < chars.length) {
            val c = chars[i]
            when {
                isCjk(c) -> {
                    flushLatin()
                    // unigram
                    val uni = c.toString()
                    if (uni !in STOPWORDS) {
                        tokens.add(uni)
                    }
                    // bigram with next CJK
                    if (i + 1 < chars.length && isCjk(chars[i + 1])) {
                        val bi = "" + c + chars[i + 1]
                        if (bi !in STOPWORDS) {
                            tokens.add(bi)
                        }
                    }
                    i++
                }
                c.isLetterOrDigit() -> {
                    latin.append(c)
                    i++
                }
                else -> {
                    flushLatin()
                    i++
                }
            }
        }
        flushLatin()
        return tokens
    }

    /**
     * 去重后的 token 集合（查询侧可用）。
     */
    fun tokenizeUnique(text: String): List<String> =
        tokenize(text).distinct()

    private fun isCjk(c: Char): Boolean {
        val type = Character.UnicodeBlock.of(c) ?: return false
        return type == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            type == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            type == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            type == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            type == Character.UnicodeBlock.HIRAGANA ||
            type == Character.UnicodeBlock.KATAKANA ||
            type == Character.UnicodeBlock.HANGUL_SYLLABLES
    }
}
