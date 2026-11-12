package com.mysidebar.ui.sidebar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysidebar.AppContainer
import com.mysidebar.data.Side
import com.mysidebar.ui.theme.Purple
import com.mysidebar.ui.theme.PurpleLight
import com.mysidebar.ui.theme.Teal
import com.mysidebar.ui.theme.TextPrimary
import com.mysidebar.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * 顶层引导气泡：独立的、仅包裹自身尺寸的可触摸悬浮窗内容。
 * 箭头随停靠方向翻转并指向屏幕边缘的细条热区；「知道了」点击后淡出并持久化关闭。
 */
@Composable
fun GuideBubble(onDismiss: () -> Unit) {
    val settings by AppContainer.settings.settings.collectAsState()
    val side = settings.side

    val breathing = rememberInfiniteTransition(label = "guide-breathing")
    val glow by breathing.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "guide-glow"
    )

    var leaving by remember { mutableStateOf(false) }
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (leaving) 0f else 1f,
        animationSpec = tween(220),
        label = "guide-alpha"
    )
    LaunchedEffect(leaving) {
        if (leaving) {
            delay(230)
            onDismiss()
        }
    }

    Row(
        modifier = Modifier.graphicsLayer { alpha = bubbleAlpha },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (side == Side.LEFT) GuideArrow(isLeft = true, glow = glow)

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xF22C2C42), Color(0xF2181826)))
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(PurpleLight.copy(alpha = glow), Teal.copy(alpha = glow))
                    ),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 198.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Teal)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "从这里唤出侧边栏",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "从屏幕${if (side == Side.LEFT) "左" else "右"}侧边缘向内滑动，或轻点边缘细条即可唤出。",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                Spacer(Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Brush.horizontalGradient(listOf(Purple, PurpleLight)))
                        .clickable { leaving = true }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "知道了",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (side == Side.RIGHT) GuideArrow(isLeft = false, glow = glow)
    }
}

/** 用旋转的菱形做指向箭头，始终指向屏幕边缘热区。 */
@Composable
private fun GuideArrow(isLeft: Boolean, glow: Float) {
    Box(
        modifier = Modifier
            .offset(x = if (isLeft) 7.dp else (-7).dp)
            .size(13.dp)
            .rotate(45f)
            .background(
                Brush.linearGradient(
                    listOf(PurpleLight.copy(alpha = glow), Teal.copy(alpha = glow))
                )
            )
    )
}
