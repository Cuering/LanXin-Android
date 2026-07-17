package com.lanxin.android.data.network

import com.lanxin.android.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderModelListSupportTest {

    @Test
    fun `openai-compatible types support remote model list`() {
        assertTrue(ProviderModelListSupport.supportsOpenAiCompatibleModelList(ClientType.OPENAI))
        assertTrue(ProviderModelListSupport.supportsOpenAiCompatibleModelList(ClientType.GROQ))
        assertTrue(ProviderModelListSupport.supportsOpenAiCompatibleModelList(ClientType.OLLAMA))
        assertTrue(ProviderModelListSupport.supportsOpenAiCompatibleModelList(ClientType.OPENROUTER))
        assertTrue(ProviderModelListSupport.supportsOpenAiCompatibleModelList(ClientType.CUSTOM))
    }

    @Test
    fun `non openai types do not support remote model list`() {
        assertFalse(ProviderModelListSupport.supportsOpenAiCompatibleModelList(ClientType.ANTHROPIC))
        assertFalse(ProviderModelListSupport.supportsOpenAiCompatibleModelList(ClientType.GOOGLE))
        assertFalse(ProviderModelListSupport.supportsOpenAiCompatibleModelList(ClientType.LANXIN))
    }

    @Test
    fun `chat routing map matches repository openai branches`() {
        assertTrue(ProviderModelListSupport.usesOpenAiCompatibleChat(ClientType.OPENAI))
        assertTrue(ProviderModelListSupport.usesOpenAiCompatibleChat(ClientType.CUSTOM))
        assertFalse(ProviderModelListSupport.usesOpenAiCompatibleChat(ClientType.ANTHROPIC))
        assertFalse(ProviderModelListSupport.usesOpenAiCompatibleChat(ClientType.GOOGLE))
    }

    @Test
    fun `models endpoint normalizes trailing slash`() {
        assertEquals(
            "https://api.example.com/v1/models",
            ProviderModelListSupport.modelsEndpoint("https://api.example.com/v1/")
        )
        assertEquals(
            "https://api.example.com/v1/models",
            ProviderModelListSupport.modelsEndpoint("https://api.example.com/v1")
        )
        assertEquals("", ProviderModelListSupport.modelsEndpoint("  "))
    }

    @Test
    fun `normalizeModelIds sorts case-insensitively and dedupes`() {
        assertEquals(
            listOf("A", "b", "c"),
            ProviderModelListSupport.normalizeModelIds(listOf("b", "A", "a", "c", " "))
        )
    }
}
