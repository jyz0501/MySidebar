package com.mysidebar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mysidebar.AppContainer
import com.mysidebar.util.Permissions

/** 开机 / 应用更新后，在开关允许时恢复悬浮窗服务。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val settings = AppContainer.settings.current()
        if (!settings.masterEnabled || !settings.autoStart) return
        if (!Permissions.canDrawOverlays(context)) return
        ServiceController.start(context)
    }
}
