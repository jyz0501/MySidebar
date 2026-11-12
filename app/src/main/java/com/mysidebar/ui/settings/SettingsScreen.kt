package com.mysidebar.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mysidebar.AppContainer
import com.mysidebar.data.Side
import com.mysidebar.data.SidebarSettings
import com.mysidebar.service.ServiceBridge
import com.mysidebar.service.ServiceController
import com.mysidebar.ui.components.HintText
import com.mysidebar.ui.components.SectionCard
import com.mysidebar.ui.components.SettingNavItem
import com.mysidebar.ui.theme.Purple
import com.mysidebar.ui.theme.PurpleLight
import com.mysidebar.ui.theme.SurfaceVariant
import com.mysidebar.ui.theme.Teal
import com.mysidebar.ui.theme.TextPrimary
import com.mysidebar.ui.theme.TextSecondary
import com.mysidebar.ui.theme.Warning
import com.mysidebar.util.Permissions

@Composable
fun SettingsHome(
    settings: SidebarSettings,
    onNavigate: (SettingsPage) -> Unit
) {
    val context = LocalContext.current
    val overlayGranted by rememberOverlayPermission()

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "总开关",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.masterEnabled,
                    onCheckedChange = { toggleMaster(context, it) },
                    colors = sidebarSwitchColors()
                )
            }
            Spacer(Modifier.width(16.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "悬浮窗权限",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = overlayGranted,
                    onCheckedChange = { Permissions.openOverlaySettings(context) },
                    colors = sidebarSwitchColors()
                )
            }
        }
    }

    SectionCard(title = "功能设置") {
        SettingNavItem(
            Icons.Default.Settings,
            "外观与动画",
            "位置 · 宽度 · 高度 · 划出速度",
            PurpleLight
        ) { onNavigate(SettingsPage.Appearance) }
        Spacer(Modifier.height(3.dp))
        SettingNavItem(
            Icons.Default.Menu,
            "手势设置",
            "灵敏度 · 触发区高度",
            Teal
        ) { onNavigate(SettingsPage.Gesture) }
        Spacer(Modifier.height(3.dp))
        SettingNavItem(
            Icons.Default.Add,
            "图标管理",
            "${settings.icons.size} 个应用 · 添加 / 删除 / 锁定 / 排序",
            Warning
        ) { onNavigate(SettingsPage.Icons) }
        Spacer(Modifier.height(3.dp))
        SettingNavItem(
            Icons.Default.Notifications,
            "通用设置",
            "暂停唤出 · 自启动",
            Purple
        ) { onNavigate(SettingsPage.General) }
    }

    HintText("提示：手势条平时隐藏，轻点屏幕${if (settings.side == Side.LEFT) "左" else "右"}侧边缘即可唤出侧边栏；点击空白处自动收起。")
}

private fun toggleMaster(context: android.content.Context, enabled: Boolean) {
    AppContainer.settings.update { it.copy(masterEnabled = enabled) }
    ServiceController.sync(context)
    ServiceBridge.sync()
}

/** 首页两枚开关共用同一套配色，保持与设置页其它开关一致。 */
@Composable
private fun sidebarSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Purple,
    uncheckedThumbColor = TextSecondary,
    uncheckedTrackColor = SurfaceVariant
)

@Composable
private fun rememberOverlayPermission(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Permissions.canDrawOverlays(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = Permissions.canDrawOverlays(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return state
}
