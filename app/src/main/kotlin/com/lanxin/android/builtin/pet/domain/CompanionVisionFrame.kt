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

/**
 * 内存中的一帧缩略图（**不落盘**、不写日志原图）。
 *
 * @property jpegBase64 降采样 JPEG 的 base64（无 data: 前缀）
 * @property mimeType 固定 image/jpeg
 * @property width 编码后宽
 * @property height 编码后高
 * @property capturedAtMs epoch ms
 */
data class CompanionVisionFrame(
    val jpegBase64: String,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
    val capturedAtMs: Long = 0L
) {
    fun dataUri(): String = "data:$mimeType;base64,$jpegBase64"

    companion object {
        const val MAX_EDGE_PX = 768
        const val JPEG_QUALITY = 85
    }
}
