package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalInferenceConfigTest {

    @Test
    fun `defaults are safe`() {
        val c = LocalInferenceConfig()
        assertFalse(c.enabled)
        assertEquals("", c.modelPath)
        assertEquals(LocalInferenceConfig.DEFAULT_MAX_TOKENS, c.maxTokens)
        assertEquals(LocalInferenceConfig.DEFAULT_TEMPERATURE, c.temperature, 0.001f)
        assertFalse("showThinking 必须默认关", c.showThinking)
        assertEquals(
            LocalInferenceConfig.DEFAULT_CONTEXT_WINDOW_TOKENS,
            c.contextWindowTokens
        )
    }

    @Test
    fun `token bounds constants ordered`() {
        assert(LocalInferenceConfig.MIN_MAX_TOKENS < LocalInferenceConfig.DEFAULT_MAX_TOKENS)
        assert(LocalInferenceConfig.DEFAULT_MAX_TOKENS < LocalInferenceConfig.MAX_MAX_TOKENS)
        assert(LocalInferenceConfig.MIN_CONTEXT_WINDOW_TOKENS
            < LocalInferenceConfig.DEFAULT_CONTEXT_WINDOW_TOKENS)
        assert(LocalInferenceConfig.DEFAULT_CONTEXT_WINDOW_TOKENS
            < LocalInferenceConfig.MAX_CONTEXT_WINDOW_TOKENS)
        // 生成上限与上下文窗口分离：生成 max < 默认 ctx
        assert(LocalInferenceConfig.MAX_MAX_TOKENS
            < LocalInferenceConfig.DEFAULT_CONTEXT_WINDOW_TOKENS)
    }
}
