package com.rapidraw.ui.community

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest

// ── Theme Colors ──
private val HasselbladOrange = Color(0xFFE85D04)
private val EditorBackground = Color(0xFF0D0D0D)
private val EditorSurface = Color(0xFF1A1A1A)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB0B0B0)
private val TextTertiary = Color(0xFF707070)

// ── Data Model ──
data class LutItem(
    val id: String,
    val name: String,
    val author: String,
    val thumbnailUrl: String,
    val downloadUrl: String,
    val category: String,
    val downloads: Int,
    val rating: Float,
    val isPremium: Boolean
)

// ── Mock Data ──
private val sampleLuts = listOf(
    LutItem("1", "Hasselblad 907X", "Nordic Light", "https://picsum.photos/seed/lut1/400/300", "https://example.com/lut1.cube", "Film", 12430, 4.8f, false),
    LutItem("2", "Portra 400 Warm", "ColorGrade Pro", "https://picsum.photos/seed/lut2/400/300", "https://example.com/lut2.cube", "Portrait", 8920, 4.7f, false),
    LutItem("3", "Moody Forest", "Landscape Lab", "https://picsum.photos/seed/lut3/400/300", "https://example.com/lut3.cube", "Landscape", 5670, 4.5f, false),
    LutItem("4", "Golden Hour", "Sunset Studio", "https://picsum.photos/seed/lut4/400/300", "https://example.com/lut4.cube", "Vintage", 15400, 4.9f, true),
    LutItem("5", "Cinematic Teal", "FilmGrade", "https://picsum.photos/seed/lut5/400/300", "https://example.com/lut5.cube", "Cinematic", 11200, 4.6f, false),
    LutItem("6", "Classic B&W", "Mono Masters", "https://picsum.photos/seed/lut6/400/300", "https://example.com/lut6.cube", "BlackWhite", 7800, 4.4f, false),
    LutItem("7", "Fuji 400H", "Analog Revival", "https://picsum.photos/seed/lut7/400/300", "https://example.com/lut7.cube", "Film", 9600, 4.7f, true),
    LutItem("8", "Soft Skin Tone", "Portrait Lux", "https://picsum.photos/seed/lut8/400/300", "https://example.com/lut8.cube", "Portrait", 6750, 4.3f, false),
    LutItem("9", "Desert Dunes", "Earth Tones", "https://picsum.photos/seed/lut9/400/300", "https://example.com/lut9.cube", "Landscape", 4320, 4.2f, false),
    LutItem("10", "70s Retro", "Groovy Colors", "https://picsum.photos/seed/lut10/400/300", "https://example.com/lut10.cube", "Vintage", 8900, 4.5f, false),
    LutItem("11", "Cyberpunk Night", "Neon Grade", "https://picsum.photos/seed/lut11/400/300", "https://example.com/lut11.cube", "Cinematic", 10200, 4.8f, true),
    LutItem("12", "High Contrast", "Contrast Co", "https://picsum.photos/seed/lut12/400/300", "https://example.com/lut12.cube", "BlackWhite", 5100, 4.1f, false)
)

private val categories = listOf("All", "Film", "Portrait", "Landscape", "Vintage", "Cinematic", "BlackWhite")

// ── Main Screen ──
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LutMarketScreen(
    onBack: () -> Unit,
    onApplyLut: (LutItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedLut by remember { mutableStateOf<LutItem?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }

    val filteredLuts = remember(searchQuery, selectedCategory) {
        sampleLuts.filter { lut ->
            val matchesCategory = selectedCategory == "All" || lut.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                lut.name.contains(searchQuery, ignoreCase = true) ||
                lut.author.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = EditorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "LUT Marketplace",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EditorSurface
                )
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search bar
            item(span = { GridItemSpan(2) }) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search LUTs...", color = TextTertiary) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = EditorSurface,
                        unfocusedContainerColor = EditorSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = HasselbladOrange,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            // Category chips
            item(span = { GridItemSpan(2) }) {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        val isSelected = category == selectedCategory
                        val bgColor by animateColorAsState(
                            if (isSelected) HasselbladOrange else EditorSurface,
                            label = "chipBg"
                        )
                        val textColor = if (isSelected) TextPrimary else TextSecondary

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(bgColor)
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                category,
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // LUT cards
            items(filteredLuts, key = { it.id }) { lut ->
                LutCard(
                    lut = lut,
                    onClick = { selectedLut = lut; showDetailDialog = true },
                    onLongClick = { selectedLut = lut; showDetailDialog = true }
                )
            }

            // Empty state
            if (filteredLuts.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No LUTs found",
                            color = TextTertiary,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    // Detail dialog
    if (showDetailDialog && selectedLut != null) {
        LutDetailDialog(
            lut = selectedLut!!,
            onDismiss = { showDetailDialog = false },
            onApply = {
                showDetailDialog = false
                onApplyLut(selectedLut!!)
            }
        )
    }
}

// ── LUT Card ──
@Composable
private fun LutCard(
    lut: LutItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EditorSurface)
    ) {
        Column {
            // Thumbnail
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(lut.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = lut.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )

                // Premium badge
                if (lut.isPremium) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(HasselbladOrange)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "PRO",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Rating badge
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = HasselbladOrange,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        String.format("%.1f", lut.rating),
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Info
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    lut.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    lut.author,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            formatDownloads(lut.downloads),
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        lut.category,
                        color = HasselbladOrange.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ── Detail Dialog ──
@Composable
private fun LutDetailDialog(
    lut: LutItem,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = EditorSurface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Thumbnail
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(lut.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = lut.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    lut.name,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "by ${lut.author}",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))

                // Rating
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { index ->
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = if (index < lut.rating.toInt()) HasselbladOrange else TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${lut.rating}",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoChip("${formatDownloads(lut.downloads)} downloads")
                    InfoChip(lut.category)
                    if (lut.isPremium) {
                        InfoChip("Premium", accent = true)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(EditorBackground)
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HasselbladOrange)
                            .clickable(onClick = onApply)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Apply LUT", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, accent: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (accent) HasselbladOrange.copy(alpha = 0.2f) else EditorBackground)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = if (accent) HasselbladOrange else TextSecondary,
            fontSize = 12.sp
        )
    }
}

private fun formatDownloads(count: Int): String {
    return when {
        count >= 10_000 -> "${count / 1_000}k"
        count >= 1_000 -> "${"%.1f".format(count / 1_000f)}k"
        else -> count.toString()
    }
}