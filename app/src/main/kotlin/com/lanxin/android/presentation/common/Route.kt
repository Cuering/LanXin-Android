package com.lanxin.android.presentation.common

object Route {

    const val GET_STARTED = "get_started"

    const val SETUP_ROUTE = "setup_route"
    const val SETUP_PLATFORM_LIST = "setup_platform_list"
    const val SETUP_PLATFORM_TYPE = "setup_platform_type"
    const val SETUP_PLATFORM_WIZARD = "setup_platform_wizard"
    const val SETUP_COMPLETE = "setup_complete"

    // Legacy routes (deprecated - kept for reference)
    const val SELECT_PLATFORM = "select_platform"
    const val TOKEN_INPUT = "token_input"
    const val OPENAI_MODEL_SELECT = "openai_model_select"
    const val ANTHROPIC_MODEL_SELECT = "anthropic_model_select"
    const val GOOGLE_MODEL_SELECT = "google_model_select"
    const val GROQ_MODEL_SELECT = "groq_model_select"
    const val OLLAMA_MODEL_SELECT = "ollama_model_select"
    const val OLLAMA_API_ADDRESS = "ollama_api_address"

    const val CHAT_LIST = "chat_list"
    const val CHAT_ROOM = "chat_room/{chatRoomId}?enabled={enabledPlatforms}"

    const val SETTING_ROUTE = "setting_route"
    const val SETTINGS = "settings"
    const val ADD_PLATFORM = "add_platform"
    const val PLATFORM_SETTINGS = "platform_settings/{platformUid}"
    const val OPENAI_SETTINGS = "openai_settings"
    const val ANTHROPIC_SETTINGS = "anthropic_settings"
    const val GOOGLE_SETTINGS = "google_settings"
    const val GROQ_SETTINGS = "groq_settings"
    const val OLLAMA_SETTINGS = "ollama_settings"
    const val ABOUT_PAGE = "about"
    const val LICENSE = "license"

    const val MIGRATE_V2 = "migrate_v2"

    const val MEMORY_LIST = "memory_list"

    const val LOGGER = "logger"

    const val PERSONA_LIST = "persona_list"
    const val PERSONA_CREATE = "persona_create"
    const val PERSONA_EDIT = "persona_edit/{personaId}"

    const val STATISTICS = "statistics"

    const val KNOWLEDGE = "knowledge"

    const val TASK_LIST = "task_list"
    const val TASK_CREATE = "task_create"
    const val TASK_EDIT = "task_edit/{taskId}"

    const val UNIFIED_INBOX = "unified_inbox"
    const val UNIFIED_FILE_BROWSER = "unified_file_browser"
}
