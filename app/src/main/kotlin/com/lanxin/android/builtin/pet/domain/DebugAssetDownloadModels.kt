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
 * App 内 Debug 资源下载：分项、镜像源、进度与结果（纯模型，可单测）。
 *
 * 下载落盘 [DebugOpenSourcePaths.ROOT_DIR]（用户可访问的 `LanXin/`，见 [DebugAssetStorage]）。
 * **禁止**把 ASR/TTS 权重 commit 进 git；**禁止**在 AstrBot 服务器当下载盘。
 */
enum class DebugAssetKind {
    /** 可选：覆盖/备用官方 Sample（仓内 Mao 已内置）。 */
    LIVE2D,

    /** sherpa-onnx 中文 ASR 小模型。 */
    ASR,

    /** sherpa-onnx 中文 TTS（matcha-baker 优先）。 */
    TTS
}

/**
 * 下载源：官方优先可选手动镜像；失败时 downloader 会按回退顺序尝试。
 */
enum class DebugAssetMirror {
    /** 官方 GitHub releases / raw。 */
    OFFICIAL,

    /** 国内常见 ghproxy 前缀镜像（失败回退 OFFICIAL）。 */
    MIRROR_GHPROXY
}

/** UI / 许可短文案。 */
object DebugAssetLicense {
    const val LIVE2D_SAMPLE_TERMS_URL =
        "https://www.live2d.com/en/learn/sample/model-terms"

    const val LIVE2D_HINT =
        "Live2D 官方 Sample 受 Sample Data Terms 约束，仅学习/SDK 集成测试。" +
            "许可：$LIVE2D_SAMPLE_TERMS_URL"

    const val ASR_TTS_HINT =
        "ASR/TTS 使用 sherpa-onnx 开源模型（Apache-2.0）。大文件仅存本机 LanXin/，不进 git。"
}

/**
 * 单资源目录项：官方 URL 列表（按顺序尝试）+ 解压目标相对下载 baseDir。
 */
data class DebugAssetSpec(
    val kind: DebugAssetKind,
    val displayName: String,
    val sizeHint: String,
    val officialUrls: List<String>,
    val relativeReadyPath: String,
    val extractDirRel: String,
    val licenseHint: String
)

/** 进度事件。 */
sealed class DebugAssetDownloadEvent {
    data object Started : DebugAssetDownloadEvent()

    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val percent: Int,
        val mirror: DebugAssetMirror,
        val phase: String = "downloading"
    ) : DebugAssetDownloadEvent()

    data class Completed(
        val kind: DebugAssetKind,
        val readyPath: String,
        val mirror: DebugAssetMirror
    ) : DebugAssetDownloadEvent()

    data class Failed(
        val kind: DebugAssetKind,
        val message: String
    ) : DebugAssetDownloadEvent()

    data object Cancelled : DebugAssetDownloadEvent()
}

/** 当前某分项 UI 状态。 */
data class DebugAssetItemUi(
    val kind: DebugAssetKind,
    val displayName: String,
    val sizeHint: String,
    val licenseHint: String,
    val ready: Boolean = false,
    val readyPath: String = "",
    val downloading: Boolean = false,
    val percent: Int = -1,
    val statusText: String = "",
    val lastError: String? = null
)
