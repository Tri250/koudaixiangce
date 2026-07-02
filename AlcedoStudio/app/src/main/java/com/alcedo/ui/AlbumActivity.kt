package com.alcedo.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcedo.core.AlbumDataSource
import com.alcedo.core.SmartClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * A-01: 相册时序浏览 — 主 Activity
 * A-02: 文件夹视图 — 支持切换到目录树模式
 * A-03: 跳转 RapidRAW — 长按 RAW → 唤起编辑
 * A-04: 跳转 PixelFruit — RAW → 滤镜
 * A-07: 外部存储（SD/OTG）— SAF 授权
 * A-06: 智能分类 — 人脸/场景分组
 *
 * 对标 RapidRAW 桌面 folder tree + 三模块核心链路。
 */
class AlbumActivity : ComponentActivity() {

    private val albumDataSource = AlbumDataSource(this)
    private val smartClassifier = SmartClassifier(this)

    // A-03: SharedPreferences 持久化上次浏览的文件夹路径
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("alcedo_album_prefs", MODE_PRIVATE)
    }

    // 权限状态
    private var permissionDenied = false
    private var showWelcome = true

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            permissionDenied = false
            showWelcome = false
            loadImages()
        } else {
            permissionDenied = true
            showWelcome = false
            // A-02: 权限拒绝时不再仅 Toast，而是显示引导页面
            Toast.makeText(this, "需要存储权限才能浏览相册", Toast.LENGTH_LONG).show()
        }
    }

    // A-01: MANAGE_EXTERNAL_STORAGE 请求 (Android 10+)
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                permissionDenied = false
                showWelcome = false
                loadImages()
            } else {
                permissionDenied = true
                showWelcome = false
            }
        }
    }

    // SAF 外部存储授权
    private val safLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Toast.makeText(this, "外部存储已授权", Toast.LENGTH_SHORT).show()
            loadImages()
        }
    }

    // A-02: SAF 降级 — 使用 ACTION_OPEN_DOCUMENT_TREE 作为权限拒绝后的备选方案
    private val safFallbackLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            permissionDenied = false
            Toast.makeText(this, "已通过系统选择器授权文件夹访问", Toast.LENGTH_SHORT).show()
            loadImages()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                AlbumScreen(
                    onRequestPermission = { requestPermissions() },
                    onOpenExternalStorage = { openExternalStorage() },
                    onOpenSafPicker = { openSafFallback() },
                    onEditRaw = { uri -> dispatchEdit(uri, "rapidraw") },
                    onEditFilter = { uri -> dispatchEdit(uri, "pixelfruit") },
                    albumDataSource = albumDataSource,
                    smartClassifier = smartClassifier,
                    showWelcome = showWelcome,
                    permissionDenied = permissionDenied,
                    onDismissWelcome = { showWelcome = false },
                    onRequestManageStorage = { requestManageStorage() },
                )
            }
        }

        if (hasPermissions()) {
            showWelcome = false
            loadImages()
        } else {
            // A-01: 首次启动显示欢迎页，不立即请求权限
            showWelcome = true
        }
    }

    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用 MANAGE_EXTERNAL_STORAGE
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // A-01: Android 11+ 请求 MANAGE_EXTERNAL_STORAGE
            requestManageStorage()
        } else {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissions)
        }
    }

    // A-01: Android 10+ 请求 MANAGE_EXTERNAL_STORAGE
    private fun requestManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            manageStorageLauncher.launch(intent)
        }
    }

    private fun openExternalStorage() {
        safLauncher.launch(null)
    }

    // A-02: 权限拒绝后的 SAF 降级选择器
    private fun openSafFallback() {
        safFallbackLauncher.launch(null)
    }

    private fun loadImages() {
        // 图片加载在 Composable 中通过 LaunchedEffect 触发
    }

    // A-03: 持久化上次浏览的文件夹路径
    fun saveLastFolder(folderPath: String?) {
        prefs.edit().putString("last_folder", folderPath).apply()
    }

    fun restoreLastFolder(): String? {
        return prefs.getString("last_folder", null)
    }

    /**
     * A-03 / A-04: 分发编辑意图
     * @param uri 图片 URI
     * @param target "rapidraw" 或 "pixelfruit"
     */
    private fun dispatchEdit(uri: Uri, target: String) {
        val intent = Intent("com.alcedo.action.EDIT_RAW").apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // 如果 EditDispatchActivity 不可用，直接跳转
            val directIntent = when (target) {
                "pixelfruit" -> Intent("com.pixelfruit.action.EDIT_FILTER").apply {
                    setDataAndType(uri, "image/*")
                }
                else -> Intent("com.rapidraw.action.EDIT").apply {
                    setDataAndType(uri, "image/*")
                }
            }
            directIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                startActivity(directIntent)
            } catch (e2: Exception) {
                Toast.makeText(this, "未安装编辑模块", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ── Compose UI ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    onRequestPermission: () -> Unit,
    onOpenExternalStorage: () -> Unit,
    onOpenSafPicker: () -> Unit,
    onEditRaw: (Uri) -> Unit,
    onEditFilter: (Uri) -> Unit,
    albumDataSource: AlbumDataSource,
    smartClassifier: SmartClassifier,
    showWelcome: Boolean = false,
    permissionDenied: Boolean = false,
    onDismissWelcome: () -> Unit = {},
    onRequestManageStorage: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? AlbumActivity

    var images by remember { mutableStateOf<List<AlbumDataSource.AlbumItem>>(emptyList()) }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    // A-03: 冷启动恢复上次浏览的文件夹
    var selectedFolder by rememberSaveable {
        mutableStateOf(activity?.restoreLastFolder())
    }
    var isFolderView by remember { mutableStateOf(false) }
    var showClassification by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<SmartClassifier.Category?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // A-01: 欢迎页 / 引导页
    if (showWelcome) {
        WelcomeScreen(
            onRequestPermission = onRequestPermission,
            onRequestManageStorage = onRequestManageStorage,
            onDismiss = onDismissWelcome,
        )
        return
    }

    // A-02: 权限拒绝引导页 — 显示系统选择器按钮
    if (permissionDenied) {
        PermissionDeniedScreen(
            onOpenSafPicker = onOpenSafPicker,
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            },
        )
        return
    }

    // A-01: 加载图片
    LaunchedEffect(selectedFolder) {
        isLoading = true
        images = withContext(Dispatchers.IO) {
            albumDataSource.queryImages(folderPath = selectedFolder)
        }
        folders = withContext(Dispatchers.IO) {
            albumDataSource.getFolderPaths()
        }
        // A-03: 持久化已选择的文件夹
        activity?.saveLastFolder(selectedFolder)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isFolderView) "文件夹" else "相册",
                        fontSize = 20.sp,
                    )
                },
                actions = {
                    // A-02: 文件夹视图切换
                    TextButton(onClick = { isFolderView = !isFolderView }) {
                        Text(if (isFolderView) "时间线" else "文件夹", fontSize = 14.sp)
                    }
                    // A-06: 智能分类
                    TextButton(onClick = { showClassification = !showClassification }) {
                        Text("分类", fontSize = 14.sp)
                    }
                    // A-07: 外部存储
                    TextButton(onClick = onOpenExternalStorage) {
                        Text("OTG", fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                ),
            )
        },
        containerColor = Color(0xFF0D0D1A),
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF4A9EFF))
            }
        } else if (isFolderView) {
            // A-02: 文件夹目录树
            FolderListView(
                folders = folders,
                selectedFolder = selectedFolder,
                onSelectFolder = { selectedFolder = it },
                modifier = Modifier.padding(padding),
            )
        } else if (showClassification && selectedCategory != null) {
            // A-06: 分类展示
            Text(
                text = "分类: ${selectedCategory!!.name}",
                color = Color.White,
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            ImageGridView(
                images = images,
                onEditRaw = onEditRaw,
                onEditFilter = onEditFilter,
                modifier = Modifier.padding(padding),
            )
        } else {
            // A-01: 时间线视图
            ImageGridView(
                images = images,
                onEditRaw = onEditRaw,
                onEditFilter = onEditFilter,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
fun FolderListView(
    folders: List<String>,
    selectedFolder: String?,
    onSelectFolder: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            TextButton(onClick = { onSelectFolder(null) }) {
                Text(
                    text = "📁 全部图片",
                    color = if (selectedFolder == null) Color(0xFF4A9EFF) else Color.White,
                    fontSize = 16.sp,
                )
            }
        }
        items(folders) { folder ->
            TextButton(onClick = { onSelectFolder(folder) }) {
                Text(
                    text = "📁 $folder",
                    color = if (selectedFolder == folder) Color(0xFF4A9EFF) else Color.Gray,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ImageGridView(
    images: List<AlbumDataSource.AlbumItem>,
    onEditRaw: (Uri) -> Unit,
    onEditFilter: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无图片", color = Color.Gray, fontSize = 16.sp)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier.padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(images, key = { it.id }) { item ->
            ImageCard(
                item = item,
                onEditRaw = { onEditRaw(item.uri) },
                onEditFilter = { onEditFilter(item.uri) },
            )
        }
    }
}

@Composable
fun ImageCard(
    item: AlbumDataSource.AlbumItem,
    onEditRaw: () -> Unit,
    onEditFilter: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val isRaw = item.mimeType.contains("dng", ignoreCase = true) ||
            item.mimeType.contains("raw", ignoreCase = true) ||
            item.displayName.let { name ->
                listOf("arw", "cr2", "cr3", "nef", "raf", "orf", "rw2", "pef", "srw", "dng")
                    .any { name.lowercase().endsWith(it) }
            }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color(0xFF2A2A3E))
            .clickable { showMenu = true }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .size(256)
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // RAW 标签
        if (isRaw) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color(0xFF4A9EFF).copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("RAW", color = Color.White, fontSize = 10.sp)
            }
        }

        // B-03: HEIF 标签
        val isHeif = item.mimeType.contains("heif", ignoreCase = true) ||
                item.mimeType.contains("heic", ignoreCase = true) ||
                item.displayName.let { name ->
                    listOf("heif", "heic").any { name.lowercase().endsWith(it) }
                }
        if (isHeif) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color(0xFF30D158).copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("HEIF", color = Color.White, fontSize = 10.sp)
            }
        }

        // 上下文菜单
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("RAW 编辑") },
                onClick = { showMenu = false; onEditRaw() },
            )
            DropdownMenuItem(
                text = { Text("滤镜") },
                onClick = { showMenu = false; onEditFilter() },
            )
        }
    }
}

// ── A-01: 欢迎页 / 引导页 ─────────────────────────────────────────────────

@Composable
fun WelcomeScreen(
    onRequestPermission: () -> Unit,
    onRequestManageStorage: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D1A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "AlcedoStudio",
                color = Color(0xFF4A9EFF),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "专业的 RAW 照片管理与编辑工具",
                color = Color.Gray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "浏览您的相册、管理文件夹、智能分类照片",
                color = Color.DarkGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        onRequestManageStorage()
                    } else {
                        onRequestPermission()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF)),
                modifier = Modifier.fillMaxWidth(0.7f),
            ) {
                Text("授予存储权限", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onDismiss) {
                Text("稍后再说", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

// ── A-02: 权限拒绝引导页 ──────────────────────────────────────────────────

@Composable
fun PermissionDeniedScreen(
    onOpenSafPicker: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D1A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "需要存储权限",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "AlcedoStudio 需要访问您的照片才能浏览相册。\n您可以通过以下方式授予访问权限：",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            // A-02: SAF 降级 — 使用系统文件选择器
            OutlinedButton(
                onClick = onOpenSafPicker,
                modifier = Modifier.fillMaxWidth(0.8f),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFF4A9EFF), Color(0xFF30D158))
                    )
                ),
            ) {
                Text("使用系统选择器授权文件夹", fontSize = 14.sp, color = Color(0xFF4A9EFF))
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onOpenSettings) {
                Text("前往系统设置", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}