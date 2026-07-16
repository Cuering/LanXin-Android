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

package com.lanxin.android.builtin.voice.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.lanxin.android.builtin.voice.domain.MicPermissionChecker
import com.lanxin.android.builtin.voice.domain.MicPermissionState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 ContextCompat 的麦克风权限检查。
 *
 * 注意：PERMANENTLY_DENIED 需 Activity 侧 shouldShowRequestPermissionRationale
 * 才能精确区分；此处仅区分 GRANTED / DENIED。
 * UI 层在二次拒绝后可映射为 PERMANENTLY_DENIED。
 */
@Singleton
class AndroidMicPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) : MicPermissionChecker {

    override fun check(): MicPermissionState {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) {
            MicPermissionState.GRANTED
        } else {
            MicPermissionState.DENIED
        }
    }
}
