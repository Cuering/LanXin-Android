package com.lanxin.android.presentation.ui.chat

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.knowledge.domain.AutoKnowledgeService
import com.lanxin.android.builtin.persona.domain.PersonaCapabilityFilter
import com.lanxin.android.builtin.persona.domain.PersonaMoodFormatter
import com.lanxin.android.builtin.persona.domain.PersonaRepository
import com.lanxin.android.builtin.statistics.domain.ChatTurnStatEvent
import com.lanxin.android.builtin.statistics.domain.ProviderStat
import com.lanxin.android.builtin.statistics.domain.StatisticsRepository
import com.lanxin.android.builtin.unifiedsearch.domain.UnifiedSearchService
import com.lanxin.android.data.repository.SettingRepository
import com.lanxin.android.plugin.ToolCallEngine
import com.lanxin.android.plugin.ToolDef
import com.lanxin.android.skill.SkillEngine
import com.lanxin.android.plugins.chat.data.AttachmentUploadCoordinator
import com.lanxin.android.plugins.chat.data.ChatRepository
import com.lanxin.android.plugins.chat.data.entity.ACTIVE_REVISION_LATEST
import com.lanxin.android.plugins.chat.data.entity.ChatRoomV2
import com.lanxin.android.plugins.chat.data.entity.MessageV2
import com.lanxin.android.plugins.chat.data.entity.PlatformV2
import com.lanxin.android.plugins.chat.data.entity.effectiveContent
import com.lanxin.android.plugins.chat.data.entity.effectiveThoughts
import com.lanxin.android.plugins.chat.data.entity.resetActiveRevision
import com.lanxin.android.plugins.chat.data.entity.selectRevision
import com.lanxin.android.plugins.chat.data.entity.snapshotLatestAssistantRevision
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import com.lanxin.android.plugins.memory.domain.memory.MemoryInjector
import com.lanxin.android.util.AttachmentPayloadCache
import com.lanxin.android.util.FileUtils
import com.lanxin.android.util.getPlatformName
import com.lanxin.android.util.handleStates
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository,
    private val attachmentUploadCoordinator: AttachmentUploadCoordinator,
    private val memoryRepository: MemoryRepository,
    private val memoryInjector: MemoryInjector,
    private val unifiedSearchService: UnifiedSearchService,
    private val toolCallEngine: ToolCallEngine,
    private val personaRepository: PersonaRepository,
    private val statisticsRepository: StatisticsRepository,
    private val autoKnowledgeService: AutoKnowledgeService,
    private val skillEngine: SkillEngine
) : ViewModel() {
    sealed class LoadingState {
        data object Idle : LoadingState()
        data object Loading : LoadingState()
    }

    data class GroupedMessages(
        val userMessages: List<MessageV2> = listOf(),
        val assistantMessages: List<List<MessageV2>> = listOf()
    )

    enum class MessageEditRole {
        USER,
        ASSISTANT
    }

    data class MessageEditSession(
        val message: MessageV2,
        val role: MessageEditRole,
        val turnIndex: Int? = null,
        val platformIndex: Int? = null,
        val attachments: List<ChatAttachmentDraft> = emptyList()
    )

    private val chatRoomId: Int = checkNotNull(savedStateHandle["chatRoomId"])
    private val enabledPlatformString: String = checkNotNull(savedStateHandle["enabledPlatforms"])
    val enabledPlatformsInChat = enabledPlatformString.split(',')

    private val currentTimeStamp: Long
        get() = System.currentTimeMillis() / 1000

    private val _chatRoom = MutableStateFlow(ChatRoomV2(id = -1, title = "", enabledPlatform = enabledPlatformsInChat))
    val chatRoom = _chatRoom.asStateFlow()

    private val _isChatTitleDialogOpen = MutableStateFlow(false)
    val isChatTitleDialogOpen = _isChatTitleDialogOpen.asStateFlow()

    private val _messageEditSession = MutableStateFlow<MessageEditSession?>(null)
    val messageEditSession = _messageEditSession.asStateFlow()

    private val _isSelectTextSheetOpen = MutableStateFlow(false)
    val isSelectTextSheetOpen = _isSelectTextSheetOpen.asStateFlow()

    private val _isChatModelDialogOpen = MutableStateFlow(false)
    val isChatModelDialogOpen = _isChatModelDialogOpen.asStateFlow()

    private val _chatPlatformModels = MutableStateFlow<Map<String, String>>(emptyMap())
    val chatPlatformModels = _chatPlatformModels.asStateFlow()

    private val _platformsInApp = MutableStateFlow(listOf<PlatformV2>())
    val platformsInApp = _platformsInApp.asStateFlow()

    private val _enabledPlatformsInApp = MutableStateFlow(listOf<PlatformV2>())
    val enabledPlatformsInApp = _enabledPlatformsInApp.asStateFlow()

    val question = TextFieldState()

    private val _selectedAttachments = MutableStateFlow(listOf<ChatAttachmentDraft>())
    val selectedAttachments = _selectedAttachments.asStateFlow()

    private val _attachmentNotice = MutableStateFlow<String?>(null)
    val attachmentNotice = _attachmentNotice.asStateFlow()

    private val _groupedMessages = MutableStateFlow(GroupedMessages())
    val groupedMessages = _groupedMessages.asStateFlow()

    private val _indexStates = MutableStateFlow(listOf<Int>())
    val indexStates = _indexStates.asStateFlow()

    private val _loadingStates = MutableStateFlow(List<LoadingState>(enabledPlatformsInChat.size) { LoadingState.Idle })
    val loadingStates = _loadingStates.asStateFlow()

    private val _selectedText = MutableStateFlow("")
    val selectedText = _selectedText.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()

    private var pendingQuestionText: String? = null

    init {
        fetchChatRoom()
        viewModelScope.launch { fetchMessages() }
        fetchEnabledPlatformsInApp()
        observeStateChanges()
    }

    fun addMessage(userMessage: MessageV2) {
        _groupedMessages.update {
            it.copy(
                userMessages = it.userMessages + listOf(userMessage),
                assistantMessages = it.assistantMessages + listOf(
                    enabledPlatformsInChat.map { p -> MessageV2(chatId = chatRoomId, content = "", platformType = p) }
                )
            )
        }
        _indexStates.update { it + listOf(0) }
    }

    fun askQuestion() {
        val questionText = question.text.toString()
        val hasReadyAttachments = _selectedAttachments.value.any { it.status == ChatAttachmentDraft.Status.Ready }
        val hasPreparingAttachments = _selectedAttachments.value.any { it.status == ChatAttachmentDraft.Status.Preparing }
        if (questionText.isBlank() && !hasReadyAttachments && !hasPreparingAttachments) return
        if (_selectedAttachments.value.any { it.status == ChatAttachmentDraft.Status.Failed }) {
            _attachmentNotice.update { "Remove failed attachments before sending." }
            return
        }

        if (hasPreparingAttachments) {
            pendingQuestionText = questionText
            question.clearText()
            _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Loading } }
            trySendPendingQuestionIfReady()
            return
        }

        sendQuestion(questionText, _selectedAttachments.value)
    }

    /**
     * 定时任务 ACTIVE_AGENT 通知唤起：预填输入框，可选自动发送。
     */
    fun applySchedulerPrompt(prompt: String, autoStart: Boolean) {
        if (prompt.isBlank()) return
        question.setTextAndPlaceCursorAtEnd(prompt)
        if (autoStart) {
            viewModelScope.launch {
                var retries = 0
                while (!_isLoaded.value && retries < 40) {
                    kotlinx.coroutines.delay(50)
                    retries++
                }
                if (question.text.toString().isNotBlank()) {
                    askQuestion()
                }
            }
        }
    }

    override fun onCleared() {
        AttachmentPayloadCache.clear()
        super.onCleared()
    }

    fun closeChatTitleDialog() = _isChatTitleDialogOpen.update { false }

    fun discardMessageEditDialog() {
        _messageEditSession.value?.attachments?.forEach { attachment ->
            if (attachment.cleanupOnDiscard) {
                attachment.preparedFilePath?.let { AttachmentPayloadCache.remove(it) }
                deleteDraftFiles(attachment)
            }
        }
        _messageEditSession.update { null }
    }

    fun finishMessageEditDialog() {
        _messageEditSession.update { null }
    }

    fun closeSelectTextSheet() {
        _isSelectTextSheetOpen.update { false }
        _selectedText.update { "" }
    }

    fun closeChatModelDialog() = _isChatModelDialogOpen.update { false }

    fun openChatTitleDialog() = _isChatTitleDialogOpen.update { true }
    fun openChatModelDialog() = _isChatModelDialogOpen.update { true }

    fun openUserMessageEditDialog(question: MessageV2) {
        _messageEditSession.update {
            MessageEditSession(
                message = question,
                role = MessageEditRole.USER,
                attachments = question.attachments.map(ChatAttachmentDraft::fromAttachment)
            )
        }
    }

    fun openAssistantMessageEditDialog(turnIndex: Int, platformIndex: Int) {
        val assistantMessage = _groupedMessages.value.assistantMessages
            .getOrNull(turnIndex)
            ?.getOrNull(platformIndex)
            ?: return
        _messageEditSession.update {
            MessageEditSession(
                message = assistantMessage,
                role = MessageEditRole.ASSISTANT,
                turnIndex = turnIndex,
                platformIndex = platformIndex,
                attachments = assistantMessage.attachments.map(ChatAttachmentDraft::fromAttachment)
            )
        }
    }

    fun openSelectTextSheet(content: String) {
        _selectedText.update { content }
        _isSelectTextSheetOpen.update { true }
    }

    fun generateDefaultChatTitle(): String? = chatRepository.generateDefaultChatTitle(_groupedMessages.value.userMessages)

    fun updateChatPlatformModels(models: Map<String, String>) {
        val sanitizedModels = models
            .filterKeys { it in enabledPlatformsInChat }
            .mapValues { (_, model) -> model.trim() }

        _chatPlatformModels.update { it + sanitizedModels }

        if (_chatRoom.value.id > 0) {
            viewModelScope.launch {
                chatRepository.saveChatPlatformModels(_chatRoom.value.id, _chatPlatformModels.value)
            }
        }
    }

    fun retryChat(turnIndex: Int, platformIndex: Int) {
        if (turnIndex !in _groupedMessages.value.assistantMessages.indices) return
        if (platformIndex >= enabledPlatformsInChat.size || platformIndex < 0) return
        val platform = _enabledPlatformsInApp.value.firstOrNull { it.uid == enabledPlatformsInChat[platformIndex] } ?: return
        val platformWithChatModel = resolvePlatformModel(platform)
        val revisionToAppendOnSuccess = _groupedMessages.value.assistantMessages
            .getOrNull(turnIndex)
            ?.getOrNull(platformIndex)
            ?.snapshotLatestAssistantRevision(currentTimeStamp)
        _loadingStates.update { it.toMutableList().apply { this[platformIndex] = LoadingState.Loading } }
        _groupedMessages.update {
            updateAssistantSlot(
                groupedMessages = it,
                turnIndex = turnIndex,
                platformIndex = platformIndex
            ) { currentMessage ->
                createRetryAssistantMessage(
                    currentMessage = currentMessage,
                    chatId = chatRoomId,
                    platformUid = platformWithChatModel.uid
                )
            }
        }

        viewModelScope.launch {
            val retryContext = groupedMessagesThroughTurn(_groupedMessages.value, turnIndex)
            runChatWithTools(
                userMessages = retryContext.userMessages,
                assistantMessages = retryContext.assistantMessages,
                platform = platformWithChatModel,
                turnIndex = turnIndex,
                platformIndex = platformIndex,
                revisionToAppendOnSuccess = revisionToAppendOnSuccess
            )
        }
    }

    fun updateChatTitle(title: String) {
        if (_chatRoom.value.id > 0) {
            _chatRoom.update { it.copy(title = title) }
            viewModelScope.launch {
                chatRepository.updateChatTitle(_chatRoom.value, title)
            }
        }
    }

    fun updateChatPlatformIndex(assistantIndex: Int, platformIndex: Int) {
        if (assistantIndex >= _indexStates.value.size || assistantIndex < 0) return
        if (platformIndex >= enabledPlatformsInChat.size || platformIndex < 0) return

        _indexStates.update {
            val updatedIndex = it.toMutableList()
            updatedIndex[assistantIndex] = platformIndex
            updatedIndex
        }
    }

    fun addSelectedFile(filePath: String) {
        addDraftFile(
            currentAttachments = { _selectedAttachments.value },
            updateAttachments = { attachments -> _selectedAttachments.update { attachments } },
            filePath = filePath,
            onNotice = { notice -> _attachmentNotice.update { notice } }
        )
    }

    fun removeSelectedFile(filePath: String) {
        removeDraftFile(
            currentAttachments = { _selectedAttachments.value },
            updateAttachments = { attachments -> _selectedAttachments.update { attachments } },
            filePath = filePath
        )
        trySendPendingQuestionIfReady()
    }

    fun addMessageEditFile(filePath: String) {
        addDraftFile(
            currentAttachments = { _messageEditSession.value?.attachments.orEmpty() },
            updateAttachments = ::updateMessageEditAttachments,
            filePath = filePath,
            onNotice = { notice -> _attachmentNotice.update { notice } }
        )
    }

    fun removeMessageEditFile(filePath: String) {
        removeDraftFile(
            currentAttachments = { _messageEditSession.value?.attachments.orEmpty() },
            updateAttachments = ::updateMessageEditAttachments,
            filePath = filePath
        )
    }

    fun clearSelectedFiles() {
        _selectedAttachments.value.forEach { attachment ->
            attachment.preparedFilePath?.let { AttachmentPayloadCache.remove(it) }
        }
        _selectedAttachments.update { emptyList() }
    }

    fun consumeAttachmentNotice() {
        _attachmentNotice.update { null }
    }

    fun notifyAttachmentCopyFailed() {
        _attachmentNotice.update { "Failed to copy attachment." }
    }

    fun saveUserMessageEdit(
        editedMessage: MessageV2,
        attachments: List<ChatAttachmentDraft>
    ): Boolean {
        if (attachments.any { it.status != ChatAttachmentDraft.Status.Ready }) {
            _attachmentNotice.update { "Wait for attachments to finish processing before saving." }
            return false
        }

        val userMessages = _groupedMessages.value.userMessages
        val assistantMessages = _groupedMessages.value.assistantMessages

        val messageIndex = userMessages.indexOfFirst { it.id == editedMessage.id }
        if (messageIndex == -1) return false

        val updatedUserMessages = userMessages.toMutableList()
        updatedUserMessages[messageIndex] = editedMessage.copy(
            attachments = attachments.mapNotNull { it.attachment },
            createdAt = currentTimeStamp
        )

        val remainingUserMessages = updatedUserMessages.take(messageIndex + 1)
        val remainingAssistantMessages = assistantMessages.take(messageIndex)

        _groupedMessages.update {
            GroupedMessages(
                userMessages = remainingUserMessages,
                assistantMessages = remainingAssistantMessages
            )
        }

        _groupedMessages.update {
            it.copy(
                assistantMessages = it.assistantMessages + listOf(
                    enabledPlatformsInChat.map { p -> MessageV2(chatId = chatRoomId, content = "", platformType = p) }
                )
            )
        }

        val removedMessagesCount = userMessages.size - remainingUserMessages.size
        _indexStates.update {
            val currentStates = it.toMutableList()
            repeat(removedMessagesCount) { currentStates.removeLastOrNull() }
            currentStates
        }

        completeChat()
        return true
    }

    fun saveAssistantMessageEdit(
        editedMessage: MessageV2,
        thoughts: String,
        attachments: List<ChatAttachmentDraft>
    ): Boolean {
        if (attachments.any { it.status != ChatAttachmentDraft.Status.Ready }) {
            _attachmentNotice.update { "Wait for attachments to finish processing before saving." }
            return false
        }

        val session = _messageEditSession.value ?: return false
        val turnIndex = session.turnIndex ?: return false
        val platformIndex = session.platformIndex ?: return false
        val currentMessage = _groupedMessages.value.assistantMessages
            .getOrNull(turnIndex)
            ?.getOrNull(platformIndex)
            ?: return false

        val updatedContent = editedMessage.content
        val updatedThoughts = thoughts
        val updatedAttachments = attachments.mapNotNull { it.attachment }

        val textChanged = currentMessage.content != updatedContent || currentMessage.thoughts != updatedThoughts
        val updatedRevisions = if (textChanged) {
            currentMessage.snapshotLatestAssistantRevision(currentTimeStamp)
                ?.let { listOf(it) + currentMessage.revisions }
                ?: currentMessage.revisions
        } else {
            currentMessage.revisions
        }

        _groupedMessages.update {
            updateAssistantSlot(
                groupedMessages = it,
                turnIndex = turnIndex,
                platformIndex = platformIndex
            ) { assistantMessage ->
                assistantMessage.copy(
                    content = updatedContent,
                    thoughts = updatedThoughts,
                    attachments = updatedAttachments,
                    revisions = updatedRevisions,
                    createdAt = assistantMessage.createdAt
                ).resetActiveRevision()
            }
        }
        persistCurrentChatSnapshot()
        return true
    }

    fun showPreviousAssistantRevision(turnIndex: Int, platformIndex: Int) {
        updateAssistantRevisionSelection(
            turnIndex,
            platformIndex,
            { message ->
                when {
                    message.revisions.isEmpty() -> message.activeRevisionIndex
                    message.activeRevisionIndex == ACTIVE_REVISION_LATEST -> 0
                    message.activeRevisionIndex < message.revisions.lastIndex -> message.activeRevisionIndex + 1
                    else -> message.activeRevisionIndex
                }
            }
        )
    }

    fun showNextAssistantRevision(turnIndex: Int, platformIndex: Int) {
        updateAssistantRevisionSelection(
            turnIndex,
            platformIndex,
            { message ->
                when {
                    message.activeRevisionIndex == ACTIVE_REVISION_LATEST -> ACTIVE_REVISION_LATEST
                    message.activeRevisionIndex == 0 -> ACTIVE_REVISION_LATEST
                    else -> message.activeRevisionIndex - 1
                }
            }
        )
    }

    fun exportChat(): Pair<String, String> {
        val chatHistoryMarkdown = buildString {
            appendLine("# Chat Export: \"${chatRoom.value.title}\"")
            appendLine()
            appendLine("**Exported on:** ${formatCurrentDateTime()}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Chat History")
            appendLine()
            _groupedMessages.value.userMessages.forEachIndexed { i, message ->
                appendLine("**User:**")
                appendLine(message.content)
                appendLine()

                _groupedMessages.value.assistantMessages[i].forEach { message ->
                    val platformName = message.platformType
                        ?.let { _platformsInApp.value.getPlatformName(it) }
                        ?: "Unknown"
                    appendLine("**Assistant ($platformName):**")
                    appendLine(message.effectiveContent())
                    appendLine()
                }
            }
        }

        val fileName = "export_${chatRoom.value.title}_${System.currentTimeMillis()}.md"
        return Pair(fileName, chatHistoryMarkdown)
    }

    private fun completeChat() {
        _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Loading } }
        val turnIndex = _groupedMessages.value.assistantMessages.lastIndex

        enabledPlatformsInChat.forEachIndexed { idx, platformUid ->
            val platform = _enabledPlatformsInApp.value.firstOrNull { it.uid == platformUid } ?: return@forEachIndexed
            val platformWithChatModel = resolvePlatformModel(platform)
            viewModelScope.launch {
                runChatWithTools(
                    userMessages = _groupedMessages.value.userMessages,
                    assistantMessages = _groupedMessages.value.assistantMessages,
                    platform = platformWithChatModel,
                    turnIndex = turnIndex,
                    platformIndex = idx
                )
            }
        }
    }

    /**
     * 带 MCP 工具调用循环的对话完成：
     * 1. 注入人格 system prompt + 记忆 + 工具系统提示词
     * 2. 流式请求模型并更新 UI
     * 3. 若回复含 tool_call，路由到 PluginManager 并回填，再请求（最多 [MAX_TOOL_ROUNDS] 轮）
     * 4. 工具中间态不写入聊天历史，最终回复覆盖同一 assistant 槽位
     * 5. 首个平台成功回复后异步抽取自动知识（P3，静默失败）
     */
    private suspend fun runChatWithTools(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        turnIndex: Int,
        platformIndex: Int,
        revisionToAppendOnSuccess: com.lanxin.android.plugins.chat.data.entity.AssistantRevision? = null
    ) {
        val baseSystemPrompt = resolveSystemPrompt(platform.systemPrompt)
        val filteredTools = resolvePersonaFilteredTools()
        val allowedToolNames = filteredTools.allowedNames
        val platformWithTools = platform.copy(
            systemPrompt = toolCallEngine.mergeSystemPrompt(
                existing = baseSystemPrompt,
                tools = filteredTools.tools
            )
        )

        var workingUserMessages = injectMemoryIntoLastUserMessage(userMessages)
        var workingAssistantMessages = assistantMessages
        var remainingRounds = MAX_TOOL_ROUNDS
        var pendingRevision = revisionToAppendOnSuccess
        val turnStartMs = System.currentTimeMillis()
        var lastAssistantText = ""
        var recordedError = false

        try {
            while (true) {
                chatRepository.completeChat(
                    workingUserMessages,
                    workingAssistantMessages,
                    platformWithTools
                ).handleStates(
                    messageFlow = _groupedMessages,
                    turnIndex = turnIndex,
                    platformIdx = platformIndex,
                    onLoadingComplete = {},
                    revisionToAppendOnSuccess = pendingRevision
                )

                val assistantText = _groupedMessages.value
                    .assistantMessages
                    .getOrNull(turnIndex)
                    ?.getOrNull(platformIndex)
                    ?.content
                    .orEmpty()
                lastAssistantText = assistantText
                if (assistantText.contains("[Response stopped:")) {
                    recordedError = true
                }

                if (remainingRounds <= 0) break

                val toolRound = toolCallEngine.processAssistantReply(
                    assistantText = assistantText,
                    allowedToolNames = allowedToolNames
                ) ?: break

                // 清理助手消息中的 tool_call 标签（用户可见的中间提示）
                val cleaned = toolRound.cleanedAssistantText
                _groupedMessages.update { grouped ->
                    updateAssistantSlot(
                        groupedMessages = grouped,
                        turnIndex = turnIndex,
                        platformIndex = platformIndex
                    ) { current ->
                        current.copy(content = cleaned)
                    }
                }

                // 构造下一轮模型上下文：历史 + 本轮清理后的 assistant + 工具结果 user + 空 assistant
                val historyUsers = workingUserMessages
                val historyAssistants = workingAssistantMessages.toMutableList()

                // 确保当前 turn 的 assistant 槽有清理后的内容
                if (turnIndex in historyAssistants.indices) {
                    val row = historyAssistants[turnIndex].toMutableList()
                    if (platformIndex in row.indices) {
                        row[platformIndex] = row[platformIndex].copy(content = cleaned)
                        historyAssistants[turnIndex] = row
                    }
                }

                val toolUserMessage = MessageV2(
                    chatId = chatRoomId,
                    content = toolRound.followUpUserMessage,
                    platformType = null,
                    createdAt = currentTimeStamp
                )
                val placeholderAssistant = MessageV2(
                    chatId = chatRoomId,
                    content = "",
                    platformType = platform.uid
                )

                workingUserMessages = historyUsers + toolUserMessage
                workingAssistantMessages = historyAssistants + listOf(listOf(placeholderAssistant))

                // 下一轮覆盖同一 UI 槽位，不再追加 revision
                pendingRevision = null
                remainingRounds -= 1

                // 清空 UI 槽位，准备接收最终/下一轮回复
                _groupedMessages.update { grouped ->
                    updateAssistantSlot(
                        groupedMessages = grouped,
                        turnIndex = turnIndex,
                        platformIndex = platformIndex
                    ) { current ->
                        current.copy(content = "", thoughts = "")
                    }
                }
            }
        } catch (t: Throwable) {
            recordedError = true
            throw t
        } finally {
            recordChatTurnStats(
                platform = platform,
                userMessages = workingUserMessages,
                assistantText = lastAssistantText,
                startTimeMs = turnStartMs,
                isError = recordedError,
                countAsMessage = platformIndex == 0
            )
            // P3：首个平台成功回复后异步抽取知识，静默失败
            if (!recordedError && platformIndex == 0 && lastAssistantText.isNotBlank()) {
                maybeExtractAutoKnowledge(platformIndex)
            }
            _loadingStates.update {
                it.toMutableList().apply { this[platformIndex] = LoadingState.Idle }
            }
        }
    }

    private fun maybeExtractAutoKnowledge(platformIndex: Int) {
        val snapshot = _groupedMessages.value
        val sessionId = _chatRoom.value.id.toString().ifBlank { chatRoomId.toString() }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val recent = autoKnowledgeService.toConversationMessages(
                    userMessages = snapshot.userMessages,
                    assistantMessages = snapshot.assistantMessages,
                    platformIndex = platformIndex
                )
                autoKnowledgeService.extractAndStore(sessionId, recent)
            }
        }
    }

    private fun recordChatTurnStats(
        platform: PlatformV2,
        userMessages: List<MessageV2>,
        assistantText: String,
        startTimeMs: Long,
        isError: Boolean,
        countAsMessage: Boolean
    ) {
        val inputTexts = buildList {
            userMessages.forEach { add(it.content) }
        }
        val status = if (isError) ProviderStat.STATUS_ERROR else ProviderStat.STATUS_COMPLETED
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                statisticsRepository.recordChatTurn(
                    ChatTurnStatEvent(
                        providerId = platform.name.ifBlank { platform.uid },
                        providerModel = platform.model,
                        chatId = chatRoomId.takeIf { it > 0 },
                        status = status,
                        inputTexts = inputTexts,
                        outputText = assistantText,
                        startTimeMs = startTimeMs,
                        countAsMessage = countAsMessage
                    )
                )
            }
        }
    }

    private companion object {
        private const val MAX_TOOL_ROUNDS = 3
    }

    /**
     * 解析最终 system prompt：
     * - 当前人格 prompt 优先作为底座
     * - 若人格配置 mood_imitation_dialogs，拼接情绪风格示例
     * - 若平台自身也配置了 systemPrompt，则拼在人格之后
     */
    private suspend fun resolveSystemPrompt(platformPrompt: String?): String {
        val persona = runCatching {
            personaRepository.getCurrent()
        }.getOrNull()
        val personaPrompt = persona?.systemPrompt?.trim().orEmpty()
        val withMood = PersonaMoodFormatter.appendToSystemPrompt(
            base = personaPrompt,
            dialogs = persona?.moodImitationDialogs
        )
        val platform = platformPrompt?.trim().orEmpty()
        return when {
            withMood.isEmpty() -> platform
            platform.isEmpty() -> withMood
            withMood == platform -> withMood
            else -> "$withMood\n\n$platform"
        }
    }

    /**
     * 按当前人格 tools/skills 过滤 MCP 工具。
     * persona 为 null 或 tools/skills 均为 null 时不限制。
     */
    private suspend fun resolvePersonaFilteredTools(): PersonaFilteredTools {
        val globalTools = toolCallEngine.getRegisteredTools()
        val persona = runCatching { personaRepository.getCurrent() }.getOrNull()
        if (persona == null || (persona.tools == null && persona.skills == null)) {
            return PersonaFilteredTools(
                tools = globalTools,
                allowedNames = null
            )
        }
        val knownSkills = runCatching {
            skillEngine.getSkills().map { it.name }.toSet()
        }.getOrDefault(emptySet())
        val filtered = PersonaCapabilityFilter.filterTools(
            tools = globalTools,
            allowedTools = persona.tools,
            allowedSkills = persona.skills,
            knownSkillNames = knownSkills
        )
        return PersonaFilteredTools(
            tools = filtered,
            allowedNames = filtered.map { it.name }.toSet()
        )
    }

    private data class PersonaFilteredTools(
        val tools: List<ToolDef>,
        /** null 表示不限制执行 */
        val allowedNames: Set<String>?
    )

    /**
     * 仅在发给模型时注入上下文，界面仍显示用户原始消息。
     *
     * Phase 4.4：优先走 UnifiedSearch 四路 RRF；关闭时回退 MemoryInjector。
     */
    private suspend fun injectMemoryIntoLastUserMessage(userMessages: List<MessageV2>): List<MessageV2> {
        if (userMessages.isEmpty()) return userMessages
        val last = userMessages.last()
        val enriched = if (unifiedSearchService.enabled) {
            unifiedSearchService.inject(last.content)
        } else {
            memoryInjector.inject(last.content)
        }
        if (enriched == last.content) return userMessages
        return userMessages.dropLast(1) + last.copy(content = enriched)
    }

    fun rememberMessage(content: String, type: String, importance: Float = 3f) {
        if (content.isBlank()) return
        viewModelScope.launch {
            memoryRepository.addMemory(content.trim(), type, importance)
            _attachmentNotice.update { "已记入记忆" }
        }
    }

    private fun updateMessageEditAttachments(attachments: List<ChatAttachmentDraft>) {
        _messageEditSession.update { session ->
            session?.copy(attachments = attachments)
        }
    }

    private fun addDraftFile(
        currentAttachments: () -> List<ChatAttachmentDraft>,
        updateAttachments: (List<ChatAttachmentDraft>) -> Unit,
        filePath: String,
        onNotice: (String?) -> Unit = {}
    ) {
        if (currentAttachments().any { it.sourceFilePath == filePath }) return

        updateAttachments(currentAttachments() + ChatAttachmentDraft(sourceFilePath = filePath))
        preprocessDraftAttachment(
            currentAttachments = currentAttachments,
            updateAttachments = updateAttachments,
            filePath = filePath,
            onNotice = onNotice
        )
    }

    private fun removeDraftFile(
        currentAttachments: () -> List<ChatAttachmentDraft>,
        updateAttachments: (List<ChatAttachmentDraft>) -> Unit,
        filePath: String
    ) {
        val removedAttachment = currentAttachments().firstOrNull { it.sourceFilePath == filePath }
        removedAttachment?.preparedFilePath?.let { AttachmentPayloadCache.remove(it) }
        if (removedAttachment?.cleanupOnDiscard == true) {
            removedAttachment.let(::deleteDraftFiles)
        }
        updateAttachments(currentAttachments().filter { it.sourceFilePath != filePath })
    }

    private fun preprocessDraftAttachment(
        currentAttachments: () -> List<ChatAttachmentDraft>,
        updateAttachments: (List<ChatAttachmentDraft>) -> Unit,
        filePath: String,
        onNotice: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val mimeType = withContext(Dispatchers.IO) {
                FileUtils.getMimeType(context, filePath)
            }

            if (!FileUtils.isSupportedUploadMimeType(mimeType)) {
                rejectDraftAttachment(
                    currentAttachments = currentAttachments,
                    updateAttachments = updateAttachments,
                    filePath = filePath,
                    notice = "Only image attachments are currently supported."
                )
                trySendPendingQuestionIfReady()
                return@launch
            }

            val fileSize = withContext(Dispatchers.IO) {
                FileUtils.getFileSize(context, filePath)
            }

            if (fileSize > FileUtils.MAX_UPLOAD_SIZE_BYTES) {
                rejectDraftAttachment(
                    currentAttachments = currentAttachments,
                    updateAttachments = updateAttachments,
                    filePath = filePath,
                    notice = "Files larger than 50 MB cannot be attached."
                )
                trySendPendingQuestionIfReady()
                return@launch
            }

            val currentDraftBytes = withContext(Dispatchers.IO) {
                currentAttachments()
                    .filter { it.sourceFilePath != filePath }
                    .sumOf { FileUtils.getFileSize(context, it.sourceFilePath).coerceAtLeast(0L) }
            }

            if (FileUtils.wouldExceedTotalUploadLimit(currentDraftBytes, fileSize)) {
                rejectDraftAttachment(
                    currentAttachments = currentAttachments,
                    updateAttachments = updateAttachments,
                    filePath = filePath,
                    notice = "Total attachments cannot exceed 50 MB."
                )
                trySendPendingQuestionIfReady()
                return@launch
            }

            val preparationResult = withContext(Dispatchers.IO) {
                attachmentUploadCoordinator.prepareLocalAttachment(context, filePath)
            }

            if (currentAttachments().none { it.sourceFilePath == filePath }) {
                if (preparationResult != null && preparationResult.preparedFilePath != filePath) {
                    java.io.File(preparationResult.preparedFilePath).delete()
                }
                return@launch
            }

            updateAttachments(
                currentAttachments().map { attachment ->
                    if (attachment.sourceFilePath != filePath) {
                        attachment
                    } else if (preparationResult == null) {
                        attachment.copy(
                            status = ChatAttachmentDraft.Status.Failed,
                            errorMessage = "Failed to prepare attachment."
                        )
                    } else {
                        attachment.copy(
                            attachment = preparationResult,
                            preparedFilePath = preparationResult.preparedFilePath,
                            mimeType = preparationResult.mimeType,
                            status = ChatAttachmentDraft.Status.Ready,
                            cleanupOnDiscard = true,
                            notice = if (preparationResult.wasResized) {
                                "Large images are resized before upload."
                            } else {
                                null
                            },
                            errorMessage = null
                        )
                    }
                }
            )

            if (preparationResult?.wasResized == true) {
                onNotice("Large images are resized before upload.")
            } else if (preparationResult == null) {
                onNotice("Failed to prepare attachment.")
            }

            trySendPendingQuestionIfReady()
        }
    }

    private fun trySendPendingQuestionIfReady() {
        val queuedQuestion = pendingQuestionText ?: return
        val attachments = _selectedAttachments.value

        if (attachments.any { it.status == ChatAttachmentDraft.Status.Failed }) {
            restoreQueuedQuestion(queuedQuestion)
            pendingQuestionText = null
            _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Idle } }
            return
        }

        if (attachments.any { it.status == ChatAttachmentDraft.Status.Preparing }) {
            return
        }

        if (queuedQuestion.isBlank() && attachments.none { it.status == ChatAttachmentDraft.Status.Ready }) {
            pendingQuestionText = null
            _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Idle } }
            return
        }

        pendingQuestionText = null
        sendQuestion(queuedQuestion, attachments)
    }

    private fun sendQuestion(questionText: String, attachments: List<ChatAttachmentDraft>) {
        MessageV2(
            chatId = chatRoomId,
            content = questionText,
            attachments = attachments.mapNotNull { it.attachment },
            platformType = null,
            createdAt = currentTimeStamp
        ).let { addMessage(it) }
        question.clearText()
        clearSelectedFiles()
        completeChat()
    }

    private fun rejectDraftAttachment(
        currentAttachments: () -> List<ChatAttachmentDraft>,
        updateAttachments: (List<ChatAttachmentDraft>) -> Unit,
        filePath: String,
        notice: String
    ) {
        val rejectedAttachment = currentAttachments().firstOrNull { it.sourceFilePath == filePath }
        rejectedAttachment?.preparedFilePath?.let { AttachmentPayloadCache.remove(it) }
        if (rejectedAttachment?.cleanupOnDiscard == true) {
            rejectedAttachment.let(::deleteDraftFiles)
        }
        updateAttachments(currentAttachments().filter { it.sourceFilePath != filePath })
        _attachmentNotice.update { notice }
    }

    private fun restoreQueuedQuestion(questionText: String) {
        if (questionText.isBlank()) return
        question.setTextAndPlaceCursorAtEnd(questionText)
    }

    private fun deleteDraftFiles(attachment: ChatAttachmentDraft) {
        if (!attachment.cleanupOnDiscard) return
        java.io.File(attachment.sourceFilePath).delete()
        attachment.preparedFilePath
            ?.takeIf { it != attachment.sourceFilePath }
            ?.let { java.io.File(it).delete() }
    }

    private fun updateAssistantRevisionSelection(
        turnIndex: Int,
        platformIndex: Int,
        nextIndex: (MessageV2) -> Int
    ) {
        _groupedMessages.update {
            updateAssistantSlot(
                groupedMessages = it,
                turnIndex = turnIndex,
                platformIndex = platformIndex
            ) { message ->
                message.selectRevision(nextIndex(message))
            }
        }
        persistCurrentChatSnapshot()
    }

    private fun formatCurrentDateTime(): String {
        val currentDate = java.util.Date()
        val format = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm a", java.util.Locale.getDefault())
        return format.format(currentDate)
    }

    private suspend fun fetchMessages() {
        if (chatRoomId != 0) {
            _groupedMessages.update { fetchGroupedMessages(chatRoomId) }
            if (_groupedMessages.value.assistantMessages.size != _indexStates.value.size) {
                _indexStates.update { List(_groupedMessages.value.assistantMessages.size) { 0 } }
            }
            _loadingStates.update { List(enabledPlatformsInChat.size) { LoadingState.Idle } }
            _isLoaded.update { true }
            return
        }

        if (_chatRoom.value.id != 0) {
            _groupedMessages.update { fetchGroupedMessages(_chatRoom.value.id) }
            return
        }
    }

    private suspend fun fetchGroupedMessages(chatId: Int): GroupedMessages {
        val messages = chatRepository.fetchMessagesV2(chatId).sortedBy { it.createdAt }

        val userMessages = mutableListOf<MessageV2>()
        val assistantMessages = mutableListOf<MutableList<MessageV2>>()

        messages.forEach { message ->
            if (message.platformType == null) {
                userMessages.add(message)
                assistantMessages.add(mutableListOf())
            } else {
                assistantMessages.last().add(message)
            }
        }

        val normalizedAssistantMessages = assistantMessages.map { assistantMessage ->
            normalizeAssistantRow(
                assistantMessages = assistantMessage,
                enabledPlatformsInChat = enabledPlatformsInChat,
                chatId = chatId
            )
        }

        return GroupedMessages(userMessages, normalizedAssistantMessages)
    }

    private fun fetchChatRoom() {
        viewModelScope.launch {
            _chatRoom.update {
                if (chatRoomId == 0) {
                    ChatRoomV2(id = 0, title = "Untitled Chat", enabledPlatform = enabledPlatformsInChat)
                } else {
                    chatRepository.fetchChatListV2().first { it.id == chatRoomId }
                }
            }
        }
    }

    private fun fetchEnabledPlatformsInApp() {
        viewModelScope.launch {
            val allPlatforms = settingRepository.fetchPlatformV2s()
            _platformsInApp.update { allPlatforms }
            _enabledPlatformsInApp.update { allPlatforms.filter { it.enabled } }
            initializeChatPlatformModels(allPlatforms)
        }
    }

    private suspend fun initializeChatPlatformModels(platforms: List<PlatformV2>) {
        val defaultModels = enabledPlatformsInChat.associateWith { uid ->
            platforms.firstOrNull { it.uid == uid }?.model ?: ""
        }
        val persistedModels = if (chatRoomId != 0) {
            chatRepository.fetchChatPlatformModels(chatRoomId)
        } else {
            emptyMap()
        }

        val mergedModels = defaultModels.mapValues { (uid, defaultModel) ->
            persistedModels[uid]?.takeIf { it.isNotBlank() } ?: defaultModel
        }

        _chatPlatformModels.update { mergedModels }

        if (chatRoomId != 0 && mergedModels != persistedModels) {
            chatRepository.saveChatPlatformModels(chatRoomId, mergedModels)
        }
    }

    private fun observeStateChanges() {
        viewModelScope.launch {
            _loadingStates.collect { states ->
                if (_chatRoom.value.id != -1 &&
                    states.all { it == LoadingState.Idle } &&
                    (_groupedMessages.value.userMessages.isNotEmpty() && _groupedMessages.value.assistantMessages.isNotEmpty()) &&
                    (_groupedMessages.value.userMessages.size == _groupedMessages.value.assistantMessages.size)
                ) {
                    val chatRoom = _chatRoom.value
                    val groupedMessages = _groupedMessages.value
                    val chatPlatformModels = _chatPlatformModels.value

                    val savedChatRoom = withContext(Dispatchers.IO) {
                        chatRepository.saveChat(
                            chatRoom = chatRoom,
                            messages = persistableMessages(groupedMessages),
                            chatPlatformModels = chatPlatformModels
                        )
                    }
                    _chatRoom.update { currentChatRoom ->
                        if (currentChatRoom.id == chatRoom.id && chatRoom.id == 0) {
                            savedChatRoom
                        } else {
                            currentChatRoom
                        }
                    }

                    fetchMessages()
                }
            }
        }
    }

    private fun resolvePlatformModel(platform: PlatformV2): PlatformV2 {
        val chatModel = _chatPlatformModels.value[platform.uid]?.trim().orEmpty()
        if (chatModel.isBlank() || chatModel == platform.model) return platform

        return platform.copy(model = chatModel)
    }

    private fun persistCurrentChatSnapshot() {
        viewModelScope.launch {
            val chatRoom = _chatRoom.value
            val groupedMessages = _groupedMessages.value
            if (chatRoom.id <= 0) return@launch
            if (groupedMessages.userMessages.isEmpty()) return@launch
            if (groupedMessages.userMessages.size != groupedMessages.assistantMessages.size) return@launch

            withContext(Dispatchers.IO) {
                chatRepository.saveChat(
                    chatRoom = chatRoom,
                    messages = persistableMessages(groupedMessages),
                    chatPlatformModels = _chatPlatformModels.value
                )
            }
        }
    }
}

internal fun groupedMessagesThroughTurn(
    groupedMessages: ChatViewModel.GroupedMessages,
    turnIndex: Int
): ChatViewModel.GroupedMessages = groupedMessages.copy(
    userMessages = groupedMessages.userMessages.take(turnIndex + 1),
    assistantMessages = groupedMessages.assistantMessages.take(turnIndex + 1)
)

internal fun persistableMessages(groupedMessages: ChatViewModel.GroupedMessages): List<MessageV2> {
    val merged = groupedMessages.userMessages + groupedMessages.assistantMessages.flatten()
    return merged
        .filter {
            it.effectiveContent().isNotBlank() ||
                it.effectiveThoughts().isNotBlank() ||
                it.attachments.isNotEmpty()
        }
        .sortedBy { it.createdAt }
}

internal fun createEmptyAssistantMessage(chatId: Int, platformUid: String): MessageV2 = MessageV2(
    chatId = chatId,
    content = "",
    platformType = platformUid
)

internal fun createRetryAssistantMessage(
    currentMessage: MessageV2,
    chatId: Int,
    platformUid: String
): MessageV2 = createEmptyAssistantMessage(chatId, platformUid)
    .copy(
        revisions = currentMessage.revisions
    )

internal fun normalizeAssistantRow(
    assistantMessages: List<MessageV2>,
    enabledPlatformsInChat: List<String>,
    chatId: Int
): List<MessageV2> {
    if (enabledPlatformsInChat.isEmpty()) return assistantMessages

    val consumedIndexes = mutableSetOf<Int>()
    val normalizedMessages = enabledPlatformsInChat.map { platformUid ->
        val matchedIndex = assistantMessages.indices.firstOrNull { index ->
            index !in consumedIndexes && assistantMessages[index].platformType == platformUid
        }

        if (matchedIndex == null) {
            createEmptyAssistantMessage(chatId, platformUid)
        } else {
            consumedIndexes += matchedIndex
            assistantMessages[matchedIndex]
        }
    }
    val overflowMessages = assistantMessages.filterIndexed { index, _ -> index !in consumedIndexes }

    return normalizedMessages + overflowMessages
}

internal fun updateAssistantSlot(
    groupedMessages: ChatViewModel.GroupedMessages,
    turnIndex: Int,
    platformIndex: Int,
    transform: (MessageV2) -> MessageV2
): ChatViewModel.GroupedMessages {
    if (turnIndex !in groupedMessages.assistantMessages.indices) return groupedMessages

    val currentTurnMessages = groupedMessages.assistantMessages[turnIndex]
    if (platformIndex !in currentTurnMessages.indices) return groupedMessages

    val updatedTurnMessages = currentTurnMessages.toMutableList()
    val updatedMessage = transform(updatedTurnMessages[platformIndex])
    if (updatedMessage == updatedTurnMessages[platformIndex]) return groupedMessages

    updatedTurnMessages[platformIndex] = updatedMessage
    val assistantMessages = groupedMessages.assistantMessages.toMutableList()
    assistantMessages[turnIndex] = updatedTurnMessages

    return groupedMessages.copy(assistantMessages = assistantMessages)
}
