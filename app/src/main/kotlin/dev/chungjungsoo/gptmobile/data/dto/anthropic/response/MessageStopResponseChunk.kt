package com.lanxin.android.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("message_stop")
data object MessageStopResponseChunk : MessageResponseChunk()
