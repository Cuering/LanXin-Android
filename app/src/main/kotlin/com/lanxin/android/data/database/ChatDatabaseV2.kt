package com.lanxin.android.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lanxin.android.data.database.dao.ChatPlatformModelV2Dao
import com.lanxin.android.data.database.dao.ChatRoomV2Dao
import com.lanxin.android.data.database.dao.MessageV2Dao
import com.lanxin.android.data.database.dao.PlatformV2Dao
import com.lanxin.android.data.database.entity.AssistantRevisionListConverter
import com.lanxin.android.data.database.entity.ChatAttachmentListConverter
import com.lanxin.android.data.database.entity.ChatPlatformModelV2
import com.lanxin.android.data.database.entity.ChatRoomV2
import com.lanxin.android.data.database.entity.MessageV2
import com.lanxin.android.data.database.entity.PlatformV2
import com.lanxin.android.data.database.entity.StringListConverter

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
