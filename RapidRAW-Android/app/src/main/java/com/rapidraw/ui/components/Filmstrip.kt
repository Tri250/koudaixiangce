package com.rapidraw.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.rapidraw.data.model.ImageFile
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange

@Composable
fun Filmstrip(
    images: List<ImageFile>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    thumbnails: Map<String, android.graphics.Bitmap>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex < images.size) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(EditorSurface)
            .padding(vertical = 4.dp),
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = images,
            key = { _, image -> image.path }
        ) { index, image ->
            val isSelected = index == selectedIndex
            val bitmap = thumbnails[image.path]

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(EditorBorder)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 3.dp,
                                color = HasselbladOrange,
                                shape = RoundedCornerShape(4.dp),
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = image.fileName,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(2.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }

                // Hasselblad Orange bottom border for selected item
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
    }
}
