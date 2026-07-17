/*
 * Copyright 2025 LanXin Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lanxin.android.builtin.pet.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import com.lanxin.android.R
import com.lanxin.android.builtin.pet.domain.Live2dDisplayController
import com.lanxin.android.builtin.pet.domain.MeijuDebugPaths
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeMessage
import com.lanxin.android.builtin.pet.domain.PetSettings
import com.lanxin.android.builtin.pet.domain.VoiceSessionCoordinator
import com.lanxin.android.presentation.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 桌宠悬浮层 Service：WindowManager + WebView 加载 `file:///android_asset/pet/desktop-pet.html`。
 *
 * 默认不启动；需 SYSTEM_ALERT_WINDOW + 设置总开关。
 * 不偷偷录音、不截屏。
 */
@AndroidEntryPoint
class FloatingPetService : Service() {

    @Inject
    lateinit var sessionCoordinator: VoiceSessionCoordinator

    @Inject
    lateinit var petSettings: PetSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var webView: WebView? = null
    private var desktopBridge: DesktopPetBridge? = null
    private var voiceBridge: AndroidVoiceBridge? = null

    /** 最近一次 Live2D 显示决策（供设置页/调试观察）。 */
    @Volatile
    var lastLive2dDecision: Live2dDisplayController.Decision? = null
        private set

    @Volatile
    var lastLive2dWebMode: String = ""
        private set

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        attachOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { petSettings.setOverlayRunning(false) }
                stopSelf()
            }
            ACTION_DEMO_ROUND -> {
                scope.launch {
                    val result = sessionCoordinator.runDemoRound()
                    pushSessionToWeb()
                    desktopBridge?.encodeBubble(result.subtitle.ifBlank { result.replyText })
                        ?.let { pushRawToWeb(it) }
                }
            }
            else -> pushSessionToWeb()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        detachOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun attachOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            dp(180),
            dp(240),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(120)
        }

        desktopBridge = DesktopPetBridge { msg -> handleBridgeMessage(msg) }
        voiceBridge = AndroidVoiceBridge(
            snapshotProvider = { sessionCoordinator.current() },
            onStartVoice = {
                scope.launch {
                    sessionCoordinator.runDemoRound()
                    pushSessionToWeb()
                }
            },
            onStopVoice = {
                scope.launch {
                    sessionCoordinator.reset()
                    pushSessionToWeb()
                }
            }
        )

        webView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            // 允许 WebView 读 filesDir（debug 妹居 L2D 旁路）；业务只传路径，不打包资源
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = true
            addJavascriptInterface(desktopBridge!!, DesktopPetBridge.JS_NAME)
            addJavascriptInterface(voiceBridge!!, AndroidVoiceBridge.JS_NAME)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pushLive2dPathToWeb()
                    pushSessionToWeb()
                }
            }
            loadUrl(ASSET_URL)
        }
        windowManager?.addView(webView, params)
        scope.launch { petSettings.setOverlayRunning(true) }
    }

    private fun detachOverlay() {
        try {
            webView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
            // ignore
        }
        webView?.destroy()
        webView = null
        windowManager = null
        desktopBridge = null
        voiceBridge = null
        scope.launch { petSettings.setOverlayRunning(false) }
    }

    private fun handleBridgeMessage(msg: PetBridgeMessage) {
        when (msg.command) {
            PetBridgeCommand.CLOSE_PET -> {
                scope.launch { petSettings.setOverlayRunning(false) }
                stopSelf()
            }
            PetBridgeCommand.START_VOICE -> {
                scope.launch {
                    sessionCoordinator.runDemoRound()
                    pushSessionToWeb()
                }
            }
            PetBridgeCommand.STOP_VOICE -> {
                scope.launch {
                    sessionCoordinator.reset()
                    pushSessionToWeb()
                }
            }
            PetBridgeCommand.PING -> {
                // no-op
            }
            PetBridgeCommand.LIVE2D_STATUS -> {
                lastLive2dWebMode = msg.payload["live2dMode"].orEmpty()
            }
            else -> {
                // SESSION_STATE 等由 native 主动推
            }
        }
    }

    private fun pushLive2dPathToWeb() {
        // M2b：路径决策 + LOAD_LIVE2D；换模型不改 VoiceSession 状态机。资源不在 git。
        // 与设置页一致：PetResourceResolver 解析配置 / debug-assets 旁路。
        scope.launch {
            val pet = petSettings.getConfig()
            val filesDir = applicationContext.filesDir
            val isDebug = (applicationContext.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val path = if (isDebug) {
                MeijuDebugPaths.resolveLive2dIfPresent(filesDir, pet.live2dModelPath)
            } else {
                pet.live2dModelPath.trim()
            }
            val decision = Live2dDisplayController.decide(path)
            lastLive2dDecision = decision

            // 兼容旧钩子
            val escapedPath = path
                .replace("\\", "\\\\")
                .replace("'", "\\'")
            webView?.evaluateJavascript(
                "window.onLive2dModelPath && window.onLive2dModelPath('$escapedPath');",
                null
            )

            // M2b 结构化推送
            val encoded = desktopBridge?.encodeLoadLive2d(decision) ?: return@launch
            pushRawToWeb(encoded)
        }
    }

    private fun pushSessionToWeb() {
        val snap = sessionCoordinator.current()
        val encoded = desktopBridge?.encodeSession(snap) ?: return
        pushRawToWeb(encoded)
        val bubble = snap.subtitle.ifBlank { snap.replyText }
        if (bubble.isNotBlank()) {
            desktopBridge?.encodeBubble(bubble)?.let { pushRawToWeb(it) }
        }
    }

    private fun pushRawToWeb(raw: String) {
        val escaped = raw
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
        webView?.evaluateJavascript("window.onNativePetMessage && window.onNativePetMessage('$escaped');", null)
    }

    private fun buildNotification(): Notification {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "桌宠",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingPetService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("兰心桌宠")
            .setContentText("语音陪伴运行中（可随时关闭）")
            .setSmallIcon(R.mipmap.ic_lanxin)
            .setContentIntent(open)
            .addAction(0, "停止", stop)
            .setOngoing(true)
            .build()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_STOP = "com.lanxin.android.pet.STOP"
        const val ACTION_DEMO_ROUND = "com.lanxin.android.pet.DEMO_ROUND"
        const val CHANNEL_ID = "lanxin_desktop_pet"
        const val NOTIFICATION_ID = 64061
        const val ASSET_URL = "file:///android_asset/pet/desktop-pet.html"

        fun start(context: Context) {
            val i = Intent(context, FloatingPetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FloatingPetService::class.java).setAction(ACTION_STOP)
            )
        }

        fun demoRound(context: Context) {
            val i = Intent(context, FloatingPetService::class.java).setAction(ACTION_DEMO_ROUND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
