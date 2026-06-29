package com.rapidraw.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.ColorOS16Colors
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeBright
import com.rapidraw.ui.theme.TextSecondary

/**
 * Portrait retouching control panel.
 * Provides sliders for skin smoothing, eye brighten, teeth whiten, face shape adjustments,
 * and other portrait enhancement features.
 */
@Composable
fun PortraitPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val adjustments = viewModel.adjustments.value

    val sectionExpanded = remember { mutableStateMapOf(
        "face_shape" to true,
        "skin" to true,
        "eyes" to false,
        "features" to false,
    ) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                ColorOS16Colors.Surface2,
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            )
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ColorOS16Colors.HairlineStrong),
            )
        }

        // Title
        Text(
            text = "人像修饰",
            color = ColorOS16Colors.TextHigh,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // ── Face Shape Section ──────────────────────────────────────
        CollapsibleSection(
            title = "脸型",
            expanded = sectionExpanded["face_shape"] == true,
            onToggle = { sectionExpanded["face_shape"] = !(sectionExpanded["face_shape"] == true) },
        ) {
            HasselSlider(
                label = "瘦脸",
                value = adjustments.portraitFaceSlim,
                range = 0f..100f,
                onValueChange = { viewModel.updateAdjustment("portraitFaceSlim", it) },
                defaultValue = 0f,
            )
            HasselSlider(
                label = "下颌",
                value = adjustments.portraitJawContour,
                range = -100f..100f,
                onValueChange = { viewModel.updateAdjustment("portraitJawContour", it) },
                defaultValue = 0f,
            )
            HasselSlider(
                label = "鼻子",
                value = adjustments.portraitNoseReshape,
                range = -100f..100f,
                onValueChange = { viewModel.updateAdjustment("portraitNoseReshape", it) },
                defaultValue = 0f,
            )
        }

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            color = ColorOS16Colors.Hairline,
            thickness = 1.dp,
        )

        // ── Skin Section ────────────────────────────────────────────
        CollapsibleSection(
            title = "皮肤",
            expanded = sectionExpanded["skin"] == true,
            onToggle = { sectionExpanded["skin"] = !(sectionExpanded["skin"] == true) },
        ) {
            HasselSlider(
                label = "磨皮",
                value = adjustments.portraitSkinSmoothing,
                range = 0f..100f,
                onValueChange = { viewModel.updateAdjustment("portraitSkinSmoothing", it) },
                defaultValue = 0f,
            )
        }

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            color = ColorOS16Colors.Hairline,
            thickness = 1.dp,
        )

        // ── Eyes Section ────────────────────────────────────────────
        CollapsibleSection(
            title = "眼部",
            expanded = sectionExpanded["eyes"] == true,
            onToggle = { sectionExpanded["eyes"] = !(sectionExpanded["eyes"] == true) },
        ) {
            HasselSlider(
                label = "亮眼",
                value = adjustments.portraitEyeBrighten,
                range = 0f..100f,
                onValueChange = { viewModel.updateAdjustment("portraitEyeBrighten", it) },
                defaultValue = 0f,
            )
            HasselSlider(
                label = "大眼",
                value = adjustments.portraitEyeEnlarge,
                range = 0f..100f,
                onValueChange = { viewModel.updateAdjustment("portraitEyeEnlarge", it) },
                defaultValue = 0f,
            )
        }

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            color = ColorOS16Colors.Hairline,
            thickness = 1.dp,
        )

        // ── Features Section ────────────────────────────────────────
        CollapsibleSection(
            title = "五官",
            expanded = sectionExpanded["features"] == true,
            onToggle = { sectionExpanded["features"] = !(sectionExpanded["features"] == true) },
        ) {
            HasselSlider(
                label = "白牙",
                value = adjustments.portraitTeethWhiten,
                range = 0f..100f,
                onValueChange = { viewModel.updateAdjustment("portraitTeethWhiten", it) },
                defaultValue = 0f,
            )
            HasselSlider(
                label = "唇色",
                value = adjustments.portraitLipColor,
                range = 0f..100f,
                onValueChange = { viewModel.updateAdjustment("portraitLipColor", it) },
                defaultValue = 0f,
            )
            HasselSlider(
                label = "腮红",
                value = adjustments.portraitCheekBlush,
                range = 0f..100f,
                onValueChange = { viewModel.updateAdjustment("portraitCheekBlush", it) },
                defaultValue = 0f,
            )
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val accentColor by animateColorAsState(
        targetValue = if (expanded) HasselbladOrangeBright else TextSecondary,
        label = "sectionAccent_$title",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = ColorOS16Colors.TextHigh,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "\u25BC" else "\u25B6",
                color = accentColor,
                fontSize = 12.sp,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                content()
            }
        }
    }
}