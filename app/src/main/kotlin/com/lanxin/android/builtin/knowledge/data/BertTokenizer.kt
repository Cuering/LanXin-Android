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

package com.lanxin.android.builtin.knowledge.data

import java.io.InputStream
import java.text.Normalizer
import org.json.JSONObject

/**
 * 兼容 WordPiece Tokenizer（标准 HuggingFace BERT / GTE tokenizer.json）
 *
 * 流程：GTE-small 推理必备组件：normalize → basic tokenize → wordpiece → pad/truncate
 */
class BertTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val unkTokenId: Int,
    private val clsTokenId: Int,
    private val sepTokenId: Int,
    private val padTokenId: Int,
    private val doLowerCase: Boolean
) {
    data class Encoding(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray
    )

    fun encode(text: String, maxLength: Int = EmbeddingConstants.MAX_SEQ_LEN): Encoding {
        val tokens = mutableListOf(clsTokenId)
        for (word in basicTokenize(text)) {
            tokens.addAll(wordPiece(word))
        }
        tokens.add(sepTokenId)

        val truncated =
            if (tokens.size > maxLength) {
                tokens.subList(0, maxLength - 1).toMutableList().also { it.add(sepTokenId) }
            } else {
                tokens
            }

        val inputIds = LongArray(maxLength) { padTokenId.toLong() }
        val attentionMask = LongArray(maxLength)
        val tokenTypeIds = LongArray(maxLength)
        for (i in truncated.indices) {
            inputIds[i] = truncated[i].toLong()
            attentionMask[i] = 1L
        }
        return Encoding(inputIds, attentionMask, tokenTypeIds)
    }

    private fun basicTokenize(text: String): List<String> {
        var s = Normalizer.normalize(text, Normalizer.Form.NFD)
        s = s.replace("\\p{Mn}+".toRegex(), "")
        if (doLowerCase) s = s.lowercase()
        s = s.replace("\\s+".toRegex(), " ").trim()
        if (s.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                result.add(sb.toString())
                sb.clear()
            }
        }
        for (ch in s) {
            when {
                ch.isWhitespace() -> flush()
                isPunctuation(ch) || ch.code in 0x4E00..0x9FFF ||
                    ch.code in 0x3400..0x4DBF ||
                    ch.code in 0x20000..0x2A6DF -> {
                    flush()
                    result.add(ch.toString())
                }
                else -> sb.append(ch)
            }
        }
        flush()
        return result
    }

    private fun wordPiece(token: String): List<Int> {
        if (token in vocab) return listOf(vocab.getValue(token))
        val chars = token.toCharArray()
        if (chars.isEmpty()) return listOf(unkTokenId)

        val output = mutableListOf<Int>()
        var start = 0
        while (start < chars.size) {
            var end = chars.size
            var cur: String? = null
            while (start < end) {
                val substr =
                    buildString {
                        if (start > 0) append("##")
                        append(chars, start, end - start)
                    }
                if (substr in vocab) {
                    cur = substr
                    break
                }
                end--
            }
            if (cur == null) {
                return listOf(unkTokenId)
            }
            output.add(vocab.getValue(cur))
            start = end
        }
        return output
    }

    private fun isPunctuation(ch: Char): Boolean {
        val type = Character.getType(ch)
        return type == Character.CONNECTOR_PUNCTUATION.toInt() ||
            type == Character.DASH_PUNCTUATION.toInt() ||
            type == Character.START_PUNCTUATION.toInt() ||
            type == Character.END_PUNCTUATION.toInt() ||
            type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
            type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            type == Character.OTHER_PUNCTUATION.toInt() ||
            ch in "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
    }

    companion object {
        fun fromInputStream(stream: InputStream): BertTokenizer {
            val text = stream.bufferedReader().use { it.readText() }
            return fromJson(text)
        }

        fun fromJson(jsonText: String): BertTokenizer {
            // tokenizer.json 结构较大，简单使用 org.json 解析 model.vocab
            val root = JSONObject(jsonText)
            val model = root.getJSONObject("model")
            val vocabObj = model.getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vocabObj.length())
            val keys = vocabObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                vocab[key] = vocabObj.getInt(key)
            }

            var unk = vocab["[UNK]"] ?: 100
            var cls = vocab["[CLS]"] ?: 101
            var sep = vocab["[SEP]"] ?: 102
            var pad = vocab["[PAD]"] ?: 0
            var doLower = true

            // 解析 tokenizer 中 special tokens 定义在 added_tokens
            if (root.has("added_tokens")) {
                val added = root.getJSONArray("added_tokens")
                for (i in 0 until added.length()) {
                    val t = added.getJSONObject(i)
                    val content = t.optString("content")
                    val id = t.optInt("id")
                    when (content) {
                        "[UNK]" -> unk = id
                        "[CLS]" -> cls = id
                        "[SEP]" -> sep = id
                        "[PAD]" -> pad = id
                    }
                    vocab.putIfAbsent(content, id)
                }
            }

            // normalizer lowercase
            if (root.has("normalizer")) {
                val normalizer = root.opt("normalizer")
                doLower = normalizer.toString().contains("Lowercase", ignoreCase = true) ||
                    normalizer.toString().contains("\"lowercase\"", ignoreCase = true)
            }

            return BertTokenizer(
                vocab = vocab,
                unkTokenId = unk,
                clsTokenId = cls,
                sepTokenId = sep,
                padTokenId = pad,
                doLowerCase = doLower
            )
        }

        /** 测试用最小 tokenizer */
        fun createMinimalForTest(): BertTokenizer {
            val vocab = mapOf(
                "[PAD]" to 0,
                "[UNK]" to 100,
                "[CLS]" to 101,
                "[SEP]" to 102,
                "hello" to 7592,
                "world" to 2088,
                "你好" to 2000,
                "世界" to 2001,
                "##ing" to 3000
            )
            return BertTokenizer(vocab, 100, 101, 102, 0, doLowerCase = true)
        }
    }
}
