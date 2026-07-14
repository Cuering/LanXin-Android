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

package com.lanxin.android.builtin.knowledge

import com.lanxin.android.builtin.knowledge.data.MarkdownDocumentParser
import com.lanxin.android.builtin.knowledge.data.TxtDocumentParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownStripTest {

    private val parser = MarkdownDocumentParser(TxtDocumentParser())

    @Test
    fun `strips headings bold links and keeps code content`() {
        val md = """
            # 标题
            
            这是 **加粗** 和 *斜体* 文本。
            
            [链接文字](https://example.com)
            
            ![图片](https://example.com/a.png)
            
            - 列表项一
            - 列表项二
            
            ```kotlin
            fun hello() = "world"
            ```
            
            普通段落。
        """.trimIndent()

        val plain = parser.stripMarkdown(md)
        assertFalse(plain.contains("# 标题"))
        assertTrue(plain.contains("标题"))
        assertTrue(plain.contains("加粗"))
        assertTrue(plain.contains("斜体"))
        assertTrue(plain.contains("链接文字"))
        assertFalse(plain.contains("https://example.com"))
        assertTrue(plain.contains("fun hello()"))
        assertTrue(plain.contains("列表项一"))
        assertTrue(plain.contains("普通段落"))
    }

    @Test
    fun `empty markdown yields empty`() {
        assertTrue(parser.stripMarkdown("").isEmpty())
    }
}
