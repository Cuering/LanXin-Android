package com.lanxin.android.core.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun `basic semver compare`() {
        assertEquals(1, VersionComparator.compareVersion("1.2.0", "1.1.9"))
        assertEquals(-1, VersionComparator.compareVersion("1.0.0", "1.0.1"))
        assertEquals(0, VersionComparator.compareVersion("v1.0.0", "1.0.0"))
    }

    @Test
    fun `prerelease lower than release`() {
        assertEquals(-1, VersionComparator.compareVersion("1.0.0-alpha", "1.0.0"))
        assertEquals(1, VersionComparator.compareVersion("1.0.0", "1.0.0-rc.1"))
    }

    @Test
    fun `isNewer helper`() {
        assertTrue(VersionComparator.isNewer("0.8.0", "0.7.6"))
        assertTrue(!VersionComparator.isNewer("0.7.6", "0.7.6"))
    }
}
