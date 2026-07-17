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

import java.io.File
import java.io.InputStream

/**
 * HTTP 下载传输抽象：单测 mock；生产用 Ktor。
 *
 * 不负责解压；只把字节落到 [destFile]。
 */
interface AssetDownloadTransport {

    /**
     * 下载 [url] 到 [destFile]。
     *
     * @param onProgress (downloaded, totalOr-1) — suspend 便于 UI 进度
     * @throws Exception 网络/HTTP 失败
     * @throws kotlinx.coroutines.CancellationException 取消
     */
    suspend fun downloadToFile(
        url: String,
        destFile: File,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    )

    /**
     * 打开 [url] 为流（小文件）。调用方负责关闭。
     */
    suspend fun openStream(url: String): InputStream
}
