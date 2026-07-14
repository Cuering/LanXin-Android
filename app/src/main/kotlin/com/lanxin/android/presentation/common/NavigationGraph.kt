package com.lanxin.android.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.lanxin.android.builtin.persona.presentation.PersonaEditScreen
import com.lanxin.android.builtin.persona.presentation.PersonaListScreen
import com.lanxin.android.builtin.scheduler.presentation.TaskEditScreen
import com.lanxin.android.builtin.scheduler.presentation.TaskListScreen
import com.lanxin.android.builtin.statistics.presentation.StatisticsScreen
import com.lanxin.android.plugins.logger.presentation.ui.LoggerScreen
import com.lanxin.android.plugins.memory.presentation.ui.memory.MemoryScreen
import com.lanxin.android.presentation.ui.chat.ChatScreen
import com.lanxin.android.presentation.ui.home.HomeScreen
import com.lanxin.android.presentation.ui.migrate.MigrateScreen
import com.lanxin.android.presentation.ui.setting.AboutScreen
import com.lanxin.android.presentation.ui.setting.AddPlatformScreen
import com.lanxin.android.presentation.ui.setting.LicenseScreen
import com.lanxin.android.presentation.ui.setting.PlatformSettingScreen
import com.lanxin.android.presentation.ui.setting.SettingScreen
import com.lanxin.android.presentation.ui.setting.SettingViewModelV2
import com.lanxin.android.presentation.ui.setup.SetupCompleteScreen
import com.lanxin.android.presentation.ui.setup.SetupPlatformListScreen
import com.lanxin.android.presentation.ui.setup.SetupPlatformTypeScreen
import com.lanxin.android.presentation.ui.setup.SetupPlatformWizardScreen
import com.lanxin.android.presentation.ui.setup.SetupViewModelV2
import com.lanxin.android.presentation.ui.startscreen.StartScreen

@Composable
fun SetupNavGraph(navController: NavHostController) {
    NavHost(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        navController = navController,
        startDestination = Route.CHAT_LIST
    ) {
        homeScreenNavigation(navController)
        migrationScreenNavigation(navController)
        startScreenNavigation(navController)
        setupNavigation(navController)
        settingNavigation(navController)
        chatScreenNavigation(navController)
        memoryScreenNavigation(navController)
        loggerScreenNavigation(navController)
        personaScreenNavigation(navController)
        statisticsScreenNavigation(navController)
        schedulerScreenNavigation(navController)
    }
}

fun NavGraphBuilder.statisticsScreenNavigation(navController: NavHostController) {
    composable(Route.STATISTICS) {
        StatisticsScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.schedulerScreenNavigation(navController: NavHostController) {
    composable(Route.TASK_LIST) {
        TaskListScreen(
            onBackAction = { navController.navigateUp() },
            onNavigateToEdit = { taskId ->
                if (taskId.isNullOrBlank()) {
                    navController.navigate(Route.TASK_CREATE)
                } else {
                    navController.navigate(
                        Route.TASK_EDIT.replace("{taskId}", taskId)
                    )
                }
            }
        )
    }
    composable(Route.TASK_CREATE) {
        TaskEditScreen(
            taskId = null,
            onBackAction = { navController.navigateUp() },
            onSaved = { navController.navigateUp() }
        )
    }
    composable(
        route = Route.TASK_EDIT,
        arguments = listOf(
            navArgument("taskId") { type = NavType.StringType }
        )
    ) { entry ->
        val taskId = entry.arguments?.getString("taskId")
        TaskEditScreen(
            taskId = taskId,
            onBackAction = { navController.navigateUp() },
            onSaved = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.personaScreenNavigation(navController: NavHostController) {
    composable(Route.PERSONA_LIST) {
        PersonaListScreen(
            onBackAction = { navController.navigateUp() },
            onNavigateToEdit = { personaId ->
                if (personaId.isNullOrBlank()) {
                    navController.navigate(Route.PERSONA_CREATE)
                } else {
                    navController.navigate(
                        Route.PERSONA_EDIT.replace("{personaId}", personaId)
                    )
                }
            }
        )
    }
    composable(Route.PERSONA_CREATE) {
        PersonaEditScreen(
            personaId = null,
            onBackAction = { navController.navigateUp() },
            onSaved = { navController.navigateUp() }
        )
    }
    composable(
        route = Route.PERSONA_EDIT,
        arguments = listOf(
            navArgument("personaId") { type = NavType.StringType }
        )
    ) { entry ->
        val personaId = entry.arguments?.getString("personaId")
        PersonaEditScreen(
            personaId = personaId,
            onBackAction = { navController.navigateUp() },
            onSaved = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.memoryScreenNavigation(navController: NavHostController) {
    composable(Route.MEMORY_LIST) {
        MemoryScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.loggerScreenNavigation(navController: NavHostController) {
    composable(Route.LOGGER) {
        LoggerScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.migrationScreenNavigation(navController: NavHostController) {
    composable(Route.MIGRATE_V2) {
        MigrateScreen {
            navController.navigate(Route.CHAT_LIST) {
                popUpTo(Route.MIGRATE_V2) { inclusive = true }
            }
        }
    }
}

fun NavGraphBuilder.startScreenNavigation(navController: NavHostController) {
    composable(Route.GET_STARTED) {
        StartScreen { navController.navigate(Route.CHAT_LIST) { popUpTo(Route.GET_STARTED) { inclusive = true } } }
    }
}

fun NavGraphBuilder.setupNavigation(
    navController: NavHostController
) {
    navigation(startDestination = Route.SETUP_PLATFORM_LIST, route = Route.SETUP_ROUTE) {
        composable(route = Route.SETUP_PLATFORM_LIST) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModelV2 = hiltViewModel(parentEntry)
            SetupPlatformListScreen(
                setupViewModel = setupViewModel,
                onAddPlatform = { navController.navigate(Route.SETUP_PLATFORM_TYPE) },
                onComplete = { navController.navigate(Route.SETUP_COMPLETE) },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.SETUP_PLATFORM_TYPE) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModelV2 = hiltViewModel(parentEntry)
            SetupPlatformTypeScreen(
                setupViewModel = setupViewModel,
                onPlatformTypeSelected = { navController.navigate(Route.SETUP_PLATFORM_WIZARD) },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.SETUP_PLATFORM_WIZARD) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETUP_ROUTE)
            }
            val setupViewModel: SetupViewModelV2 = hiltViewModel(parentEntry)
            SetupPlatformWizardScreen(
                setupViewModel = setupViewModel,
                onComplete = {
                    // Go back to platform list after adding a platform
                    navController.popBackStack(Route.SETUP_PLATFORM_LIST, inclusive = false)
                },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.SETUP_COMPLETE) {
            SetupCompleteScreen(
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Route.GET_STARTED) { inclusive = true }
                    }
                },
                onBackAction = { navController.navigateUp() }
            )
        }
    }
}

fun NavGraphBuilder.homeScreenNavigation(navController: NavHostController) {
    composable(Route.CHAT_LIST) {
        HomeScreen(
            settingOnClick = { navController.navigate(Route.SETTING_ROUTE) { launchSingleTop = true } },
            memoryOnClick = { navController.navigate(Route.MEMORY_LIST) { launchSingleTop = true } },
            onExistingChatClick = { chatRoom ->
                val enabledPlatformString = chatRoom.enabledPlatform.joinToString(",")
                navController.navigate(
                    Route.CHAT_ROOM
                        .replace(oldValue = "{chatRoomId}", newValue = "${chatRoom.id}")
                        .replace(oldValue = "{enabledPlatforms}", newValue = enabledPlatformString)
                )
            },
            navigateToNewChat = {
                val enabledPlatformString = it.joinToString(",")
                navController.navigate(
                    Route.CHAT_ROOM
                        .replace(oldValue = "{chatRoomId}", newValue = "0")
                        .replace(oldValue = "{enabledPlatforms}", newValue = enabledPlatformString)
                )
            }
        )
    }
}

fun NavGraphBuilder.chatScreenNavigation(navController: NavHostController) {
    composable(
        Route.CHAT_ROOM,
        arguments = listOf(
            navArgument("chatRoomId") { type = NavType.IntType },
            navArgument("enabledPlatforms") { defaultValue = "" }
        )
    ) {
        ChatScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.settingNavigation(navController: NavHostController) {
    navigation(startDestination = Route.SETTINGS, route = Route.SETTING_ROUTE) {
        composable(Route.SETTINGS) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            SettingScreen(
                settingViewModel = settingViewModel,
                onNavigationClick = { navController.navigateUp() },
                onNavigateToAddPlatform = { navController.navigate(Route.ADD_PLATFORM) },
                onNavigateToPlatformSetting = { platformUid ->
                    navController.navigate(
                        Route.PLATFORM_SETTINGS.replace("{platformUid}", platformUid)
                    )
                },
                onNavigateToAboutPage = { navController.navigate(Route.ABOUT_PAGE) },
                onNavigateToLogger = { navController.navigate(Route.LOGGER) },
                onNavigateToPersona = { navController.navigate(Route.PERSONA_LIST) },
                onNavigateToStatistics = { navController.navigate(Route.STATISTICS) },
                onNavigateToScheduler = { navController.navigate(Route.TASK_LIST) }
            )
        }
        composable(Route.ADD_PLATFORM) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            AddPlatformScreen(
                onNavigationClick = { navController.navigateUp() },
                onSave = { platform ->
                    settingViewModel.addPlatform(platform)
                    navController.navigateUp()
                }
            )
        }
        composable(
            Route.PLATFORM_SETTINGS,
            arguments = listOf(navArgument("platformUid") { type = NavType.StringType })
        ) {
            PlatformSettingScreen(
                onNavigationClick = { navController.navigateUp() }
            )
        }
        composable(Route.ABOUT_PAGE) {
            AboutScreen(
                onNavigationClick = { navController.navigateUp() },
                onNavigationToLicense = { navController.navigate(Route.LICENSE) }
            )
        }
        composable(Route.LICENSE) {
            LicenseScreen(onNavigationClick = { navController.navigateUp() })
        }
    }
}
