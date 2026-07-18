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
 * Debug 资源目录：URL 与落盘约定。
 *
 * 源站优先级（用户实测 GitHub raw/releases + 旧 ghproxy 均不可达）：
 * 1. jsDelivr / HuggingFace / hf-mirror
 * 2. 官方 GitHub 回退
 *
 * Live2D 仅使用**单文件** URL（禁止目录请求）。
 */
object DebugAssetCatalog {

    private const val ASR_RELEASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"
    private const val TTS_RELEASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

    private const val LIVE2D_RAW_BASE =
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/develop/Samples/Resources/Mao"
    private const val LIVE2D_JSDELIVR_BASE =
        "https://cdn.jsdelivr.net/gh/Live2D/CubismWebSamples@develop/Samples/Resources/Mao"
    private const val LIVE2D_FASTLY_BASE =
        "https://fastly.jsdelivr.net/gh/Live2D/CubismWebSamples@develop/Samples/Resources/Mao"

    private const val ASR_MODEL_DIR = "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
    private const val TTS_MODEL_DIR = "matcha-icefall-zh-baker"
    private const val HF_ASR_REPO = "csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
    private const val HF_TTS_REPO = "csukuangfj/matcha-icefall-zh-baker"
    private const val MS_LOCAL_LLM_REPO = "MNN/Qwen2.5-1.5B-Instruct-MNN"
    private const val HF_LOCAL_LLM_REPO = "taobao-mnn/Qwen2.5-1.5B-Instruct-MNN"

    val asr: DebugAssetSpec = DebugAssetSpec(
        kind = DebugAssetKind.ASR,
        displayName = "ASR（zipformer-zh-14M）",
        sizeHint = "~70MB",
        officialUrls = listOf(
            "$ASR_RELEASE/$ASR_MODEL_DIR.tar.bz2"
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
        displayName = "Live2D Mao（可选更新）",
        sizeHint = "~4.2MB",
        officialUrls = listOf("$LIVE2D_RAW_BASE/Mao.model3.json"),
        relativeReadyPath = DebugOpenSourcePaths.LIVE2D_MAO_MODEL3_REL,
        extractDirRel = "${DebugOpenSourcePaths.ROOT_DIR}/live2d/Mao",
        licenseHint = DebugAssetLicense.LIVE2D_HINT
    )

    /** 本地脑轻量：ModelScope MNN/Qwen2.5-1.5B-Instruct-MNN 运行时文件。 */
    val localLlm: DebugAssetSpec = DebugAssetSpec(
        kind = DebugAssetKind.LOCAL_LLM,
        displayName = "本地脑（Qwen2.5-1.5B MNN）",
        sizeHint = "~880MB",
        officialUrls = listOf(
            "https://huggingface.co/taobao-mnn/Qwen2.5-1.5B-Instruct-MNN/resolve/main/llm.mnn"
        ),
        relativeReadyPath = DebugOpenSourcePaths.LOCAL_LLM_LIGHT_DIR_REL,
        extractDirRel = DebugOpenSourcePaths.LOCAL_LLM_LIGHT_DIR_REL,
        licenseHint = DebugAssetLicense.LOCAL_LLM_HINT
    )

    fun spec(kind: DebugAssetKind): DebugAssetSpec = when (kind) {
        DebugAssetKind.LIVE2D -> live2d
        DebugAssetKind.ASR -> asr
        DebugAssetKind.TTS -> tts
        DebugAssetKind.LOCAL_LLM -> localLlm
    }

    fun all(): List<DebugAssetSpec> = listOf(live2d, asr, tts, localLlm)

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

    /** ASR 目录必要文件（HF 无单 tar 时按列表下载）。 */
    val asrModelRelativeFiles: List<String> = listOf(
        "tokens.txt",
        "encoder-epoch-99-avg-1.int8.onnx",
        "decoder-epoch-99-avg-1.int8.onnx",
        "joiner-epoch-99-avg-1.int8.onnx"
    )

    /** TTS matcha-baker 目录必要文件。 */
    val ttsModelRelativeFiles: List<String> = listOf(
        "model-steps-3.onnx",
        "tokens.txt",
        "lexicon.txt",
        "date.fst",
        "number.fst",
        "phone.fst",
        "dict/jieba.dict.utf8",
        "dict/hmm_model.utf8",
        "dict/idf.utf8",
        "dict/stop_words.utf8",
        "dict/user.dict.utf8",
        "dict/pos_dict/char_state_tab.utf8",
        "dict/pos_dict/prob_emit.utf8",
        "dict/pos_dict/prob_start.utf8",
        "dict/pos_dict/prob_trans.utf8"
    )

    /**
     * 本地脑 MNN 运行时必要文件（不含 README / .gitattributes）。
     * 源：ModelScope `MNN/Qwen2.5-1.5B-Instruct-MNN`。
     */
    val localLlmRelativeFiles: List<String> = listOf(
        "config.json",
        "configuration.json",
        "llm_config.json",
        "llm.mnn",
        "llm.mnn.json",
        "llm.mnn.weight",
        "tokenizer.txt"
    )

    /** 默认单文件 URL：优先 jsDelivr。 */
    fun live2dFileUrl(relativeFile: String): String {
        val rel = normalizeRelFile(relativeFile)
        return "$LIVE2D_JSDELIVR_BASE/$rel"
    }

    /**
     * Live2D 单文件候选：jsDelivr → fastly → github-raw。
     * **不含**旧 ghproxy。
     */
    fun live2dFileCandidates(
        relativeFile: String,
        preferred: DebugAssetMirror = DebugAssetMirror.MIRROR_CDN
    ): List<DebugAssetUrlCandidate> {
        val rel = normalizeRelFile(relativeFile)
        val raw = "$LIVE2D_RAW_BASE/$rel"
        val jsdelivr = "$LIVE2D_JSDELIVR_BASE/$rel"
        val fastly = "$LIVE2D_FASTLY_BASE/$rel"
        val cdnFirst = listOf(
            DebugAssetUrlCandidate(jsdelivr, DebugAssetMirror.MIRROR_CDN, "jsdelivr"),
            DebugAssetUrlCandidate(fastly, DebugAssetMirror.MIRROR_CDN, "fastly-jsdelivr"),
            DebugAssetUrlCandidate(raw, DebugAssetMirror.OFFICIAL, "github-raw")
        )
        val officialFirst = listOf(
            DebugAssetUrlCandidate(raw, DebugAssetMirror.OFFICIAL, "github-raw"),
            DebugAssetUrlCandidate(jsdelivr, DebugAssetMirror.MIRROR_CDN, "jsdelivr"),
            DebugAssetUrlCandidate(fastly, DebugAssetMirror.MIRROR_CDN, "fastly-jsdelivr")
        )
        return when (preferred) {
            DebugAssetMirror.MIRROR_CDN -> cdnFirst
            DebugAssetMirror.OFFICIAL -> officialFirst
        }
    }

    /** 兼容别名。 */
    fun live2dFileCandidatesOrdered(
        relativeFile: String,
        preferred: DebugAssetMirror
    ): List<DebugAssetUrlCandidate> = live2dFileCandidates(relativeFile, preferred)

    data class MultiFileSource(
        val baseUrl: String,
        val mirror: DebugAssetMirror,
        val label: String,
        val relativeFiles: List<String>,
        val modelDirRel: String
    )

    fun asrMultiFileSources(
        preferred: DebugAssetMirror = DebugAssetMirror.MIRROR_CDN
    ): List<MultiFileSource> {
        val files = asrModelRelativeFiles
        val modelDirRel = "${DebugOpenSourcePaths.ROOT_DIR}/asr/$ASR_MODEL_DIR"
        val mirrorFirst = MultiFileSource(
            baseUrl = "https://hf-mirror.com/$HF_ASR_REPO/resolve/main",
            mirror = DebugAssetMirror.MIRROR_CDN,
            label = "hf-mirror",
            relativeFiles = files,
            modelDirRel = modelDirRel
        )
        val official = MultiFileSource(
            baseUrl = "https://huggingface.co/$HF_ASR_REPO/resolve/main",
            mirror = DebugAssetMirror.OFFICIAL,
            label = "huggingface",
            relativeFiles = files,
            modelDirRel = modelDirRel
        )
        return if (preferred == DebugAssetMirror.OFFICIAL) {
            listOf(official, mirrorFirst)
        } else {
            listOf(mirrorFirst, official)
        }
    }

    fun ttsMultiFileSources(
        preferred: DebugAssetMirror = DebugAssetMirror.MIRROR_CDN
    ): List<MultiFileSource> {
        val files = ttsModelRelativeFiles
        val modelDirRel = "${DebugOpenSourcePaths.ROOT_DIR}/tts/$TTS_MODEL_DIR"
        val mirrorFirst = MultiFileSource(
            baseUrl = "https://hf-mirror.com/$HF_TTS_REPO/resolve/main",
            mirror = DebugAssetMirror.MIRROR_CDN,
            label = "hf-mirror",
            relativeFiles = files,
            modelDirRel = modelDirRel
        )
        val official = MultiFileSource(
            baseUrl = "https://huggingface.co/$HF_TTS_REPO/resolve/main",
            mirror = DebugAssetMirror.OFFICIAL,
            label = "huggingface",
            relativeFiles = files,
            modelDirRel = modelDirRel
        )
        return if (preferred == DebugAssetMirror.OFFICIAL) {
            listOf(official, mirrorFirst)
        } else {
            listOf(mirrorFirst, official)
        }
    }

    /**
     * 本地脑多文件源：ModelScope（国内优先）→ hf-mirror → HuggingFace。
     * preferred=OFFICIAL 时 HF 先于 ModelScope。
     *
     * ModelScope 同时提供 `modelscope.cn` 与 `www.modelscope.cn` 候选，
     * 部分运营商/DNS 对裸域建连慢或失败。
     */
    fun localLlmMultiFileSources(
        preferred: DebugAssetMirror = DebugAssetMirror.MIRROR_CDN
    ): List<MultiFileSource> {
        val files = localLlmRelativeFiles
        val modelDirRel = DebugOpenSourcePaths.LOCAL_LLM_LIGHT_DIR_REL
        val modelscope = MultiFileSource(
            baseUrl = "https://modelscope.cn/models/$MS_LOCAL_LLM_REPO/resolve/master",
            mirror = DebugAssetMirror.MIRROR_CDN,
            label = "modelscope",
            relativeFiles = files,
            modelDirRel = modelDirRel
        )
        val modelscopeWww = MultiFileSource(
            baseUrl = "https://www.modelscope.cn/models/$MS_LOCAL_LLM_REPO/resolve/master",
            mirror = DebugAssetMirror.MIRROR_CDN,
            label = "modelscope-www",
            relativeFiles = files,
            modelDirRel = modelDirRel
        )
        val hfMirror = MultiFileSource(
            baseUrl = "https://hf-mirror.com/$HF_LOCAL_LLM_REPO/resolve/main",
            mirror = DebugAssetMirror.MIRROR_CDN,
            label = "hf-mirror",
            relativeFiles = files,
            modelDirRel = modelDirRel
        )
        val huggingface = MultiFileSource(
            baseUrl = "https://huggingface.co/$HF_LOCAL_LLM_REPO/resolve/main",
            mirror = DebugAssetMirror.OFFICIAL,
            label = "huggingface",
            relativeFiles = files,
            modelDirRel = modelDirRel
        )
        return if (preferred == DebugAssetMirror.OFFICIAL) {
            listOf(huggingface, hfMirror, modelscope, modelscopeWww)
        } else {
            listOf(modelscope, modelscopeWww, hfMirror, huggingface)
        }
    }

    /**
     * ASR/TTS 归档候选：仅官方 GitHub release（最后回退）。
     * 不再使用 ghproxy 包装。LOCAL_LLM 无归档。
     */
    fun archiveCandidates(
        kind: DebugAssetKind,
        preferred: DebugAssetMirror
    ): List<DebugAssetUrlCandidate> {
        @Suppress("UNUSED_PARAMETER")
        val ignored = preferred
        require(kind == DebugAssetKind.ASR || kind == DebugAssetKind.TTS) {
            "archiveCandidates 仅支持 ASR/TTS，收到 $kind"
        }
        return spec(kind).officialUrls.map { url ->
            DebugAssetUrlCandidate(url, DebugAssetMirror.OFFICIAL, "github-release")
        }
    }

    /**
     * 兼容旧 API。CDN 不再改写 URL（不再前缀 ghproxy）。
     */
    fun resolveUrl(officialUrl: String, mirror: DebugAssetMirror, prefixIndex: Int = 0): String {
        @Suppress("UNUSED_VARIABLE")
        val ignored = prefixIndex
        return officialUrl
    }

    fun mirrorAttemptOrder(preferred: DebugAssetMirror): List<Pair<DebugAssetMirror, Int>> {
        return when (preferred) {
            DebugAssetMirror.OFFICIAL -> listOf(
                DebugAssetMirror.OFFICIAL to 0,
                DebugAssetMirror.MIRROR_CDN to 0
            )
            DebugAssetMirror.MIRROR_CDN -> listOf(
                DebugAssetMirror.MIRROR_CDN to 0,
                DebugAssetMirror.OFFICIAL to 0
            )
        }
    }

    private fun normalizeRelFile(relativeFile: String): String {
        val rel = relativeFile.trim().trimStart('/')
        require(rel.isNotBlank() && !rel.endsWith('/')) {
            "Live2D URL 必须是单文件，不能是目录: $relativeFile"
        }
        return rel
    }

    const val LIVE2D_DOWNLOAD_FAIL_HINT =
        "在线更新失败；已使用内置 Mao，无需下载即可显示 Live2D。"

    const val LIVE2D_BUILTIN_PRIMARY_HINT =
        "Live2D 默认使用仓内官方 Sample Mao，开箱即用，无需联网下载。" +
            "下方「更新」仅覆盖到本机 LanXin/；失败不影响内置模型。"

    /**
     * 本地脑下载失败提示：Wi‑Fi 重试 + 电脑手动落盘路径。
     * 手动路径：`LanXin/models/local-llm/light/`（config.json / llm.mnn / weight 等）。
     */
    const val LOCAL_LLM_DOWNLOAD_FAIL_HINT =
        "可连 Wi‑Fi 重试；或电脑下载后放到 LanXin/models/local-llm/light/" +
            "（config.json、llm_config.json、llm.mnn、llm.mnn.json、llm.mnn.weight、tokenizer 等）。"
}
