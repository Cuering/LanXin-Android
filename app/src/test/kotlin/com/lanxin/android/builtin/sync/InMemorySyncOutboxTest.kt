package com.lanxin.android.builtin.sync

import com.lanxin.android.builtin.sync.data.InMemorySyncOutbox
import com.lanxin.android.builtin.sync.domain.SyncItem
import com.lanxin.android.builtin.sync.domain.SyncItemType
import com.lanxin.android.builtin.sync.domain.SyncOutboxOp
import com.lanxin.android.builtin.sync.domain.SyncSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemorySyncOutboxTest {

    private fun item(id: String, content: String = "c") = SyncItem(
        id = id,
        type = SyncItemType.MEMORY,
        content = content,
        updatedAt = 1L,
        source = SyncSource.ANDROID
    )

    @Test
    fun `enqueue and snapshot`() = runBlocking {
        val box = InMemorySyncOutbox()
        box.enqueue(item("memory:1"), SyncOutboxOp.UPSERT)
        box.enqueue(item("memory:2"), SyncOutboxOp.DELETE)
        assertEquals(2, box.snapshot().size)
    }

    @Test
    fun `same itemId overwrites previous`() = runBlocking {
        val box = InMemorySyncOutbox()
        box.enqueue(item("memory:1", "old"), SyncOutboxOp.UPSERT)
        box.enqueue(item("memory:1", "new"), SyncOutboxOp.UPSERT)
        val snap = box.snapshot()
        assertEquals(1, snap.size)
        assertEquals("new", snap[0].payload.content)
    }

    @Test
    fun `removeByItemIds`() = runBlocking {
        val box = InMemorySyncOutbox()
        box.enqueue(item("a"), SyncOutboxOp.UPSERT)
        box.enqueue(item("b"), SyncOutboxOp.UPSERT)
        box.removeByItemIds(listOf("a"))
        assertEquals(listOf("b"), box.snapshot().map { it.itemId })
    }

    @Test
    fun `markAttempt increments`() = runBlocking {
        val box = InMemorySyncOutbox()
        val e = box.enqueue(item("x"), SyncOutboxOp.UPSERT)
        box.markAttempt(e.localId, "boom")
        val again = box.snapshot().single()
        assertEquals(1, again.attempts)
        assertEquals("boom", again.lastError)
        assertTrue(again.localId > 0)
    }
}
