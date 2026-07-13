package com.lanxin.android.core.updater

/**
 * Semver 版本比较器，参考 AstrBot VersionComparator。
 *
 * 返回 1 表示 v1 > v2，-1 表示 v1 < v2，0 表示相等。
 */
object VersionComparator {

    fun compareVersion(v1: String, v2: String): Int {
        val a = v1.lowercase().replace("v", "")
        val b = v2.lowercase().replace("v", "")

        val (parts1, pre1) = splitVersion(a)
        val (parts2, pre2) = splitVersion(b)

        val length = maxOf(parts1.size, parts2.size)
        val p1 = parts1 + List(length - parts1.size) { 0 }
        val p2 = parts2 + List(length - parts2.size) { 0 }

        for (i in 0 until length) {
            if (p1[i] > p2[i]) return 1
            if (p1[i] < p2[i]) return -1
        }

        if (pre1 == null && pre2 != null) return 1
        if (pre1 != null && pre2 == null) return -1
        if (pre1 != null && pre2 != null) {
            val preLen = maxOf(pre1.size, pre2.size)
            for (i in 0 until preLen) {
                val x = pre1.getOrNull(i)
                val y = pre2.getOrNull(i)
                when {
                    x == null && y != null -> return -1
                    x != null && y == null -> return 1
                    x is Int && y is String -> return -1
                    x is String && y is Int -> return 1
                    x is Int && y is Int -> {
                        if (x > y) return 1
                        if (x < y) return -1
                    }
                    x is String && y is String -> {
                        val cmp = x.compareTo(y)
                        if (cmp != 0) return if (cmp > 0) 1 else -1
                    }
                }
            }
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean =
        compareVersion(candidate, current) > 0

    private fun splitVersion(version: String): Pair<List<Int>, List<Any>?> {
        val regex = Regex(
            """^([0-9]+(?:\.[0-9]+)*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+(.+))?$"""
        )
        val match = regex.matchEntire(version) ?: return emptyList<Int>() to null
        val parts = match.groupValues[1].split(".").map { it.toInt() }
        val prerelease = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
        return parts to splitPrerelease(prerelease)
    }

    private fun splitPrerelease(prerelease: String?): List<Any>? {
        if (prerelease.isNullOrBlank()) return null
        return prerelease.split(".").map { part ->
            part.toIntOrNull() ?: part
        }
    }
}
