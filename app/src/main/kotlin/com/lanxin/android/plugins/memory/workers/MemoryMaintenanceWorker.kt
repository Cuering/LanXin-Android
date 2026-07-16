package com.lanxin.android.plugins.memory.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import com.lanxin.android.plugins.memory.domain.memory.DecideResult
import com.lanxin.android.plugins.memory.domain.memory.JudgmentCandidate
import com.lanxin.android.plugins.memory.domain.memory.JudgmentPackage
import com.lanxin.android.plugins.memory.sync.NutstoreSyncProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 记忆维护 Worker：日 01:00 运行
 * - 自适应衰减
 * - 过期清理
 * - 进化索引维护
 * - 对话归档
 */
class MemoryMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val repository = MemoryRepository.getInstance(applicationContext)

    override suspend fun doWork(): Result {
        Timber.d("[MemoryMaintenance] Starting maintenance cycle")

        return withContext(Dispatchers.IO) {
            try {
                // 1. 自适应衰减
                val decayed = repository.applyAdaptiveDecay()
                if (decayed > 0) {
                    Timber.d("[MemoryMaintenance] Decayed $decayed expired memories")
                }

                // 2. 对话归档
                repository.archiveDialogs()
                Timber.d("[MemoryMaintenance] Archived old dialogs")

                // 3. 进化索引检查
                checkEvolutionIndex()

                Timber.d("[MemoryMaintenance] Maintenance cycle completed")
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "[MemoryMaintenance] Maintenance failed")
                Result.failure()
            }
        }
    }

    private suspend fun checkEvolutionIndex() {
        val entries = repository.getEvolutionEntries(10)
        if (entries.isEmpty()) {
            // 首次初始化进化索引
            repository.addEvolutionEntry(
                date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                content = "记忆系统初始化完成，等待首次进化"
            )
        }
    }
}
