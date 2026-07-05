package com.alcedo.studio.ui.adjustments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.Adjustments
import com.alcedo.studio.ui.components.AdjustSection
import com.alcedo.studio.ui.components.AdjustSlider

@Composable
fun LightAdjustmentsPanel(
    adjustments: Adjustments,
    onAdjustmentsChange: (Adjustments) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AdjustSection("基础") {
            AdjustSlider(
                value = adjustments.exposure,
                onValueChange = { onAdjustmentsChange(adjustments.copy(exposure = it)) },
                label = "曝光",
                unit = "EV",
                valueRange = -50f..50f,
                defaultValue = 0f,
                tintColor = Color(0xFFFFD60A)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.brightness,
                onValueChange = { onAdjustmentsChange(adjustments.copy(brightness = it)) },
                label = "亮度",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFFD60A)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.contrast,
                onValueChange = { onAdjustmentsChange(adjustments.copy(contrast = it)) },
                label = "对比度",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFFD60A)
            )
        }

        AdjustSection("色调") {
            AdjustSlider(
                value = adjustments.highlights,
                onValueChange = { onAdjustmentsChange(adjustments.copy(highlights = it)) },
                label = "高光",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFFD60A)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.shadows,
                onValueChange = { onAdjustmentsChange(adjustments.copy(shadows = it)) },
                label = "阴影",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFFD60A)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.whites,
                onValueChange = { onAdjustmentsChange(adjustments.copy(whites = it)) },
                label = "白场",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFFD60A)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.blacks,
                onValueChange = { onAdjustmentsChange(adjustments.copy(blacks = it)) },
                label = "黑场",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFFD60A)
            )
        }

        AdjustSection("色调映射") {
            val toneMappers = listOf("标准" to "standard", "AgX" to "agx", "ACES" to "aces", "Reinhard" to "reinhard")
            com.alcedo.studio.ui.components.SegmentedControl(
                tabs = toneMappers.map { it.first },
                selectedIndex = toneMappers.indexOfFirst { it.second == adjustments.toneMapper }
                    .coerceAtLeast(0),
                onTabSelected = { index ->
                    onAdjustmentsChange(adjustments.copy(toneMapper = toneMappers[index].second))
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (adjustments.toneMapper == "agx") {
                Spacer(modifier = Modifier.height(12.dp))
                AdjustSlider(
                    value = adjustments.agxContrast,
                    onValueChange = { onAdjustmentsChange(adjustments.copy(agxContrast = it)) },
                    label = "AgX 对比度",
                    valueRange = -50f..50f,
                    defaultValue = 0f,
                    tintColor = Color(0xFFFFD60A)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ColorAdjustmentsPanel(
    adjustments: Adjustments,
    onAdjustmentsChange: (Adjustments) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AdjustSection("白平衡") {
            AdjustSlider(
                value = adjustments.temperature,
                onValueChange = { onAdjustmentsChange(adjustments.copy(temperature = it)) },
                label = "色温",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFF9500)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.tint,
                onValueChange = { onAdjustmentsChange(adjustments.copy(tint = it)) },
                label = "色调",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF32D74B)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.greenMagenta,
                onValueChange = { onAdjustmentsChange(adjustments.copy(greenMagenta = it)) },
                label = "绿品轴",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFBF5AF2)
            )
        }

        AdjustSection("自然饱和度") {
            AdjustSlider(
                value = adjustments.vibrance,
                onValueChange = { onAdjustmentsChange(adjustments.copy(vibrance = it)) },
                label = "自然饱和度",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF007AFF)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.saturation,
                onValueChange = { onAdjustmentsChange(adjustments.copy(saturation = it)) },
                label = "饱和度",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF007AFF)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.lightness,
                onValueChange = { onAdjustmentsChange(adjustments.copy(lightness = it)) },
                label = "明度",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF007AFF)
            )
        }

        AdjustSection("色彩分级") {
            TextSection("阴影")
            AdjustSlider(
                value = adjustments.colorGrading.shadows.hue,
                onValueChange = {
                    val cg = adjustments.colorGrading.copy(
                        shadows = adjustments.colorGrading.shadows.copy(hue = it)
                    )
                    onAdjustmentsChange(adjustments.copy(colorGrading = cg))
                },
                label = "色相",
                valueRange = 0f..360f,
                defaultValue = 0f,
                tintColor = Color(0xFF5E5CE6)
            )
            Spacer(modifier = Modifier.height(8.dp))
            AdjustSlider(
                value = adjustments.colorGrading.shadows.saturation,
                onValueChange = {
                    val cg = adjustments.colorGrading.copy(
                        shadows = adjustments.colorGrading.shadows.copy(saturation = it)
                    )
                    onAdjustmentsChange(adjustments.copy(colorGrading = cg))
                },
                label = "饱和度",
                valueRange = 0f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF5E5CE6)
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextSection("中间调")
            AdjustSlider(
                value = adjustments.colorGrading.midtones.hue,
                onValueChange = {
                    val cg = adjustments.colorGrading.copy(
                        midtones = adjustments.colorGrading.midtones.copy(hue = it)
                    )
                    onAdjustmentsChange(adjustments.copy(colorGrading = cg))
                },
                label = "色相",
                valueRange = 0f..360f,
                defaultValue = 0f,
                tintColor = Color(0xFF5E5CE6)
            )
            Spacer(modifier = Modifier.height(8.dp))
            AdjustSlider(
                value = adjustments.colorGrading.midtones.saturation,
                onValueChange = {
                    val cg = adjustments.colorGrading.copy(
                        midtones = adjustments.colorGrading.midtones.copy(saturation = it)
                    )
                    onAdjustmentsChange(adjustments.copy(colorGrading = cg))
                },
                label = "饱和度",
                valueRange = 0f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF5E5CE6)
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextSection("高光")
            AdjustSlider(
                value = adjustments.colorGrading.highlights.hue,
                onValueChange = {
                    val cg = adjustments.colorGrading.copy(
                        highlights = adjustments.colorGrading.highlights.copy(hue = it)
                    )
                    onAdjustmentsChange(adjustments.copy(colorGrading = cg))
                },
                label = "色相",
                valueRange = 0f..360f,
                defaultValue = 0f,
                tintColor = Color(0xFF5E5CE6)
            )
            Spacer(modifier = Modifier.height(8.dp))
            AdjustSlider(
                value = adjustments.colorGrading.highlights.saturation,
                onValueChange = {
                    val cg = adjustments.colorGrading.copy(
                        highlights = adjustments.colorGrading.highlights.copy(saturation = it)
                    )
                    onAdjustmentsChange(adjustments.copy(colorGrading = cg))
                },
                label = "饱和度",
                valueRange = 0f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF5E5CE6)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.colorGrading.balance,
                onValueChange = {
                    val cg = adjustments.colorGrading.copy(balance = it)
                    onAdjustmentsChange(adjustments.copy(colorGrading = cg))
                },
                label = "平衡",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF5E5CE6)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun EffectsAdjustmentsPanel(
    adjustments: Adjustments,
    onAdjustmentsChange: (Adjustments) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AdjustSection("清晰度") {
            AdjustSlider(
                value = adjustments.clarity,
                onValueChange = { onAdjustmentsChange(adjustments.copy(clarity = it)) },
                label = "清晰度",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFF375F)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.dehaze,
                onValueChange = { onAdjustmentsChange(adjustments.copy(dehaze = it)) },
                label = "去雾",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFF375F)
            )
        }

        AdjustSection("晕影") {
            AdjustSlider(
                value = adjustments.vignetteAmount,
                onValueChange = { onAdjustmentsChange(adjustments.copy(vignetteAmount = it)) },
                label = "数量",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF5E5CE6)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.vignetteMidpoint,
                onValueChange = { onAdjustmentsChange(adjustments.copy(vignetteMidpoint = it)) },
                label = "中点",
                valueRange = 0f..100f,
                defaultValue = 50f,
                tintColor = Color(0xFF5E5CE6)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.vignetteFeather,
                onValueChange = { onAdjustmentsChange(adjustments.copy(vignetteFeather = it)) },
                label = "羽化",
                valueRange = 0f..100f,
                defaultValue = 50f,
                tintColor = Color(0xFF5E5CE6)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.vignetteRoundness,
                onValueChange = { onAdjustmentsChange(adjustments.copy(vignetteRoundness = it)) },
                label = "圆度",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF5E5CE6)
            )
        }

        AdjustSection("胶片颗粒") {
            AdjustSlider(
                value = adjustments.grainAmount,
                onValueChange = { onAdjustmentsChange(adjustments.copy(grainAmount = it)) },
                label = "数量",
                valueRange = 0f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF8E8E93)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.grainSize,
                onValueChange = { onAdjustmentsChange(adjustments.copy(grainSize = it)) },
                label = "大小",
                valueRange = 0f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF8E8E93)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.grainRoughness,
                onValueChange = { onAdjustmentsChange(adjustments.copy(grainRoughness = it)) },
                label = "粗糙度",
                valueRange = 0f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF8E8E93)
            )
        }

        AdjustSection("创艺光效") {
            AdjustSlider(
                value = adjustments.glowAmount,
                onValueChange = { onAdjustmentsChange(adjustments.copy(glowAmount = it)) },
                label = "发光",
                valueRange = 0f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFBF5AF2)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.halationAmount,
                onValueChange = { onAdjustmentsChange(adjustments.copy(halationAmount = it)) },
                label = "光晕",
                valueRange = 0f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFBF5AF2)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DetailsAdjustmentsPanel(
    adjustments: Adjustments,
    onAdjustmentsChange: (Adjustments) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AdjustSection("锐化") {
            AdjustSlider(
                value = adjustments.sharpness,
                onValueChange = { onAdjustmentsChange(adjustments.copy(sharpness = it)) },
                label = "锐化",
                valueRange = 0f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFF9500)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.structure,
                onValueChange = { onAdjustmentsChange(adjustments.copy(structure = it)) },
                label = "结构",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFF9500)
            )
        }

        AdjustSection("降噪") {
            AdjustSlider(
                value = adjustments.lumaNoiseReduction,
                onValueChange = { onAdjustmentsChange(adjustments.copy(lumaNoiseReduction = it)) },
                label = "亮度降噪",
                valueRange = 0f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF30D158)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.colorNoiseReduction,
                onValueChange = { onAdjustmentsChange(adjustments.copy(colorNoiseReduction = it)) },
                label = "色彩降噪",
                valueRange = 0f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF30D158)
            )
        }

        AdjustSection("镜头校正") {
            AdjustSlider(
                value = adjustments.lensDistortion,
                onValueChange = { onAdjustmentsChange(adjustments.copy(lensDistortion = it)) },
                label = "畸变校正",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF007AFF)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.chromaticAberrationRedCyan,
                onValueChange = {
                    onAdjustmentsChange(adjustments.copy(chromaticAberrationRedCyan = it))
                },
                label = "色差（红/青）",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF007AFF)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.chromaticAberrationBlueYellow,
                onValueChange = {
                    onAdjustmentsChange(adjustments.copy(chromaticAberrationBlueYellow = it))
                },
                label = "色差（蓝/黄）",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF007AFF)
            )
        }

        AdjustSection("色彩校准") {
            AdjustSlider(
                value = adjustments.colorCalibration.shadowsTint,
                onValueChange = {
                    val cc = adjustments.colorCalibration.copy(shadowsTint = it)
                    onAdjustmentsChange(adjustments.copy(colorCalibration = cc))
                },
                label = "阴影色调",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFBF5AF2)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun GeometryAdjustmentsPanel(
    adjustments: Adjustments,
    onAdjustmentsChange: (Adjustments) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AdjustSection("变换") {
            AdjustSlider(
                value = adjustments.transformVertical,
                onValueChange = { onAdjustmentsChange(adjustments.copy(transformVertical = it)) },
                label = "垂直透视",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFF9500)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.transformHorizontal,
                onValueChange = { onAdjustmentsChange(adjustments.copy(transformHorizontal = it)) },
                label = "水平透视",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFFFF9500)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.transformRotate,
                onValueChange = { onAdjustmentsChange(adjustments.copy(transformRotate = it)) },
                label = "旋转",
                unit = "°",
                valueRange = -45f..45f,
                defaultValue = 0f,
                tintColor = Color(0xFFFF9500)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.transformScale,
                onValueChange = { onAdjustmentsChange(adjustments.copy(transformScale = it)) },
                label = "缩放",
                unit = "%",
                valueRange = 50f..150f,
                defaultValue = 100f,
                tintColor = Color(0xFFFF9500)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.transformAspect,
                onValueChange = { onAdjustmentsChange(adjustments.copy(transformAspect = it)) },
                label = "宽高比",
                valueRange = -50f..50f,
                defaultValue = 0f,
                tintColor = Color(0xFFFF9500)
            )
        }

        AdjustSection("位移") {
            AdjustSlider(
                value = adjustments.transformXOffset,
                onValueChange = { onAdjustmentsChange(adjustments.copy(transformXOffset = it)) },
                label = "水平偏移",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF007AFF)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AdjustSlider(
                value = adjustments.transformYOffset,
                onValueChange = { onAdjustmentsChange(adjustments.copy(transformYOffset = it)) },
                label = "垂直偏移",
                valueRange = -100f..100f,
                defaultValue = 0f,
                tintColor = Color(0xFF007AFF)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TextSection(text: String) {
    androidx.compose.material3.Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = androidx.compose.ui.Modifier.padding(bottom = 4.dp)
    )
}
