package com.lanxin.android.data.model

enum class ClientType {
    OPENAI,
    ANTHROPIC,
    GOOGLE,
    GROQ,
    OPENROUTER,
    OLLAMA,
    CUSTOM,
    LANXIN,

    /** MNNChat OpenAI-compatible local API (phone :8080/v1) */
    MNNCHAT
}
