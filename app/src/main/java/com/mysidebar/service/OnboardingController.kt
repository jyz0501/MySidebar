package com.mysidebar.service

import android.content.Context
import com.mysidebar.AppContainer
import com.mysidebar.util.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 首次使用演示的唯一编排入口。
 * 只有「权限就绪 + 总开关开启 + 悬浮服务运行中 + 从未演示过」才会播放一次；
 * 条件不满足时不写已完成标记，下次回到前台重新计时重试。
 */
object OnboardingController {

    private const val DEMO_DELAY_MS = 3000L
    private const val READY_TIMEOUT_MS = 5000L
    private const val READY_POLL_MS = 200L
    private const val HOLD_MS = 1800L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var demoJob: Job? = null

    fun play(context: Context) {
        if (AppContainer.settings.onboarding.value.demoPlayed) return
        if (demoJob?.isActive == true) return

        val appContext = context.applicationContext
        demoJob = scope.launch {
            delay(DEMO_DELAY_MS)

            val ready = withTimeoutOrNull(READY_TIMEOUT_MS) {
                while (!isReady(appContext)) delay(READY_POLL_MS)
                true
            } ?: false
            if (!ready) return@launch
            if (AppContainer.settings.onboarding.value.demoPlayed) return@launch
            if (!isReady(appContext)) return@launch

            val duration = AppContainer.settings.current().animationDurationMs.toLong()
            AppContainer.settings.markDemoPlayed()
            ServiceBridge.demoActive.value = true
            try {
                delay(duration + HOLD_MS)
            } finally {
                ServiceBridge.demoActive.value = false
            }
        }
    }

    private fun isReady(context: Context): Boolean {
        val settings = AppContainer.settings.current()
        return settings.masterEnabled &&
            !settings.paused &&
            Permissions.canDrawOverlays(context) &&
            ServiceBridge.overlayRunning.value
    }

    fun dismissGuide() {
        AppContainer.settings.dismissGuide()
    }

    /** 切后台、服务销毁、总开关关闭、暂停或用户真实唤出时取消演示。 */
    fun cancel() {
        demoJob?.cancel()
        demoJob = null
        ServiceBridge.demoActive.value = false
    }
}
