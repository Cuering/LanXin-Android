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

package com.lanxin.android.builtin.pet.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * SYSTEM_ALERT_WINDOW 权限检查与引导。
 *
 * 不偷偷申请；由设置页按钮显式触发。
 */
object OverlayPermissionHelper {

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * 打开系统「显示在其他应用上层」设置页。
     */
    fun createManageOverlayIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    const val DENIED_HINT =
        "需要「显示在其他应用上层」权限才能启动桌宠悬浮窗。请在系统设置中开启后重试。"
}
