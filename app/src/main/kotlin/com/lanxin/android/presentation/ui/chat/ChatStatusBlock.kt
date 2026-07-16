package com.lanxin.android.presentation.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lanxin.android.R
import com.lanxin.android.presentation.theme.LanXinTheme

/**
 * 生成过程中的动态状态文案（ThinkingBlock 风格，非进度条）。
 * 活动阶段显示 spinner + 文案；Done 可短暂展示后由上层收起。
 */
@Composable
fun ChatStatusBlock(
    phase: ChatGenerationPhase,
    modifier: Modifier = Modifier,
    showWhenDone: Boolean = false
) {
    val visible = phase.isActive || (showWhenDone && phase == ChatGenerationPhase.DONE)
    if (!visible) return

    val label = statusLabel(phase)
    if (label.isBlank()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (phase.isActive) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Text(text = "✓", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun statusLabel(phase: ChatGenerationPhase): String = when (phase) {
    ChatGenerationPhase.PREPARING -> stringResource(R.string.chat_status_preparing)
    ChatGenerationPhase.SEARCHING_MEMORY -> stringResource(R.string.chat_status_searching_memory)
    ChatGenerationPhase.SEARCHING_KNOWLEDGE -> stringResource(R.string.chat_status_searching_knowledge)
    ChatGenerationPhase.GENERATING -> stringResource(R.string.chat_status_generating)
    ChatGenerationPhase.CALLING_TOOLS -> stringResource(R.string.chat_status_calling_tools)
    ChatGenerationPhase.DONE -> stringResource(R.string.chat_status_done)
    ChatGenerationPhase.IDLE -> ""
}

/**
 * 记忆 / 知识库引用 chip 行；无命中不展示。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatRefsRow(
    refs: List<ChatRef>,
    onRefClick: (ChatRef) -> Unit,
    modifier: Modifier = Modifier
) {
    if (refs.isEmpty()) return

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 8.dp, end = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.chat_refs_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                refs.forEach { ref ->
                    ChatRefChip(ref = ref, onClick = { onRefClick(ref) })
                }
            }
        }
    }
}

@Composable
private fun ChatRefChip(
    ref: ChatRef,
    onClick: () -> Unit
) {
    val typeLabel = when (ref.type) {
        ChatRefType.MEMORY -> stringResource(R.string.chat_ref_memory)
        ChatRefType.KNOWLEDGE -> stringResource(R.string.chat_ref_knowledge)
    }
    val display = buildString {
        append(typeLabel)
        if (ref.title.isNotBlank() && ref.title != typeLabel) {
            append(" · ")
            append(ref.title.take(16))
        } else if (ref.snippet.isNotBlank()) {
            append(" · ")
            append(ref.snippet.take(16))
        }
    }
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = display,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium
            )
        }
    )
}

@Preview
@Composable
private fun ChatStatusBlockPreview() {
    LanXinTheme {
        Column {
            ChatStatusBlock(phase = ChatGenerationPhase.SEARCHING_MEMORY)
            ChatStatusBlock(phase = ChatGenerationPhase.GENERATING)
            ChatStatusBlock(phase = ChatGenerationPhase.DONE, showWhenDone = true)
        }
    }
}
