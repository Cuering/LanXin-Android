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

package com.lanxin.android.builtin.systemtools.data.files

import com.lanxin.android.builtin.systemtools.domain.ConfirmationLevel
import com.lanxin.android.builtin.systemtools.domain.DeviceCapability
import com.lanxin.android.builtin.systemtools.domain.DevicePermission
import com.lanxin.android.builtin.systemtools.domain.DeviceTool
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
import com.lanxin.android.builtin.systemtools.domain.DeviceToolSideEffect
import com.lanxin.android.builtin.systemtools.domain.UserFileCatalog
import com.lanxin.android.builtin.systemtools.domain.UserFileEntry
import com.lanxin.android.builtin.systemtools.domain.UserFileIoGateway
import com.lanxin.android.builtin.systemtools.domain.UserFileIoResult
import com.lanxin.android.builtin.systemtools.domain.UserFileSource
import com.lanxin.android.builtin.systemtools.domain.guessMimeFromName
import com.lanxin.android.builtin.systemtools.domain.intArg
import com.lanxin.android.builtin.systemtools.domain.parseUserFileSort
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7.4 用户文件工具。
 *
 * - [FilePickDeviceTool]：登记 SAF 选取的 content Uri（可 copy 到应用目录）
 * - [FileListDeviceTool]：合并目录登记 + 应用私有 imports，按类型/日期排序
 * - [FileReadTextDeviceTool]：读文本摘要
 * - [FileWriteDeviceTool]：写 SAF uri 或应用私有文件
 * - [FileShareDeviceTool]：分享 Uri / 文本
 * - [FileDeleteDeviceTool]：删除应用私有文件或目录登记（高危）
 */

@Singleton
class FilePickDeviceTool @Inject constructor(
    private val catalog: UserFileCatalog,
    private val io: UserFileIoGateway
) : DeviceTool {
    override val name = DeviceToolIds.FILE_PICK
    override val description =
        "登记用户通过 SAF 选取的文件；import=true 时复制到应用 imports 目录"
    override val capability = DeviceCapability.USER_FILE
    override val permissions = listOf(DevicePermission.SAF_TREE, DevicePermission.APP_PRIVATE_STORAGE)
    override val sideEffect = DeviceToolSideEffect.WRITE
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val uri = args["uri"]?.toString()?.trim().orEmpty()
        if (uri.isEmpty()) {
            return DeviceToolOutcome.Error(
                "uri 必填（设置页 OpenDocument 选择后传入）",
                "uri_required"
            )
        }
        val doImport = when (val v = args["import"]) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true) || v == "1"
            else -> true
        }
        val preferredName = args["name"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        return try {
            io.takePersistableIfPossible(uri)
            if (doImport) {
                when (val r = io.copyToAppPrivate(uri, preferredName)) {
                    is UserFileIoResult.Error -> DeviceToolOutcome.Error(r.message, r.code)
                    is UserFileIoResult.Ok -> {
                        val path = r.uri.orEmpty()
                        val name = r.name ?: preferredName ?: path.substringAfterLast('/')
                        val entry = UserFileEntry(
                            id = "app:$path",
                            uriOrPath = path,
                            name = name,
                            isDirectory = false,
                            sizeBytes = r.bytes.toLong(),
                            mimeType = guessMimeFromName(name),
                            modifiedAtEpochMs = System.currentTimeMillis(),
                            source = "app_private"
                        )
                        catalog.upsert(entry)
                        // 同时登记原始 SAF uri 便于再读
                        catalog.upsert(
                            UserFileEntry(
                                id = "saf:$uri",
                                uriOrPath = uri,
                                name = name,
                                isDirectory = false,
                                sizeBytes = r.bytes.toLong(),
                                mimeType = entry.mimeType,
                                modifiedAtEpochMs = System.currentTimeMillis(),
                                source = "saf"
                            )
                        )
                        DeviceToolOutcome.Ok(
                            data = mapOf(
                                "ok" to true,
                                "imported" to true,
                                "id" to entry.id,
                                "name" to entry.name,
                                "path" to path,
                                "source_uri" to uri,
                                "bytes" to r.bytes
                            ),
                            message = "已导入 ${entry.name}"
                        )
                    }
                }
            } else {
                val probe = io.probe(uri)
                val name = preferredName ?: probe?.name ?: uri.substringAfterLast('/')
                val entry = UserFileEntry(
                    id = "saf:$uri",
                    uriOrPath = uri,
                    name = name,
                    isDirectory = false,
                    sizeBytes = probe?.sizeBytes,
                    mimeType = probe?.mimeType ?: guessMimeFromName(name),
                    modifiedAtEpochMs = probe?.modifiedAtEpochMs
                        ?: System.currentTimeMillis(),
                    source = "saf"
                )
                catalog.upsert(entry)
                DeviceToolOutcome.Ok(
                    data = mapOf(
                        "ok" to true,
                        "imported" to false,
                        "id" to entry.id,
                        "name" to entry.name,
                        "uri" to uri,
                        "size_bytes" to entry.sizeBytes
                    ),
                    message = "已登记 ${entry.name}"
                )
            }
        } catch (e: Exception) {
            DeviceToolOutcome.Error(e.message ?: e.toString(), "pick_failed")
        }
    }
}

@Singleton
class FileListDeviceTool @Inject constructor(
    private val catalog: UserFileCatalog,
    private val io: UserFileIoGateway
) : DeviceTool {
    override val name = DeviceToolIds.FILE_LIST
    override val description =
        "列出用户文件（应用 imports + 已登记 SAF）；sort=date|name|type|size"
    override val capability = DeviceCapability.USER_FILE
    override val permissions = listOf(DevicePermission.APP_PRIVATE_STORAGE, DevicePermission.SAF_TREE)
    override val sideEffect = DeviceToolSideEffect.READ
    override val confirmationLevel = ConfirmationLevel.NONE

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val sort = parseUserFileSort(args["sort"]?.toString())
        val limit = (args.intArg("limit") ?: 100).coerceIn(1, 500)
        val sourceFilter = when (args["source"]?.toString()?.lowercase()?.trim()) {
            "saf" -> UserFileSource.SAF
            "app", "app_private", "private" -> UserFileSource.APP_PRIVATE
            else -> null
        }

        // 同步应用私有目录实体文件到目录
        val privateFiles = runCatching { io.listAppPrivateFiles() }.getOrDefault(emptyList())
        for (f in privateFiles) {
            catalog.upsert(f)
        }

        val listed = catalog.list(sort = sort, limit = limit, source = sourceFilter)
        return DeviceToolOutcome.Ok(
            data = mapOf(
                "ok" to true,
                "count" to listed.size,
                "total" to catalog.count(),
                "sort" to sort.name.lowercase(),
                "files" to listed.map { it.toMap() }
            )
        )
    }
}

@Singleton
class FileReadTextDeviceTool @Inject constructor(
    private val catalog: UserFileCatalog,
    private val io: UserFileIoGateway
) : DeviceTool {
    override val name = DeviceToolIds.FILE_READ_TEXT
    override val description = "读取用户文件文本（uri / id / 应用路径）；限制 max_chars"
    override val capability = DeviceCapability.USER_FILE
    override val permissions = listOf(DevicePermission.SAF_TREE, DevicePermission.APP_PRIVATE_STORAGE)
    override val sideEffect = DeviceToolSideEffect.READ
    override val confirmationLevel = ConfirmationLevel.NONE

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val maxChars = args.intArg("max_chars")
            ?: args.intArg("maxChars")
            ?: UserFileIoGateway.DEFAULT_MAX_CHARS
        val target = resolveTarget(args, catalog)
            ?: return DeviceToolOutcome.Error("需要 uri / id / path", "invalid_args")
        return when (val r = io.readText(target, maxChars)) {
            is UserFileIoResult.Ok -> DeviceToolOutcome.Ok(
                data = mapOf(
                    "ok" to true,
                    "uri" to target,
                    "bytes" to r.bytes,
                    "truncated" to r.truncated,
                    "text" to r.message,
                    "preview" to (r.preview ?: r.message.take(500))
                ),
                message = if (r.truncated) "已截断至 $maxChars 字符" else "读取成功"
            )
            is UserFileIoResult.Error -> DeviceToolOutcome.Error(r.message, r.code)
        }
    }
}

@Singleton
class FileWriteDeviceTool @Inject constructor(
    private val catalog: UserFileCatalog,
    private val io: UserFileIoGateway
) : DeviceTool {
    override val name = DeviceToolIds.FILE_WRITE
    override val description =
        "写入用户文件：mode=saf 写 uri；mode=app 写应用 imports（默认）"
    override val capability = DeviceCapability.USER_FILE
    override val permissions = listOf(DevicePermission.SAF_TREE, DevicePermission.APP_PRIVATE_STORAGE)
    override val sideEffect = DeviceToolSideEffect.WRITE
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val text = args["text"]?.toString()
            ?: args["content"]?.toString()
            ?: return DeviceToolOutcome.Error("text 必填", "invalid_args")
        val mode = args["mode"]?.toString()?.lowercase()?.trim().orEmpty().ifBlank { "app" }
        val mime = args["mime"]?.toString()
            ?: args["mime_type"]?.toString()
            ?: "text/plain"

        return when (mode) {
            "saf", "uri" -> {
                val uri = args["uri"]?.toString()?.trim().orEmpty()
                if (uri.isEmpty()) {
                    return DeviceToolOutcome.Error("mode=saf 需要 uri", "uri_required")
                }
                when (val r = io.writeText(uri, text, mime)) {
                    is UserFileIoResult.Error -> DeviceToolOutcome.Error(r.message, r.code)
                    is UserFileIoResult.Ok -> {
                        val name = args["name"]?.toString()
                            ?: io.probe(uri)?.name
                            ?: uri.substringAfterLast('/')
                        catalog.upsert(
                            UserFileEntry(
                                id = "saf:$uri",
                                uriOrPath = uri,
                                name = name,
                                sizeBytes = r.bytes.toLong(),
                                mimeType = mime,
                                modifiedAtEpochMs = System.currentTimeMillis(),
                                source = "saf"
                            )
                        )
                        DeviceToolOutcome.Ok(
                            data = mapOf(
                                "ok" to true,
                                "mode" to "saf",
                                "uri" to uri,
                                "bytes" to r.bytes
                            ),
                            message = r.message
                        )
                    }
                }
            }
            "app", "private", "app_private" -> {
                val name = args["name"]?.toString()?.trim().orEmpty()
                    .ifBlank { "lanxin_${System.currentTimeMillis()}.txt" }
                when (val r = io.writeAppPrivateText(name, text, mime)) {
                    is UserFileIoResult.Error -> DeviceToolOutcome.Error(r.message, r.code)
                    is UserFileIoResult.Ok -> {
                        val path = r.uri.orEmpty()
                        val entry = UserFileEntry(
                            id = "app:$path",
                            uriOrPath = path,
                            name = r.name ?: name,
                            sizeBytes = r.bytes.toLong(),
                            mimeType = mime.ifBlank { guessMimeFromName(r.name ?: name) },
                            modifiedAtEpochMs = System.currentTimeMillis(),
                            source = "app_private"
                        )
                        catalog.upsert(entry)
                        DeviceToolOutcome.Ok(
                            data = mapOf(
                                "ok" to true,
                                "mode" to "app",
                                "id" to entry.id,
                                "name" to entry.name,
                                "path" to path,
                                "bytes" to r.bytes
                            ),
                            message = r.message
                        )
                    }
                }
            }
            else -> DeviceToolOutcome.Error("mode 仅支持 app / saf", "invalid_args")
        }
    }
}

@Singleton
class FileShareDeviceTool @Inject constructor(
    private val catalog: UserFileCatalog,
    private val io: UserFileIoGateway
) : DeviceTool {
    override val name = DeviceToolIds.FILE_SHARE
    override val description = "分享用户文件（uri/id/path）或纯文本 text"
    override val capability = DeviceCapability.USER_FILE
    override val permissions = listOf(DevicePermission.SAF_TREE, DevicePermission.APP_PRIVATE_STORAGE)
    override val sideEffect = DeviceToolSideEffect.LAUNCH_INTENT
    override val confirmationLevel = ConfirmationLevel.NONE

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val text = args["text"]?.toString()
        if (!text.isNullOrEmpty()) {
            val mime = args["mime"]?.toString() ?: "text/plain"
            return when (val r = io.shareText(text, mime)) {
                is UserFileIoResult.Ok -> DeviceToolOutcome.Ok(
                    data = mapOf("ok" to true, "mode" to "text", "bytes" to r.bytes),
                    message = r.message
                )
                is UserFileIoResult.Error -> DeviceToolOutcome.Error(r.message, r.code)
            }
        }
        val target = resolveTarget(args, catalog)
            ?: return DeviceToolOutcome.Error("需要 uri / id / path 或 text", "invalid_args")
        val mime = args["mime"]?.toString()
        return when (val r = io.shareUri(target, mime)) {
            is UserFileIoResult.Ok -> DeviceToolOutcome.Ok(
                data = mapOf("ok" to true, "mode" to "uri", "uri" to target),
                message = r.message
            )
            is UserFileIoResult.Error -> DeviceToolOutcome.Error(r.message, r.code)
        }
    }
}

@Singleton
class FileDeleteDeviceTool @Inject constructor(
    private val catalog: UserFileCatalog,
    private val io: UserFileIoGateway
) : DeviceTool {
    override val name = DeviceToolIds.FILE_DELETE
    override val description = "删除应用 imports 内文件或目录登记（高危，需 confirmed）"
    override val capability = DeviceCapability.USER_FILE
    override val permissions = listOf(DevicePermission.APP_PRIVATE_STORAGE)
    override val sideEffect = DeviceToolSideEffect.DELETE
    override val confirmationLevel = ConfirmationLevel.EXPLICIT_APPROVE

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val id = args["id"]?.toString()?.trim().orEmpty()
        val path = args["path"]?.toString()?.trim().orEmpty()
        val uri = args["uri"]?.toString()?.trim().orEmpty()
        val entry = when {
            id.isNotEmpty() -> catalog.get(id)
            path.isNotEmpty() -> catalog.get(path) ?: catalog.get("app:$path")
            uri.isNotEmpty() -> catalog.get("saf:$uri") ?: catalog.get(uri)
            else -> null
        }
        val targetPath = entry?.uriOrPath
            ?: path.takeIf { it.isNotEmpty() }
            ?: return DeviceToolOutcome.Error("需要 id / path", "invalid_args")

        // app_private：删 imports 实体；SAF content://：DocumentsContract.deleteDocument
        // 也可只传 id 移除目录登记（无实体时）
        val source = entry?.source
            ?: if (targetPath.startsWith("content://")) "saf" else "app_private"
        val hardDelete = args["hard"]?.toString()?.equals("true", ignoreCase = true) == true ||
            args["hard"] == true ||
            source == "app_private" ||
            targetPath.startsWith("content://")
        return try {
            if (hardDelete) {
                when (val r = io.deleteDocument(targetPath)) {
                    is UserFileIoResult.Error -> {
                        // 登记项可仍移除；实体不存在时继续清目录
                        if (r.code != "not_found" && source == "app_private") {
                            return DeviceToolOutcome.Error(r.message, r.code)
                        }
                    }
                    is UserFileIoResult.Ok -> { /* ok */ }
                }
            }
            val removedId = entry?.id ?: id.ifEmpty { targetPath }
            catalog.remove(removedId)
            if (entry != null && entry.id != entry.uriOrPath) {
                catalog.remove(entry.uriOrPath)
            }
            // 同步移除配对的 saf:/app: 登记
            if (uri.isNotEmpty()) catalog.remove("saf:$uri")
            if (path.isNotEmpty()) catalog.remove("app:$path")
            DeviceToolOutcome.Ok(
                data = mapOf(
                    "ok" to true,
                    "deleted" to true,
                    "id" to removedId,
                    "source" to source,
                    "remaining" to catalog.count()
                ),
                message = "已删除 $removedId"
            )
        } catch (e: Exception) {
            DeviceToolOutcome.Error(e.message ?: e.toString(), "delete_failed")
        }
    }
}

private suspend fun resolveTarget(
    args: Map<String, Any?>,
    catalog: UserFileCatalog
): String? {
    val uri = args["uri"]?.toString()?.trim().orEmpty()
    if (uri.isNotEmpty()) return uri
    val path = args["path"]?.toString()?.trim().orEmpty()
    if (path.isNotEmpty()) return path
    val id = args["id"]?.toString()?.trim().orEmpty()
    if (id.isNotEmpty()) {
        val entry = catalog.get(id)
        if (entry != null) return entry.uriOrPath
        // 允许直接传 app: 前缀 id
        if (id.startsWith("app:")) return id.removePrefix("app:")
        if (id.startsWith("saf:")) return id.removePrefix("saf:")
    }
    return null
}

private fun UserFileEntry.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "uri" to uriOrPath,
    "is_directory" to isDirectory,
    "size_bytes" to sizeBytes,
    "mime" to mimeType,
    "modified_at" to modifiedAtEpochMs,
    "source" to source
)

/** 供单测构造唯一 id。 */
internal fun newUserFileId(prefix: String = "uf"): String =
    "$prefix-${UUID.randomUUID()}"
