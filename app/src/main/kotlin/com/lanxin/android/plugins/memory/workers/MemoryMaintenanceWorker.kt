package com.lanxin.android.plugins.memory.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lanxin.android.plugins.memory.data.memory.MemoryDatabase
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记忆维护 Worker：日维护
 * - 自适应衰减
 * - 过期清理
 * - 进化索引维护
 * - 对话归档
 */
class MemoryMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val dao = MemoryDatabase.getInstance(applicationContext).memoryDao()
                val repository = MemoryRepository(dao)

                val decayed = repository.applyAdaptiveDecay()
                repository.archiveDialogs()

                val entries = repository.getEvolutionEntries(10)
                if (entries.isEmpty()) {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    repository.addEvolutionEntry(
                        date = today,
                        content = "记忆系统初始化完成，等待首次进化"
                    )
                }

                Result.success()
            } catch (_: Exception) {
                Result.failure()
            }
        }
    }
}
