@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.qianxian.MySidebar.ui.sidebar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qianxian.MySidebar.AppContainer
import com.qianxian.MySidebar.data.AppRepository
import com.qianxian.MySidebar.data.IconEntry
import com.qianxian.MySidebar.data.Side
import com.qianxian.MySidebar.data.SidebarSettings
import com.qianxian.MySidebar.data.VerticalAnchor
import com.qianxian.MySidebar.service.ServiceBridge
import com.qianxian.MySidebar.ui.theme.Purple
import com.qianxian.MySidebar.ui.theme.Teal
import com.qianxian.MySidebar.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

sealed interface SidebarAction {
    data object Close : SidebarAction
    data object ToggleEdit : SidebarAction
    data object OpenPicker : SidebarAction
    data class Launch(val packageName: String, val component: String? = null) : SidebarAction
    data class Remove(val packageName: String) : SidebarAction
    data class ToggleLock(val packageName: String) : SidebarAction
    data class Move(val packageName: String, val delta: Int) : SidebarAction
}

/**
 * 悬浮窗根布局：全屏遮罩（点击空白处收起）+ 停靠在左/右的胶囊图标条。
 * 动画进度在 layout/draw 阶段读取，避免每一帧都触发重组。
 */
@Composable
fun SidebarRoot(
    expandedState: StateFlow<Boolean>,
    editingState: StateFlow<Boolean>,
    configVersion: StateFlow<Int>,
    screenHeightProvider: () -> Int,
    apps: AppRepository,
    onAction: (SidebarAction) -> Unit
) {
    val settings by AppContainer.settings.settings.collectAsState()
    val expanded by expandedState.collectAsState()
    val editing by editingState.collectAsState()
    val version by configVersion.collectAsState()
    val preview by ServiceBridge.previewMode.collectAsState()
    val demo by ServiceBridge.demoActive.collectAsState()
    val screenHeightPx = remember(version) { screenHeightProvider() }

    // 演示态与常驻预览态相互独立：演示结束只复位 demo，不会关掉外观页预览。
    val shown = expanded || preview || demo
    val passThrough = preview || demo
    val progress = animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(settings.animationDurationMs, easing = FastOutSlowInEasing),
        label = "sidebar-progress"
    )
    val direction = if (settings.side == Side.LEFT) -1f else 1f
    val widthPx = with(LocalDensity.current) { settings.panelWidthDp.dp.toPx() }
    val panelHeight = with(LocalDensity.current) {
        (screenHeightPx * settings.panelHeightPercent / 100f).toDp()
    }
    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (passThrough) 0f else progress.value * 0.45f }
                .background(Brush.radialGradient(listOf(Color(0x33000000), Color(0x99000000))))
                .then(
                    if (expanded && !passThrough) Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onAction(SidebarAction.Close) }
                    ) else Modifier
                )
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = when (settings.anchor) {
                VerticalAnchor.TOP ->
                    if (settings.side == Side.LEFT) Alignment.TopStart else Alignment.TopEnd
                VerticalAnchor.CENTER ->
                    if (settings.side == Side.LEFT) Alignment.CenterStart else Alignment.CenterEnd
                VerticalAnchor.BOTTOM ->
                    if (settings.side == Side.LEFT) Alignment.BottomStart else Alignment.BottomEnd
            }
        ) {
            SidebarPanel(
                settings = settings,
                editing = editing,
                apps = apps,
                modifier = Modifier
                    .width(settings.panelWidthDp.dp)
                    .height(panelHeight)
                    .offset {
                        IntOffset((direction * widthPx * (1f - progress.value)).roundToInt(), 0)
                    }
                    .graphicsLayer {
                        alpha = settings.panelAlpha * progress.value
                        scaleX = 0.92f + 0.08f * progress.value
                        scaleY = 0.96f + 0.04f * progress.value
                    },
                onAction = onAction
            )
        }
    }
}

@Composable
private fun SidebarPanel(
    settings: SidebarSettings,
    editing: Boolean,
    apps: AppRepository,
    modifier: Modifier,
    onAction: (SidebarAction) -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val scrollState = rememberScrollState()
    var itemHeightPx by remember { mutableStateOf(0f) }
    var draggingPackage by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val currentIcons by rememberUpdatedState(settings.icons)
    val currentEditing by rememberUpdatedState(editing)

    LaunchedEffect(settings.panelWidthDp) { itemHeightPx = 0f }

    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xEE28283C), Color(0xF2141420))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color(0x55FFFFFF), Teal.copy(alpha = 0.35f))),
                shape
            )
            .padding(horizontal = 5.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                // 长按进入编辑并原地拖拽排序；未触发长按时不消费事件，列表仍可正常滚动。
                .pointerInput(Unit) {
                    var draggedPkg: String? = null
                    var accumulated = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            // 长按即进入编辑模式；若落点是可编辑图标，则同时开始拖拽。
                            if (!currentEditing) onAction(SidebarAction.ToggleEdit)
                            val list = currentIcons
                            val height = (itemHeightPx + 2.dp.toPx()).coerceAtLeast(1f)
                            val index = ((offset.y + scrollState.value) / height)
                                .toInt()
                                .coerceIn(0, list.lastIndex.coerceAtLeast(0))
                            if (list.isEmpty() || list[index].locked) {
                                draggedPkg = null
                            } else {
                                draggedPkg = list[index].packageName
                                draggingPackage = draggedPkg
                            }
                            accumulated = 0f
                            dragOffset = 0f
                        },
                        onDrag = { _, amount ->
                            val pkg = draggedPkg ?: return@detectDragGesturesAfterLongPress
                            accumulated += amount.y
                            dragOffset = accumulated
                            val height = (itemHeightPx + 2.dp.toPx()).coerceAtLeast(1f)
                            val steps = (accumulated / height).toInt()
                            if (steps == 0) return@detectDragGesturesAfterLongPress
                            val list = currentIcons
                            val from = list.indexOfFirst { it.packageName == pkg }
                            if (from < 0) return@detectDragGesturesAfterLongPress
                            val target = (from + steps).coerceIn(0, list.lastIndex)
                            if (target != from && !list[target].locked) {
                                onAction(SidebarAction.Move(pkg, target - from))
                                accumulated -= steps * height
                                dragOffset = accumulated
                            } else {
                                accumulated = 0f
                                dragOffset = 0f
                            }
                        },
                        onDragEnd = {
                            draggedPkg = null
                            draggingPackage = null
                            accumulated = 0f
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            draggedPkg = null
                            draggingPackage = null
                            accumulated = 0f
                            dragOffset = 0f
                        }
                    )
                }
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (settings.icons.isEmpty()) {
                Text(
                    text = "暂无图标",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 18.dp)
                )
            } else {
                settings.icons.forEach { entry ->
                    SidebarIconItem(
                        entry = entry,
                        editing = editing,
                        apps = apps,
                        widthDp = settings.panelWidthDp,
                        dragging = draggingPackage == entry.packageName,
                        dragOffset = if (draggingPackage == entry.packageName) dragOffset else 0f,
                        onItemMeasured = { height ->
                            if (itemHeightPx <= 0f && height > 0f) itemHeightPx = height
                        },
                        onAction = onAction
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxWidth(0.6f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Purple, Color.Transparent)))
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallActionButton(Icons.Default.Add, Teal, "添加应用") {
                onAction(SidebarAction.OpenPicker)
            }
        }
    }
}

@Composable
private fun SidebarIconItem(
    entry: IconEntry,
    editing: Boolean,
    apps: AppRepository,
    widthDp: Int,
    dragging: Boolean,
    dragOffset: Float,
    onItemMeasured: (Float) -> Unit,
    onAction: (SidebarAction) -> Unit
) {
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = entry.packageName) {
        value = apps.iconFor(entry.packageName)
    }
    val iconSize = (widthDp * 0.5f).dp.coerceIn(24.dp, 50.dp)
    val boxSize = iconSize + 12.dp
    val label = entry.label.ifBlank { entry.packageName.substringAfterLast('.') }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .onGloballyPositioned { onItemMeasured(it.size.height.toFloat()) }
            .graphicsLayer {
                if (dragging) {
                    translationY = dragOffset
                    scaleX = 1.08f
                    scaleY = 1.08f
                    shadowElevation = 12f
                }
            }
            .background(
                if (dragging) Purple.copy(alpha = 0.22f) else Color.Transparent,
                RoundedCornerShape(15.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 外层不裁剪，右上角 X 角标才能溢出显示。
        Box(modifier = Modifier.size(boxSize), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(boxSize)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (editing) Color.White.copy(alpha = 0.11f)
                        else Color.White.copy(alpha = 0.05f)
                    )
                    .clickable {
                        // 编辑态点一下图标即退出编辑，不再依赖顶部完成按钮。
                        if (editing) onAction(SidebarAction.ToggleEdit)
                        else onAction(SidebarAction.Launch(entry.packageName, entry.component))
                    },
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon!!,
                        contentDescription = label,
                        modifier = Modifier.size(iconSize)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .clip(CircleShape)
                            .background(Purple.copy(alpha = 0.55f))
                    )
                }
                if (entry.locked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "已锁定，不可删除或拖动",
                        tint = Teal,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp)
                            .size(11.dp)
                    )
                }
            }

            if (editing && !entry.locked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE74C3C))
                        .clickable { onAction(SidebarAction.Remove(entry.packageName)) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "删除 $label",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        if (widthDp >= 62) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun SmallActionButton(
    icon: ImageVector,
    tint: Color,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
    }
}
