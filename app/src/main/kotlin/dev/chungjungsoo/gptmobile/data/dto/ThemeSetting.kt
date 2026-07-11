package com.lanxin.android.data.dto

import com.lanxin.android.data.model.DynamicTheme
import com.lanxin.android.data.model.ThemeMode

data class ThemeSetting(
    val dynamicTheme: DynamicTheme = DynamicTheme.OFF,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
