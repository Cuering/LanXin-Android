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

package com.lanxin.android.presentation.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #122：路径选择默认策略 — 手填关闭；本地脑只接受文件夹包。
 *
 * Compose 默认参数无法在 JVM 直接读，这里用源码契约 + 包校验兜底。
 */
class PathPickerPolicyTest {

    @Test
    fun pathPickerField_source_defaultsShowManualEntryFalse() {
        val src = locateSource("presentation/common/PathPickerField.kt")
        assertTrue(src.contains("showManualEntry: Boolean = false"))
        assertFalse(
            "default must not stay true",
            src.contains("showManualEntry: Boolean = true")
        )
    }

    @Test
    fun localInferenceScreen_source_hasNoSingleFilePickerUi() {
        val src = locateSource(
            "builtin/localinference/presentation/LocalInferenceScreen.kt"
        )
        assertFalse(src.contains("选单文件"))
        assertFalse(src.contains("modelFilePicker"))
        assertTrue(src.contains("选择文件夹"))
        assertTrue(src.contains("showManualEntry = false") || src.contains("OpenDocumentTree"))
    }

    @Test
    fun desktopPetScreen_source_localLlmLinksToUniqueSource() {
        val src = locateSource("builtin/pet/presentation/DesktopPetScreen.kt")
        assertTrue(src.contains("onOpenLocalInference"))
        assertTrue(src.contains("前往本地模型设置"))
        assertFalse(src.contains("importLocalLlmFromDocument"))
        assertFalse(src.contains("选择文件"))
        assertFalse(src.contains("localLlmPicker"))
    }

    private fun locateSource(relativeUnderMainKotlin: String): String {
        val candidates = listOf(
            File("app/src/main/kotlin/com/lanxin/android/$relativeUnderMainKotlin"),
            File("src/main/kotlin/com/lanxin/android/$relativeUnderMainKotlin")
        )
        val f = candidates.firstOrNull { it.isFile }
            ?: error("missing source: $relativeUnderMainKotlin")
        return f.readText()
    }
}
