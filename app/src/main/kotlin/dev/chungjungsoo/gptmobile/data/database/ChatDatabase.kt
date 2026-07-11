package com.lanxin.android.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lanxin.android.data.database.dao.ChatRoomDao
import com.lanxin.android.data.database.dao.MessageDao
import com.lanxin.android.data.database.entity.APITypeConverter
import com.lanxin.android.data.database.entity.ChatRoom
import com.lanxin.android.data.database.entity.Message

@Database(
    entities = [ChatRoom::class, Message::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
@TypeConverters(APITypeConverter::class)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatRoomDao(): ChatRoomDao
    abstract fun messageDao(): MessageDao
}
