package com.mysidebar.data

/** 侧边栏停靠方向。 */
enum class Side { LEFT, RIGHT }

/** 侧边栏在屏幕纵向的停靠位置，与 [Side] 组合成左上/左中/左下/右上/右中/右下六种。 */
enum class VerticalAnchor { TOP, CENTER, BOTTOM }

/** 侧边栏上的一个应用快捷方式。locked 为 true 时不可删除、不可移动。 */
data class IconEntry(
    val packageName: String,
    val label: String = "",
    val locked: Boolean = false,
    /** 可选启动组件，形如 "com.autolink.engineermode/.MainActivity"；为空时使用包的默认入口。 */
    val component: String? = null
)

/**
 * 全部用户可调配置。所有数值在写入前统一经过 [sanitized] 约束，
 * 防止从外部导入的配置把界面拖到不可用状态。
 */
data class SidebarSettings(
    val masterEnabled: Boolean = true,
    val paused: Boolean = false,
    val side: Side = Side.LEFT,
    val anchor: VerticalAnchor = VerticalAnchor.CENTER,
    val panelWidthDp: Int = 56,
    val panelHeightPercent: Int = 45,
    val panelAlpha: Float = 0.92f,
    val animationDurationMs: Int = 280,
    val edgeSensitivityDp: Int = 16,
    val triggerHeightPercent: Int = 65,
    val autoStart: Boolean = true,
    val icons: List<IconEntry> = emptyList()
) {
    fun sanitized(): SidebarSettings = copy(
        panelWidthDp = panelWidthDp.coerceIn(44, 96),
        panelHeightPercent = panelHeightPercent.coerceIn(25, 85),
        panelAlpha = panelAlpha.coerceIn(0.25f, 1f),
        animationDurationMs = animationDurationMs.coerceIn(80, 1200),
        edgeSensitivityDp = edgeSensitivityDp.coerceIn(6, 64),
        triggerHeightPercent = triggerHeightPercent.coerceIn(15, 100),
        icons = icons
            .map { it.copy(packageName = it.packageName.trim(), label = it.label.take(48)) }
            .filter { it.packageName.isNotBlank() }
            .distinctBy { it.packageName }
            .take(40)
    )

    companion object {
        val WIDTH_RANGE = 44f..96f
        val HEIGHT_RANGE = 25f..85f
        val ALPHA_RANGE = 0.25f..1f
        val DURATION_RANGE = 80f..1200f
        val SENSITIVITY_RANGE = 6f..64f
        val TRIGGER_HEIGHT_RANGE = 15f..100f
    }
}
