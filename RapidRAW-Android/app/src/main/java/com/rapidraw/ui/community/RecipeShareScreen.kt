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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Theme Colors ──
private val HasselbladOrange = Color(0xFFE85D04)
private val EditorBackground = Color(0xFF0D0D0D)
private val EditorSurface = Color(0xFF1A1A1A)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB0B0B0)
private val TextTertiary = Color(0xFF707070)

// ── Data Model ──
data class SharedRecipe(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val thumbnailUrl: String,
    val tags: List<String>,
    val likes: Int,
    val downloads: Int,
    val adjustments: String /* JSON */
)

// ── Mock Data ──
private val sampleRecipes = listOf(
    SharedRecipe(
        "1", "Golden Portrait", "Anna Chen",
        "Warm golden tones with soft skin smoothing. Perfect for outdoor portraits in natural light.",
        "https://picsum.photos/seed/recipe1/400/300",
        listOf("Portrait", "Warm", "Soft"), 2340, 5670,
        """{"exposure":0.3,"contrast":5,"saturation":8,"temperature":5200}"""
    ),
    SharedRecipe(
        "2", "Moody Landscape", "Mark Rivers",
        "Deep contrast with desaturated greens. Great for dramatic nature shots.",
        "https://picsum.photos/seed/recipe2/400/300",
        listOf("Landscape", "Moody", "Contrast"), 1890, 4320,
        """{"exposure":-0.2,"contrast":25,"saturation":-5,"temperature":4800}"""
    ),
    SharedRecipe(
        "3", "Film Emulation 400H", "Yuki Tanaka",
        "Faithful Fuji 400H film simulation with subtle grain and pastel colors.",
        "https://picsum.photos/seed/recipe3/400/300",
        listOf("Film", "Vintage", "Pastel"), 3450, 8900,
        """{"exposure":0.1,"contrast":10,"saturation":12,"temperature":5500,"grain":15}"""
    ),
    SharedRecipe(
        "4", "Cyberpunk City", "Alex Novak",
        "Neon-tinted shadows with crushed blacks. Inspired by Blade Runner aesthetics.",
        "https://picsum.photos/seed/recipe4/400/300",
        listOf("Cinematic", "Neon", "Urban"), 4210, 10200,
        """{"exposure":-0.5,"contrast":40,"saturation":20,"temperature":3800,"tint":15}"""
    ),
    SharedRecipe(
        "5", "Classic Monochrome", "David Kim",
        "Rich black and white conversion with film-like grain. Timeless look.",
        "https://picsum.photos/seed/recipe5/400/300",
        listOf("B&W", "Classic", "Film"), 1560, 3780,
        """{"exposure":0.0,"contrast":30,"saturation":-100,"grain":20}"""
    ),
    SharedRecipe(
        "6", "Sunset Glow", "Maria Santos",
        "Enhance golden hour with warm highlights and soft shadows. Ideal for beach and sunset shots.",
        "https://picsum.photos/seed/recipe6/400/300",
        listOf("Sunset", "Warm", "Glow"), 2780, 6540,
        """{"exposure":0.5,"contrast":8,"saturation":15,"temperature":6000,"highlights":-10}"""
    ),
    SharedRecipe(
        "7", "Editorial Fashion", "Liam O'Brien",
        "Clean, desaturated look with lifted blacks. Magazine-ready style.",
        "https://picsum.photos/seed/recipe7/400/300",
        listOf("Fashion", "Editorial", "Clean"), 3120, 7800,
        """{"exposure":0.2,"contrast":15,"saturation":-8,"temperature":5000,"blacks":10}"""
    ),
    SharedRecipe(
        "8", "Night Street", "Hiroshi Sato",
        "Optimized for night photography with controlled highlights and deep shadows.",
        "https://picsum.photos/seed/recipe8/400/300",
        listOf("Night", "Street", "Urban"), 1980, 4890,
        """{"exposure":-0.3,"contrast":20,"saturation":5,"temperature":4200,"highlights":-15}"""
    )
)

private val featuredRecipes = sampleRecipes.take(4)

private val categoryFilters = listOf("All", "Portrait", "Landscape", "Film", "Cinematic", "B&W", "Fashion", "Night")

// ── Main Screen ──
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeShareScreen(
    onBack: () -> Unit,
    onApplyRecipe: (SharedRecipe) -> Unit,
    modifier: Modifier = Modifier
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }
    var likedRecipes by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    val filteredRecipes = remember(selectedCategory) {
        if (selectedCategory == "All") sampleRecipes
        else sampleRecipes.filter { recipe -> recipe.tags.any { it.equals(selectedCategory, ignoreCase = true) } }
    }

    val onRefresh: () -> Unit = {
        scope.launch {
            isRefreshing = true
            delay(1200)
            isRefreshing = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = EditorBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Recipe Community",
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* Share recipe action */ },
                containerColor = HasselbladOrange,
                contentColor = TextPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Share Your Recipe")
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // "Share Your Recipe" banner
                item(span = { GridItemSpan(2) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        HasselbladOrange.copy(alpha = 0.3f),
                                        EditorSurface
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Share Your Recipe",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Upload your editing preset and inspire the community",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(HasselbladOrange)
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Featured carousel
                item(span = { GridItemSpan(2) }) {
                    Text(
                        "Featured",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        items(featuredRecipes, key = { "featured_${it.id}" }) { recipe ->
                            FeaturedRecipeCard(
                                recipe = recipe,
                                onClick = { onApplyRecipe(recipe) }
                            )
                        }
                    }
                }

                // Category filters
                item(span = { GridItemSpan(2) }) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Browse",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    val scrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryFilters.forEach { category ->
                            val isSelected = category == selectedCategory
                            val bgColor by animateColorAsState(
                                if (isSelected) HasselbladOrange else EditorSurface,
                                label = "filterBg"
                            )
                            val textColor = if (isSelected) TextPrimary else TextSecondary

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(bgColor)
                                    .clickable { selectedCategory = category }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    category,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Recipe grid
                items(filteredRecipes, key = { "grid_${it.id}" }) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        isLiked = likedRecipes.contains(recipe.id),
                        onLike = {
                            likedRecipes = if (likedRecipes.contains(recipe.id)) {
                                likedRecipes - recipe.id
                            } else {
                                likedRecipes + recipe.id
                            }
                        },
                        onClick = { onApplyRecipe(recipe) }
                    )
                }

                if (filteredRecipes.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No recipes found",
                                color = TextTertiary,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Featured Recipe Card (wider, for carousel) ──
@Composable
private fun FeaturedRecipeCard(
    recipe: SharedRecipe,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EditorSurface)
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(recipe.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = recipe.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    recipe.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    recipe.author,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = HasselbladOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            formatCount(recipe.likes),
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            formatCount(recipe.downloads),
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Recipe Card ──
@Composable
private fun RecipeCard(
    recipe: SharedRecipe,
    isLiked: Boolean,
    onLike: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EditorSurface)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(recipe.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = recipe.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )

                // Like button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(onClick = onLike)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) HasselbladOrange else TextPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    recipe.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    recipe.author,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))

                // Tags
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    recipe.tags.take(3).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(HasselbladOrange.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                tag,
                                color = HasselbladOrange,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isLiked) HasselbladOrange else TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            formatCount(recipe.likes + if (isLiked) 1 else 0),
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            formatCount(recipe.downloads),
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 10_000 -> "${count / 1_000}k"
        count >= 1_000 -> "${"%.1f".format(count / 1_000f)}k"
        else -> count.toString()
    }
}