package com.lanxin.android.builtin.sync

import com.lanxin.android.builtin.sync.domain.LwwResolver
import com.lanxin.android.builtin.sync.domain.SyncItem
import com.lanxin.android.builtin.sync.domain.SyncItemType
import com.lanxin.android.builtin.sync.domain.SyncSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LwwResolverTest {

    private fun item(
        id: String = "memory:1",
        updatedAt: Long,
        deleted: Boolean = false,
        source: String = SyncSource.ANDROID,
        content: String = "body"
    ) = SyncItem(
        id = id,
        type = SyncItemType.MEMORY,
        content = content,
        updatedAt = updatedAt,
        deleted = deleted,
        source = source
    )

    @Test
    fun `newer updated_at wins`() {
        val local = item(updatedAt = 100)
        val remote = item(updatedAt = 200, content = "remote")
        val winner = LwwResolver.pick(local, remote)
        assertEquals("remote", winner.content)
        assertEquals(200L, winner.updatedAt)
    }

    @Test
    fun `older remote loses`() {
        val local = item(updatedAt = 300, content = "local")
        val remote = item(updatedAt = 100, content = "remote")
        assertEquals("local", LwwResolver.pick(local, remote).content)
    }

    @Test
    fun `tombstone wins on equal timestamp`() {
        val local = item(updatedAt = 100, deleted = false)
        val remote = item(updatedAt = 100, deleted = true)
        assertTrue(LwwResolver.pick(local, remote).deleted)
    }

    @Test
    fun `source lexicographic tie-break`() {
        val local = item(updatedAt = 100, source = "android")
        val remote = item(updatedAt = 100, source = "astrbot", content = "from-bot")
        // "astrbot" > "android"
        assertEquals("from-bot", LwwResolver.pick(local, remote).content)
    }

    @Test
    fun `preferRemote on full tie`() {
        val local = item(updatedAt = 100, source = "android", content = "L")
        val remote = item(updatedAt = 100, source = "android", content = "R")
        assertEquals("R", LwwResolver.pick(local, remote, preferRemote = true).content)
        assertEquals("L", LwwResolver.pick(local, remote, preferRemote = false).content)
    }

    @Test
    fun `shouldApply null existing`() {
        assertTrue(LwwResolver.shouldApply(null, item(updatedAt = 1)))
    }

    @Test
    fun `shouldApply false when local newer`() {
        val existing = item(updatedAt = 500, content = "local")
        val candidate = item(updatedAt = 100, content = "remote")
        assertFalse(LwwResolver.shouldApply(existing, candidate))
    }

    @Test
    fun `mergeById combines lists`() {
        val local = listOf(
            item(id = "a", updatedAt = 10, content = "la"),
            item(id = "b", updatedAt = 50, content = "lb")
        )
        val remote = listOf(
            item(id = "a", updatedAt = 20, content = "ra"),
            item(id = "c", updatedAt = 1, content = "rc")
        )
        val merged = LwwResolver.mergeById(local, remote)
        assertEquals(3, merged.size)
        assertEquals("ra", merged.first { it.id == "a" }.content)
        assertEquals("lb", merged.first { it.id == "b" }.content)
        assertEquals("rc", merged.first { it.id == "c" }.content)
    }
}
