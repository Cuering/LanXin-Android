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

package com.lanxin.android.builtin.scheduler.registry

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.lanxin.android.builtin.scheduler.domain.TaskActionHandler
import com.lanxin.android.core.log.LogManager
import com.lanxin.android.data.network.NetworkClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class TaskActionRegistry @Inject constructor(
    private val networkClient: NetworkClient,
    private val logManager: LogManager
) {
    private val handlers = ConcurrentHashMap<String, TaskActionHandler>()
    private val logger get() = logManager.getLogger("TaskActionRegistry")

    init {
        registerBuiltinHandlers()
    }

    fun register(action: String, handler: TaskActionHandler) {
        handlers[action] = handler
    }

    fun unregister(action: String) {
        handlers.remove(action)
    }

    fun getHandler(action: String): TaskActionHandler? = handlers[action]

    fun listActions(): List<String> = handlers.keys.sorted()

    private fun registerBuiltinHandlers() {
        register("http_request", TaskActionHandler { _, payload ->
            runCatching {
                val url = payload["url"] ?: error("http_request 需要 url")
                val method = (payload["method"] ?: "GET").uppercase()
                val body = payload["body"]
                val headersJson = payload["headers"]
                val headers: Map<String, String> = if (headersJson.isNullOrBlank()) {
                    emptyMap()
                } else {
                    runCatching {
                        val obj = Json.parseToJsonElement(headersJson) as? JsonObject
                        obj?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
                    }.getOrDefault(emptyMap())
                }

                val response = networkClient().request(url) {
                    this.method = HttpMethod.parse(method)
                    headers.forEach { (k, v) -> header(k, v) }
                    if (!body.isNullOrEmpty() && method != "GET" && method != "HEAD") {
                        setBody(body)
                    }
                }
                val text = response.bodyAsText()
                logger.info("http_request $method $url -> ${response.status} (${text.take(200)})")
            }
        })

        register("app_broadcast", TaskActionHandler { context, payload ->
            runCatching {
                val action = payload["broadcast_action"]
                    ?: payload["intent_action"]
                    ?: error("app_broadcast 需要 broadcast_action")
                val intent = Intent(action).apply {
                    setPackage(context.packageName)
                    payload.forEach { (k, v) ->
                        if (k != "action" && k != "broadcast_action" && k != "intent_action") {
                            putExtra(k, v)
                        }
                    }
                }
                context.sendBroadcast(intent)
                logger.info("app_broadcast sent: $action")
            }
        })

        register("log_event", TaskActionHandler { _, payload ->
            runCatching {
                val message = payload["message"] ?: payload["content"] ?: "scheduler log_event"
                val level = (payload["level"] ?: "info").lowercase()
                when (level) {
                    "warn", "warning" -> logger.warning(message)
                    "error" -> logger.error(message)
                    "debug" -> logger.debug(message)
                    else -> logger.info(message)
                }
                Log.i("LanXinScheduler", message)
            }
        })

        register("toast_notify", TaskActionHandler { context, payload ->
            runCatching {
                val message = payload["message"] ?: payload["content"] ?: "定时任务触发"
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
