package com.lanxin.android.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.lanxin.android.data.context.ContextBuilder
import com.lanxin.android.data.database.dao.ChatPlatformModelV2Dao
import com.lanxin.android.data.database.dao.ChatRoomDao
import com.lanxin.android.data.database.dao.ChatRoomV2Dao
import com.lanxin.android.data.database.dao.MessageDao
import com.lanxin.android.data.database.dao.MessageV2Dao
import com.lanxin.android.data.network.AnthropicAPI
import com.lanxin.android.data.network.GoogleAPI
import com.lanxin.android.data.network.GroqAPI
import com.lanxin.android.data.network.LanXinAPI
import com.lanxin.android.data.network.OpenAIAPI
import com.lanxin.android.data.repository.ChatRepository
import com.lanxin.android.data.repository.ChatRepositoryImpl
import com.lanxin.android.data.repository.SettingRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChatRepositoryModule {

    @Provides
    @Singleton
    fun provideChatRepository(
        @ApplicationContext context: Context,
        chatRoomDao: ChatRoomDao,
        messageDao: MessageDao,
        chatRoomV2Dao: ChatRoomV2Dao,
        messageV2Dao: MessageV2Dao,
        chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
        settingRepository: SettingRepository,
        openAIAPI: OpenAIAPI,
        groqAPI: GroqAPI,
        anthropicAPI: AnthropicAPI,
        googleAPI: GoogleAPI,
        lanXinAPI: LanXinAPI,
        attachmentUploadCoordinator: com.lanxin.android.data.repository.AttachmentUploadCoordinator,
        contextBuilder: ContextBuilder
    ): ChatRepository = ChatRepositoryImpl(
        context,
        chatRoomDao,
        messageDao,
        chatRoomV2Dao,
        messageV2Dao,
        chatPlatformModelV2Dao,
        settingRepository,
        openAIAPI,
        groqAPI,
        anthropicAPI,
        googleAPI,
        lanXinAPI,
        attachmentUploadCoordinator,
        contextBuilder
    )
}
