package com.lanxin.android.core.updater.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanxin.android.core.updater.ReleaseInfo

/**
 * 下载进度弹窗。
 */
@Composable
fun DownloadProgressDialog(
    title: String = "正在下载更新",
    percent: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = { /* block dismiss while downloading */ },
        title = { Text(title) },
        text = {
            Column {
                if (percent in 0..100) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier = Modifier.height(8.dp))
                    Text("$percent%  (${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)})")
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("已下载 ${formatBytes(downloadedBytes)}")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text("取消") }
            }
        }
    )
}

/**
 * 版本选择列表弹窗（更新 / 回退）。
 */
@Composable
fun VersionSelectDialog(
    currentVersion: String,
    releases: List<ReleaseInfo>,
    onSelect: (ReleaseInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择版本（当前 v$currentVersion）") },
        text = {
            if (releases.isEmpty()) {
                Text("暂无可用版本")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(releases, key = { it.tagName }) { release ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(release) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = release.name.ifBlank { release.tagName },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = buildString {
                                    append(release.tagName)
                                    if (release.prerelease) append(" · 预发布")
                                    if (release.publishedAt.isNotBlank()) append(" · ${release.publishedAt.take(10)}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (release.body.isNotBlank()) {
                                Text(
                                    text = release.body.lineSequence().take(2).joinToString(" "),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/**
 * 简单确认弹窗：安装前备份提示。
 */
@Composable
fun UpdateConfirmDialog(
    release: ReleaseInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安装 ${release.tagName}？") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("将先自动备份当前数据，再下载并安装 APK。")
                if (release.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(release.body, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("继续") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "?"
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1fKB", kb)
    val mb = kb / 1024.0
    return String.format("%.1fMB", mb)
}
