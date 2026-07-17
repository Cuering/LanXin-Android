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
 * Debug 资源目录：URL 与落盘约定，对齐 scripts/download-debug-*.sh。
 */
object DebugAssetCatalog {

    private const val ASR_RELEASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"
    private const val TTS_RELEASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

    private const val LIVE2D_RAW_BASE =
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/develop/Samples/Resources/Mao"

    val ghproxyPrefixes: List<String> = listOf(
        "https://ghproxy.net/",
        "https://mirror.ghproxy.com/",
        "https://gh.ddlc.top/"
    )

    val asr: DebugAssetSpec = DebugAssetSpec(
        kind = DebugAssetKind.ASR,
        displayName = "ASR（zipformer-zh-14M）",
        sizeHint = "~70MB",
        officialUrls = listOf(
            "$ASR_RELEASE/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2"
        ),
        relativeReadyPath = DebugOpenSourcePaths.ASR_ZIPFORMER_14M_REL,
        extractDirRel = "${DebugOpenSourcePaths.ROOT_DIR}/asr",
        licenseHint = DebugAssetLicense.ASR_TTS_HINT
    )

    val tts: DebugAssetSpec = DebugAssetSpec(
        kind = DebugAssetKind.TTS,
        displayName = "TTS（matcha-icefall-zh-baker）",
        sizeHint = "~50–80MB",
        officialUrls = listOf(
            "$TTS_RELEASE/matcha-icefall-zh-baker.tar.bz2",
            "$TTS_RELEASE/sherpa-onnx-matcha-icefall-zh-baker.tar.bz2"
        ),
        relativeReadyPath = DebugOpenSourcePaths.TTS_MATCHA_BAKER_REL,
        extractDirRel = "${DebugOpenSourcePaths.ROOT_DIR}/tts",
        licenseHint = DebugAssetLicense.ASR_TTS_HINT
    )

    val live2d: DebugAssetSpec = DebugAssetSpec(
        kind = DebugAssetKind.LIVE2D,
        displayName = "Live2D Mao（备用/更新）",
        sizeHint = "~4.2MB",
        officialUrls = listOf("$LIVE2D_RAW_BASE/Mao.model3.json"),
        relativeReadyPath = DebugOpenSourcePaths.LIVE2D_MAO_MODEL3_REL,
        extractDirRel = "${DebugOpenSourcePaths.ROOT_DIR}/live2d/Mao",
        licenseHint = DebugAssetLicense.LIVE2D_HINT
    )

    fun spec(kind: DebugAssetKind): DebugAssetSpec = when (kind) {
        DebugAssetKind.LIVE2D -> live2d
        DebugAssetKind.ASR -> asr
        DebugAssetKind.TTS -> tts
    }

    fun all(): List<DebugAssetSpec> = listOf(live2d, asr, tts)

    val live2dMaoRelativeFiles: List<String> = listOf(
        "Mao.model3.json",
        "Mao.moc3",
        "Mao.physics3.json",
        "Mao.pose3.json",
        "Mao.cdi3.json",
        "Mao.2048/texture_00.png",
        "expressions/exp_01.exp3.json",
        "expressions/exp_02.exp3.json",
        "expressions/exp_03.exp3.json",
        "expressions/exp_04.exp3.json",
        "expressions/exp_05.exp3.json",
        "expressions/exp_06.exp3.json",
        "expressions/exp_07.exp3.json",
        "expressions/exp_08.exp3.json",
        "motions/mtn_01.motion3.json",
        "motions/mtn_02.motion3.json",
        "motions/mtn_03.motion3.json",
        "motions/mtn_04.motion3.json",
        "motions/sample_01.motion3.json",
        "motions/special_01.motion3.json",
        "motions/special_02.motion3.json",
        "motions/special_03.motion3.json"
    )

    fun live2dFileUrl(relativeFile: String): String =
        "$LIVE2D_RAW_BASE/$relativeFile"

    fun resolveUrl(officialUrl: String, mirror: DebugAssetMirror, prefixIndex: Int = 0): String {
        if (mirror == DebugAssetMirror.OFFICIAL) return officialUrl
        val prefixes = ghproxyPrefixes
        val prefix = prefixes.getOrElse(prefixIndex.coerceAtLeast(0)) { prefixes.first() }
        return prefix + officialUrl
    }

    fun mirrorAttemptOrder(preferred: DebugAssetMirror): List<Pair<DebugAssetMirror, Int>> {
        return when (preferred) {
            DebugAssetMirror.OFFICIAL -> listOf(DebugAssetMirror.OFFICIAL to 0)
            DebugAssetMirror.MIRROR_GHPROXY -> {
                buildList {
                    ghproxyPrefixes.indices.forEach { add(DebugAssetMirror.MIRROR_GHPROXY to it) }
                    add(DebugAssetMirror.OFFICIAL to 0)
                }
            }
        }
    }
}
