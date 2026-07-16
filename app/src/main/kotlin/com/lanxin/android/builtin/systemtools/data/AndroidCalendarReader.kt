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

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.lanxin.android.builtin.systemtools.domain.CalendarCreateResult
import com.lanxin.android.builtin.systemtools.domain.CalendarEvent
import com.lanxin.android.builtin.systemtools.domain.CalendarGateway
import com.lanxin.android.builtin.systemtools.domain.CalendarIntentBuilder
import com.lanxin.android.builtin.systemtools.domain.CalendarListResult
import com.lanxin.android.builtin.systemtools.domain.CalendarQueryParams
import com.lanxin.android.builtin.systemtools.domain.CreateCalendarEventRequest
import com.lanxin.android.builtin.systemtools.domain.IntentLaunchResult
import com.lanxin.android.builtin.systemtools.domain.SystemToolsIntentLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 真机日历：
 * - 读：[CalendarContract.Instances]
 * - 写：优先 Intent（`ACTION_INSERT` + Events.CONTENT_URI），不申请 WRITE_CALENDAR
 *
 * 无 `READ_CALENDAR` 时 list 返回 [CalendarListResult.PermissionDenied]，不崩溃。
 */
@Singleton
class AndroidCalendarReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val intentLauncher: SystemToolsIntentLauncher,
    private val stubFallback: StubCalendarGateway
) : CalendarGateway {

    override fun listUpcoming(
        limit: Int,
        afterEpochMs: Long,
        days: Int
    ): CalendarListResult {
        if (!hasReadCalendarPermission()) {
            return CalendarListResult.PermissionDenied()
        }
        val n = CalendarQueryParams.normalizeLimit(limit)
        val dayCount = CalendarQueryParams.normalizeDays(days)
        val endMs = CalendarQueryParams.windowEndEpochMs(afterEpochMs, dayCount)
        return try {
            val events = queryInstances(afterEpochMs, endMs, n)
            CalendarListResult.Ok(events)
        } catch (se: SecurityException) {
            CalendarListResult.PermissionDenied(
                message = se.message
                    ?: "READ_CALENDAR 被拒绝（SecurityException）"
            )
        } catch (e: Exception) {
            CalendarListResult.Error(e.message ?: e.toString())
        }
    }

    override fun create(request: CreateCalendarEventRequest): CalendarCreateResult {
        // 优先 Intent 少权限；mode=stub 由 DeviceTool 层直接走 StubCalendarGateway
        return try {
            require(request.endEpochMs >= request.startEpochMs) { "end 必须 ≥ start" }
            val spec = CalendarIntentBuilder.fromCreateRequest(request)
            when (val result = intentLauncher.launch(spec)) {
                is IntentLaunchResult.Ok -> CalendarCreateResult.IntentLaunched(
                    action = result.action,
                    description = result.description.ifBlank { spec.description },
                    resolvedActivity = result.resolvedActivity,
                    request = request
                )
                is IntentLaunchResult.ActivityNotFound ->
                    CalendarCreateResult.ActivityNotFound(result.message)
                is IntentLaunchResult.Error ->
                    CalendarCreateResult.Error(result.message, result.code)
            }
        } catch (e: IllegalArgumentException) {
            CalendarCreateResult.Error(e.message ?: "invalid args", "invalid_args")
        } catch (e: Exception) {
            CalendarCreateResult.Error(e.message ?: e.toString())
        }
    }

    /** 单测 / 降级：内存 stub 写入。 */
    fun createStub(request: CreateCalendarEventRequest): CalendarCreateResult =
        stubFallback.create(request)

    fun hasReadCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun queryInstances(
        beginMs: Long,
        endMs: Long,
        limit: Int
    ): List<CalendarEvent> {
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, beginMs)
        ContentUris.appendId(builder, endMs)
        val uri = builder.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID
        )
        val sort = "${CalendarContract.Instances.BEGIN} ASC"

        val out = ArrayList<CalendarEvent>(limit)
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            sort
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
            val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            val calIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
            while (cursor.moveToNext() && out.size < limit) {
                val id = if (idIdx >= 0) cursor.getLong(idIdx).toString() else out.size.toString()
                val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else ""
                val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else beginMs
                val end = if (endIdx >= 0) cursor.getLong(endIdx) else begin
                val location = if (locIdx >= 0) cursor.getString(locIdx) else null
                val calId = if (calIdx >= 0) cursor.getLong(calIdx).toString() else null
                out.add(
                    CalendarEvent(
                        id = id,
                        title = title.ifBlank { "(无标题)" },
                        startEpochMs = begin,
                        endEpochMs = end,
                        location = location?.takeIf { it.isNotBlank() },
                        calendarId = calId
                    )
                )
            }
        }
        return out
    }
}
