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
 * 内置背景音乐：assets → 用户可见 `LanXin/music/`（与 live2d 同根）。
 *
 * 测试曲 `test-loop.wav` 为程序生成正弦音，CC0 / 公共域。
 */
object BuiltInMusicAssets {

    const val ASSET_ROOT = "pet/music"
    const val TEST_TRACK_ASSET = "$ASSET_ROOT/test-loop.wav"
    const val TEST_TRACK_NAME = "test-loop.wav"

    /** 相对 baseDir 的目录（与 [DebugOpenSourcePaths.ROOT_DIR] 同级布局）。 */
    const val MUSIC_DIR_REL = "${DebugOpenSourcePaths.ROOT_DIR}/music"

    val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac")

    fun musicDir(baseDir: File): File = File(baseDir, MUSIC_DIR_REL)

    fun musicDirFromStorage(context: Context): File {
        val root = DebugAssetStorage.resolve(context)
        val dir = File(root.lanXinDir, "music")
        dir.mkdirs()
        return dir
    }

    /**
     * 确保测试曲落盘到 `LanXin/music/`；已存在且非空则跳过。
     * @return 测试曲绝对路径，失败返回 null
     */
    fun ensureTestTrackInstalled(context: Context): String? {
        return runCatching {
            val destDir = musicDirFromStorage(context)
            val dest = File(destDir, TEST_TRACK_NAME)
            if (dest.isFile && dest.length() > 0L) {
                return dest.absolutePath
            }
            context.assets.open(TEST_TRACK_ASSET).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.takeIf { it.isFile && it.length() > 0L }?.absolutePath
        }.getOrNull()
    }

    fun isAudioFile(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val ext = file.extension.lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    /** 扫描目录内可播曲目（浅层 + 一层子目录）。 */
    fun listTracks(musicDir: File): List<File> {
        if (!musicDir.isDirectory) return emptyList()
        val out = mutableListOf<File>()
        musicDir.listFiles()?.forEach { f ->
            when {
                isAudioFile(f) -> out += f
                f.isDirectory -> {
                    f.listFiles()?.filter { isAudioFile(it) }?.let { out.addAll(it) }
                }
            }
        }
        return out.sortedBy { it.name.lowercase() }
    }
}
