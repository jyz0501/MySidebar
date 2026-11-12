package com.qianxian.MySidebar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.qianxian.MySidebar.AppContainer
import com.qianxian.MySidebar.data.Side
import com.qianxian.MySidebar.data.SidebarSettings
import com.qianxian.MySidebar.data.VerticalAnchor
import com.qianxian.MySidebar.service.ServiceBridge
import com.qianxian.MySidebar.ui.components.SectionCard
import com.qianxian.MySidebar.ui.components.SettingSlider
import com.qianxian.MySidebar.ui.theme.Purple
import com.qianxian.MySidebar.ui.theme.PurpleLight
import com.qianxian.MySidebar.ui.theme.SurfaceVariant
import com.qianxian.MySidebar.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * 外观设置：进入本页即在真实悬浮侧边栏上实时预览，
 * 预览是独立顶层浮层，不改变设置页布局；遮罩透明且窗口触摸穿透。
 */
@Composable
fun AppearanceSection(settings: SidebarSettings) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> ServiceBridge.startPreview()
                Lifecycle.Event.ON_PAUSE -> ServiceBridge.stopPreview()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            ServiceBridge.startPreview()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            ServiceBridge.stopPreview()
        }
    }

    SectionCard(title = "停靠位置", subtitle = "侧边栏与边缘触发条会一起移动到所选位置") {
        AnchorGrid(settings)
    }

    SectionCard(title = "尺寸与手感") {
        SettingSlider(
            label = "侧边栏宽度",
            value = settings.panelWidthDp.toFloat(),
            range = SidebarSettings.WIDTH_RANGE,
            display = "${settings.panelWidthDp} dp",
            onValueChange = { AppContainer.settings.update { s -> s.copy(panelWidthDp = it.roundToInt()) } }
        )
        SettingSlider(
            label = "侧边栏高度",
            value = settings.panelHeightPercent.toFloat(),
            range = SidebarSettings.HEIGHT_RANGE,
            display = "${settings.panelHeightPercent} %",
            onValueChange = { AppContainer.settings.update { s -> s.copy(panelHeightPercent = it.roundToInt()) } }
        )
        SettingSlider(
            label = "整体透明度",
            value = settings.panelAlpha,
            range = SidebarSettings.ALPHA_RANGE,
            display = "${(settings.panelAlpha * 100).roundToInt()} %",
            onValueChange = { AppContainer.settings.update { s -> s.copy(panelAlpha = it) } }
        )
        SettingSlider(
            label = "划出动画速度",
            value = settings.animationDurationMs.toFloat(),
            range = SidebarSettings.DURATION_RANGE,
            display = "${settings.animationDurationMs} ms",
            onValueChange = { AppContainer.settings.update { s -> s.copy(animationDurationMs = it.roundToInt()) } },
            hint = "数值越小划出越快"
        )
    }
}

/** 停靠位置六宫格：左上 / 左中 / 左下 / 右上 / 右中 / 右下。 */
@Composable
private fun AnchorGrid(settings: SidebarSettings) {
    val options = listOf(
        Triple(Side.LEFT, VerticalAnchor.TOP, "左上"),
        Triple(Side.LEFT, VerticalAnchor.CENTER, "左中"),
        Triple(Side.LEFT, VerticalAnchor.BOTTOM, "左下"),
        Triple(Side.RIGHT, VerticalAnchor.TOP, "右上"),
        Triple(Side.RIGHT, VerticalAnchor.CENTER, "右中"),
        Triple(Side.RIGHT, VerticalAnchor.BOTTOM, "右下")
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        options.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { (side, anchor, label) ->
                    AnchorButton(
                        label = label,
                        selected = settings.side == side && settings.anchor == anchor,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppContainer.settings.update { it.copy(side = side, anchor = anchor) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AnchorButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) Brush.horizontalGradient(listOf(Purple, PurpleLight))
                else Brush.horizontalGradient(listOf(SurfaceVariant, SurfaceVariant))
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
