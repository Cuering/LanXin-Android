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
import com.lanxin.android.builtin.knowledge.presentation.KnowledgeScreen
import com.lanxin.android.builtin.persona.presentation.PersonaEditScreen
import com.lanxin.android.builtin.persona.presentation.PersonaListScreen
import com.lanxin.android.builtin.scheduler.presentation.TaskEditScreen
import com.lanxin.android.builtin.scheduler.presentation.TaskListScreen
import com.lanxin.android.builtin.statistics.presentation.StatisticsScreen
import com.lanxin.android.builtin.unifiedsearch.presentation.UnifiedSearchScreen
import com.lanxin.android.plugins.logger.presentation.ui.LoggerScreen
import com.lanxin.android.plugins.memory.presentation.ui.memory.MemoryScreen
import com.lanxin.android.plugins.unifiedinbox.presentation.CrossSessionHistoryScreen
import com.lanxin.android.plugins.unifiedinbox.presentation.UnifiedFileBrowserScreen
import com.lanxin.android.presentation.ui.chat.ChatScreen
import com.lanxin.android.presentation.ui.home.HomeScreen
import com.lanxin.android.presentation.ui.migrate.MigrateScreen
import com.lanxin.android.presentation.ui.plugin.PluginManagerScreen
import com.lanxin.android.presentation.ui.plugin.PluginMarketScreen
import com.lanxin.android.presentation.ui.setting.AboutScreen
import com.lanxin.android.presentation.ui.setting.AddPlatformScreen
import com.lanxin.android.presentation.ui.setting.LicenseScreen
import com.lanxin.android.presentation.ui.setting.PlatformSettingScreen
import com.lanxin.android.presentation.ui.setting.SettingScreen
import com.lanxin.android.builtin.localinference.presentation.LocalInferenceScreen
import com.lanxin.android.builtin.pet.presentation.CompanionScreen
import com.lanxin.android.builtin.pet.presentation.DesktopPetScreen
import com.lanxin.android.builtin.systemtools.presentation.SystemToolsScreen
import com.lanxin.android.builtin.platform.presentation.DeviceSensingScreen
import com.lanxin.android.builtin.platform.presentation.SceneSensingScreen
import com.lanxin.android.builtin.capabilities.presentation.SmartCapabilitiesScreen
import com.lanxin.android.builtin.platform.presentation.WebSearchScreen
import com.lanxin.android.builtin.voice.presentation.VoiceAsrScreen
import com.lanxin.android.plugin.claw.presentation.ClawHostScreen
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
        knowledgeScreenNavigation(navController)
        schedulerScreenNavigation(navController)
        unifiedInboxScreenNavigation(navController)
        unifiedSearchScreenNavigation(navController)
        pluginManagerScreenNavigation(navController)
        pluginMarketScreenNavigation(navController)
        smartCapabilitiesScreenNavigation(navController)
        localInferenceScreenNavigation(navController)
        offlineAsrScreenNavigation(navController)
        desktopPetScreenNavigation(navController)
        companionScreenNavigation(navController)
        systemToolsScreenNavigation(navController)
        webSearchScreenNavigation(navController)
        deviceSensingScreenNavigation(navController)
        sceneSensingScreenNavigation(navController)
        clawHostScreenNavigation(navController)
    }
}

fun NavGraphBuilder.pluginManagerScreenNavigation(navController: NavHostController) {
    composable(Route.PLUGIN_MANAGER) {
        PluginManagerScreen(
            onBackAction = { navController.navigateUp() },
            onNavigateToMarket = { navController.navigate(Route.PLUGIN_MARKET) }
        )
    }
}

fun NavGraphBuilder.pluginMarketScreenNavigation(navController: NavHostController) {
    composable(Route.PLUGIN_MARKET) {
        PluginMarketScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}


fun NavGraphBuilder.smartCapabilitiesScreenNavigation(navController: NavHostController) {
    composable(Route.SMART_CAPABILITIES) {
        SmartCapabilitiesScreen(
            onBackAction = { navController.navigateUp() },
            onNavigateToLocalInference = { navController.navigate(Route.LOCAL_INFERENCE) },
            onNavigateToVoice = { navController.navigate(Route.OFFLINE_ASR) },
            onNavigateToSystemTools = { navController.navigate(Route.SYSTEM_TOOLS) },
            onNavigateToWebSearch = { navController.navigate(Route.WEB_SEARCH) },
            onNavigateToDeviceSensing = { navController.navigate(Route.DEVICE_SENSING) },
            onNavigateToSceneVision = { navController.navigate(Route.SCENE_SENSING) }
        )
    }
}

fun NavGraphBuilder.localInferenceScreenNavigation(navController: NavHostController) {
    composable(Route.LOCAL_INFERENCE) {
        LocalInferenceScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.offlineAsrScreenNavigation(navController: NavHostController) {
    composable(Route.OFFLINE_ASR) {
        VoiceAsrScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.desktopPetScreenNavigation(navController: NavHostController) {
    composable(Route.DESKTOP_PET) {
        DesktopPetScreen(
            onBackAction = { navController.navigateUp() },
            onOpenCompanion = { navController.navigate(Route.COMPANION) }
        )
    }
}

fun NavGraphBuilder.companionScreenNavigation(navController: NavHostController) {
    composable(Route.COMPANION) {
        CompanionScreen(
            onBackAction = { navController.navigateUp() },
            onOpenSettings = { navController.navigate(Route.DESKTOP_PET) }
        )
    }
}


fun NavGraphBuilder.systemToolsScreenNavigation(navController: NavHostController) {
    composable(Route.SYSTEM_TOOLS) {
        SystemToolsScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.webSearchScreenNavigation(navController: NavHostController) {
    composable(Route.WEB_SEARCH) {
        WebSearchScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.deviceSensingScreenNavigation(navController: NavHostController) {
    composable(Route.DEVICE_SENSING) {
        DeviceSensingScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.sceneSensingScreenNavigation(navController: NavHostController) {
    composable(Route.SCENE_SENSING) {
        SceneSensingScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.clawHostScreenNavigation(navController: NavHostController) {
    composable(Route.CLAW_HOST) {
        ClawHostScreen(
            onBackAction = { navController.navigateUp() },
            onNavigateToPluginManager = { navController.navigate(Route.PLUGIN_MANAGER) },
            onNavigateToPluginMarket = { navController.navigate(Route.PLUGIN_MARKET) }
        )
    }
}


fun NavGraphBuilder.unifiedSearchScreenNavigation(navController: NavHostController) {
    composable(Route.UNIFIED_SEARCH) {
        UnifiedSearchScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.unifiedInboxScreenNavigation(navController: NavHostController) {
    composable(Route.UNIFIED_INBOX) {
        CrossSessionHistoryScreen(
            onBackAction = { navController.navigateUp() },
            onNavigateToFileBrowser = {
                navController.navigate(Route.UNIFIED_FILE_BROWSER)
            }
        )
    }
    composable(Route.UNIFIED_FILE_BROWSER) {
        UnifiedFileBrowserScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.statisticsScreenNavigation(navController: NavHostController) {
    composable(Route.STATISTICS) {
        StatisticsScreen(
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.knowledgeScreenNavigation(navController: NavHostController) {
    composable(
        route = Route.KNOWLEDGE_DETAIL,
        arguments = listOf(
            navArgument("externalId") { type = NavType.StringType },
            navArgument("snippet") {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { entry ->
        val externalId = entry.arguments?.getString("externalId").orEmpty()
        val snippet = entry.arguments?.getString("snippet").orEmpty()
        com.lanxin.android.builtin.knowledge.presentation.KnowledgeDetailScreen(
            externalId = externalId,
            snippet = snippet,
            onBackAction = { navController.navigateUp() }
        )
    }
    composable(Route.KNOWLEDGE) {
        KnowledgeScreen(
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
    composable(
        route = Route.MEMORY_EDIT,
        arguments = listOf(navArgument("memoryId") { type = NavType.StringType })
    ) { entry ->
        val memoryId = entry.arguments?.getString("memoryId")?.toLongOrNull()
        MemoryScreen(
            onBackAction = { navController.navigateUp() },
            openMemoryId = memoryId
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
            companionOnClick = {
                navController.navigate(Route.COMPANION) { launchSingleTop = true }
            },
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
            onBackAction = { navController.navigateUp() },
            onOpenMemoryRef = { memoryId ->
                navController.navigate(
                    Route.MEMORY_EDIT.replace(
                        "{memoryId}",
                        android.net.Uri.encode(memoryId)
                    )
                )
            },
            onOpenKnowledgeRef = { externalId, snippet ->
                val encodedSnippet = android.net.Uri.encode(snippet.take(200))
                navController.navigate(
                    Route.KNOWLEDGE_DETAIL
                        .replace("{externalId}", android.net.Uri.encode(externalId))
                        .replace("{snippet}", encodedSnippet)
                )
            }
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
                onNavigateToKnowledge = { navController.navigate(Route.KNOWLEDGE) },
                onNavigateToScheduler = { navController.navigate(Route.TASK_LIST) },
                onNavigateToUnifiedInbox = { navController.navigate(Route.UNIFIED_INBOX) },
                onNavigateToUnifiedSearch = { navController.navigate(Route.UNIFIED_SEARCH) },
                onNavigateToPluginManager = { navController.navigate(Route.PLUGIN_MANAGER) },
                onNavigateToPluginMarket = { navController.navigate(Route.PLUGIN_MARKET) },
                onNavigateToSmartCapabilities = {
                    navController.navigate(Route.SMART_CAPABILITIES)
                },
                onNavigateToDesktopPet = { navController.navigate(Route.DESKTOP_PET) },
                onNavigateToClawHost = { navController.navigate(Route.CLAW_HOST) },
                onNavigateToLocalInference = { navController.navigate(Route.LOCAL_INFERENCE) },
                onNavigateToOfflineAsr = { navController.navigate(Route.OFFLINE_ASR) },
                onNavigateToSystemTools = { navController.navigate(Route.SYSTEM_TOOLS) },
                onNavigateToWebSearch = { navController.navigate(Route.WEB_SEARCH) },
                onNavigateToDeviceSensing = { navController.navigate(Route.DEVICE_SENSING) },
                onNavigateToSceneSensing = { navController.navigate(Route.SCENE_SENSING) }
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
