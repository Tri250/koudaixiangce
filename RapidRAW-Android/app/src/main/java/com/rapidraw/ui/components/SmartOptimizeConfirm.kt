package com.rapidraw.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.theme.BadgeBg
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary

@Composable
fun SmartOptimizeConfirm(
    visible: Boolean,
    adjustments: Adjustments,
    onAccept: () -> Unit,
    onUndo: () -> Unit,
    onCompare: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            cornerRadius = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
            // Title row with badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "智能优化完成",
                    color = TextPrimary,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "已优化",
                    color = HasselbladOrange,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(BadgeBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Parameter summary
            val changes = buildList {
                if (adjustments.exposure != 0f) add("曝光 ${if (adjustments.exposure > 0) "+" else ""}${String.format("%.1f", adjustments.exposure)}")
                if (adjustments.contrast != 0f) add("对比 ${adjustments.contrast.toInt()}")
                if (adjustments.highlights != 0f) add("高光 ${adjustments.highlights.toInt()}")
                if (adjustments.shadows != 0f) add("阴影 ${adjustments.shadows.toInt()}")
                if (adjustments.temperature != 0f) add("冷暖 ${adjustments.temperature.toInt()}")
                if (adjustments.saturation != 0f) add("饱和 ${adjustments.saturation.toInt()}")
                if (adjustments.clarity != 0f) add("清晰度 ${adjustments.clarity.toInt()}")
                if (adjustments.vibrance != 0f) add("自然饱和 ${adjustments.vibrance.toInt()}")
                if (adjustments.dehaze != 0f) add("去雾 ${adjustments.dehaze.toInt()}")
            }
            
            if (changes.isNotEmpty()) {
                Text(
                    text = changes.joinToString("  ·  "),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onUndo,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("撤销", color = TextSecondary)
                }
                
                TextButton(
                    onClick = onCompare,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("查看对比", color = TextSecondary)
                }
                
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HasselbladOrange,
                    ),
                ) {
                    Text("接受", color = TextPrimary)
                }
            }
            }
        }
    }
}
