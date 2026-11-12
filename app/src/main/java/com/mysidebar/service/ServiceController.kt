package com.mysidebar.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.mysidebar.AppContainer
import com.mysidebar.util.Permissions

/** 统一决定悬浮窗服务的启停，避免各处重复判断权限与总开关。 */
object ServiceController {

    private const val TAG = "ServiceController"

    fun sync(context: Context) {
        val settings = AppContainer.settings.current()
        if (settings.masterEnabled && Permissions.canDrawOverlays(context)) start(context) else stop(context)
    }

    fun start(context: Context) {
        val intent = Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_SYNC)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (t: Throwable) {
            // Android 12+ 后台启动前台服务受限时，用户下次打开设置页会再次 sync。
            Log.w(TAG, "Unable to start overlay service", t)
        }
    }

    fun stop(context: Context) {
        try {
            context.stopService(Intent(context, OverlayService::class.java))
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to stop overlay service", t)
        }
    }
}
