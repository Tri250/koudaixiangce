package com.alcedo.studio.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.alcedo.studio.ui.editor.EditorActivity
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.alcedo.studio.core.AlbumDataSource
import com.alcedo.studio.core.SmartClassifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class AlbumActivity : ComponentActivity() {

    private val albumDataSource = AlbumDataSource(this)
    private val smartClassifier = SmartClassifier(this)

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("alcedo_album_prefs", MODE_PRIVATE)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要存储权限才能浏览相册", Toast.LENGTH_LONG).show()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "已获得完整存储权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val safLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Toast.makeText(this, "外部存储已授权", Toast.LENGTH_SHORT).show()
        }
    }

    private val safFallbackLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            Toast.makeText(this, "已通过系统选择器授权文件夹访问", Toast.LENGTH_SHORT).show()
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
                    onEditRaw = { uri -> dispatchEdit(uri) },
                    onEditFilter = { uri -> dispatchEdit(uri) },
                    albumDataSource = albumDataSource,
                    smartClassifier = smartClassifier,
                    onRequestManageStorage = { requestManageStorage() },
                )
            }
        }
    }

    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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

    private fun openSafFallback() {
        safFallbackLauncher.launch(null)
    }

    fun saveLastFolder(folderPath: String?) {
        prefs.edit().putString("last_folder", folderPath).apply()
    }

    fun restoreLastFolder(): String? {
        return prefs.getString("last_folder", null)
    }

    private fun dispatchEdit(uri: Uri) {
        openStudioEditor(uri)
    }

    private fun openStudioEditor(uri: Uri) {
        val intent = Intent(this, EditorActivity::class.java).apply {
            data = uri
            putExtra(EditorActivity.EXTRA_DISPLAY_NAME, uri.lastPathSegment ?: "Untitled")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }
}

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
    onRequestManageStorage: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? AlbumActivity
    val scope = rememberCoroutineScope()

    var images by remember { mutableStateOf<List<AlbumDataSource.AlbumItem>>(emptyList()) }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedFolder by rememberSaveable { mutableStateOf(activity?.restoreLastFolder()) }
    var isFolderView by remember { mutableStateOf(false) }
    var showClassification by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<SmartClassifier.Category?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showWelcome by remember { mutableStateOf(true) }
    var permissionDenied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val hasPerm = activity?.hasPermissions() ?: false
        showWelcome = !hasPerm
        permissionDenied = false
    }

    if (showWelcome) {
        WelcomeScreen(
            onRequestPermission = onRequestPermission,
            onRequestManageStorage = onRequestManageStorage,
            onDismiss = { showWelcome = false },
        )
        return
    }

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

    LaunchedEffect(selectedFolder) {
        isLoading = true
        images = withContext(Dispatchers.IO) {
            albumDataSource.queryImages(folderPath = selectedFolder)
        }
        folders = withContext(Dispatchers.IO) {
            albumDataSource.getFolderPaths()
        }
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
                    TextButton(onClick = { isFolderView = !isFolderView }) {
                        Text(if (isFolderView) "时间线" else "文件夹", fontSize = 14.sp)
                    }
                    TextButton(onClick = { showClassification = !showClassification }) {
                        Text("分类", fontSize = 14.sp)
                    }
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
            FolderListView(
                folders = folders,
                selectedFolder = selectedFolder,
                onSelectFolder = { selectedFolder = it },
                modifier = Modifier.padding(padding),
            )
        } else if (showClassification && selectedCategory != null) {
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
                    text = "全部图片",
                    color = if (selectedFolder == null) Color(0xFF4A9EFF) else Color.White,
                    fontSize = 16.sp,
                )
            }
        }
        items(folders) { folder ->
            TextButton(onClick = { onSelectFolder(folder) }) {
                Text(
                    text = folder,
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
    val rawExtensions = setOf("arw", "cr2", "cr3", "nef", "raf", "orf", "rw2", "pef", "srw", "dng")
    val isRaw = item.mimeType.contains("dng", ignoreCase = true) ||
            item.mimeType.contains("raw", ignoreCase = true) ||
            rawExtensions.any { item.displayName.lowercase().endsWith(it) }

    val heifExtensions = setOf("heif", "heic")
    val isHeif = item.mimeType.contains("heif", ignoreCase = true) ||
            item.mimeType.contains("heic", ignoreCase = true) ||
            heifExtensions.any { item.displayName.lowercase().endsWith(it) }

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
            OutlinedButton(
                onClick = onOpenSafPicker,
                modifier = Modifier.fillMaxWidth(0.8f),
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
