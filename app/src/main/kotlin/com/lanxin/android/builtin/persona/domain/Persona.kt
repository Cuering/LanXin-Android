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

package com.lanxin.android.builtin.persona.domain

/**
 * 人格领域模型（对齐 AstrBot Persona）。
 *
 * @param id 唯一标识（预设用固定字符串，自定义用 UUID）
 * @param name 显示名称
 * @param systemPrompt 注入对话的 system prompt
 * @param beginDialogs 预设对话列表（交替 user/assistant，null=无）
 * @param tools 工具列表（null=全部，[]=禁用，[...]=仅列出的）
 * @param skills 技能列表（null=全部，[]=禁用，[...]=仅列出的）
 * @param customErrorMessage 自定义报错回复（API 失败时用）
 * @param folderId 所属文件夹 ID
 * @param sortOrder 排序顺序
 * @param isBuiltin 是否为内置预设（不可删除）
 * @param createdAt 创建时间戳
 * @param updatedAt 更新时间戳
 */
data class Persona(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val beginDialogs: List<String>? = null,
    val tools: List<String>? = null,
    val skills: List<String>? = null,
    val customErrorMessage: String? = null,
    val folderId: String? = null,
    val sortOrder: Int = 0,
    val isBuiltin: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Persona 文件夹，支持树形层级管理（对齐 AstrBot PersonaFolder）。
 */
data class PersonaFolder(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val description: String? = null,
    val sortOrder: Int = 0
)

/**
 * AstrBot 默认兜底人格。
 */
const val FALLBACK_SYSTEM_PROMPT = "You are a helpful and friendly assistant."

/**
 * 内置预设人格。
 */
object BuiltinPersonas {
    const val DEFAULT_ID = "default"
    const val CUTE_ID = "cute"
    const val PROFESSIONAL_ID = "professional"

    val DEFAULT = Persona(
        id = DEFAULT_ID,
        name = "默认助理",
        systemPrompt = "你是兰心，一个温柔体贴的 AI 助理。请用简洁、友善的方式帮助用户。",
        isBuiltin = true,
        sortOrder = 0
    )

    val CUTE = Persona(
        id = CUTE_ID,
        name = "可爱风格",
        systemPrompt = "你是兰心，元气满满的可爱小助理！说话轻快活泼，适当使用可爱语气，但保持有用和准确。",
        isBuiltin = true,
        sortOrder = 1
    )

    val PROFESSIONAL = Persona(
        id = PROFESSIONAL_ID,
        name = "专业风格",
        systemPrompt = "你是 LanXin，专业高效的 AI 助手。回答结构清晰、用词准确，优先给出可执行的结论与步骤。",
        isBuiltin = true,
        sortOrder = 2
    )

    val ALL: List<Persona> = listOf(DEFAULT, CUTE, PROFESSIONAL)
}
