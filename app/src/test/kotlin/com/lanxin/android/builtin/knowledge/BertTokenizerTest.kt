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
        assertEquals(101, encoding.inputIds[0])
        assertEquals(7592, encoding.inputIds[1])
        assertEquals(2088, encoding.inputIds[2])
        assertEquals(102, encoding.inputIds[3])
        assertEquals(0, encoding.inputIds[4])  // [PAD]
        // attention mask
        assertEquals(1, encoding.attentionMask[0])
        assertEquals(1, encoding.attentionMask[1])
        assertEquals(1, encoding.attentionMask[2])
        assertEquals(1, encoding.attentionMask[3])
        assertEquals(0, encoding.attentionMask[4])
    }

    @Test
    fun `encode empty string returns cls sep`() {
        val encoding = tokenizer.encode("")
        assertEquals(101, encoding.inputIds[0])
        assertEquals(102, encoding.inputIds[1])
    }

    @Test
    fun `wordpiece subword tokenization with hash hash`() {
        val encoding = tokenizer.encode("testing")
        // test + ##ing
        assertEquals(101, encoding.inputIds[0])
        // test should be [UNK]=100, ##ing=3000
        // Actually the minimal vocab doesn't have "test", but has "##ing"
        // So "testing" -> unk + ##ing ?
        // wordPiece splits: t e s t i n g, start=0 end=7
        // tries testing, testin, testi, test -> not found
        // tests... eventually end=0, shouldn't happen
        // Actually with wordPiece algorithm, it tries the longest prefix
        // For "testing", the loop goes:
        // start=0, end=7, substr="testing" not in vocab
        // end=6, "testin" not in vocab
        // end=5, "testi" not in vocab
        // end=4, "test" not in vocab (only has "##ing")
        // end=3, "tes" not in vocab
        // end=2, "te" not in vocab
        // end=1, "t" not in vocab
        // No match found -> return [UNK]
        // So "testing" -> [CLS][UNK][SEP]
        assertEquals(100, encoding.inputIds[1])
    }

    @Test
    fun `chinese characters are not split`() {
        val encoding = tokenizer.encode("\u4f60\u597d")
        assertEquals(101, encoding.inputIds[0])
        assertEquals(2000, encoding.inputIds[1])
        assertEquals(102, encoding.inputIds[2])
    }

    @Test
    fun `truncation works for very long input`() {
        val longText = "hello ".repeat(600)
        val encoding = tokenizer.encode(longText)
        assertEquals(512, encoding.inputIds.size)
        // 512th token should be [SEP]
        assertEquals(102, encoding.inputIds[511])
        // first token is [CLS]
        assertEquals(101, encoding.inputIds[0])
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
                assertEquals(0, encoding.attentionMask[i].toInt())
            }
        }
    }
}
