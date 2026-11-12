package com.mysidebar.ui.settings

import androidx.compose.runtime.Composable
import com.mysidebar.AppContainer
import com.mysidebar.data.SidebarSettings
import com.mysidebar.ui.components.HintText
import com.mysidebar.ui.components.SectionCard
import com.mysidebar.ui.components.SettingSlider
import kotlin.math.roundToInt

@Composable
fun GestureSection(settings: SidebarSettings) {
    SectionCard(title = "边缘灵敏度", subtitle = "触发所需的横向滑动距离") {
        SettingSlider(
            label = "滑动触发距离",
            value = settings.edgeSensitivityDp.toFloat(),
            range = SidebarSettings.SENSITIVITY_RANGE,
            display = "${settings.edgeSensitivityDp} dp",
            onValueChange = {
                AppContainer.settings.update { s -> s.copy(edgeSensitivityDp = it.roundToInt()) }
            },
            hint = sensitivityHint(settings.edgeSensitivityDp)
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
