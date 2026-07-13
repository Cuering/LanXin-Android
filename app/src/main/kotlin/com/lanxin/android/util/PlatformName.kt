package com.lanxin.android.util

import com.lanxin.android.plugins.chat.data.entity.PlatformV2

fun List<PlatformV2>.getPlatformName(uid: String): String = this.find { it.uid == uid }?.name ?: "Unknown"
