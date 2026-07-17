package com.lanxin.android.builtin.knowledge

import com.lanxin.android.builtin.knowledge.data.sparse.SparseFtsContentEntity
import com.lanxin.android.builtin.knowledge.data.sparse.SparseFtsDao
import com.lanxin.android.builtin.knowledge.data.sparse.SparseFtsHit
import com.lanxin.android.builtin.knowledge.data.sparse.SparseStore
import com.lanxin.android.builtin.knowledge.domain.EmbeddingService
import com.lanxin.android.builtin.knowledge.domain.MarkdownChunker
import com.lanxin.android.builtin.knowledge.domain.TextChunker
import com.lanxin.android.builtin.knowledge.domain.VectorPipeline
import com.lanxin.android.builtin.knowledge.domain.VectorStore
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgePluginTest {

    @Test
    fun `kb_chunk defaults to text mode`() = runBlocking {
        val tools = registerTools()

        val result = tools.getValue("kb_chunk").handler(
            buildJsonObject {
                put("text", "alpha beta gamma")
            }
        )

        assertEquals("text", result["format"]?.jsonPrimitive?.contentOrNull)
        val chunks = result["chunks"]?.jsonArray ?: JsonArray(emptyList())
        assertEquals(1, chunks.size)
        val first = chunks.first().jsonObject
        assertEquals("alpha beta gamma", first["preview"]?.jsonPrimitive?.contentOrNull)
        assertFalse(first.containsKey("heading"))
        assertFalse(first.containsKey("heading_path"))
    }

    @Test
    fun `kb_chunk supports markdown format with heading metadata`() = runBlocking {
        val tools = registerTools()
        val markdown = """
            # Root
            Intro line.
            
            ## Child
            Child body line 1.
            Child body line 2.
        """.trimIndent()

        val result = tools.getValue("kb_chunk").handler(
            buildJsonObject {
                put("text", markdown)
                put("format", "markdown")
            }
        )

        assertEquals("markdown", result["format"]?.jsonPrimitive?.contentOrNull)
        val chunks = result["chunks"]?.jsonArray ?: JsonArray(emptyList())
        assertTrue(chunks.size >= 2)

        val root = chunks[0].jsonObject
        assertEquals("Root", root["heading"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Root", root["heading_path"]?.jsonPrimitive?.contentOrNull)

        val child = chunks[1].jsonObject
        assertEquals("Child", child["heading"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Root > Child", child["heading_path"]?.jsonPrimitive?.contentOrNull)
        assertTrue(child["preview"]?.jsonPrimitive?.contentOrNull.orEmpty().contains("Root"))
    }

    @Test
    fun `kb_chunk markdown can disable heading context injection`() = runBlocking {
        val tools = registerTools()
        val markdown = """
            # Root
            Intro line.
            
            ## Child
            Child body line 1.
            Child body line 2.
        """.trimIndent()

        val result = tools.getValue("kb_chunk").handler(
            buildJsonObject {
                put("text", markdown)
                put("format", "markdown")
                put("include_heading_context", false)
            }
        )

        val chunks = result["chunks"]?.jsonArray ?: JsonArray(emptyList())
        val child = chunks[1].jsonObject
        assertEquals("Child", child["heading"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Root > Child", child["heading_path"]?.jsonPrimitive?.contentOrNull)
        assertFalse(child["preview"]?.jsonPrimitive?.contentOrNull.orEmpty().startsWith("Root\n\n"))
        assertTrue(child["preview"]?.jsonPrimitive?.contentOrNull.orEmpty().startsWith("## Child"))
    }

    private suspend fun registerTools(): Map<String, ToolDef> {
        val embedding = FakeEmbeddingService()
        val vectorStore = FakeVectorStore()
        val plugin = KnowledgePlugin(
            embeddingService = embedding,
            vectorStore = vectorStore,
            pipeline = VectorPipeline(embedding, vectorStore, SparseStore(FakeSparseFtsDao())),
            textChunker = TextChunker(),
            markdownChunker = MarkdownChunker(TextChunker())
        )
        val tools = linkedMapOf<String, ToolDef>()
        plugin.onLoad(object : PluginContext {
            override fun registerTool(tool: ToolDef) {
                tools[tool.name] = tool
            }

            override val filesDir: File =
                File(System.getProperty("java.io.tmpdir"), "knowledge-plugin-test").also { it.mkdirs() }

            override suspend fun sendMessage(message: String) = Unit
        })
        return tools
    }
}

private class FakeEmbeddingService : EmbeddingService {
    override val dimensions: Int = 384
    override val isReady: Boolean = true
    override suspend fun embed(text: String): FloatArray = FloatArray(dimensions) { 0f }
}

private class FakeVectorStore : VectorStore {
    override suspend fun upsert(externalId: Long, source: String, embedding: FloatArray, textPreview: String): Long = 1L
    override suspend fun delete(id: Long) = Unit
    override suspend fun deleteByExternal(externalId: Long, source: String) = Unit
    override suspend fun deleteBySource(source: String) = Unit
    override suspend fun search(query: FloatArray, topK: Int, source: String?): List<com.lanxin.android.builtin.knowledge.domain.VectorHit> = emptyList()
    override suspend fun count(source: String?): Long = 0L
    override suspend fun clear() = Unit
}

private class FakeSparseFtsDao : SparseFtsDao {
    override suspend fun upsertContent(entity: SparseFtsContentEntity): Long = entity.rowId
    override suspend fun deleteByDocument(documentId: Long, source: String) = Unit
    override suspend fun deleteBySource(source: String) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun searchFts(matchQuery: String, limit: Int): List<SparseFtsHit> = emptyList()
    override suspend fun searchFtsBySource(matchQuery: String, source: String, limit: Int): List<SparseFtsHit> = emptyList()
    override suspend fun count(): Long = 0L
    override suspend fun countBySource(source: String): Long = 0L
    override suspend fun getAllContent(): List<SparseFtsContentEntity> = emptyList()
    override suspend fun getContentBySource(source: String): List<SparseFtsContentEntity> = emptyList()
    override suspend fun getByDocument(documentId: Long, source: String): SparseFtsContentEntity? = null
}
