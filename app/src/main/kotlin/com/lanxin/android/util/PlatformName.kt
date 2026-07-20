package com.lanxin.android.util

import com.lanxin.android.builtin.localinference.domain.LocalModelPlatform
import com.lanxin.android.plugins.chat.data.entity.PlatformV2

fun List<PlatformV2>.getPlatformName(uid: String): String =
    when {
        LocalModelPlatform.isLocalUid(uid) -> LocalModelPlatform.DISPLAY_NAME
        else -> this.find { it.uid == uid }?.name ?: "Unknown"
    }
