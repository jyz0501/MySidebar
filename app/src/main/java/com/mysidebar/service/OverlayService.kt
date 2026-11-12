package com.mysidebar.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import com.mysidebar.AppContainer
import com.mysidebar.MainActivity
import com.mysidebar.R
import com.mysidebar.data.SidebarSettings
import com.mysidebar.ui.sidebar.OverlayLifecycleOwner
import com.mysidebar.ui.sidebar.SidebarAction
import com.mysidebar.ui.sidebar.SidebarWindow
import com.mysidebar.util.SidebarSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 前台服务：持有悬浮窗、响应设置变更与手势命令。 */
class OverlayService : Service() {

    companion object {
        const val ACTION_SYNC = "com.mysidebar.action.SYNC"
        const val ACTION_HIDE = "com.mysidebar.action.HIDE"
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 1001
        private const val HANDLE_ALPHA_VISIBLE = 0.9f
        private const val CHANNEL_VISIBLE = "sidebar_visible"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val expanded = MutableStateFlow(false)
    private val editing = MutableStateFlow(false)
    private val configVersion = MutableStateFlow(0)

    private lateinit var windowManager: WindowManager
    private lateinit var sidebarWindow: SidebarWindow
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var panelView: ComposeView? = null
    private var handleView: View? = null
    private var guideView: ComposeView? = null
    private var guideLifecycleOwner: OverlayLifecycleOwner? = null
    private var lastNotificationKey: String? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannels()

        val settings = AppContainer.settings.current()
        startForeground(NOTIFICATION_ID, buildNotification(settings))
        lastNotificationKey = notificationKey(settings)
        ServiceBridge.overlayRunning.value = true

        sidebarWindow = SidebarWindow(this, expanded, editing, configVersion, ::handleAction)
        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        lifecycleOwner = owner
        val panel = sidebarWindow.createPanelView(owner)
        val handle = sidebarWindow.createHandleView { ServiceBridge.open() }
        panelView = panel
        handleView = handle

        try {
            windowManager.addView(handle, sidebarWindow.handleParams(settings))
            windowManager.addView(panel, sidebarWindow.panelParams(false))
            applySettings(settings, force = true)
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to attach overlay windows", t)
            stopSelf()
            return
        }

        scope.launch {
            AppContainer.settings.settings.collect { applySettings(it) }
        }
        scope.launch {
            // 设置界面进出前台时同步手势条的显示与隐藏。
            ServiceBridge.hostInForeground.collect { foreground ->
                val handle = handleView ?: return@collect
                applyHandleVisibility(handle, AppContainer.settings.current())
            }
        }
        scope.launch {
            ServiceBridge.commands.collect { command ->
                when (command) {
                    SidebarCommand.Open -> {
                        OnboardingController.cancel()
                        if (ServiceBridge.previewMode.value) ServiceBridge.stopPreview()
                        setExpanded(true)
                    }
                    SidebarCommand.Close -> {
                        OnboardingController.cancel()
                        if (ServiceBridge.previewMode.value) ServiceBridge.stopPreview()
                        setExpanded(false)
                    }
                    SidebarCommand.Toggle -> {
                        OnboardingController.cancel()
                        if (ServiceBridge.previewMode.value) ServiceBridge.stopPreview()
                        setExpanded(!expanded.value)
                    }
                    SidebarCommand.Sync -> applySettings(AppContainer.settings.current())
                }
            }
        }
        scope.launch {
            combine(
                ServiceBridge.previewMode,
                ServiceBridge.demoActive,
                AppContainer.settings.onboarding
            ) { preview, demo, onboarding -> Triple(preview, demo, onboarding) }
                .distinctUntilChanged()
                .collect { (preview, demo, onboarding) ->
                    if (preview) {
                        editing.value = false
                        expanded.value = false
                    }
                    refreshPanelTouchFlags()
                    setGuideBubble(!onboarding.guideDismissed && (onboarding.demoPlayed || demo))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> setExpanded(false)
            ACTION_SYNC -> applySettings(AppContainer.settings.current())
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        OnboardingController.cancel()
        setExpanded(false)
        configVersion.value++
        applySettings(AppContainer.settings.current())
    }

    private fun applySettings(settings: SidebarSettings, force: Boolean = false) {
        if (!settings.masterEnabled) {
            OnboardingController.cancel()
            setExpanded(false)
            stopSelf()
            return
        }
        val handle = handleView ?: return
        applyHandleVisibility(handle, settings)
        try {
            windowManager.updateViewLayout(handle, sidebarWindow.handleParams(settings))
        } catch (t: Throwable) {
            Log.w(TAG, "update handle params failed", t)
        }
        guideView?.let { view ->
            try {
                windowManager.updateViewLayout(view, sidebarWindow.guideParams(settings))
            } catch (t: Throwable) {
                Log.w(TAG, "update guide params failed", t)
            }
        }
        if (settings.paused) {
            OnboardingController.cancel()
            setExpanded(false)
        }

        val key = notificationKey(settings)
        if (force || key != lastNotificationKey) {
            lastNotificationKey = key
            try {
                startForeground(NOTIFICATION_ID, buildNotification(settings))
            } catch (t: Throwable) {
                Log.w(TAG, "refresh notification failed", t)
            }
        }
    }

    /**
     * 边缘手势条：设置界面在前台时显示出来，日常使用时隐藏；
     * 隐藏时仍保留 View 本身（alpha=0），因此轻点边缘依旧能唤出侧边栏。
     */
    private fun applyHandleVisibility(handle: View, settings: SidebarSettings) {
        if (settings.paused) {
            handle.visibility = View.GONE
            return
        }
        handle.visibility = View.VISIBLE
        handle.alpha = if (ServiceBridge.hostInForeground.value) HANDLE_ALPHA_VISIBLE else 0f
    }

    private fun setExpanded(value: Boolean) {
        val settings = AppContainer.settings.current()
        val target = value && settings.masterEnabled && !settings.paused
        if (expanded.value == target) return
        expanded.value = target
        if (target) SidebarSound.play()
        refreshPanelTouchFlags()
        if (!target) editing.value = false
    }

    /** 只有「正常展开」才接收触摸；收起态、预览态与演示态都让触摸穿透到底层应用。 */
    private fun refreshPanelTouchFlags() {
        val view = panelView ?: return
        val touchable = expanded.value &&
            !ServiceBridge.previewMode.value &&
            !ServiceBridge.demoActive.value
        try {
            windowManager.updateViewLayout(view, sidebarWindow.panelParams(touchable))
        } catch (t: Throwable) {
            Log.w(TAG, "update panel params failed", t)
        }
    }

    /** 气泡窗口只在可见时存在；「知道了」写盘后由合并流回调这里移除。 */
    private fun setGuideBubble(show: Boolean) {
        if (!show) {
            guideView?.let { runCatching { windowManager.removeViewImmediate(it) } }
            guideView = null
            guideLifecycleOwner?.onDestroy()
            guideLifecycleOwner = null
            return
        }
        if (guideView != null) return

        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        val view = try {
            sidebarWindow.createGuideView(owner) { OnboardingController.dismissGuide() }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to create guide bubble", t)
            owner.onDestroy()
            return
        }
        try {
            windowManager.addView(view, sidebarWindow.guideParams(AppContainer.settings.current()))
            guideView = view
            guideLifecycleOwner = owner
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to attach guide bubble", t)
            owner.onDestroy()
        }
    }

    private fun handleAction(action: SidebarAction) {
        when (action) {
            SidebarAction.Close -> setExpanded(false)
            SidebarAction.ToggleEdit -> editing.value = !editing.value
            SidebarAction.OpenPicker -> {
                setExpanded(false)
                openMainActivity(MainActivity.EXTRA_OPEN_PICKER)
            }
            is SidebarAction.Launch -> {
                setExpanded(false)
                if (!AppContainer.apps.isInstalled(action.packageName)) {
                    // 应用被卸载或从未安装：只提示，不抛异常。
                    Toast.makeText(this, "「${AppContainer.apps.labelFor(action.packageName)}」未安装", Toast.LENGTH_SHORT).show()
                } else if (!AppContainer.apps.launch(action.packageName, action.component)) {
                    Toast.makeText(this, "无法启动该应用，请允许后台弹出界面", Toast.LENGTH_SHORT).show()
                }
            }
            is SidebarAction.Remove -> AppContainer.settings.update { current ->
                val target = current.icons.firstOrNull { it.packageName == action.packageName }
                    ?: return@update current
                if (target.locked) {
                    current
                } else {
                    current.copy(icons = current.icons.filterNot { it.packageName == action.packageName })
                }
            }
            is SidebarAction.ToggleLock -> AppContainer.settings.update { current ->
                current.copy(
                    icons = current.icons.map {
                        if (it.packageName == action.packageName) it.copy(locked = !it.locked) else it
                    }
                )
            }
            is SidebarAction.Move -> AppContainer.settings.update { current ->
                val list = current.icons.toMutableList()
                val from = list.indexOfFirst { it.packageName == action.packageName }
                val to = from + action.delta
                if (from < 0 || to !in list.indices || list[from].locked || list[to].locked) {
                    current
                } else {
                    val item = list.removeAt(from)
                    list.add(to, item)
                    current.copy(icons = list)
                }
            }
        }
    }

    private fun openMainActivity(extra: String? = null) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (extra != null) intent.putExtra(extra, true)
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            try {
                PendingIntent.getActivity(
                    this,
                    42,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                ).send()
            } catch (t2: Throwable) {
                Log.w(TAG, "Unable to open settings", t2)
            }
        }
    }

    private fun notificationKey(settings: SidebarSettings) = "${settings.paused}"

    private fun buildNotification(settings: SidebarSettings): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val hideIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_VISIBLE)
            .setSmallIcon(R.drawable.ic_stat_sidebar)
            .setContentTitle(
                getString(
                    if (settings.paused) R.string.notification_paused else R.string.notification_title
                )
            )
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stat_sidebar, getString(R.string.action_hide), hideIntent)
            .build()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        NotificationChannel(
            CHANNEL_VISIBLE,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "侧边栏运行状态"
            setShowBadge(false)
            enableVibration(false)
            manager.createNotificationChannel(this)
        }
    }

    override fun onDestroy() {
        OnboardingController.cancel()
        ServiceBridge.stopPreview()
        ServiceBridge.overlayRunning.value = false
        scope.cancel()
        panelView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        handleView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        guideView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        panelView = null
        handleView = null
        guideView = null
        guideLifecycleOwner?.onDestroy()
        guideLifecycleOwner = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        expanded.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
