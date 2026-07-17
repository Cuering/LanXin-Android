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
    fun specs_coverThreeKinds() {
        assertEquals(3, DebugAssetCatalog.all().size)
        assertEquals(DebugAssetKind.ASR, DebugAssetCatalog.asr.kind)
        assertEquals(DebugAssetKind.TTS, DebugAssetCatalog.tts.kind)
        assertEquals(DebugAssetKind.LIVE2D, DebugAssetCatalog.live2d.kind)
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
    }
}
