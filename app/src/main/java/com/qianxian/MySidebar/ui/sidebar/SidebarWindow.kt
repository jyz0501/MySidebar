package com.qianxian.MySidebar.ui.sidebar

import android.app.Service
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.lang.reflect.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.qianxian.MySidebar.AppContainer
import com.qianxian.MySidebar.data.Side
import com.qianxian.MySidebar.data.SidebarSettings
import com.qianxian.MySidebar.data.VerticalAnchor
import com.qianxian.MySidebar.ui.theme.MySidebarTheme
import com.qianxian.MySidebar.util.EdgeGestureDetector
import com.qianxian.MySidebar.util.gestureConfig
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * 悬浮窗装配：
 * - 细条热区窗口：常驻、可触摸，承载边缘滑动检测；
 * - 面板窗口：全屏但收起时 NOT_TOUCHABLE，展开时接收点击（点空白处收起）。
 * 分离两个窗口是为了让收起状态下完全不拦截其它应用的触摸事件。
 */
class SidebarWindow(
    private val service: Service,
    private val expanded: StateFlow<Boolean>,
    private val editing: StateFlow<Boolean>,
    private val configVersion: StateFlow<Int>,
    private val onAction: (SidebarAction) -> Unit
) {
    private val density: Float get() = service.resources.displayMetrics.density

    /** 每次读取都取当前真实屏幕尺寸，横竖屏切换后仍准确。 */
    private val screenHeightPx: Int get() = gestureConfig(service).screenHeightPx

    fun createPanelView(owner: OverlayLifecycleOwner): ComposeView = ComposeView(service).apply {
        attachOwners(this, owner)
        setContent {
            MySidebarTheme {
                SidebarRoot(
                    expandedState = expanded,
                    editingState = editing,
                    configVersion = configVersion,
                    screenHeightProvider = { screenHeightPx },
                    apps = AppContainer.apps,
                    onAction = onAction
                )
            }
        }
    }

    /** 引导气泡单独成窗：面板窗口触摸穿透，气泡必须可触摸才能点「知道了」。 */
    fun createGuideView(owner: OverlayLifecycleOwner, onDismiss: () -> Unit): ComposeView =
        ComposeView(service).apply {
            attachOwners(this, owner)
            setContent {
                MySidebarTheme {
                    GuideBubble(onDismiss = onDismiss)
                }
            }
        }

    fun guideParams(settings: SidebarSettings): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = when (settings.anchor) {
                VerticalAnchor.TOP -> Gravity.TOP
                VerticalAnchor.CENTER -> Gravity.CENTER_VERTICAL
                VerticalAnchor.BOTTOM -> Gravity.BOTTOM
            } or (if (settings.side == Side.LEFT) Gravity.START else Gravity.END)
            x = (settings.panelWidthDp * density).roundToInt() + (10f * density).roundToInt()
            y = 0
        }

    fun createHandleView(onTrigger: () -> Unit): View {
        val detector = EdgeGestureDetector(
            configProvider = { gestureConfig(service) },
            onTrigger = {
                val settings = AppContainer.settings.current()
                if (settings.masterEnabled && !settings.paused) onTrigger()
            }
        )
        return View(service).apply {
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.argb(150, 139, 124, 248))
                cornerRadius = 9f * density
            }
            // 默认隐藏：只有设置界面在前台时才由 OverlayService 调亮。
            alpha = 0f
            setOnTouchListener { view, event ->
                detector.feed(event)
                if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                true
            }
        }
    }

    fun handleParams(settings: SidebarSettings): WindowManager.LayoutParams {
        val height = (screenHeightPx * settings.triggerHeightPercent / 100f).roundToInt()
        val width = (14f * density).roundToInt().coerceAtLeast(8)
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or (if (settings.side == Side.LEFT) Gravity.START else Gravity.END)
            x = 0
            y = when (settings.anchor) {
                VerticalAnchor.TOP -> 0
                VerticalAnchor.CENTER -> (screenHeightPx - height) / 2
                VerticalAnchor.BOTTOM -> screenHeightPx - height
            }
        }
    }

    fun panelParams(isExpanded: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (isExpanded) WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
}

/**
 * 把 LifecycleOwner / ViewModelStoreOwner / SavedStateRegistryOwner 挂到 ComposeView 上。
 * 这些 ViewTree* 类被 AndroidX 标记 @RestrictTo(LIBRARY_GROUP)，应用模块无法直接引用，
 * 故反射调用其公开的静态 set 方法（运行时无访问限制，AndroidX 自带 keep 规则保留）。
 */
private fun attachOwners(view: View, owner: OverlayLifecycleOwner) {
    callStatic(
        "androidx.lifecycle.ViewTreeLifecycleOwner", "set",
        arrayOf(View::class.java, LifecycleOwner::class.java), arrayOf(view, owner)
    )
    callStatic(
        "androidx.lifecycle.ViewTreeViewModelStoreOwner", "set",
        arrayOf(View::class.java, ViewModelStoreOwner::class.java), arrayOf(view, owner)
    )
    callStatic(
        "androidx.savedstate.ViewTreeSavedStateRegistryOwner", "set",
        arrayOf(View::class.java, SavedStateRegistryOwner::class.java), arrayOf(view, owner)
    )
}

private fun callStatic(cls: String, method: String, paramTypes: Array<Class<*>>, args: Array<Any?>) {
    try {
        val target = try {
            Class.forName(cls)
        } catch (_: Throwable) {
            Class.forName(cls + "Kt")
        }
        val m = try {
            target.getMethod(method, *paramTypes)
        } catch (_: Throwable) {
            return
        }
        // Kotlin object / companion 上的方法可能是实例方法，需要 INSTANCE 作为 receiver。
        val receiver = if (Modifier.isStatic(m.modifiers)) {
            null
        } else {
            try {
                target.getField("INSTANCE").get(null)
            } catch (_: Throwable) {
                return
            }
        }
        m.invoke(receiver, *args)
    } catch (_: Throwable) {
        // 任一 owner 类在极端版本下不存在时忽略，ComposeView 退回到默认行为。
    }
}
