package com.qianxian.MySidebar.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class SidebarCommand {
    object Open : SidebarCommand()
    object Close : SidebarCommand()
    object Toggle : SidebarCommand()
    object Sync : SidebarCommand()
}

/** 悬浮服务、通知栏动作与设置界面之间的进程内通信桥。 */
object ServiceBridge {

    private val _commands = MutableSharedFlow<SidebarCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<SidebarCommand> = _commands.asSharedFlow()

    val overlayRunning = MutableStateFlow(false)

    /**
     * 设置界面是否处于前台。为 true 时屏幕边缘的手势条会显示出来，
     * 日常使用时隐藏（但仍保留触摸唤出能力）。
     */
    val hostInForeground = MutableStateFlow(false)

    /**
     * 设置页「实时预览」态：让真实的悬浮侧边栏保持划出状态，
     * 但遮罩透明且窗口不拦截触摸，用户可继续拖动设置页的滑杆。
     */
    val previewMode = MutableStateFlow(false)

    /**
     * 首次启动的一次性演示态。独立于 [previewMode]，
     * 演示结束时不会误关外观页的常驻预览。
     */
    val demoActive = MutableStateFlow(false)

    fun startPreview() {
        previewMode.value = true
    }

    fun stopPreview() {
        previewMode.value = false
    }

    fun open() {
        _commands.tryEmit(SidebarCommand.Open)
    }

    fun close() {
        _commands.tryEmit(SidebarCommand.Close)
    }

    fun toggle() {
        _commands.tryEmit(SidebarCommand.Toggle)
    }

    fun sync() {
        _commands.tryEmit(SidebarCommand.Sync)
    }
}
