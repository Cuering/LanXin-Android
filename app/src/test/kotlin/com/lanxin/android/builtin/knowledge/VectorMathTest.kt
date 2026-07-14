package com.lanxin.android.builtin.knowledge

import com.lanxin.android.builtin.knowledge.data.EmbeddingConstants
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VectorMathTest {

    @Test
    fun `l2 normalize produces unit vector`() {
        val v = floatArrayOf(3f, 4f)  // norm = 5
        val normalized = normalize(v)
        assertArrayEquals(
            floatArrayOf(3f / 5, 4f / 5),
            normalized,
            1e-6f
        )
        var norm = 0.0
        for (x in normalized) norm += x * x
        assertEquals(1.0, sqrt(norm), 1e-6)
    }

    @Test
    fun `zero vector stays zero`() {
        val v = floatArrayOf(0f, 0f, 0f)
        val normalized = normalize(v)
        assertArrayEquals(v, normalized, 1e-6f)
    }

    @Test
    fun `small vector does not explode`() {
        val v = floatArrayOf(1e-10f, 2e-10f)
        val normalized = normalize(v)
        var norm = 0.0
        for (x in normalized) norm += x * x
        // should be close to unit
        assertEquals(1.0, sqrt(norm), 1e-6)
    }

    @Test
    fun `dimensions constant matches expectation`() {
        assertEquals(384, EmbeddingConstants.DIMENSIONS)
    }

    @Test
    fun `mean pooling with mask`() {
        // Simulate a mean pooling: [1, 4] seq, 3 dims
        val hidden = arrayOf(
            floatArrayOf(1f, 2f, 3f),
            floatArrayOf(4f, 5f, 6f),
            floatArrayOf(7f, 8f, 9f),
            floatArrayOf(10f, 11f, 12f)
        )
        val mask = longArrayOf(1L, 1L, 1L, 0L)  // last token masked
        val sum = FloatArray(3)
        var count = 0f
        for (i in hidden.indices) {
            if (mask[i] == 0L) continue
            for (d in 0 until 3) sum[d] += hidden[i][d]
            count += 1f
        }
        for (d in 0 until 3) sum[d] /= count
        // Expected: (1+4+7)/3=4, (2+5+8)/3=5, (3+6+9)/3=6
        assertArrayEquals(floatArrayOf(4f, 5f, 6f), sum, 1e-6f)
    }

    @Test
    fun `cosine similarity between identical vectors is 1`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        val cos = cosineSimilarity(a, b)
        assertEquals(1f, cos, 1e-6f)
    }

    @Test
    fun `cosine similarity of orthogonal vectors is 0`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val cos = cosineSimilarity(a, b)
        assertEquals(0f, cos, 1e-6f)
    }

    private fun normalize(v: FloatArray): FloatArray {
        var norm = 0.0
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm < 1e-12) return v
        return FloatArray(v.size) { (v[it] / norm).toFloat() }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i].toDouble()
            na += a[i] * a[i].toDouble()
            nb += b[i] * b[i].toDouble()
        }
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }
}
