package com.lanxin.android.presentation.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.lanxin.android.builtin.scheduler.domain.PendingSchedulerChat
import com.lanxin.android.builtin.scheduler.worker.SchedulerTaskWorker
import com.lanxin.android.presentation.common.LocalDynamicTheme
import com.lanxin.android.presentation.common.LocalThemeMode
import com.lanxin.android.presentation.common.Route
import com.lanxin.android.presentation.common.SetupNavGraph
import com.lanxin.android.presentation.common.ThemeSettingProvider
import com.lanxin.android.presentation.theme.LanXinTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private var navControllerRef: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                !mainViewModel.isReady.value
            }
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Prevent keyboard from pushing the entire view up - composable handles insets via imePadding()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        handleSchedulerIntent(intent)

        setContent {
            val navController = rememberNavController()
            navControllerRef = navController
            navController.checkForExistingSettings()

            ThemeSettingProvider {
                LanXinTheme(
                    dynamicTheme = LocalDynamicTheme.current,
                    themeMode = LocalThemeMode.current
                ) {
                    SetupNavGraph(navController)
                }
            }
        }

        // 启动完成后若有 pending scheduler chat，导航到新会话
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                maybeNavigateToSchedulerChat()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSchedulerIntent(intent)
        lifecycleScope.launch {
            maybeNavigateToSchedulerChat()
        }
    }

    private fun handleSchedulerIntent(intent: Intent?) {
        if (intent == null) return
        val isScheduler = intent.action == SchedulerTaskWorker.ACTION_SCHEDULER_CHAT ||
            intent.hasExtra(SchedulerTaskWorker.EXTRA_PROMPT)
        if (!isScheduler) return

        val prompt = intent.getStringExtra(SchedulerTaskWorker.EXTRA_PROMPT).orEmpty()
        if (prompt.isBlank()) return
        val taskId = intent.getStringExtra(SchedulerTaskWorker.EXTRA_TASK_ID).orEmpty()
        val autoStart = intent.getBooleanExtra(SchedulerTaskWorker.EXTRA_AUTO_START, true)
        PendingSchedulerChat.set(
            PendingSchedulerChat.Request(
                taskId = taskId,
                prompt = prompt,
                autoStart = autoStart
            )
        )
        // 避免重复消费同一 intent
        intent.replaceExtras(Bundle())
        intent.action = null
    }

    private suspend fun maybeNavigateToSchedulerChat() {
        // 有定时任务意图时，直接确保落在全屏陪伴（不再开会话页）
        PendingSchedulerChat.peek() ?: return
        val nav = navControllerRef ?: return
        nav.navigate(Route.COMPANION) {
            launchSingleTop = true
            popUpTo(Route.COMPANION) { inclusive = false }
        }
    }

    private fun NavHostController.checkForExistingSettings() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                mainViewModel.event.collect { event ->
                    when (event) {
                        // 启动页已是 COMPANION；仅迁移需要额外跳转
                        MainViewModel.SplashEvent.OpenMigrate -> {
                            navigate(Route.MIGRATE_V2) {
                                popUpTo(Route.COMPANION) { inclusive = false }
                            }
                        }
                        else -> {
                            // OpenIntro / OpenHome：留在全屏陪伴
                        }
                    }
                }
            }
        }
    }
}
