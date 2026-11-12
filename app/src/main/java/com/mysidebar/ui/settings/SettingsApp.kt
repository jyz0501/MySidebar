package com.mysidebar.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mysidebar.AppContainer
import com.mysidebar.service.ServiceBridge
import com.mysidebar.ui.components.AppHeader
import com.mysidebar.ui.components.StatusPill

enum class SettingsPage { Home, Appearance, Gesture, Icons, General }

@Composable
fun SettingsApp(
    startPage: SettingsPage = SettingsPage.Home,
    openPicker: Boolean = false
) {
    var page by remember { mutableStateOf(startPage) }
    var pickerPending by remember { mutableStateOf(openPicker) }
    val settings by AppContainer.settings.settings.collectAsState()
    val previewActive by ServiceBridge.previewMode.collectAsState()

    val title = when (page) {
        SettingsPage.Home -> "轻边栏"
        SettingsPage.Appearance -> "外观与动画"
        SettingsPage.Gesture -> "手势设置"
        SettingsPage.Icons -> "图标管理"
        SettingsPage.General -> "通用设置"
    }
    val subtitle = when (page) {
        SettingsPage.Home -> "让常用应用，触手可及"
        SettingsPage.Appearance -> "所见即所得地调整侧边栏"
        SettingsPage.Gesture -> "自定义边缘唤出的手感"
        SettingsPage.Icons -> "添加、锁定与拖拽排序"
        SettingsPage.General -> "暂停唤出与开机自启动"
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF121212), Color(0xFF161226), Color(0xFF101018))
                    )
                )
                .padding(padding)
        ) {
            // 顶部氛围光晕
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0x226C5CE7), Color.Transparent),
                            radius = 900f
                        )
                    )
            )

            Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                AppHeader(
                    title = title,
                    subtitle = subtitle,
                    onBack = if (page != SettingsPage.Home) {
                        { page = SettingsPage.Home }
                    } else null,
                    trailing = {
                        when {
                            page == SettingsPage.Home -> StatusPill(
                                ok = settings.masterEnabled && !settings.paused,
                                okText = "运行中",
                                badText = if (settings.paused) "已暂停" else "已停用"
                            )
                            page == SettingsPage.Appearance && previewActive -> StatusPill(
                                ok = true,
                                okText = "预览中",
                                badText = ""
                            )
                        }
                    }
                )

                Crossfade(targetState = page, modifier = Modifier.weight(1f), label = "settings-page") { target ->
                    // 预览侧边栏是独立顶层浮层，设置页布局与宽度完全不变。
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        when (target) {
                            SettingsPage.Home -> SettingsHome(
                                settings = settings,
                                onNavigate = { page = it }
                            )
                            SettingsPage.Appearance -> AppearanceSection(settings = settings)
                            SettingsPage.Gesture -> GestureSection(settings = settings)
                            SettingsPage.Icons -> IconsSection(
                                settings = settings,
                                openPickerInitially = pickerPending,
                                onPickerConsumed = { pickerPending = false }
                            )
                            SettingsPage.General -> GeneralSection(settings = settings)
                        }
                    }
                }
            }
        }
    }
}
