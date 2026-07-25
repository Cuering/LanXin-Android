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

package com.lanxin.android.builtin.localinference.domain

import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地推理 / 陪伴诊断环缓冲。
 *
 * 目的：用户聊几句后，在「设置 → 本地推理」一键复制/导出，
 * 把 PERF、reuseKv、history、stub 降级等关键信息直接转给开发者。
 */
@Singleton
class LocalInferenceDiagnostics @Inject constructor() {

    private val lock = Any()
    private val events = ArrayDeque<String>(MAX_EVENTS + 4)

    fun log(tag: String, message: String) {
        val line = "${now()} | $tag | $message"
        synchronized(lock) {
            events.addLast(line)
            while (events.size > MAX_EVENTS) {
                events.removeFirst()
            }
        }
        Log.i(LOG_TAG, "$tag | $message")
    }

    fun clear() {
        synchronized(lock) { events.clear() }
    }

    fun snapshotLines(): List<String> = synchronized(lock) { events.toList() }

    /**
     * 生成可转发报告（剪贴板 + 可选写文件）。
     */
    fun buildReport(
        engineState: String,
        lastError: String?,
        modelPath: String,
        usingNative: Boolean?,
        routePreview: String,
        extraHeader: Map<String, String> = emptyMap()
    ): String = buildString {
        appendLine("=== 兰心 MNN 诊断报告 ===")
        appendLine("time_utc=${now()}")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")
        appendLine("engine=$engineState usingNative=$usingNative")
        appendLine("lastError=${lastError ?: "(null)"}")
        appendLine("modelPath=${modelPath.ifBlank { "(empty)" }}")
        appendLine("route=$routePreview")
        extraHeader.forEach { (k, v) -> appendLine("$k=$v") }
        appendLine("--- events (newest last, max $MAX_EVENTS) ---")
        val lines = snapshotLines()
        if (lines.isEmpty()) {
            appendLine("(no events yet — 请先在陪伴/本地聊 2～3 句再导出)")
        } else {
            lines.forEach { appendLine(it) }
        }
        appendLine("--- end ---")
        appendLine("请把本段全文转给开发者（可直接粘贴）。")
    }

    /**
     * 写入应用外部文件目录旁的 LanXin/logs（若可写），返回路径；失败返回 null。
     */
    fun writeReportFile(report: String, baseDir: File?): String? {
        if (baseDir == null) return null
        return try {
            val dir = File(baseDir, "LanXin/logs").apply { mkdirs() }
            val name = "mnn_diag_${fileStamp()}.txt"
            val f = File(dir, name)
            f.writeText(report)
            f.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    private fun now(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(Date())
    }

    private fun fileStamp(): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return fmt.format(Date())
    }

    companion object {
        const val LOG_TAG = "LanXinMnnDiag"
        const val MAX_EVENTS = 80
    }
}
