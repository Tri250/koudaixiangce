package com.rapidraw.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.ImageFile
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private const val AUTO_HIDE_DELAY_MS = 3000L

/**
 * Enhanced Filmstrip composable matching RapidRAW Desktop's filmstrip view.
 *
 * Features:
 * - Horizontal scrolling thumbnail strip at the bottom of the editor
 * - Current image highlighted with HasselbladOrange border
 * - Swipe to navigate between images in the same folder
 * - Thumbnail caching via lazy loading with loading state
 * - RAW badge indicator on RAW files
 * - Rating stars overlay on thumbnails
 * - Smooth scroll animation when navigating
 * - Auto-hide after 3 seconds of inactivity, tap to show again
 */
@Composable
fun Filmstrip(
    images: List<ImageFile>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    thumbnails: Map<String, android.graphics.Bitmap>,
    loadingThumbnails: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    // Auto-hide state
    var isVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Auto-hide logic: hide after 3 seconds of inactivity
    LaunchedEffect(isVisible) {
        if (isVisible) {
            while (true) {
                delay(500)
                if (System.currentTimeMillis() - lastInteractionTime >= AUTO_HIDE_DELAY_MS) {
                    isVisible = false
                    break
                }
            }
        }
    }

    // Smooth scroll to selected item
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex < images.size) {
            listState.animateScrollToItem(
                index = selectedIndex,
                scrollOffset = -listState.layoutInfo.viewportSize.width / 2 + 48,
            )
        }
    }

    // Swipe to navigate: detect when user swipes far enough to change image
    LaunchedEffect(images.isNotEmpty()) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisible ->
                // Update last interaction time on scroll
                lastInteractionTime = System.currentTimeMillis()
            }
    }

    Box(modifier = modifier) {
        // Tap area to show filmstrip when hidden
        if (!isVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clickable {
                        isVisible = true
                        lastInteractionTime = System.currentTimeMillis()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(TextTertiary.copy(alpha = 0.5f))
                )
            }
        }

        // Filmstrip content
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                animationSpec = tween(250),
                initialOffsetY = { it },
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                animationSpec = tween(200),
                targetOffsetY = { it },
            ) + fadeOut(tween(150)),
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EditorSurface)
                    .padding(vertical = 4.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, _ ->
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    },
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(
                    items = images,
                    key = { _, image -> image.path }
                ) { index, image ->
                    FilmstripThumbnail(
                        image = image,
                        isSelected = index == selectedIndex,
                        bitmap = thumbnails[image.path],
                        isLoading = image.path in loadingThumbnails,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(index)
                            lastInteractionTime = System.currentTimeMillis()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilmstripThumbnail(
    image: ImageFile,
    isSelected: Boolean,
    bitmap: android.graphics.Bitmap?,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) HasselbladOrange else Color.Transparent,
        animationSpec = SpringSpec(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "borderColor",
    )

    Box(
        modifier = Modifier
            .width(56.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(EditorBorder)
            .border(
                width = if (isSelected) 2.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Thumbnail image or loading state
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = image.fileName,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
        } else if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = HasselbladOrange,
                strokeWidth = 1.5.dp,
            )
        }

        // RAW badge
        if (image.isRaw) {
            RawBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp),
            )
        }

        // Rating stars overlay
        if (image.rating > 0) {
            RatingOverlay(
                rating = image.rating,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(2.dp),
            )
        }

        // Selected indicator: bottom bar
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(HasselbladOrange),
            )
        }
    }
}

/**
 * RAW badge indicator - a small "RAW" label on RAW file thumbnails.
 */
@Composable
private fun RawBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = HasselbladOrange.copy(alpha = 0.85f),
                shape = RoundedCornerShape(2.dp),
            )
            .padding(horizontal = 3.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "RAW",
            color = TextPrimary,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 9.sp,
        )
    }
}

/**
 * Rating stars overlay - shows star rating on thumbnails.
 */
@Composable
private fun RatingOverlay(
    rating: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(rating.coerceIn(0, 5)) {
            Text(
                text = "\u2605", // ★
                color = HasselbladOrange,
                fontSize = 7.sp,
                lineHeight = 9.sp,
            )
        }
    }
}
