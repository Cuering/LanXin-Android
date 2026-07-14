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

package com.lanxin.android.builtin.persona.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditScreen(
    personaId: String?,
    onBackAction: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PersonaViewModel = hiltViewModel()
) {
    val isEdit = !personaId.isNullOrBlank()
    var name by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var beginDialogsText by remember { mutableStateOf("") }
    var toolsText by remember { mutableStateOf("") }
    var skillsText by remember { mutableStateOf("") }
    var customErrorMessage by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(!isEdit) }
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(personaId) {
        if (isEdit) {
            val persona = viewModel.getPersona(personaId!!)
            if (persona != null) {
                name = persona.name
                systemPrompt = persona.systemPrompt
                beginDialogsText = persona.beginDialogs?.joinToString("\n") ?: ""
                toolsText = persona.tools?.joinToString(", ") ?: ""
                skillsText = persona.skills?.joinToString(", ") ?: ""
                customErrorMessage = persona.customErrorMessage ?: ""
            }
            loaded = true
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑人格" else "新建人格") },
                navigationIcon = {
                    IconButton(onClick = onBackAction) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (!loaded) {
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                label = { Text("System Prompt") },
                supportingText = { Text("该文本会作为 system 消息注入对话") }
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = beginDialogsText,
                onValueChange = { beginDialogsText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                label = { Text("预设对话（beginDialogs）") },
                supportingText = { Text("每行一条，交替 user/assistant，留空表示无") }
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = toolsText,
                onValueChange = { toolsText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("工具列表（tools）") },
                supportingText = { Text("逗号分隔。留空=全部，空列表=禁用（输入后清空）") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = skillsText,
                onValueChange = { skillsText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("技能列表（skills）") },
                supportingText = { Text("逗号分隔。留空=全部，空列表=禁用") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = customErrorMessage,
                onValueChange = { customErrorMessage = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("自定义报错回复") },
                supportingText = { Text("API 失败时用此消息回复用户，留空使用默认文案") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val parsedBeginDialogs = beginDialogsText
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .ifEmpty { null }
                    val parsedTools = toolsText
                        .split(",", "，")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .ifEmpty {
                            if (toolsText.isBlank()) null else emptyList()
                        }
                    val parsedSkills = skillsText
                        .split(",", "，")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .ifEmpty {
                            if (skillsText.isBlank()) null else emptyList()
                        }
                    viewModel.savePersona(
                        id = personaId,
                        name = name,
                        systemPrompt = systemPrompt,
                        beginDialogs = parsedBeginDialogs,
                        tools = parsedTools,
                        skills = parsedSkills,
                        customErrorMessage = customErrorMessage.trim().ifBlank { null },
                        onDone = { onSaved() }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isEdit) "保存" else "创建")
            }
        }
    }
}
