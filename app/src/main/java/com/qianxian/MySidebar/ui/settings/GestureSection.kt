package com.qianxian.MySidebar.ui.settings

import androidx.compose.runtime.Composable
import com.qianxian.MySidebar.AppContainer
import com.qianxian.MySidebar.data.SidebarSettings
import com.qianxian.MySidebar.ui.components.HintText
import com.qianxian.MySidebar.ui.components.SectionCard
import com.qianxian.MySidebar.ui.components.SettingSlider
import kotlin.math.abs
import kotlin.math.roundToInt

/** 三档灵敏度预设：滑块只有三个吸附节点。 */
private val sensitivityLevels = listOf(12, 32, 55)
private val sensitivityNames = listOf("灵敏", "均衡", "稳健")

/** 当前配置值最近的档位下标。 */
private fun levelIndexOf(dp: Int): Int =
    sensitivityLevels.indices.minByOrNull { abs(sensitivityLevels[it] - dp) } ?: 1

@Composable
fun GestureSection(settings: SidebarSettings) {
    val level = levelIndexOf(settings.edgeSensitivityDp)
    SectionCard(title = "边缘灵敏度", subtitle = "触发所需的横向滑动距离") {
        SettingSlider(
            label = "滑动触发距离",
            value = level.toFloat(),
            range = 0f..2f,
            steps = 2,
            display = "${sensitivityNames[level]} · ${settings.edgeSensitivityDp} dp",
            onValueChange = {
                val idx = it.roundToInt().coerceIn(0, sensitivityLevels.lastIndex)
                AppContainer.settings.update { s ->
                    s.copy(edgeSensitivityDp = sensitivityLevels[idx])
                }
            },
            hint = sensitivityHint(sensitivityLevels[level])
        )
    }

    SectionCard(title = "触发区高度", subtitle = "屏幕边缘可唤出侧边栏的纵向范围") {
        SettingSlider(
            label = "热区高度",
            value = settings.triggerHeightPercent.toFloat(),
            range = SidebarSettings.TRIGGER_HEIGHT_RANGE,
            display = "${settings.triggerHeightPercent} %",
            onValueChange = {
                AppContainer.settings.update { s -> s.copy(triggerHeightPercent = it.roundToInt()) }
            },
            hint = "热区始终垂直居中；缩小热区可减少与全面屏手势的冲突"
        )
    }

    HintText("轻点屏幕边缘的手势条位置即可唤出侧边栏（手势条平时隐藏，进入设置页时会显示）；在细条上向内滑动超过设定距离同样可以。")
}

private fun sensitivityHint(value: Int): String = when {
    value <= 18 -> "非常灵敏：轻扫即可唤出，但在全屏应用中可能误触"
    value <= 40 -> "均衡：推荐日常使用"
    else -> "稳健：需要较长滑动，误触最少"
}
