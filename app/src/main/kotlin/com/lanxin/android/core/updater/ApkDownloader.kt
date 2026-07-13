package com.lanxin.android.core.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.lanxin.android.data.network.NetworkClient
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * APK 下载器，支持进度回调，并触发系统安装器。
 */
@Singleton
class ApkDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkClient: NetworkClient
) {
    private val httpClient: HttpClient get() = networkClient()
    fun download(url: String, fileName: String = "lanxin-update.apk"): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Started)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, fileName)
        if (target.exists()) target.delete()

        httpClient.prepareGet(url).execute { response ->
            val total = response.contentLength() ?: -1L
            val channel: ByteReadChannel = response.bodyAsChannel()
            var downloaded = 0L
            val buffer = ByteArray(DEFAULT_BUFFER)

            FileOutputStream(target).use { out ->
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                    emit(DownloadProgress.Progress(downloaded, total, percent))
                }
            }
            emit(DownloadProgress.Completed(target))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun installApk(apkFile: File) = withContext(Dispatchers.Main) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    companion object {
        private const val DEFAULT_BUFFER = 8 * 1024
    }
}

sealed class DownloadProgress {
    data object Started : DownloadProgress()
    data class Progress(val downloaded: Long, val total: Long, val percent: Int) : DownloadProgress()
    data class Completed(val file: File) : DownloadProgress()
    data class Failed(val message: String) : DownloadProgress()
}
