package com.lanxin.android.builtin.knowledge

import com.lanxin.android.builtin.knowledge.data.BertTokenizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BertTokenizerTest {

    private val tokenizer = BertTokenizer.createMinimalForTest()

    @Test
    fun `encode hello world`() {
        val encoding = tokenizer.encode("hello world")
        // [CLS]=101, hello=7592, world=2088, [SEP]=102, then [PAD]=0 padded
        assertEquals(512, encoding.inputIds.size)
        assertEquals(101L, encoding.inputIds[0])
        assertEquals(7592L, encoding.inputIds[1])
        assertEquals(2088L, encoding.inputIds[2])
        assertEquals(102L, encoding.inputIds[3])
        assertEquals(0L, encoding.inputIds[4])  // [PAD]
        // attention mask
        assertEquals(1L, encoding.attentionMask[0])
        assertEquals(1L, encoding.attentionMask[1])
        assertEquals(1L, encoding.attentionMask[2])
        assertEquals(1L, encoding.attentionMask[3])
        assertEquals(0L, encoding.attentionMask[4])
    }

    @Test
    fun `encode empty string returns cls sep`() {
        val encoding = tokenizer.encode("")
        assertEquals(101L, encoding.inputIds[0])
        assertEquals(102L, encoding.inputIds[1])
    }

    @Test
    fun `wordpiece subword tokenization with hash hash`() {
        val encoding = tokenizer.encode("testing")
        assertEquals(101L, encoding.inputIds[0])
        assertEquals(100L, encoding.inputIds[1])
    }

    @Test
    fun `chinese characters are tokenized per character`() {
        // BERT WordPiece 对 CJK 按单字切分：你好 → 你 + 好
        val encoding = tokenizer.encode("\u4f60\u597d")
        assertEquals(101L, encoding.inputIds[0]) // [CLS]
        assertEquals(2000L, encoding.inputIds[1]) // 你
        assertEquals(2001L, encoding.inputIds[2]) // 好
        assertEquals(102L, encoding.inputIds[3]) // [SEP]
    }

    @Test
    fun `truncation works for very long input`() {
        val longText = "hello ".repeat(600)
        val encoding = tokenizer.encode(longText)
        assertEquals(512, encoding.inputIds.size)
        // 512th token should be [SEP]
        assertEquals(102L, encoding.inputIds[511])
        // first token is [CLS]
        assertEquals(101L, encoding.inputIds[0])
    }

    @Test
    fun `inputIds and attentionMask lengths match`() {
        val encoding = tokenizer.encode("test sentence here")
        assertEquals(encoding.inputIds.size, encoding.attentionMask.size)
        assertEquals(encoding.inputIds.size, encoding.tokenTypeIds.size)
    }

    @Test
    fun `all pad tokens at tail have attention 0`() {
        val encoding = tokenizer.encode("a")
        val idx = encoding.inputIds.indexOfFirst { it == 0L }
        if (idx >= 0) {
            for (i in idx until encoding.attentionMask.size) {
                assertEquals(0L, encoding.attentionMask[i])
            }
        }
    }
}
