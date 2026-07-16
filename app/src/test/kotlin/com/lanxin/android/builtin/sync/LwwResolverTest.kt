package com.lanxin.android.builtin.sync

import com.lanxin.android.builtin.sync.domain.LwwDecision
import com.lanxin.android.builtin.sync.domain.LwwResolver
import com.lanxin.android.builtin.sync.domain.SyncItem
import com.lanxin.android.builtin.sync.domain.SyncItemType
import com.lanxin.android.builtin.sync.domain.SyncSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LwwResolverTest {

    private fun item(
        id: String = "memory:1",
        type: String = SyncItemType.MEMORY,
        updatedAt: Long,
        deleted: Boolean = false,
        source: String = SyncSource.ANDROID,
        content: String = "body"
    ) = SyncItem(
        id = id,
        type = type,
        content = content,
        updatedAt = updatedAt,
        deleted = deleted,
        source = source
    )

    // ── updated_at ──────────────────────────────────────────

    @Test
    fun `newer updated_at wins`() {
        val local = item(updatedAt = 100)
        val remote = item(updatedAt = 200, content = "remote")
        val winner = LwwResolver.pick(local, remote)
        assertEquals("remote", winner.content)
        assertEquals(200L, winner.updatedAt)
        assertSame(remote, winner)
    }

    @Test
    fun `older remote loses`() {
        val local = item(updatedAt = 300, content = "local")
        val remote = item(updatedAt = 100, content = "remote")
        assertEquals("local", LwwResolver.pick(local, remote).content)
        assertSame(local, LwwResolver.pick(local, remote))
    }

    @Test
    fun `compare orders by updated_at first`() {
        val older = item(updatedAt = 10)
        val newer = item(updatedAt = 20)
        assertTrue(LwwResolver.compare(newer, older) > 0)
        assertTrue(LwwResolver.compare(older, newer) < 0)
    }

    // ── tombstone / 防复活 ──────────────────────────────────

    @Test
    fun `tombstone wins on equal timestamp`() {
        val local = item(updatedAt = 100, deleted = false)
        val remote = item(updatedAt = 100, deleted = true)
        assertTrue(LwwResolver.pick(local, remote).deleted)
        assertSame(remote, LwwResolver.pick(local, remote))
    }

    @Test
    fun `local tombstone wins over remote live on equal timestamp`() {
        val local = item(updatedAt = 100, deleted = true, content = "")
        val remote = item(updatedAt = 100, deleted = false, content = "resurrect")
        val winner = LwwResolver.pick(local, remote)
        assertTrue(winner.deleted)
        assertSame(local, winner)
        assertFalse(LwwResolver.shouldApply(local, remote))
    }

    @Test
    fun `newer live can overwrite older tombstone`() {
        // 防复活仅在 equal timestamp；更新的写入仍可覆盖
        val local = item(updatedAt = 100, deleted = true)
        val remote = item(updatedAt = 200, deleted = false, content = "new-life")
        val winner = LwwResolver.pick(local, remote)
        assertFalse(winner.deleted)
        assertEquals("new-life", winner.content)
    }

    @Test
    fun `compare prefers deleted on equal time`() {
        val live = item(updatedAt = 50, deleted = false)
        val tomb = item(updatedAt = 50, deleted = true)
        assertTrue(LwwResolver.compare(tomb, live) > 0)
        assertTrue(LwwResolver.compare(live, tomb) < 0)
    }

    // ── source 字典序 ───────────────────────────────────────

    @Test
    fun `source lexicographic tie-break`() {
        val local = item(updatedAt = 100, source = "android")
        val remote = item(updatedAt = 100, source = "astrbot", content = "from-bot")
        // "astrbot" > "android"
        assertEquals("from-bot", LwwResolver.pick(local, remote).content)
        assertSame(remote, LwwResolver.pick(local, remote))
    }

    @Test
    fun `source android loses to z-source on equal time and deleted`() {
        val local = item(updatedAt = 100, deleted = false, source = "android", content = "a")
        val remote = item(updatedAt = 100, deleted = false, source = "z-plugin", content = "z")
        assertEquals("z", LwwResolver.pick(local, remote).content)
    }

    @Test
    fun `source loses when local source is greater`() {
        val local = item(updatedAt = 100, source = "plugin-z", content = "L")
        val remote = item(updatedAt = 100, source = "android", content = "R")
        assertEquals("L", LwwResolver.pick(local, remote).content)
        assertFalse(LwwResolver.shouldApply(local, remote))
    }

    // ── preferRemote 全等决胜 ───────────────────────────────

    @Test
    fun `preferRemote on full tie`() {
        val local = item(updatedAt = 100, source = "android", content = "L")
        val remote = item(updatedAt = 100, source = "android", content = "R")
        assertEquals("R", LwwResolver.pick(local, remote, preferRemote = true).content)
        assertEquals("L", LwwResolver.pick(local, remote, preferRemote = false).content)
    }

    @Test
    fun `shouldApply respects preferRemote on full protocol tie`() {
        val existing = item(updatedAt = 100, source = "android", content = "L")
        val candidate = item(updatedAt = 100, source = "android", content = "R")
        assertTrue(LwwResolver.shouldApply(existing, candidate, preferRemote = true))
        assertFalse(LwwResolver.shouldApply(existing, candidate, preferRemote = false))
    }

    @Test
    fun `compare returns zero on full protocol tie`() {
        val a = item(updatedAt = 100, deleted = false, source = "android", content = "x")
        val b = item(updatedAt = 100, deleted = false, source = "android", content = "y")
        assertEquals(0, LwwResolver.compare(a, b))
    }

    // ── shouldApply / decide ────────────────────────────────

    @Test
    fun `shouldApply null existing`() {
        assertTrue(LwwResolver.shouldApply(null, item(updatedAt = 1)))
        assertEquals(LwwDecision.APPLY_NEW, LwwResolver.decide(null, item(updatedAt = 1)))
    }

    @Test
    fun `shouldApply false when local newer`() {
        val existing = item(updatedAt = 500, content = "local")
        val candidate = item(updatedAt = 100, content = "remote")
        assertFalse(LwwResolver.shouldApply(existing, candidate))
        assertEquals(LwwDecision.SKIP, LwwResolver.decide(existing, candidate))
    }

    @Test
    fun `decide APPLY when remote newer`() {
        val existing = item(updatedAt = 100, content = "local")
        val candidate = item(updatedAt = 200, content = "remote")
        assertEquals(LwwDecision.APPLY, LwwResolver.decide(existing, candidate))
        assertTrue(LwwResolver.shouldApply(existing, candidate))
    }

    @Test
    fun `isConflictResolution true when remote overwrites different local`() {
        val existing = item(updatedAt = 100, content = "old")
        val candidate = item(updatedAt = 200, content = "new")
        assertTrue(LwwResolver.isConflictResolution(existing, candidate))
    }

    @Test
    fun `isConflictResolution false for brand new`() {
        assertFalse(LwwResolver.isConflictResolution(null, item(updatedAt = 1)))
    }

    @Test
    fun `isConflictResolution false when skipped`() {
        val existing = item(updatedAt = 500, content = "local")
        val candidate = item(updatedAt = 100, content = "remote")
        assertFalse(LwwResolver.isConflictResolution(existing, candidate))
    }

    // ── mergeById（memory + knowledge 列表层） ──────────────

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

    @Test
    fun `mergeById knowledge type LWW at list layer`() {
        val local = listOf(
            item(
                id = "knowledge:doc1",
                type = SyncItemType.KNOWLEDGE,
                updatedAt = 100,
                content = "chunk-local",
                source = SyncSource.ANDROID
            )
        )
        val remote = listOf(
            item(
                id = "knowledge:doc1",
                type = SyncItemType.KNOWLEDGE,
                updatedAt = 100,
                content = "chunk-remote",
                source = SyncSource.ASTRBOT
            ),
            item(
                id = "knowledge:doc2",
                type = SyncItemType.KNOWLEDGE,
                updatedAt = 50,
                content = "new-chunk",
                source = SyncSource.ASTRBOT
            )
        )
        val merged = LwwResolver.mergeById(local, remote)
        assertEquals(2, merged.size)
        // equal time → source astrbot > android
        assertEquals("chunk-remote", merged.first { it.id == "knowledge:doc1" }.content)
        assertEquals("new-chunk", merged.first { it.id == "knowledge:doc2" }.content)
    }

    @Test
    fun `mergeById tombstone prevents resurrection in list`() {
        val local = listOf(
            item(id = "memory:9", updatedAt = 200, deleted = true, content = "")
        )
        val remote = listOf(
            item(id = "memory:9", updatedAt = 200, deleted = false, content = "ghost")
        )
        val merged = LwwResolver.mergeById(local, remote)
        assertEquals(1, merged.size)
        assertTrue(merged[0].deleted)
    }

    @Test
    fun `mergeById preferRemote false keeps local on full tie`() {
        val local = listOf(item(id = "x", updatedAt = 1, source = "android", content = "L"))
        val remote = listOf(item(id = "x", updatedAt = 1, source = "android", content = "R"))
        val merged = LwwResolver.mergeById(local, remote, preferRemote = false)
        assertEquals("L", merged.single().content)
    }

    // ── id 不一致 ───────────────────────────────────────────

    @Test
    fun `pick returns remote when ids differ`() {
        val local = item(id = "memory:1", updatedAt = 999, content = "L")
        val remote = item(id = "memory:2", updatedAt = 1, content = "R")
        assertSame(remote, LwwResolver.pick(local, remote))
    }
}
