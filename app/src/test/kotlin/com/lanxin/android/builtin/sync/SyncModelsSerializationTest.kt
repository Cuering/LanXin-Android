package com.lanxin.android.builtin.sync

import com.lanxin.android.builtin.sync.domain.SyncItem
import com.lanxin.android.builtin.sync.domain.SyncItemType
import com.lanxin.android.builtin.sync.domain.SyncPullRequest
import com.lanxin.android.builtin.sync.domain.SyncPullResponse
import com.lanxin.android.builtin.sync.domain.SyncPushRequest
import com.lanxin.android.builtin.sync.domain.SyncPushResponse
import com.lanxin.android.builtin.sync.domain.SyncSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncModelsSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    @Test
    fun `sync item round trip with snake_case`() {
        val item = SyncItem(
            id = "memory:42",
            type = SyncItemType.MEMORY,
            content = "喜欢草莓",
            updatedAt = 1_700_000_000_000L,
            deleted = false,
            source = SyncSource.ANDROID,
            subtype = "preference",
            importance = 8f,
            metadata = """{"k":1}""",
            createdAt = 1_600_000_000_000L,
            deviceId = "dev-1"
        )
        val encoded = json.encodeToString(SyncItem.serializer(), item)
        assertTrue(encoded.contains("\"updated_at\""))
        assertTrue(encoded.contains("\"device_id\""))
        assertTrue(encoded.contains("喜欢草莓"))

        val decoded = json.decodeFromString(SyncItem.serializer(), encoded)
        assertEquals(item, decoded)
    }

    @Test
    fun `pull request uses protocol field names`() {
        val req = SyncPullRequest(
            deviceId = "d1",
            userId = "u1",
            since = 100L,
            types = listOf("memory"),
            limit = 50
        )
        val encoded = json.encodeToString(SyncPullRequest.serializer(), req)
        assertTrue(encoded.contains("\"device_id\""))
        assertTrue(encoded.contains("\"user_id\""))
        val decoded = json.decodeFromString(SyncPullRequest.serializer(), encoded)
        assertEquals("d1", decoded.deviceId)
        assertEquals(100L, decoded.since)
    }

    @Test
    fun `pull response decodes server payload`() {
        val raw = """
            {
              "items": [
                {
                  "id": "memory:1",
                  "type": "memory",
                  "content": "hi",
                  "updated_at": 123,
                  "deleted": false,
                  "source": "astrbot"
                }
              ],
              "next_cursor": null,
              "server_time": 456,
              "has_more": false
            }
        """.trimIndent()
        val resp = json.decodeFromString(SyncPullResponse.serializer(), raw)
        assertEquals(1, resp.items.size)
        assertEquals("memory:1", resp.items[0].id)
        assertEquals(456L, resp.serverTime)
        assertFalse(resp.hasMore)
        assertNull(resp.nextCursor)
    }

    @Test
    fun `push request and response round trip`() {
        val item = SyncItem(
            id = "memory:9",
            type = SyncItemType.MEMORY,
            content = "x",
            updatedAt = 1L,
            deleted = true,
            source = SyncSource.ANDROID
        )
        val req = SyncPushRequest(deviceId = "d", items = listOf(item))
        val reqJson = json.encodeToString(SyncPushRequest.serializer(), req)
        assertTrue(reqJson.contains("\"deleted\":true"))

        val rawResp = """
            {
              "accepted": 1,
              "rejected": [],
              "applied": [],
              "server_time": 999
            }
        """.trimIndent()
        val resp = json.decodeFromString(SyncPushResponse.serializer(), rawResp)
        assertEquals(1, resp.accepted)
        assertEquals(999L, resp.serverTime)
        assertTrue(resp.rejected.isEmpty())
    }

    @Test
    fun `decode minimal item without optional fields`() {
        val raw = """
            {
              "id": "knowledge:abc",
              "type": "knowledge",
              "content": "chunk",
              "updated_at": 1,
              "deleted": false,
              "source": "astrbot"
            }
        """.trimIndent()
        val item = json.decodeFromString(SyncItem.serializer(), raw)
        assertEquals(SyncItemType.KNOWLEDGE, item.type)
        assertNull(item.importance)
        assertNull(item.subtype)
    }
}
