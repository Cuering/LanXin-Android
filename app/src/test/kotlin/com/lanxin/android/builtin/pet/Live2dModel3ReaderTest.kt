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

import com.lanxin.android.builtin.pet.domain.Live2dDisplayController
import com.lanxin.android.builtin.pet.domain.Live2dModel3Reader
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Live2dModel3ReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sampleJson = """
        {
          "Version": 3,
          "FileReferences": {
            "Moc": "Mao.moc3",
            "Textures": ["Mao.2048/texture_00.png"]
          }
        }
    """.trimIndent()

    @Test
    fun assetPathFromFileUrl_stripsAndroidAssetPrefix() {
        val url = "file:///android_asset/pet/live2d/Mao/Mao.model3.json"
        assertEquals(
            "pet/live2d/Mao/Mao.model3.json",
            Live2dModel3Reader.assetPathFromFileUrl(url)
        )
        assertNull(Live2dModel3Reader.assetPathFromFileUrl("file:///data/foo.json"))
        assertNull(Live2dModel3Reader.assetPathFromFileUrl(""))
    }

    @Test
    fun readFileText_returnsContent() {
        val f = tmp.newFile("Mao.model3.json")
        f.writeText(sampleJson)
        val text = Live2dModel3Reader.readFileText(f)
        assertNotNull(text)
        assertTrue(text!!.contains("FileReferences"))
    }

    @Test
    fun withModel3Json_onlyOnShellMode() {
        val f = tmp.newFile("Mao.model3.json")
        f.writeText(sampleJson)
        val shell = Live2dDisplayController.decide(f.absolutePath)
        val enriched = Live2dDisplayController.withModel3Json(shell, sampleJson)
        assertEquals(sampleJson, enriched.model3Json)

        val blank = Live2dDisplayController.decide("")
        val stillBlank = Live2dDisplayController.withModel3Json(blank, sampleJson)
        assertTrue(stillBlank.model3Json.isEmpty())
    }

    @Test
    fun loadLive2dMessage_includesModel3B64WhenPresent() {
        val f = tmp.newFile("Mao.model3.json")
        f.writeText(sampleJson)
        val d = Live2dDisplayController.withModel3Json(
            Live2dDisplayController.decide(f.absolutePath),
            sampleJson
        )
        val wire = PetBridgeProtocol.encode(
            PetBridgeProtocol.loadLive2dMessage(d, timestampMs = 11L)
        )
        assertTrue(wire.contains("command=LOAD_LIVE2D"))
        assertTrue(wire.contains("live2dModel3B64="))
        val decoded = PetBridgeProtocol.decode(wire)
        val b64 = decoded.payload[PetBridgeProtocol.KEY_LIVE2D_MODEL3_B64]
        assertNotNull(b64)
        assertFalse(b64!!.contains("\n"))
        val roundTrip = PetBridgeProtocol.decodeModel3B64(b64)
        assertNotNull(roundTrip)
        assertTrue(roundTrip!!.contains("texture_00.png"))
        assertTrue(roundTrip.contains("Version"))
    }

    @Test
    fun loadLive2dMessage_omitsB64WhenEmpty() {
        val f = tmp.newFile("Mao.model3.json")
        f.writeText(sampleJson)
        val d = Live2dDisplayController.decide(f.absolutePath)
        assertTrue(d.model3Json.isEmpty())
        val wire = PetBridgeProtocol.encode(PetBridgeProtocol.loadLive2dMessage(d, timestampMs = 1L))
        assertFalse(wire.contains("live2dModel3B64="))
    }

    @Test
    fun encodeDecodeModel3B64_roundTrip() {
        val b64 = PetBridgeProtocol.encodeModel3B64(sampleJson)
        assertEquals(sampleJson, PetBridgeProtocol.decodeModel3B64(b64))
        assertNull(PetBridgeProtocol.decodeModel3B64("!!!not-base64!!!"))
        assertNull(PetBridgeProtocol.decodeModel3B64(""))
    }

    @Test
    fun decisionCopy_preservesUrlsWithJson() {
        val f = tmp.newFile("Mao.model3.json")
        f.writeText(sampleJson)
        val d = Live2dDisplayController.decide(f.absolutePath)
        val e = d.copy(model3Json = sampleJson)
        assertEquals(d.model3FileUrl, e.model3FileUrl)
        assertEquals(d.modelDirFileUrl, e.modelDirFileUrl)
        assertEquals(Live2dDisplayController.Live2dDisplayMode.LIVE2D_SHELL, e.mode)
    }
}
