package com.lanxin.android.plugins.chat.data

import android.content.Context
import com.lanxin.android.core.engine.ChatHistoryProvider
import com.lanxin.android.plugins.chat.data.dao.ChatRoomV2Dao
import com.lanxin.android.plugins.chat.data.dao.MessageV2Dao
import com.lanxin.android.plugins.chat.data.entity.MessageV2
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Singleton
class ChatHistoryProviderImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val messageV2Dao: MessageV2Dao,
    private val chatRoomV2Dao: ChatRoomV2Dao
) : ChatHistoryProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    override suspend fun search(query: String): List<MessageV2> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val chatIds = messageV2Dao.searchMessagesByContent(query.trim())
        if (chatIds.isEmpty()) return@withContext emptyList()
        chatIds.flatMap { chatId ->
            messageV2Dao.loadMessages(chatId).filter { message ->
                message.content.contains(query, ignoreCase = true) ||
                    message.revisions.any { it.content.contains(query, ignoreCase = true) }
            }
        }
    }

    override suspend fun export(): File = withContext(Dispatchers.IO) {
        val rooms = chatRoomV2Dao.getChatRooms()
        val payload = ChatExportPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            chats = rooms.map { room ->
                ChatExportItem(
                    id = room.id,
                    title = room.title,
                    enabledPlatform = room.enabledPlatform,
                    createdAt = room.createdAt,
                    updatedAt = room.updatedAt,
                    messages = messageV2Dao.loadMessages(room.id).map { msg ->
                        MessageExportItem(
                            id = msg.id,
                            chatId = msg.chatId,
                            content = msg.content,
                            thoughts = msg.thoughts,
                            platformType = msg.platformType,
                            createdAt = msg.createdAt
                        )
                    }
                )
            }
        )
        val text = json.encodeToString(ChatExportPayload.serializer(), payload)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "lanxin_chats_$stamp.json")
        file.writeText(text, Charsets.UTF_8)
        file
    }
}

@Serializable
data class ChatExportPayload(
    val version: Int,
    val exportedAt: Long,
    val chats: List<ChatExportItem>
)

@Serializable
data class ChatExportItem(
    val id: Int,
    val title: String,
    val enabledPlatform: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<MessageExportItem>
)

@Serializable
data class MessageExportItem(
    val id: Int,
    val chatId: Int,
    val content: String,
    val thoughts: String = "",
    val platformType: String? = null,
    val createdAt: Long
)
