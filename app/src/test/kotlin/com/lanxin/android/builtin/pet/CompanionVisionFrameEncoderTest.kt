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

package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.CompanionVisionFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * FrameEncoder 中与 Android Bitmap 无关的纯尺寸逻辑。
 * （完整 encode 依赖 android.graphics.Bitmap，在 JVM 单测里不跑）
 */
class CompanionVisionFrameEncoderTest {

    @Test
    fun `max edge and quality constants are P0 bounds`() {
        assertTrue(CompanionVisionFrame.MAX_EDGE_PX in 256..1024)
        assertTrue(CompanionVisionFrame.JPEG_QUALITY in 40..95)
        assertEquals(768, CompanionVisionFrame.MAX_EDGE_PX)
        assertEquals(85, CompanionVisionFrame.JPEG_QUALITY)
    }

    @Test
    fun `scaleDown formula keeps aspect for long edge`() {
        // 与 CompanionVisionFrameEncoder.scaleDown 相同的纯算术
        fun expected(w: Int, h: Int, maxEdge: Int): Pair<Int, Int> {
            val edge = maxOf(w, h)
            if (edge <= maxEdge || maxEdge <= 0) return w to h
            val scale = maxEdge.toFloat() / edge.toFloat()
            val nw = maxOf(1, (w * scale).roundToInt())
            val nh = maxOf(1, (h * scale).roundToInt())
            return nw to nh
        }
        val (nw, nh) = expected(1920, 1080, 768)
        assertEquals(768, nw)
        assertEquals(432, nh)
        val noScale = expected(640, 480, 768)
        assertEquals(640 to 480, noScale)
    }
}
