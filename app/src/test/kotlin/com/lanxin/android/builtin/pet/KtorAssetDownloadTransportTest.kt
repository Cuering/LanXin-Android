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

import com.lanxin.android.builtin.pet.data.KtorAssetDownloadTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载专用超时：不复用 API 默认短超时。
 * connect ≥ 30s；socket 长空闲；request=0 禁用（大文件靠进度 + 取消）。
 */
class KtorAssetDownloadTransportTest {

    @Test
    fun downloadTimeouts_areGenerousForLargeAssets() {
        assertTrue(
            "connectTimeout 应 ≥ 30s，弱网建连",
            KtorAssetDownloadTransport.CONNECT_TIMEOUT_MS >= 30_000L
        )
        assertTrue(
            "connectTimeout 建议 60s",
            KtorAssetDownloadTransport.CONNECT_TIMEOUT_MS >= 60_000L
        )
        assertTrue(
            "socketTimeout 应 ≥ 2min，大文件读空闲",
            KtorAssetDownloadTransport.SOCKET_TIMEOUT_MS >= 120_000L
        )
        assertEquals(
            "requestTimeout=0 禁用整包上限，避免 880MB 被掐（Ktor 3.x）",
            0L,
            KtorAssetDownloadTransport.REQUEST_TIMEOUT_MS
        )
    }
}
