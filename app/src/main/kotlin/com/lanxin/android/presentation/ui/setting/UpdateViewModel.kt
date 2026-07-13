package com.lanxin.android.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.core.updater.ApkDownloader
import com.lanxin.android.core.updater.DataBackupManager
import com.lanxin.android.core.updater.DownloadProgress
import com.lanxin.android.core.updater.ReleaseInfo
import com.lanxin.android.core.updater.UpdateCheckResult
import com.lanxin.android.core.updater.UpdateChecker
import com.lanxin.android.plugin.PluginManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    private val apkDownloader: ApkDownloader,
    private val dataBackupManager: DataBackupManager,
    private val pluginManager: PluginManager
) : ViewModel() {

    data class UiState(
        val isChecking: Boolean = false,
        val isDownloading: Boolean = false,
        val downloadPercent: Int = -1,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val currentVersion: String = "",
        val releases: List<ReleaseInfo> = emptyList(),
        val selectedRelease: ReleaseInfo? = null,
        val showVersionDialog: Boolean = false,
        val showConfirmDialog: Boolean = false,
        val showProgressDialog: Boolean = false,
        val message: String? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState(currentVersion = updateChecker.currentVersionName()))
    val uiState = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    fun checkUpdates() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isChecking = true, error = null, message = null)
            }
            try {
                when (val result = updateChecker.checkLatest(includePrerelease = true)) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                currentVersion = result.currentVersion,
                                releases = result.all,
                                showVersionDialog = true,
                                message = "发现新版本 ${result.latest.tagName}"
                            )
                        }
                    }
                    is UpdateCheckResult.UpToDate -> {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                currentVersion = result.currentVersion,
                                releases = result.all,
                                showVersionDialog = true,
                                message = "已是最新版本"
                            )
                        }
                    }
                    is UpdateCheckResult.NoRelease -> {
                        _uiState.update {
                            it.copy(
                                isChecking = false,
                                currentVersion = result.currentVersion,
                                message = "暂无可用 Release"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isChecking = false, error = e.message ?: "检查更新失败")
                }
            }
        }
    }

    fun openVersionDialog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, error = null) }
            try {
                val releases = updateChecker.fetchReleases(includePrerelease = true)
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        currentVersion = updateChecker.currentVersionName(),
                        releases = releases,
                        showVersionDialog = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isChecking = false, error = e.message ?: "获取版本列表失败")
                }
            }
        }
    }

    fun onReleaseSelected(release: ReleaseInfo) {
        _uiState.update {
            it.copy(
                selectedRelease = release,
                showVersionDialog = false,
                showConfirmDialog = true
            )
        }
    }

    fun dismissDialogs() {
        _uiState.update {
            it.copy(
                showVersionDialog = false,
                showConfirmDialog = false,
                showProgressDialog = false
            )
        }
    }

    fun confirmAndInstall() {
        val release = _uiState.value.selectedRelease ?: return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showConfirmDialog = false,
                    showProgressDialog = true,
                    isDownloading = true,
                    downloadPercent = 0,
                    error = null,
                    message = "正在备份数据…"
                )
            }
            try {
                val pluginVersions = pluginManager.getPlugins().associate { it.id to it.version }
                dataBackupManager.createBackup(pluginVersions)

                _uiState.update { it.copy(message = "正在下载 ${release.apkName}…") }
                apkDownloader.download(release.apkUrl, release.apkName).collect { progress ->
                    when (progress) {
                        is DownloadProgress.Started -> {
                            _uiState.update { it.copy(downloadPercent = 0) }
                        }
                        is DownloadProgress.Progress -> {
                            _uiState.update {
                                it.copy(
                                    downloadPercent = progress.percent,
                                    downloadedBytes = progress.downloaded,
                                    totalBytes = progress.total
                                )
                            }
                        }
                        is DownloadProgress.Completed -> {
                            _uiState.update {
                                it.copy(
                                    isDownloading = false,
                                    showProgressDialog = false,
                                    message = "下载完成，正在打开安装器…"
                                )
                            }
                            apkDownloader.installApk(progress.file)
                        }
                        is DownloadProgress.Failed -> {
                            _uiState.update {
                                it.copy(
                                    isDownloading = false,
                                    showProgressDialog = false,
                                    error = progress.message
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        showProgressDialog = false,
                        error = e.message ?: "更新失败"
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _uiState.update {
            it.copy(isDownloading = false, showProgressDialog = false, message = "已取消下载")
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}
