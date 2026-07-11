package com.lanxin.android.data.dto.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ping")
data object PingResponseChunk : MessageResponseChunk()
