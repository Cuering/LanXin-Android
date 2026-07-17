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

    /** 从聊天引用打开记忆编辑；memoryId 为 Long 字符串。 */
    const val MEMORY_EDIT = "memory_edit/{memoryId}"

    /** 知识条目只读详情（预留路由，可从聊天引用打开）。 */
    const val KNOWLEDGE_DETAIL = "knowledge_detail/{externalId}?snippet={snippet}"

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

    const val UNIFIED_SEARCH = "unified_search"

    const val PLUGIN_MANAGER = "plugin_manager"

    const val PLUGIN_MARKET = "plugin_market"

    const val LOCAL_INFERENCE = "local_inference"

    const val OFFLINE_ASR = "offline_asr"

    /** 桌宠 / 语音陪伴（Phase 6 主线 M1） */
    const val DESKTOP_PET = "desktop_pet"

    /** 系统能力：日历 / 闹钟 / 笔记 / 用户文件（Phase 7） */
    const val SYSTEM_TOOLS = "system_tools"

    /** 联网搜索（WebSearch / web_search 配置） */
    const val WEB_SEARCH = "web_search_settings"

    /** 设备感知（system_info 配置） */
    const val DEVICE_SENSING = "device_sensing_settings"
}
