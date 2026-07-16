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

package com.lanxin.android.builtin.systemtools.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lanxin.android.builtin.systemtools.domain.IntentLaunchResult
import com.lanxin.android.builtin.systemtools.domain.IntentLaunchSpec
import com.lanxin.android.builtin.systemtools.domain.SystemToolsIntentLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 真机 Intent 启动：Application Context + [Intent.FLAG_ACTIVITY_NEW_TASK]。
 *
 * 用于：
 * - AlarmClock.ACTION_SET_ALARM / ACTION_SHOW_ALARMS
 * - Calendar INSERT（Events.CONTENT_URI）
 */
@Singleton
class AndroidSystemToolsIntentLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) : SystemToolsIntentLauncher {

    override fun launch(spec: IntentLaunchSpec): IntentLaunchResult {
        return try {
            val intent = buildIntent(spec)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = intent.resolveActivity(context.packageManager)
            if (resolved == null) {
                return IntentLaunchResult.ActivityNotFound(
                    message = "没有可处理 ${spec.action} 的应用",
                    action = spec.action
                )
            }
            context.startActivity(intent)
            IntentLaunchResult.Ok(
                action = spec.action,
                launched = true,
                resolvedActivity = resolved.flattenToString(),
                description = spec.description
            )
        } catch (e: ActivityNotFoundException) {
            IntentLaunchResult.ActivityNotFound(
                message = e.message ?: "ActivityNotFoundException",
                action = spec.action
            )
        } catch (e: SecurityException) {
            IntentLaunchResult.Error(
                message = "权限不足：${e.message}",
                code = "security"
            )
        } catch (e: Exception) {
            IntentLaunchResult.Error(message = e.message ?: e.toString())
        }
    }

    internal fun buildIntent(spec: IntentLaunchSpec): Intent {
        val intent = Intent(spec.action)
        if (!spec.dataUri.isNullOrBlank()) {
            intent.data = Uri.parse(spec.dataUri)
        }
        if (!spec.mimeType.isNullOrBlank()) {
            intent.type = spec.mimeType
        }
        for ((key, value) in spec.extras) {
            putExtra(intent, key, value)
        }
        return intent
    }

    private fun putExtra(intent: Intent, key: String, value: Any?) {
        when (value) {
            null -> Unit
            is Boolean -> intent.putExtra(key, value)
            is Int -> intent.putExtra(key, value)
            is Long -> intent.putExtra(key, value)
            is Float -> intent.putExtra(key, value)
            is Double -> intent.putExtra(key, value)
            is String -> intent.putExtra(key, value)
            is ArrayList<*> -> {
                @Suppress("UNCHECKED_CAST")
                when {
                    value.isEmpty() || value.firstOrNull() is Int ->
                        intent.putIntegerArrayListExtra(
                            key,
                            value as ArrayList<Int>
                        )
                    else -> intent.putExtra(key, value.map { it?.toString() }.toTypedArray())
                }
            }
            is List<*> -> {
                val ints = value.mapNotNull {
                    when (it) {
                        is Number -> it.toInt()
                        is String -> it.toIntOrNull()
                        else -> null
                    }
                }
                if (ints.size == value.size) {
                    intent.putIntegerArrayListExtra(key, ArrayList(ints))
                } else {
                    intent.putExtra(key, value.map { it?.toString() }.toTypedArray())
                }
            }
            is Number -> {
                // 默认 Long（epoch ms 等）
                if (value is Int) intent.putExtra(key, value)
                else intent.putExtra(key, value.toLong())
            }
            else -> intent.putExtra(key, value.toString())
        }
    }
}
