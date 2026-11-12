@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.qianxian.MySidebar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qianxian.MySidebar.AppContainer
import com.qianxian.MySidebar.data.IconEntry
import com.qianxian.MySidebar.data.SidebarSettings
import com.qianxian.MySidebar.ui.components.AppIcon
import com.qianxian.MySidebar.ui.components.AppPickerDialog
import com.qianxian.MySidebar.ui.components.IconActionButton
import com.qianxian.MySidebar.ui.components.PrimaryButton
import com.qianxian.MySidebar.ui.components.SectionCard
import com.qianxian.MySidebar.ui.theme.Purple
import com.qianxian.MySidebar.ui.theme.PurpleLight
import com.qianxian.MySidebar.ui.theme.Teal
import com.qianxian.MySidebar.ui.theme.TextPrimary
import com.qianxian.MySidebar.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun IconsSection(
    settings: SidebarSettings,
    openPickerInitially: Boolean = false,
    onPickerConsumed: () -> Unit = {}
) {
    var showPicker by remember { mutableStateOf(openPickerInitially) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (openPickerInitially) {
            showPicker = true
            onPickerConsumed()
        }
    }

    SectionCard(
        title = "应用快捷方式",
        subtitle = "长按拖拽排序，点图标右上角 X 角标删除；锁定项不可删除或移动"
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PrimaryButton("添加应用", onClick = { showPicker = true }, modifier = Modifier.weight(1f))
            PrimaryButton(
                text = "恢复默认",
                outlined = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        val defaults = AppContainer.apps.defaultIconEntries()
                        AppContainer.settings.update { it.copy(icons = defaults) }
                    }
                }
            )
        }
        Spacer(Modifier.height(5.dp))
    }

    if (settings.icons.isEmpty()) {
        SectionCard {
            EmptyState(onAdd = { showPicker = true })
        }
    } else {
        IconList(icons = settings.icons)
    }

    if (showPicker) {
        AppPickerDialog(
            onDismiss = { showPicker = false },
            onConfirm = { apps ->
                AppContainer.settings.update { current ->
                    val added = apps.map { IconEntry(it.packageName, it.label) }
                    current.copy(
                        icons = (current.icons + added)
                            .distinctBy { it.packageName }
                            .take(40)
                    )
                }
                showPicker = false
            }
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Brush.linearGradient(listOf(Purple, PurpleLight))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("还没有任何图标", color = TextPrimary, fontSize = 15.sp)
        Text(
            "添加常用应用，或从默认图标集开始",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 5.dp)
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton("添加应用", onClick = onAdd)
    }
}

@Composable
private fun IconList(icons: List<IconEntry>) {
    val itemHeight = 60.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val currentIcons by rememberUpdatedState(icons)
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragDelta by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1B1B26))
            .pointerInput(Unit) {
                var index = -1
                var accumulated = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val list = currentIcons
                        val candidate = (offset.y / itemHeightPx).toInt()
                            .coerceIn(0, list.lastIndex.coerceAtLeast(0))
                        if (list[candidate].locked) {
                            index = -1
                            draggingIndex = -1
                        } else {
                            index = candidate
                            draggingIndex = candidate
                        }
                        accumulated = 0f
                        dragDelta = 0f
                    },
                    onDrag = { _, amount ->
                        if (index < 0) return@detectDragGesturesAfterLongPress
                        accumulated += amount.y
                        dragDelta = accumulated
                        val steps = (accumulated / itemHeightPx).toInt()
                        if (steps == 0) return@detectDragGesturesAfterLongPress
                        val list = currentIcons
                        val target = (index + steps).coerceIn(0, list.lastIndex)
                        if (target != index && !list[target].locked) {
                            AppContainer.settings.update { current ->
                                val mutable = current.icons.toMutableList()
                                if (index in mutable.indices && target in mutable.indices) {
                                    val moved = mutable.removeAt(index)
                                    mutable.add(target, moved)
                                    current.copy(icons = mutable)
                                } else current
                            }
                            index = target
                            draggingIndex = target
                            accumulated -= steps * itemHeightPx
                            dragDelta = accumulated
                        } else {
                            accumulated = 0f
                            dragDelta = 0f
                        }
                    },
                    onDragEnd = {
                        index = -1
                        draggingIndex = -1
                        accumulated = 0f
                        dragDelta = 0f
                    },
                    onDragCancel = {
                        index = -1
                        draggingIndex = -1
                        accumulated = 0f
                        dragDelta = 0f
                    }
                )
            }
    ) {
        icons.forEachIndexed { index, entry ->
            IconListRow(
                entry = entry,
                index = index,
                dragging = draggingIndex == index,
                offsetY = dragDelta,
                onToggleLock = {
                    AppContainer.settings.update { current ->
                        current.copy(
                            icons = current.icons.map {
                                if (it.packageName == entry.packageName) it.copy(locked = !it.locked) else it
                            }
                        )
                    }
                },
                onRemove = {
                    if (!entry.locked) {
                        AppContainer.settings.update { current ->
                            current.copy(icons = current.icons.filterNot { it.packageName == entry.packageName })
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun IconListRow(
    entry: IconEntry,
    index: Int,
    dragging: Boolean,
    offsetY: Float,
    onToggleLock: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .graphicsLayer {
                if (dragging) {
                    translationY = offsetY
                    scaleX = 1.02f
                    scaleY = 1.02f
                    shadowElevation = 10f
                }
            }
            .background(if (dragging) Purple.copy(alpha = 0.26f) else Color.Transparent)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${index + 1}",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(18.dp)
        )
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            AppIcon(entry.packageName, size = 36.dp)
            if (!entry.locked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE74C3C))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.label.ifBlank { entry.packageName.substringAfterLast('.') },
                color = TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                entry.component ?: entry.packageName,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconActionButton(
            Icons.Default.Lock,
            if (entry.locked) Teal else TextSecondary.copy(alpha = 0.45f),
            if (entry.locked) "解锁" else "锁定",
            onClick = onToggleLock,
            size = 28.dp
        )
    }
}
