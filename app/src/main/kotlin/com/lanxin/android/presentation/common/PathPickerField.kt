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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanxin.android.util.PathImportHelper

/**
 * 路径选择行：当前值摘要 + 选择/清除 + 可选「高级：手填」折叠。
 *
 * 默认关闭手填（[showManualEntry]=false），避免误用绝对路径；
 * 实际 SAF launcher 由调用方持有；本组件只负责触发 [onPick] / [onClear] / 可选手填。
 */
@Composable
fun PathPickerField(
    label: String,
    path: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    pickButtonText: String = "选择",
    helperText: String = "",
    readyLabel: String? = null,
    ready: Boolean? = null,
    enabled: Boolean = true,
    showManualEntry: Boolean = false,
    manualDraft: String = path,
    onManualDraftChange: (String) -> Unit = {},
    onManualSave: ((String) -> Unit)? = null,
    secondaryPickText: String? = null,
    onSecondaryPick: (() -> Unit)? = null
) {
    var advancedOpen by remember { mutableStateOf(false) }
    val summary = PathImportHelper.shortSummary(path)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = if (path.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        if (readyLabel != null) {
            Text(
                text = buildString {
                    append(readyLabel)
                    if (ready == true) append(" ✓")
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (ready) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (helperText.isNotBlank()) {
            Text(
                helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onPick, enabled = enabled) {
                Text(pickButtonText)
            }
            if (secondaryPickText != null && onSecondaryPick != null) {
                OutlinedButton(onClick = onSecondaryPick, enabled = enabled) {
                    Text(secondaryPickText)
                }
            }
            if (path.isNotBlank()) {
                TextButton(onClick = onClear, enabled = enabled) {
                    Text("清除")
                }
            }
        }
        if (showManualEntry) {
            TextButton(
                onClick = { advancedOpen = !advancedOpen },
                enabled = enabled
            ) {
                Text(if (advancedOpen) "收起高级：手填路径" else "高级：手填路径")
            }
            if (advancedOpen) {
                OutlinedTextField(
                    value = manualDraft,
                    onValueChange = onManualDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("绝对路径 / stub://") },
                    singleLine = true,
                    enabled = enabled
                )
                if (onManualSave != null) {
                    OutlinedButton(
                        onClick = { onManualSave(manualDraft) },
                        enabled = enabled
                    ) {
                        Text("保存手填路径")
                    }
                }
            }
        }
    }
}
