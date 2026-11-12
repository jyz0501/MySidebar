package com.qianxian.MySidebar.util

import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.WindowManager
import com.qianxian.MySidebar.AppContainer
import com.qianxian.MySidebar.data.Side
import com.qianxian.MySidebar.data.SidebarSettings

data class GestureConfig(
    val side: Side,
    val thresholdPx: Float,
    val screenHeightPx: Int
)

/** 依据当前设置与真实屏幕尺寸计算手势参数。 */
fun gestureConfig(context: Context, settings: SidebarSettings = AppContainer.settings.current()): GestureConfig {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val screenHeightPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        wm.currentWindowMetrics.bounds.height()
    } else {
        @Suppress("DEPRECATION")
        android.util.DisplayMetrics().let { metrics ->
            wm.defaultDisplay.getRealMetrics(metrics)
            metrics.heightPixels
        }
    }
    val density = context.resources.displayMetrics.density
    return GestureConfig(
        side = settings.side,
        thresholdPx = settings.edgeSensitivityDp * density,
        screenHeightPx = screenHeightPx
    )
}

/**
 * 边缘手势检测器（只作用于屏幕边缘那根可触摸的细条热区）：
 * - 轻点细条即可唤出侧边栏；
 * - 从细条向屏幕内侧滑动超过设定距离同样唤出。
 */
class EdgeGestureDetector(
    private val configProvider: () -> GestureConfig,
    private val onTrigger: () -> Unit
) {
    private var startX = 0f
    private var startY = 0f
    private var triggered = false

    fun feed(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                triggered = false
            }

            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (triggered) return
                val config = configProvider()
                val dx = event.rawX - startX
                val dy = event.rawY - startY
                val swiped = if (config.side == Side.LEFT) dx >= config.thresholdPx
                else -dx >= config.thresholdPx
                val tap = event.actionMasked == MotionEvent.ACTION_UP &&
                    kotlin.math.abs(dx) < 20f && kotlin.math.abs(dy) < 20f
                if (swiped || tap) {
                    triggered = true
                    onTrigger()
                }
            }

            MotionEvent.ACTION_CANCEL -> triggered = false
        }
    }
}
