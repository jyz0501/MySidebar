package com.mysidebar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysidebar.AppContainer
import com.mysidebar.BuildConfig
import com.mysidebar.data.SidebarSettings
import com.mysidebar.ui.components.PrimaryButton
import com.mysidebar.ui.components.SectionCard
import com.mysidebar.ui.components.SwitchRow
import com.mysidebar.ui.theme.TextPrimary
import com.mysidebar.ui.theme.TextSecondary
import com.mysidebar.util.Permissions

@Composable
fun GeneralSection(settings: SidebarSettings) {
    val context = LocalContext.current

    SectionCard {
        SwitchRow(
            title = "暂停唤出",
            subtitle = "保留服务但暂时不响应边缘手势，通知栏提供「收起」操作",
            checked = settings.paused,
            onCheckedChange = {
                AppContainer.settings.update { s -> s.copy(paused = it) }
            },
            icon = Icons.Default.Close
        )
    }

    SectionCard(title = "自启动") {
        SwitchRow(
            title = "开机自启动",
            checked = settings.autoStart,
            onCheckedChange = {
                AppContainer.settings.update { s -> s.copy(autoStart = it) }
            },
            icon = Icons.Default.Refresh
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PrimaryButton(
                text = "自启动设置",
                modifier = Modifier.weight(1f),
                onClick = { Permissions.openAutoStartSettings(context) }
            )
        }
    }

    SectionCard(title = "关于") {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Text(
                "轻边栏 · ${BuildConfig.VERSION_NAME}",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Kotlin + Jetpack Compose + Material 3 · minSdk 26",
                color = TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "轻量、无广告、不联网；所有配置仅保存在本机。",
                color = TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
