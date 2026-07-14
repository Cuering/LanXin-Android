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
import com.lanxin.android.data.repository.SettingRepository
import com.lanxin.android.presentation.common.LocalDynamicTheme
import com.lanxin.android.presentation.common.LocalThemeMode
import com.lanxin.android.presentation.common.Route
import com.lanxin.android.presentation.common.SetupNavGraph
import com.lanxin.android.presentation.common.ThemeSettingProvider
import com.lanxin.android.presentation.theme.LanXinTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var settingRepository: SettingRepository

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
        val pending = PendingSchedulerChat.peek() ?: return
        val nav = navControllerRef ?: return
        val platforms = runCatching {
            settingRepository.fetchPlatformV2s()
                .filter { it.enabled }
                .map { it.uid }
        }.getOrDefault(emptyList())

        if (platforms.isEmpty()) {
            // 无可用平台时仍保留 pending，进入会话列表由用户配置
            return
        }

        val enabledPlatformString = platforms.joinToString(",")
        nav.navigate(
            Route.CHAT_ROOM
                .replace("{chatRoomId}", "0")
                .replace("{enabledPlatforms}", enabledPlatformString)
        ) {
            launchSingleTop = true
        }
    }

    private fun NavHostController.checkForExistingSettings() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                mainViewModel.event.collect { event ->
                    when (event) {
                        MainViewModel.SplashEvent.OpenIntro -> {
                            navigate(Route.GET_STARTED) {
                                popUpTo(Route.CHAT_LIST) { inclusive = true }
                            }
                        }

                        MainViewModel.SplashEvent.OpenMigrate -> {
                            navigate(Route.MIGRATE_V2) {
                                popUpTo(Route.CHAT_LIST) { inclusive = true }
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}
