package com.lanxin.android.plugins.chat.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lanxin.android.plugins.chat.data.dao.ChatPlatformModelV2Dao
import com.lanxin.android.plugins.chat.data.dao.ChatRoomV2Dao
import com.lanxin.android.plugins.chat.data.dao.MessageV2Dao
import com.lanxin.android.plugins.chat.data.dao.PlatformV2Dao
import com.lanxin.android.plugins.chat.data.entity.AssistantRevisionListConverter
import com.lanxin.android.plugins.chat.data.entity.ChatAttachmentListConverter
import com.lanxin.android.plugins.chat.data.entity.ChatPlatformModelV2
import com.lanxin.android.plugins.chat.data.entity.ChatRoomV2
import com.lanxin.android.plugins.chat.data.entity.MessageV2
import com.lanxin.android.plugins.chat.data.entity.PlatformV2
import com.lanxin.android.plugins.chat.data.entity.StringListConverter

@Database(
    entities = [ChatRoomV2::class, MessageV2::class, PlatformV2::class, ChatPlatformModelV2::class],
    version = 6,
    exportSchema = true
)
@TypeConverters(
    StringListConverter::class,
    ChatAttachmentListConverter::class,
    AssistantRevisionListConverter::class
)
abstract class ChatDatabaseV2 : RoomDatabase() {

    abstract fun platformDao(): PlatformV2Dao
    abstract fun chatRoomDao(): ChatRoomV2Dao
    abstract fun messageDao(): MessageV2Dao
    abstract fun chatPlatformModelDao(): ChatPlatformModelV2Dao
}
