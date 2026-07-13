package com.lanxin.android.core.updater

import android.content.Context
import android.os.Build
import com.lanxin.android.data.network.NetworkClient
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 通过 GitHub Releases 检查版本。
 */
@Singleton
class UpdateChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkClient: NetworkClient
) {
    private val httpClient: HttpClient get() = networkClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    var owner: String = DEFAULT_OWNER
    var repo: String = DEFAULT_REPO

    fun currentVersionName(): String = try {
        val pm = context.packageManager
        val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        pkg.versionName ?: "0.0.0"
    } catch (_: Exception) {
        "0.0.0"
    }

    suspend fun fetchReleases(includePrerelease: Boolean = true): List<ReleaseInfo> {
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=30"
        val body: String = httpClient.get(url) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }.bodyAsText()
        val releases = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GitHubReleaseDto.serializer()), body)
        return releases
            .filter { !it.draft }
            .filter { includePrerelease || !it.prerelease }
            .mapNotNull { dto ->
                val apk = dto.assets.firstOrNull { asset ->
                    asset.name.endsWith(".apk", ignoreCase = true) &&
                        asset.browserDownloadUrl.isNotBlank()
                } ?: return@mapNotNull null
                ReleaseInfo(
                    tagName = dto.tagName,
                    name = dto.name ?: dto.tagName,
                    body = dto.body.orEmpty(),
                    publishedAt = dto.publishedAt.orEmpty(),
                    prerelease = dto.prerelease,
                    apkName = apk.name,
                    apkUrl = apk.browserDownloadUrl,
                    apkSize = apk.size
                )
            }
            .sortedWith { a, b -> VersionComparator.compareVersion(b.tagName, a.tagName) }
    }

    suspend fun checkLatest(includePrerelease: Boolean = false): UpdateCheckResult {
        val current = currentVersionName()
        val releases = fetchReleases(includePrerelease)
        val latest = releases.firstOrNull()
            ?: return UpdateCheckResult.NoRelease(current)
        return if (VersionComparator.isNewer(latest.tagName, current)) {
            UpdateCheckResult.UpdateAvailable(current, latest, releases)
        } else {
            UpdateCheckResult.UpToDate(current, latest, releases)
        }
    }

    companion object {
        // 可在设置中覆盖；默认占位，用户可改成实际仓库
        const val DEFAULT_OWNER = "lanxin-ai"
        const val DEFAULT_REPO = "LanXin-Android"
    }
}

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val prerelease: Boolean,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long
)

sealed class UpdateCheckResult {
    abstract val currentVersion: String

    data class UpdateAvailable(
        override val currentVersion: String,
        val latest: ReleaseInfo,
        val all: List<ReleaseInfo>
    ) : UpdateCheckResult()

    data class UpToDate(
        override val currentVersion: String,
        val latest: ReleaseInfo?,
        val all: List<ReleaseInfo>
    ) : UpdateCheckResult()

    data class NoRelease(override val currentVersion: String) : UpdateCheckResult()
}

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
private data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0
)
