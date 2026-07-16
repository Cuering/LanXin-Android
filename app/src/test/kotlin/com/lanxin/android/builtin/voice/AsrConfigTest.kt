package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.domain.AsrConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AsrConfigTest {

    @Test
    fun `defaults are safe`() {
        val c = AsrConfig()
        assertFalse(c.enabled)
        assertEquals("", c.modelPath)
        assertEquals(AsrConfig.DEFAULT_LANGUAGE, c.language)
        assertEquals(AsrConfig.DEFAULT_SAMPLE_RATE_HZ, c.sampleRateHz)
    }

    @Test
    fun `sample rate bounds ordered`() {
        assert(AsrConfig.MIN_SAMPLE_RATE_HZ < AsrConfig.DEFAULT_SAMPLE_RATE_HZ)
        assert(AsrConfig.DEFAULT_SAMPLE_RATE_HZ < AsrConfig.MAX_SAMPLE_RATE_HZ)
    }
}
