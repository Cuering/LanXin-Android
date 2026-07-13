package com.lanxin.android.plugins.chat.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lanxin.android.plugins.chat.data.entity.Message

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chat_id=:chatInt")
    suspend fun loadMessages(chatInt: Int): List<Message>

    @Insert
    suspend fun addMessages(vararg messages: Message)

    @Update
    suspend fun editMessages(vararg message: Message)

    @Delete
    suspend fun deleteMessages(vararg message: Message)
}
