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

package com.lanxin.android.builtin.statistics

import com.lanxin.android.builtin.statistics.domain.TokenEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenEstimatorTest {

    @Test
    fun `empty text is zero`() {
        assertEquals(0, TokenEstimator.estimate(null))
        assertEquals(0, TokenEstimator.estimate(""))
    }

    @Test
    fun `cjk counts roughly one token per char`() {
        assertEquals(4, TokenEstimator.estimate("你好世界"))
    }

    @Test
    fun `ascii counts roughly four chars per token`() {
        assertEquals(2, TokenEstimator.estimate("abcdefgh"))
        assertEquals(1, TokenEstimator.estimate("abc"))
    }

    @Test
    fun `mixed text is sum of parts`() {
        // 2 CJK + 4 ascii = 2 + 1
        assertEquals(3, TokenEstimator.estimate("你好abcd"))
    }

    @Test
    fun `estimateMany sums all`() {
        val total = TokenEstimator.estimateMany(listOf("你好", "abcd", null, ""))
        assertTrue(total >= 3)
    }
}
