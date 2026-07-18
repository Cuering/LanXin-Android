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

import android.content.Context
import java.io.File

/**
 * Native 侧读取 model3.json 文本，供 [PetBridgeProtocol.loadLive2dMessage] 注入 WebView。
 *
 * 背景：HTML 页为 `file:///android_asset/...` 时，WebView 对另一 `file://` 的 fetch
 * 常因 CORS/安全策略失败；Native 读文件再 Base64 下发更稳。
 */
object Live2dModel3Reader {

    /**
     * 按 [Live2dDisplayController.Decision] 解析 model3 原文。
     * 优先绝对路径文件；其次内置 assets；最后从 `file:///android_asset/` URL 推导。
     */
    fun readJson(context: Context, decision: Live2dDisplayController.Decision): String? {
        if (decision.mode != Live2dDisplayController.Live2dDisplayMode.LIVE2D_SHELL) {
            return null
        }
        val path = decision.model3Path.trim()
        if (path.isNotBlank() &&
            !path.startsWith("asset://") &&
            !path.startsWith("stub://")
        ) {
            val file = File(path)
            if (file.isFile && file.canRead()) {
                readFileText(file)?.let { return it }
            }
        }
        if (BuiltInLive2dAssets.pathLooksBuiltin(path) ||
            decision.reason == "live2d_builtin_asset"
        ) {
            readAssetText(context, BuiltInLive2dAssets.MODEL3_ASSET)?.let { return it }
        }
        val assetFromUrl = assetPathFromFileUrl(decision.model3FileUrl)
        if (!assetFromUrl.isNullOrBlank()) {
            readAssetText(context, assetFromUrl)?.let { return it }
        }
        return null
    }

    /** 决策 + 注入 JSON（读失败则原样返回）。 */
    fun enrich(context: Context, decision: Live2dDisplayController.Decision): Live2dDisplayController.Decision {
        val json = readJson(context, decision)
        return Live2dDisplayController.withModel3Json(decision, json)
    }

    fun readFileText(file: File): String? {
        return runCatching {
            file.readText(Charsets.UTF_8).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun readAssetText(context: Context, assetPath: String): String? {
        if (assetPath.isBlank()) return null
        return runCatching {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** `file:///android_asset/pet/live2d/Mao/Mao.model3.json` → `pet/live2d/Mao/Mao.model3.json` */
    fun assetPathFromFileUrl(fileUrl: String): String? {
        val marker = "android_asset/"
        val idx = fileUrl.indexOf(marker)
        if (idx < 0) return null
        val path = fileUrl.substring(idx + marker.length).trim()
        return path.takeIf { it.isNotBlank() }
    }
}
