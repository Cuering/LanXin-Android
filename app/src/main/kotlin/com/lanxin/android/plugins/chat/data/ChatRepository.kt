package com.lanxin.android.plugins.chat.data

import com.lanxin.android.plugins.chat.data.entity.ChatRoom
import com.lanxin.android.plugins.chat.data.entity.ChatRoomV2
import com.lanxin.android.plugins.chat.data.entity.Message
import com.lanxin.android.plugins.chat.data.entity.MessageV2
import com.lanxin.android.plugins.chat.data.entity.PlatformV2
import com.lanxin.android.data.dto.ApiState
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    /**
     * 完成一轮对话（云端或本地）。
     *
     * @param needsTools 本轮是否需要 tool_call / MCP（Phase 6.3：优先云端）
     */
    suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        needsTools: Boolean = false
    ): Flow<ApiState>

    suspend fun fetchChatList(): List<ChatRoom>
    suspend fun fetchChatListV2(): List<ChatRoomV2>
    suspend fun searchChatsV2(query: String): List<ChatRoomV2>
    suspend fun fetchMessages(chatId: Int): List<Message>
    suspend fun fetchMessagesV2(chatId: Int): List<MessageV2>
    suspend fun fetchChatPlatformModels(chatId: Int): Map<String, String>
    suspend fun saveChatPlatformModels(chatId: Int, models: Map<String, String>)
    suspend fun migrateToChatRoomV2MessageV2()
    fun generateDefaultChatTitle(messages: List<MessageV2>): String?
    suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String)
    suspend fun saveChat(chatRoom: ChatRoomV2, messages: List<MessageV2>, chatPlatformModels: Map<String, String>): ChatRoomV2
    suspend fun duplicateChatV2(chatRoom: ChatRoomV2): ChatRoomV2
    suspend fun deleteChats(chatRooms: List<ChatRoom>)
    suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>)
}
