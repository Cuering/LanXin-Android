package com.lanxin.android.data.context

import com.lanxin.android.data.database.entity.MessageV2

data class ConversationTurn(
    val userMessage: MessageV2,
    val assistantMessage: MessageV2?,
    val isCurrentTurn: Boolean
)
