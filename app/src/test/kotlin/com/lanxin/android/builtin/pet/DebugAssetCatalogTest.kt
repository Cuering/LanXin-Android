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

import com.lanxin.android.builtin.pet.domain.DebugAssetCatalog
import com.lanxin.android.builtin.pet.domain.DebugAssetKind
import com.lanxin.android.builtin.pet.domain.DebugAssetLicense
import com.lanxin.android.builtin.pet.domain.DebugOpenSourcePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugAssetCatalogTest {

    @Test
    fun specs_coverFourKinds() {
        assertEquals(4, DebugAssetCatalog.all().size)
        assertEquals(DebugAssetKind.ASR, DebugAssetCatalog.asr.kind)
        assertEquals(DebugAssetKind.TTS, DebugAssetCatalog.tts.kind)
        assertEquals(DebugAssetKind.LIVE2D, DebugAssetCatalog.live2d.kind)
        assertEquals(DebugAssetKind.LOCAL_LLM, DebugAssetCatalog.localLlm.kind)
    }

    @Test
    fun asrUrl_pointsToSherpaRelease() {
        val url = DebugAssetCatalog.asr.officialUrls.first()
        assertTrue(url.contains("sherpa-onnx"))
        assertTrue(url.contains("zipformer-zh-14M"))
        assertTrue(
            DebugAssetCatalog.asr.relativeReadyPath.startsWith(DebugOpenSourcePaths.ROOT_DIR)
        )
    }

    @Test
    fun asr_cdnSourcesPreferHfMirror() {
        val sources = DebugAssetCatalog.asrMultiFileSources(
            com.lanxin.android.builtin.pet.domain.DebugAssetMirror.MIRROR_CDN
        )
        assertTrue(sources.first().baseUrl.contains("hf-mirror.com"))
        assertTrue(sources[1].baseUrl.contains("huggingface.co"))
        assertTrue(sources.first().baseUrl.contains("resolve/main"))
        assertTrue(DebugAssetCatalog.asrModelRelativeFiles.contains("tokens.txt"))
        assertTrue(
            DebugAssetCatalog.asrModelRelativeFiles.any { it.contains("encoder") }
        )
        assertTrue(sources.first().modelDirRel.contains("LanXin/asr/"))
    }

    @Test
    fun tts_cdnSourcesPreferHfMirror() {
        val sources = DebugAssetCatalog.ttsMultiFileSources(
            com.lanxin.android.builtin.pet.domain.DebugAssetMirror.MIRROR_CDN
        )
        assertTrue(sources.first().baseUrl.contains("hf-mirror.com"))
        assertTrue(sources.first().baseUrl.contains("matcha-icefall-zh-baker"))
        assertTrue(sources[1].baseUrl.contains("huggingface.co"))
        assertTrue(DebugAssetCatalog.ttsModelRelativeFiles.contains("model-steps-3.onnx"))
        assertTrue(DebugAssetCatalog.ttsModelRelativeFiles.contains("lexicon.txt"))
        assertTrue(sources.first().modelDirRel.endsWith("tts/matcha-icefall-zh-baker"))
    }

    @Test
    fun archiveCandidates_areGithubOnlyNoGhproxy() {
        val asr = DebugAssetCatalog.archiveCandidates(
            DebugAssetKind.ASR,
            com.lanxin.android.builtin.pet.domain.DebugAssetMirror.MIRROR_CDN
        )
        assertTrue(asr.isNotEmpty())
        assertTrue(asr.all { it.url.contains("github.com") })
        assertTrue(asr.none { it.url.contains("ghproxy") })
        val tts = DebugAssetCatalog.archiveCandidates(
            DebugAssetKind.TTS,
            com.lanxin.android.builtin.pet.domain.DebugAssetMirror.OFFICIAL
        )
        assertTrue(tts.any { it.url.contains("matcha-icefall-zh-baker") })
    }

    @Test
    fun live2d_candidatesPreferJsdelivr() {
        val url = DebugAssetCatalog.live2dFileUrl("Mao.model3.json")
        assertTrue(url.contains("cdn.jsdelivr.net"))
        val ordered = DebugAssetCatalog.live2dFileCandidatesOrdered(
            "Mao.moc3",
            com.lanxin.android.builtin.pet.domain.DebugAssetMirror.MIRROR_CDN
        )
        assertTrue(ordered.first().label.contains("jsdelivr"))
        assertTrue(ordered.none { it.url.contains("ghproxy") })
    }

    @Test
    fun live2d_hasModel3AndMoc3InFileList() {
        val files = DebugAssetCatalog.live2dMaoRelativeFiles
        assertTrue(files.contains("Mao.model3.json"))
        assertTrue(files.contains("Mao.moc3"))
        assertTrue(files.any { it.startsWith("motions/") })
        assertTrue(DebugAssetLicense.LIVE2D_HINT.contains("Sample"))
        assertTrue(DebugAssetLicense.LIVE2D_SAMPLE_TERMS_URL.contains("model-terms"))
    }

    @Test
    fun fetchHint_mentionsInAppDownload() {
        assertTrue(DebugOpenSourcePaths.FETCH_SCRIPT_HINT.contains("一键下载"))
        assertTrue(DebugOpenSourcePaths.FETCH_SCRIPT_HINT.contains("AstrBot"))
        assertTrue(DebugOpenSourcePaths.FETCH_SCRIPT_HINT.contains("LanXin"))
    }

    @Test
    fun rootDir_isUserVisibleLanXin() {
        assertEquals("LanXin", DebugOpenSourcePaths.ROOT_DIR)
        assertEquals("debug-assets", DebugOpenSourcePaths.LEGACY_ROOT_DIR)
    }

    @Test
    fun localLlm_sourcesPreferModelscope() {
        val sources = DebugAssetCatalog.localLlmMultiFileSources(
            com.lanxin.android.builtin.pet.domain.DebugAssetMirror.MIRROR_CDN
        )
        assertTrue(sources.first().baseUrl.contains("modelscope.cn"))
        assertTrue(sources.first().label == "modelscope")
        assertTrue(sources.any { it.baseUrl.contains("hf-mirror.com") })
        assertTrue(sources.any { it.baseUrl.contains("huggingface.co") })
        assertTrue(DebugAssetCatalog.localLlmRelativeFiles.contains("llm.mnn"))
        assertTrue(DebugAssetCatalog.localLlmRelativeFiles.contains("llm.mnn.weight"))
        assertTrue(DebugAssetCatalog.localLlmRelativeFiles.contains("tokenizer.txt"))
        assertTrue(
            sources.first().modelDirRel.endsWith("models/local-llm/light")
        )
        assertTrue(
            DebugAssetCatalog.localLlm.relativeReadyPath.contains("LanXin/models/local-llm")
        )
    }
}
