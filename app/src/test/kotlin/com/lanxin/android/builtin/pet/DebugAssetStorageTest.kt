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

import com.lanxin.android.builtin.pet.domain.DebugAssetStorage
import com.lanxin.android.builtin.pet.domain.DebugOpenSourcePaths
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 无 Android Context 的 [DebugAssetStorage] 契约：
 * - 无 SAF 时仍可写（fromBaseDir / File 主路径）
 * - 镜像仅在 usedFallback + safWritable + 非空 treeUri 时放行
 */
class DebugAssetStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun fromBaseDir_writableWithoutSaf() {
        val base = tmp.newFolder("app-files")
        val root = DebugAssetStorage.fromBaseDir(base)

        assertEquals(base, root.baseDir)
        assertEquals(
            File(base, DebugOpenSourcePaths.ROOT_DIR).absolutePath,
            root.lanXinDir.absolutePath
        )
        assertTrue(root.lanXinDir.isDirectory)
        assertFalse(root.usedFallback)
        assertFalse(root.safGranted)
        assertFalse(root.safWritable)
        assertEquals("", root.safTreeUri)
        // 无 SAF 时 publicWritable 由 File 直写决定
        assertTrue(root.publicWritable)

        val probe = File(root.lanXinDir, "probe.txt")
        probe.writeText("ok")
        assertTrue(probe.isFile)
        assertEquals("ok", probe.readText())
    }

    /**
     * 镜像门控与 [DebugAssetStorage.mirrorToSafIfNeeded] 一致：
     * `!usedFallback || !safWritable || blank tree → 不镜像`。
     * 实际 DocumentsContract 需 instrumented；此处锁 JVM 契约。
     */
    @Test
    fun mirrorToSafIfNeeded_gated_byRootFlags() {
        val base = tmp.newFolder("files")
        val local = File(base, "LanXin/asr/model.onnx").apply {
            parentFile?.mkdirs()
            writeText("x")
        }
        assertTrue(local.isFile)

        val baseRoot = DebugAssetStorage.Root(
            baseDir = base,
            lanXinDir = File(base, "LanXin"),
            usedFallback = false,
            displayPath = "/public/LanXin",
            safTreeUri = "content://tree",
            safGranted = true,
            safWritable = true,
            safDisplayLabel = "LanXin"
        )

        fun shouldMirror(r: DebugAssetStorage.Root): Boolean =
            r.usedFallback && r.safWritable && r.safTreeUri.isNotBlank()

        // 公共 File 可写：不镜像
        assertFalse(shouldMirror(baseRoot))
        // 回退但 SAF 不可写
        assertFalse(shouldMirror(baseRoot.copy(usedFallback = true, safWritable = false)))
        // 回退可写但 tree 空
        assertFalse(
            shouldMirror(
                baseRoot.copy(usedFallback = true, safWritable = true, safTreeUri = "")
            )
        )
        // 未授权
        assertFalse(
            shouldMirror(
                baseRoot.copy(
                    usedFallback = true,
                    safGranted = false,
                    safWritable = false,
                    safTreeUri = ""
                )
            )
        )
        // 仅：回退 + 可写 + 非空 tree → 放行镜像
        val treeUri =
            "content://com.android.externalstorage.documents/tree/primary%3ALanXin"
        assertTrue(
            shouldMirror(
                baseRoot.copy(
                    usedFallback = true,
                    safWritable = true,
                    safTreeUri = treeUri
                )
            )
        )
    }

    @Test
    fun publicWritable_trueWhenFileOrSaf() {
        val base = tmp.newFolder("x")
        val fileOk = DebugAssetStorage.Root(
            baseDir = base,
            lanXinDir = File(base, "LanXin"),
            usedFallback = false,
            displayPath = "/sdcard/LanXin"
        )
        assertTrue(fileOk.publicWritable)

        val fallbackNoSaf = fileOk.copy(usedFallback = true, safWritable = false)
        assertFalse(fallbackNoSaf.publicWritable)

        val fallbackWithSaf = fileOk.copy(usedFallback = true, safWritable = true)
        assertTrue(fallbackWithSaf.publicWritable)
    }
}
