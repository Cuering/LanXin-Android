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

package com.lanxin.android.builtin.pet.domain

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Bitmap → 内存 JPEG base64（不落盘）。
 */
object CompanionVisionFrameEncoder {

    fun encode(
        bitmap: Bitmap,
        maxEdge: Int = CompanionVisionFrame.MAX_EDGE_PX,
        quality: Int = CompanionVisionFrame.JPEG_QUALITY,
        nowMs: Long = System.currentTimeMillis()
    ): CompanionVisionFrame? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val scaled = scaleDown(bitmap, maxEdge)
        return try {
            val baos = ByteArrayOutputStream()
            val ok = scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(40, 95), baos)
            if (!ok) return null
            val bytes = baos.toByteArray()
            if (bytes.isEmpty()) return null
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            CompanionVisionFrame(
                jpegBase64 = b64,
                mimeType = "image/jpeg",
                width = scaled.width,
                height = scaled.height,
                capturedAtMs = nowMs
            )
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
        }
    }

    fun scaleDown(src: Bitmap, maxEdge: Int): Bitmap {
        val edge = max(src.width, src.height)
        if (edge <= maxEdge || maxEdge <= 0) return src
        val scale = maxEdge.toFloat() / edge.toFloat()
        val w = max(1, (src.width * scale).roundToInt())
        val h = max(1, (src.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
