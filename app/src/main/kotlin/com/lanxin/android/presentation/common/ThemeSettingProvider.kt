package com.lanxin.android.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.data.model.DynamicTheme
import com.lanxin.android.data.model.ThemeMode

val LocalDynamicTheme = compositionLocalOf { DynamicTheme.OFF }
val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }
val LocalThemeViewModel = compositionLocalOf<ThemeViewModel> {
    error("CompositionLocal LocalThemeViewModel is not present")
}

@Composable
fun ThemeSettingProvider(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    themeViewModel.themeSetting.collectAsStateWithLifecycle().value.run {
        CompositionLocalProvider(
            LocalThemeViewModel provides themeViewModel,
            LocalDynamicTheme provides dynamicTheme,
            LocalThemeMode provides themeMode,
            content = content
        )
    }
}
