package com.qianxian.MySidebar.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qianxian.MySidebar.AppContainer
import com.qianxian.MySidebar.data.AppInfo
import com.qianxian.MySidebar.ui.theme.Purple
import com.qianxian.MySidebar.ui.theme.SurfaceVariant
import com.qianxian.MySidebar.ui.theme.Teal
import com.qianxian.MySidebar.ui.theme.TextPrimary
import com.qianxian.MySidebar.ui.theme.TextSecondary

private data class PickerState(val loading: Boolean = true, val apps: List<AppInfo> = emptyList())

/** 应用图标的异步加载 + 内存缓存展示。 */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = AppContainer.apps.iconFor(packageName)
    }
    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = null,
            modifier = modifier.size(size)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(Purple.copy(alpha = 0.45f))
        )
    }
}

@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<AppInfo>) -> Unit,
    existingPackageNames: Set<String> = emptySet()
) {
    val state by produceState(initialValue = PickerState()) {
        value = PickerState(loading = false, apps = AppContainer.apps.loadLaunchableApps())
    }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    val filtered = remember(state.apps, query, existingPackageNames) {
        state.apps
            .filter { it.packageName !in existingPackageNames }
            .filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1B1B27)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("添加应用", color = TextPrimary, fontSize = 20.sp)
                Text(
                    "选择要放进侧边栏的应用，可多选；已添加的应用不显示",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("搜索应用名称", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.size(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            color = Teal,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        val userApps = filtered.filter { !it.isSystem }
                        val systemApps = filtered.filter { it.isSystem }
                        val showDivider = userApps.isNotEmpty() && systemApps.isNotEmpty()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(userApps, key = { it.packageName }) { app ->
                                AppRow(app, app.packageName in selected) { checked ->
                                    selected = if (checked) selected + app.packageName
                                    else selected - app.packageName
                                }
                            }
                            if (showDivider) {
                                item {
                                    Column {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                                .height(1.dp)
                                                .background(Color.White.copy(alpha = 0.12f))
                                        )
                                        Text(
                                            "系统应用",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(start = 10.dp, top = 2.dp)
                                        )
                                    }
                                }
                            }
                            items(systemApps, key = { it.packageName }) { app ->
                                AppRow(app, app.packageName in selected) { checked ->
                                    selected = if (checked) selected + app.packageName
                                    else selected - app.packageName
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton(
                        text = "添加 ${selected.size} 个",
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            onConfirm(state.apps.filter { it.packageName in selected })
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) Purple.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.03f))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.packageName, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                color = TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                app.packageName,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = Purple)
        )
    }
}
