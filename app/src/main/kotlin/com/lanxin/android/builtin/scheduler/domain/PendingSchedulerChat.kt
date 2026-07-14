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

package com.lanxin.android.builtin.scheduler.domain

/**
 * 跨 Activity → ChatScreen 传递 ACTIVE_AGENT 通知点击意图。
 * 消费一次后清空，避免重复触发。
 */
object PendingSchedulerChat {
    data class Request(
        val taskId: String,
        val prompt: String,
        val autoStart: Boolean
    )

    @Volatile
    private var pending: Request? = null

    fun set(request: Request) {
        pending = request
    }

    fun consume(): Request? {
        val value = pending
        pending = null
        return value
    }

    fun peek(): Request? = pending
}
