package com.rapidraw.core

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.rapidraw.data.model.ColorLabel
import com.rapidraw.data.model.DamAlbum
import com.rapidraw.data.model.DamCameraStats
import com.rapidraw.data.model.DamDateGroup
import com.rapidraw.data.model.DamFacetData
import com.rapidraw.data.model.DamImageEntry
import com.rapidraw.data.model.DamLibraryStats
import com.rapidraw.data.model.DamLensStats
import com.rapidraw.data.model.DamProjectFile
import com.rapidraw.data.model.DamProjectSettings
import com.rapidraw.data.model.DamSearchQuery
import com.rapidraw.data.model.DamSmartAlbum
import com.rapidraw.data.model.ImageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 数字资产管理 (DAM) 项目管理器
 *
 * 功能：
 * - 创建/加载 DAM 项目
 * - 扫描目录发现图像文件（RAW + JPEG + TIFF + PNG）
 * - 提取 EXIF 数据
 * - 生成和缓存缩略图（使用 ThumbnailDiskCache）
 * - 支持收藏集（用户策划的图像分组）
 * - 支持智能相册（基于规则的自动分组：按日期、相机、镜头、评分等）
 * - 支持星级评分和颜色标签
 * - 支持筛选和排序
 * - 将项目状态持久化为 JSON sidecar
 */
class DamProjectManager(private val context: Context) {

    companion object {
        private const val TAG = "DamProjectManager"
        private const val PROJECT_FILE_NAME = "project.json"
        private const val SIDECAR_EXTENSION = ".json.xmp"

        /** 支持的图像文件扩展名 */
        val SUPPORTED_EXTENSIONS: Set<String> = ImageFile.RAW_EXTENSIONS + setOf(
            "jpg", "jpeg", "jpe",
            "tif", "tiff",
            "png",
            "heif", "heic",
            "avif",
            "webp",
            "bmp",
            "dng",  // DNG 同时是 RAW
        )
    }

    // ── 状态流 ────────────────────────────────────────────────────

    private val _currentProject = MutableStateFlow<DamProjectFile?>(null)
    val currentProject: StateFlow<DamProjectFile?> = _currentProject.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _recentProjects = MutableStateFlow<List<DamProjectInfo>>(emptyList())
    val recentProjects: StateFlow<List<DamProjectInfo>> = _recentProjects.asStateFlow()

    private val _searchResults = MutableStateFlow<List<DamImageEntry>>(emptyList())
    val searchResults: StateFlow<List<DamImageEntry>> = _searchResults.asStateFlow()

    private val _facetData = MutableStateFlow(DamFacetData())
    val facetData: StateFlow<DamFacetData> = _facetData.asStateFlow()

    private val _libraryStats = MutableStateFlow(DamLibraryStats())
    val libraryStats: StateFlow<DamLibraryStats> = _libraryStats.asStateFlow()

    data class DamProjectInfo(
        val path: String,
        val name: String,
        val thumbnailPath: String?,
        val lastOpened: Long,
        val imageCount: Int,
    )

    // ── 缩略图缓存 ────────────────────────────────────────────────

    private val thumbnailCache: ThumbnailDiskCache by lazy {
        ThumbnailDiskCache(context.cacheDir)
    }

    // ── 初始化 ────────────────────────────────────────────────────

    init {
        loadRecentProjects()
    }

    // ── 项目生命周期 ──────────────────────────────────────────────

    suspend fun createNewProject(name: String, savePath: String): DamProjectFile =
        withContext(Dispatchers.IO) {
            val project = DamProjectFile(
                name = name,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
            )
            saveProject(project, savePath)
            _currentProject.value = project
            addToRecentProjects(savePath, name, null, 0)
            project
        }

    suspend fun loadProject(path: String): DamProjectFile? =
        withContext(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val file = File(path)
                if (!file.exists()) {
                    Log.e(TAG, "Project file not found: $path")
                    return@withContext null
                }

                val project = if (path.endsWith(".alcd", ignoreCase = true)) {
                    loadFromAlcdFile(path)
                } else {
                    loadFromJsonFile(path)
                }

                if (project != null) {
                    _currentProject.value = project
                    _facetData.value = project.facetData
                    _libraryStats.value = project.libraryStats
                    addToRecentProjects(
                        path,
                        project.name,
                        null,
                        project.imageEntries.size
                    )
                }

                project
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load project", e)
                null
            } finally {
                _isLoading.value = false
            }
        }

    private fun loadFromAlcdFile(path: String): DamProjectFile? {
        return try {
            ZipInputStream(FileInputStream(path)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == PROJECT_FILE_NAME) {
                        val json = zis.bufferedReader().readText()
                        return DamProjectFile.fromJson(json)
                    }
                    entry = zis.nextEntry
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load .alcd file", e)
            null
        }
    }

    private fun loadFromJsonFile(path: String): DamProjectFile? {
        return try {
            val json = File(path).readText()
            DamProjectFile.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load JSON project", e)
            null
        }
    }

    suspend fun saveProject(project: DamProjectFile, path: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val updatedProject = project.copy(modifiedAt = System.currentTimeMillis())

                if (path.endsWith(".alcd", ignoreCase = true)) {
                    saveToAlcdFile(updatedProject, path)
                } else {
                    saveToJsonFile(updatedProject, path)
                }

                _currentProject.value = updatedProject
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save project", e)
                false
            }
        }

    /**
     * 保存当前项目
     */
    suspend fun saveCurrentProject(): Boolean {
        val project = _currentProject.value ?: return false
        val recentInfo = _recentProjects.value.find {
            it.name == project.name
        }
        val path = recentInfo?.path ?: return false
        return saveProject(project, path)
    }

    private fun saveToAlcdFile(project: DamProjectFile, path: String) {
        ZipOutputStream(FileOutputStream(path)).use { zos ->
            val json = DamProjectFile.toJson(project)
            val entry = ZipEntry(PROJECT_FILE_NAME)
            zos.putNextEntry(entry)
            zos.write(json.toByteArray())
            zos.closeEntry()
        }
    }

    private fun saveToJsonFile(project: DamProjectFile, path: String) {
        File(path).writeText(DamProjectFile.toJson(project))
    }

    // ── 目录扫描 ──────────────────────────────────────────────────

    /**
     * 扫描目录发现图像文件
     * @param directory 要扫描的目录
     * @param recursive 是否递归扫描子目录
     * @return 发现的图像文件列表
     */
    suspend fun scanDirectory(
        directory: File,
        recursive: Boolean = true,
    ): List<DamImageEntry> = withContext(Dispatchers.IO) {
        _isScanning.value = true
        try {
            if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()

            val imageFiles = findImageFiles(directory, recursive)
            val entries = mutableListOf<DamImageEntry>()

            for (file in imageFiles) {
                val entry = createImageEntry(file)
                if (entry != null) {
                    entries.add(entry)
                }
            }

            entries
        } finally {
            _isScanning.value = false
        }
    }

    /**
     * 扫描目录并将结果添加到当前项目
     */
    suspend fun scanAndAddToProject(
        directory: File,
        recursive: Boolean = true,
        projectPath: String,
    ): Int = withContext(Dispatchers.IO) {
        val entries = scanDirectory(directory, recursive)
        if (entries.isEmpty()) return@withContext 0

        val project = _currentProject.value ?: return@withContext 0
        val existingPaths = project.imageEntries.map { it.path }.toSet()
        val newEntries = entries.filter { it.path !in existingPaths }

        if (newEntries.isEmpty()) return@withContext 0

        addImagesToProject(
            newEntries.map { it.toImageFile() },
            projectPath
        )

        newEntries.size
    }

    /**
     * 递归查找图像文件
     */
    private fun findImageFiles(dir: File, recursive: Boolean): List<File> {
        val result = mutableListOf<File>()
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val ext = file.name.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    result.add(file)
                }
            } else if (recursive && file.isDirectory && !file.name.startsWith(".")) {
                result.addAll(findImageFiles(file, true))
            }
        }
        return result
    }

    /**
     * 从文件创建图像条目（提取 EXIF、生成缩略图路径）
     */
    private fun createImageEntry(file: File): DamImageEntry? {
        if (!file.exists() || !file.isFile) return null

        val fileName = file.name
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val isRaw = ImageFile.isRawFile(file.absolutePath)

        // 获取图像尺寸
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: Exception) {
            // 某些 RAW 文件无法直接解码 bounds
        }

        // 提取 EXIF 数据
        val exifData = extractExifData(file)

        // 生成缩略图
        val thumbnailPath = generateThumbnail(file, isRaw)

        return DamImageEntry(
            id = UUID.randomUUID().toString(),
            path = file.absolutePath,
            fileName = fileName,
            folderPath = file.parent ?: "",
            isRaw = isRaw,
            width = if (options.outWidth > 0) options.outWidth else (exifData["width"] as? Int) ?: 0,
            height = if (options.outHeight > 0) options.outHeight else (exifData["height"] as? Int) ?: 0,
            fileSize = file.length(),
            dateModified = file.lastModified(),
            dateTaken = (exifData["dateTaken"] as? Long) ?: file.lastModified(),
            cameraMake = exifData["cameraMake"] as? String,
            cameraModel = exifData["cameraModel"] as? String,
            lensModel = exifData["lensModel"] as? String,
            focalLength = exifData["focalLength"] as? Float,
            aperture = exifData["aperture"] as? Float,
            shutterSpeed = exifData["shutterSpeed"] as? String,
            iso = exifData["iso"] as? Int,
            thumbnailPath = thumbnailPath,
        )
    }

    // ── EXIF 提取 ─────────────────────────────────────────────────

    /**
     * 从图像文件提取 EXIF 数据
     */
    private fun extractExifData(file: File): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        try {
            val exif = ExifInterface(file.absolutePath)

            // 相机信息
            result["cameraMake"] = exif.getAttribute(ExifInterface.TAG_MAKE)
            result["cameraModel"] = exif.getAttribute(ExifInterface.TAG_MODEL)

            // 镜头信息
            result["lensModel"] = exif.getAttribute("LensModel")
                ?: exif.getAttribute("LensMake")

            // 焦距
            exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { focalStr ->
                result["focalLength"] = parseFocalLength(focalStr)
            }

            // 光圈
            exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { fNumStr ->
                result["aperture"] = fNumStr.toFloatOrNull()
            }

            // 快门速度
            exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { expStr ->
                result["shutterSpeed"] = formatShutterSpeed(expStr)
            }

            // ISO
            exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { isoStr ->
                result["iso"] = isoStr.toIntOrNull()
            }
            if (result["iso"] == null) {
                exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { isoStr ->
                    result["iso"] = isoStr.toIntOrNull()
                }
            }

            // 日期
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { dateStr ->
                result["dateTaken"] = parseExifDate(dateStr)
            }
            if (result["dateTaken"] == null) {
                exif.getAttribute(ExifInterface.TAG_DATETIME)?.let { dateStr ->
                    result["dateTaken"] = parseExifDate(dateStr)
                }
            }

            // 图像尺寸
            val width = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)?.toIntOrNull()
            val height = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)?.toIntOrNull()
            if (width != null) result["width"] = width
            if (height != null) result["height"] = height

            // 方向
            result["orientation"] = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

        } catch (e: Exception) {
            // EXIF 提取失败不影响整体流程
            Log.d(TAG, "EXIF extraction failed for ${file.name}: ${e.message}")
        }

        return result
    }

    /**
     * 解析 EXIF 焦距格式 "xxx/yyy" 或纯数字
     */
    private fun parseFocalLength(value: String): Float? {
        return if (value.contains("/")) {
            val parts = value.split("/")
            if (parts.size == 2) {
                val num = parts[0].toFloatOrNull() ?: return null
                val den = parts[1].toFloatOrNull() ?: return null
                if (den != 0f) num / den else null
            } else null
        } else {
            value.toFloatOrNull()
        }
    }

    /**
     * 格式化快门速度
     */
    private fun formatShutterSpeed(exposureTime: String): String {
        return if (exposureTime.contains("/")) {
            val parts = exposureTime.split("/")
            if (parts.size == 2) {
                val num = parts[0].toDoubleOrNull() ?: return exposureTime
                val den = parts[1].toDoubleOrNull() ?: return exposureTime
                if (den != 0.0 && num == 1.0) {
                    "1/${(den / num).toInt()}"
                } else {
                    String.format("%.1f", num / den)
                }
            } else exposureTime
        } else {
            exposureTime
        }
    }

    /**
     * 解析 EXIF 日期格式 "yyyy:MM:dd HH:mm:ss"
     */
    private fun parseExifDate(dateStr: String): Long? {
        return try {
            val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            format.parse(dateStr)?.time
        } catch (_: Exception) {
            try {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                format.parse(dateStr)?.time
            } catch (_: Exception) {
                null
            }
        }
    }

    // ── 缩略图生成 ────────────────────────────────────────────────

    /**
     * 生成缩略图并缓存
     * @return 缩略图文件路径
     */
    private fun generateThumbnail(file: File, isRaw: Boolean): String? {
        return try {
            val lastModified = file.lastModified()

            // 先检查缓存
            val cached = thumbnailCache.get(file.absolutePath, lastModified)
            if (cached != null) {
                // 缩略图已缓存，返回路径
                return null // 由 ThumbnailCache 管理，通过 path+lastModified 获取
            }

            // 生成缩略图
            val settings = _currentProject.value?.settings ?: DamProjectSettings()
            val thumbnailSize = settings.thumbnailSize

            val bitmap = if (isRaw) {
                // RAW 文件：尝试解码内嵌预览
                decodeRawThumbnail(file, thumbnailSize)
            } else {
                // 普通图像：使用 inSampleSize 解码
                decodeImageThumbnail(file, thumbnailSize)
            }

            if (bitmap != null) {
                thumbnailCache.put(file.absolutePath, lastModified, bitmap)
                // 返回标识符，实际缩略图通过 thumbnailCache.get() 获取
                file.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail generation failed for ${file.name}: ${e.message}")
            null
        }
    }

    /**
     * 解码 RAW 文件缩略图
     * 优先使用 ExifInterface 提取内嵌缩略图，回退到 libraw
     */
    private fun decodeRawThumbnail(file: File, maxSize: Int): android.graphics.Bitmap? {
        // 方法1：EXIF 缩略图（最快，大部分 RAW 文件都有）
        try {
            val exif = ExifInterface(file.absolutePath)
            val thumbnail = exif.thumbnailBitmap
            if (thumbnail != null) {
                return scaleBitmap(thumbnail, maxSize)
            }
        } catch (_: Exception) {
        }

        // 方法2：libraw 解码（需要 native 库）
        return try {
            if (RawDecoder.isNativeAvailable()) {
                // 回退到完整解码并缩放
                RawDecoder.decodeRawFile(file.absolutePath)?.let { scaleBitmap(it, maxSize) }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 解码普通图像缩略图
     * 使用 BitmapFactory 的 inSampleSize 缩小解码
     */
    private fun decodeImageThumbnail(file: File, maxSize: Int): android.graphics.Bitmap? {
        // 先获取尺寸
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        // 计算 inSampleSize
        options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888

        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /**
     * 计算 inSampleSize
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 缩放 Bitmap 到指定最大尺寸
     */
    private fun scaleBitmap(
        bitmap: android.graphics.Bitmap,
        maxSize: Int,
    ): android.graphics.Bitmap {
        if (bitmap.width <= maxSize && bitmap.height <= maxSize) return bitmap

        val scale = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        return android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * 获取缩略图 Bitmap（从缓存或磁盘）
     */
    fun getThumbnail(entry: DamImageEntry): android.graphics.Bitmap? {
        val file = File(entry.path)
        if (!file.exists()) return null
        return thumbnailCache.get(entry.path, file.lastModified())
    }

    /**
     * 预生成项目中所有缺失的缩略图
     */
    suspend fun pregenerateThumbnails() = withContext(Dispatchers.IO) {
        val project = _currentProject.value ?: return@withContext
        for (entry in project.imageEntries) {
            val file = File(entry.path)
            if (!file.exists()) continue

            val lastModified = file.lastModified()
            if (thumbnailCache.get(entry.path, lastModified) != null) continue

            val isRaw = entry.isRaw
            val settings = project.settings
            val bitmap = if (isRaw) {
                decodeRawThumbnail(file, settings.thumbnailSize)
            } else {
                decodeImageThumbnail(file, settings.thumbnailSize)
            }

            if (bitmap != null) {
                thumbnailCache.put(entry.path, lastModified, bitmap)
            }
        }
    }

    // ── 图像条目管理 ──────────────────────────────────────────────

    suspend fun addImagesToProject(
        images: List<ImageFile>,
        projectPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val project = _currentProject.value ?: return@withContext false

        val newEntries = images.map { img ->
            DamImageEntry(
                id = UUID.randomUUID().toString(),
                path = img.path,
                fileName = img.fileName,
                folderPath = img.folderPath,
                isRaw = img.isRaw,
                width = img.width,
                height = img.height,
                fileSize = img.fileSize,
                dateModified = img.dateModified,
                rating = img.rating,
                colorLabel = img.colorLabel,
                tags = img.tags,
                thumbnailPath = img.thumbnailPath,
                hasAdjustments = img.adjustments != null,
                virtualCopyOf = img.virtualCopyOf,
                lastEdited = img.lastEdited,
            )
        }

        val updatedEntries = project.imageEntries + newEntries
        val updatedStats = computeLibraryStats(updatedEntries)
        val updatedFacets = computeFacetData(updatedEntries)

        val updatedProject = project.copy(
            imageEntries = updatedEntries,
            libraryStats = updatedStats,
            facetData = updatedFacets,
            modifiedAt = System.currentTimeMillis(),
        )

        saveProject(updatedProject, projectPath)
    }

    suspend fun removeImagesFromProject(
        imageIds: List<String>,
        projectPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val project = _currentProject.value ?: return@withContext false

        val updatedEntries = project.imageEntries.filter { it.id !in imageIds }
        val updatedStats = computeLibraryStats(updatedEntries)
        val updatedFacets = computeFacetData(updatedEntries)

        val updatedProject = project.copy(
            imageEntries = updatedEntries,
            libraryStats = updatedStats,
            facetData = updatedFacets,
            modifiedAt = System.currentTimeMillis(),
        )

        saveProject(updatedProject, projectPath)
    }

    fun updateImageEntry(imageId: String, updates: (DamImageEntry) -> DamImageEntry) {
        val project = _currentProject.value ?: return
        val updatedEntries = project.imageEntries.map { entry ->
            if (entry.id == imageId) updates(entry) else entry
        }
        val updatedStats = computeLibraryStats(updatedEntries)
        val updatedFacets = computeFacetData(updatedEntries)

        _currentProject.value = project.copy(
            imageEntries = updatedEntries,
            libraryStats = updatedStats,
            facetData = updatedFacets,
            modifiedAt = System.currentTimeMillis(),
        )
        _facetData.value = updatedFacets
        _libraryStats.value = updatedStats
    }

    // ── 星级评分 & 颜色标签 ───────────────────────────────────────

    /**
     * 设置图像星级评分 (0-5)
     */
    fun setRating(imageId: String, rating: Int) {
        updateImageEntry(imageId) { it.copy(rating = rating.coerceIn(0, 5)) }
    }

    /**
     * 设置图像颜色标签
     */
    fun setColorLabel(imageId: String, label: ColorLabel) {
        updateImageEntry(imageId) { it.copy(colorLabel = label) }
    }

    /**
     * 批量设置星级
     */
    fun setRatingBatch(imageIds: List<String>, rating: Int) {
        val r = rating.coerceIn(0, 5)
        imageIds.forEach { setRating(it, r) }
    }

    /**
     * 批量设置颜色标签
     */
    fun setColorLabelBatch(imageIds: List<String>, label: ColorLabel) {
        imageIds.forEach { setColorLabel(it, label) }
    }

    // ── 收藏集 (Collections / Albums) ─────────────────────────────

    /**
     * 创建收藏集
     */
    fun createAlbum(name: String): DamAlbum {
        val album = DamAlbum(
            id = UUID.randomUUID().toString(),
            name = name,
        )
        val project = _currentProject.value ?: return album
        _currentProject.value = project.copy(
            albums = project.albums + album,
            modifiedAt = System.currentTimeMillis(),
        )
        return album
    }

    /**
     * 添加图像到收藏集
     */
    fun addToAlbum(albumId: String, imageIds: List<String>) {
        val project = _currentProject.value ?: return
        _currentProject.value = project.copy(
            albums = project.albums.map { album ->
                if (album.id == albumId) {
                    album.copy(
                        imageIds = (album.imageIds + imageIds).distinct(),
                        modifiedAt = System.currentTimeMillis(),
                        coverImageId = album.coverImageId ?: imageIds.firstOrNull(),
                    )
                } else album
            },
            modifiedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 从收藏集中移除图像
     */
    fun removeFromAlbum(albumId: String, imageIds: List<String>) {
        val project = _currentProject.value ?: return
        _currentProject.value = project.copy(
            albums = project.albums.map { album ->
                if (album.id == albumId) {
                    album.copy(
                        imageIds = album.imageIds.filter { it !in imageIds },
                        modifiedAt = System.currentTimeMillis(),
                        coverImageId = if (album.coverImageId in imageIds) album.imageIds.firstOrNull { it !in imageIds } else album.coverImageId,
                    )
                } else album
            },
            modifiedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 删除收藏集
     */
    fun deleteAlbum(albumId: String) {
        val project = _currentProject.value ?: return
        _currentProject.value = project.copy(
            albums = project.albums.filter { it.id != albumId },
            modifiedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 重命名收藏集
     */
    fun renameAlbum(albumId: String, newName: String) {
        val project = _currentProject.value ?: return
        _currentProject.value = project.copy(
            albums = project.albums.map { album ->
                if (album.id == albumId) album.copy(name = newName) else album
            },
            modifiedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 获取收藏集中的图像条目
     */
    fun getAlbumImages(albumId: String): List<DamImageEntry> {
        val project = _currentProject.value ?: return emptyList()
        val album = project.albums.find { it.id == albumId } ?: return emptyList()
        val entryMap = project.imageEntries.associateBy { it.id }
        return album.imageIds.mapNotNull { entryMap[it] }
    }

    // ── 智能相册 ──────────────────────────────────────────────────

    /**
     * 创建智能相册
     */
    fun createSmartAlbum(name: String, query: DamSearchQuery): DamSmartAlbum {
        val smartAlbum = DamSmartAlbum(
            id = UUID.randomUUID().toString(),
            name = name,
            query = query,
        )
        val project = _currentProject.value ?: return smartAlbum
        _currentProject.value = project.copy(
            smartAlbums = project.smartAlbums + smartAlbum,
            modifiedAt = System.currentTimeMillis(),
        )
        return smartAlbum
    }

    /**
     * 更新智能相册规则
     */
    fun updateSmartAlbum(smartAlbumId: String, query: DamSearchQuery) {
        val project = _currentProject.value ?: return
        _currentProject.value = project.copy(
            smartAlbums = project.smartAlbums.map { sa ->
                if (sa.id == smartAlbumId) sa.copy(query = query) else sa
            },
            modifiedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 删除智能相册
     */
    fun deleteSmartAlbum(smartAlbumId: String) {
        val project = _currentProject.value ?: return
        _currentProject.value = project.copy(
            smartAlbums = project.smartAlbums.filter { it.id != smartAlbumId },
            modifiedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 获取智能相册匹配的图像
     */
    fun getSmartAlbumImages(smartAlbumId: String): List<DamImageEntry> {
        val project = _currentProject.value ?: return emptyList()
        val smartAlbum = project.smartAlbums.find { it.id == smartAlbumId } ?: return emptyList()
        return applySearchQuery(project.imageEntries, smartAlbum.query)
    }

    // ── 搜索 / 筛选 / 排序 ────────────────────────────────────────

    /**
     * 搜索图像
     */
    fun searchImages(query: DamSearchQuery) {
        val project = _currentProject.value ?: return
        _searchResults.value = applySearchQuery(project.imageEntries, query)
    }

    /**
     * 应用搜索查询
     */
    private fun applySearchQuery(entries: List<DamImageEntry>, query: DamSearchQuery): List<DamImageEntry> {
        return entries.filter { entry ->
            // 文本搜索
            if (query.text.isNotBlank()) {
                val text = query.text.lowercase()
                entry.fileName.lowercase().contains(text) ||
                    entry.tags.any { it.lowercase().contains(text) } ||
                    (entry.cameraModel?.lowercase()?.contains(text) == true) ||
                    (entry.lensModel?.lowercase()?.contains(text) == true)
            } else true
        }.filter { entry ->
            entry.rating in query.ratingMin..query.ratingMax
        }.filter { entry ->
            if (query.colorLabels.isNotEmpty()) {
                entry.colorLabel in query.colorLabels
            } else true
        }.filter { entry ->
            if (query.cameras.isNotEmpty()) {
                entry.cameraModel in query.cameras
            } else true
        }.filter { entry ->
            if (query.lenses.isNotEmpty()) {
                entry.lensModel in query.lenses
            } else true
        }.filter { entry ->
            if (query.dateFrom != null) {
                entry.dateTaken >= query.dateFrom
            } else true
        }.filter { entry ->
            if (query.dateTo != null) {
                entry.dateTaken <= query.dateTo
            } else true
        }.filter { entry ->
            if (query.fileTypes.isNotEmpty()) {
                val ext = entry.fileName.substringAfterLast('.', "").lowercase()
                ext in query.fileTypes
            } else true
        }.filter { entry ->
            if (query.tags.isNotEmpty()) {
                query.tags.all { it in entry.tags }
            } else true
        }.filter { entry ->
            when (query.hasAdjustments) {
                true -> entry.hasAdjustments
                false -> !entry.hasAdjustments
                null -> true
            }
        }.filter { entry ->
            if (query.flagStatus != null) {
                entry.flagStatus == query.flagStatus
            } else true
        }.filter { entry ->
            if (query.focalLengthMin != null) {
                entry.focalLength?.let { it >= query.focalLengthMin } ?: false
            } else true
        }.filter { entry ->
            if (query.focalLengthMax != null) {
                entry.focalLength?.let { it <= query.focalLengthMax } ?: false
            } else true
        }.filter { entry ->
            if (query.apertureMin != null) {
                entry.aperture?.let { it >= query.apertureMin } ?: false
            } else true
        }.filter { entry ->
            if (query.apertureMax != null) {
                entry.aperture?.let { it <= query.apertureMax } ?: false
            } else true
        }.filter { entry ->
            if (query.isoMin != null) {
                entry.iso?.let { it >= query.isoMin } ?: false
            } else true
        }.filter { entry ->
            if (query.isoMax != null) {
                entry.iso?.let { it <= query.isoMax } ?: false
            } else true
        }.sortedWith(compareBy<DamImageEntry> {
            when (query.sortBy) {
                "fileName" -> it.fileName
                "dateTaken" -> it.dateTaken.toString()
                "dateModified" -> it.dateModified.toString()
                "rating" -> it.rating.toString()
                "fileSize" -> it.fileSize.toString()
                else -> it.dateTaken.toString()
            }
        }.run {
            if (query.sortOrder == "desc") reversed() else this
        })
    }

    // ── 统计和分面 ────────────────────────────────────────────────

    fun computeLibraryStats(entries: List<DamImageEntry>): DamLibraryStats {
        var rawCount = 0
        var jpegCount = 0
        var totalSize = 0L
        var editedCount = 0
        var ratedCount = 0

        for (entry in entries) {
            if (entry.isRaw) rawCount++ else jpegCount++
            totalSize += entry.fileSize
            if (entry.hasAdjustments) editedCount++
            if (entry.rating > 0) ratedCount++
        }

        return DamLibraryStats(
            totalImages = entries.size,
            rawCount = rawCount,
            jpegCount = jpegCount,
            totalFileSize = totalSize,
            editedCount = editedCount,
            ratedCount = ratedCount,
        )
    }

    fun computeFacetData(entries: List<DamImageEntry>): DamFacetData {
        val cameraMap = mutableMapOf<String, MutableList<String>>()
        val lensMap = mutableMapOf<String, Int>()
        val dateMap = mutableMapOf<String, Int>()
        val focalLengthMap = mutableMapOf<Int, Int>()
        val apertureMap = mutableMapOf<String, Int>()
        val isoMap = mutableMapOf<Int, Int>()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (entry in entries) {
            val cameraKey = "${entry.cameraMake ?: "Unknown"} ${entry.cameraModel ?: "Unknown"}"
            if (entry.cameraModel != null) {
                val make = entry.cameraMake ?: "Unknown"
                val modelList = cameraMap.getOrPut(make) { mutableListOf() }
                if (entry.cameraModel !in modelList) {
                    modelList.add(entry.cameraModel)
                }
            }

            if (entry.lensModel != null) {
                lensMap[entry.lensModel] = (lensMap[entry.lensModel] ?: 0) + 1
            }

            if (entry.dateTaken > 0) {
                val dateStr = dateFormat.format(Date(entry.dateTaken))
                dateMap[dateStr] = (dateMap[dateStr] ?: 0) + 1
            }

            entry.focalLength?.let { fl ->
                val flInt = (fl / 10f).toInt() * 10
                focalLengthMap[flInt] = (focalLengthMap[flInt] ?: 0) + 1
            }

            entry.aperture?.let { ap ->
                val apStr = "f/${String.format(Locale.US, "%.1f", ap)}"
                apertureMap[apStr] = (apertureMap[apStr] ?: 0) + 1
            }

            entry.iso?.let { iso ->
                val isoBucket = when {
                    iso < 200 -> 100
                    iso < 400 -> 200
                    iso < 800 -> 400
                    iso < 1600 -> 800
                    iso < 3200 -> 1600
                    iso < 6400 -> 3200
                    else -> 6400
                }
                isoMap[isoBucket] = (isoMap[isoBucket] ?: 0) + 1
            }
        }

        val cameras = cameraMap.flatMap { (make, models) ->
            models.map { model ->
                DamCameraStats(make, model, entries.count {
                    it.cameraMake == make && it.cameraModel == model
                })
            }
        }.sortedByDescending { it.count }

        val lenses = lensMap.map { (lens, count) ->
            DamLensStats(lens, count)
        }.sortedByDescending { it.count }

        val dateGroups = dateMap.map { (date, count) ->
            DamDateGroup(date, count)
        }.sortedByDescending { it.date }

        return DamFacetData(
            cameras = cameras,
            lenses = lenses,
            dateGroups = dateGroups,
            focalLengths = focalLengthMap.toSortedMap(),
            apertureValues = apertureMap.toSortedMap(),
            isoValues = isoMap.toSortedMap(),
        )
    }

    // ── JSON Sidecar 持久化 ───────────────────────────────────────

    /**
     * 保存单个图像的 sidecar 元数据
     * 每个图像对应一个 .json.xmp sidecar 文件
     */
    suspend fun saveSidecar(entry: DamImageEntry) = withContext(Dispatchers.IO) {
        val imageFile = File(entry.path)
        val sidecarFile = File(imageFile.parent, imageFile.name + SIDECAR_EXTENSION)

        try {
            val json = kotlinx.serialization.json.Json { prettyPrint = true }
            val sidecarData = SidecarData(
                id = entry.id,
                rating = entry.rating,
                colorLabel = entry.colorLabel,
                tags = entry.tags,
                hasAdjustments = entry.hasAdjustments,
                flagStatus = entry.flagStatus,
            )
            sidecarFile.writeText(json.encodeToString(SidecarData.serializer(), sidecarData))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save sidecar for ${entry.fileName}: ${e.message}")
        }
    }

    /**
     * 加载图像的 sidecar 元数据
     */
    suspend fun loadSidecar(imagePath: String): SidecarData? = withContext(Dispatchers.IO) {
        val sidecarFile = File(imagePath + SIDECAR_EXTENSION)
        if (!sidecarFile.exists()) return@withContext null

        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            json.decodeFromString<SidecarData>(sidecarFile.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load sidecar: ${e.message}")
            null
        }
    }

    /**
     * 从 sidecar 恢复所有图像的元数据
     */
    suspend fun restoreSidecars() = withContext(Dispatchers.IO) {
        val project = _currentProject.value ?: return@withContext
        val updatedEntries = project.imageEntries.map { entry ->
            val sidecar = loadSidecar(entry.path) ?: return@map entry
            entry.copy(
                rating = sidecar.rating,
                colorLabel = sidecar.colorLabel,
                tags = sidecar.tags,
                hasAdjustments = sidecar.hasAdjustments,
                flagStatus = sidecar.flagStatus,
            )
        }
        _currentProject.value = project.copy(imageEntries = updatedEntries)
    }

    @kotlinx.serialization.Serializable
    data class SidecarData(
        val id: String,
        val rating: Int = 0,
        val colorLabel: ColorLabel = ColorLabel.NONE,
        val tags: List<String> = emptyList(),
        val hasAdjustments: Boolean = false,
        val flagStatus: Int = 0,
    )

    // ── 最近项目 ──────────────────────────────────────────────────

    private fun loadRecentProjects() {
        val prefs = context.getSharedPreferences("dam_projects", Context.MODE_PRIVATE)
        val count = prefs.getInt("recent_count", 0)
        val projects = mutableListOf<DamProjectInfo>()
        for (i in 0 until count) {
            val path = prefs.getString("recent_${i}_path", null) ?: continue
            val name = prefs.getString("recent_${i}_name", "") ?: ""
            val thumb = prefs.getString("recent_${i}_thumb", null)
            val lastOpened = prefs.getLong("recent_${i}_time", 0L)
            val imageCount = prefs.getInt("recent_${i}_count", 0)
            projects.add(DamProjectInfo(path, name, thumb, lastOpened, imageCount))
        }
        _recentProjects.value = projects.sortedByDescending { it.lastOpened }
    }

    private fun addToRecentProjects(
        path: String,
        name: String,
        thumbnailPath: String?,
        imageCount: Int
    ) {
        val prefs = context.getSharedPreferences("dam_projects", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val existing = _recentProjects.value.filter { it.path != path }.take(9)
        val updated = listOf(
            DamProjectInfo(path, name, thumbnailPath, System.currentTimeMillis(), imageCount)
        ) + existing

        editor.putInt("recent_count", updated.size)
        updated.forEachIndexed { index, info ->
            editor.putString("recent_${index}_path", info.path)
            editor.putString("recent_${index}_name", info.name)
            editor.putString("recent_${index}_thumb", info.thumbnailPath)
            editor.putLong("recent_${index}_time", info.lastOpened)
            editor.putInt("recent_${index}_count", info.imageCount)
        }
        editor.apply()

        _recentProjects.value = updated
    }

    // ── 导出 ──────────────────────────────────────────────────────

    suspend fun exportProjectToZip(project: DamProjectFile, outputPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                ZipOutputStream(FileOutputStream(outputPath)).use { zos ->
                    val json = DamProjectFile.toJson(project)
                    zos.putNextEntry(ZipEntry("project.alcd"))
                    zos.write(json.toByteArray())
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("manifest.txt"))
                    val manifest = buildString {
                        append("Alcedo Studio Project\n")
                        append("Version: ${project.version}\n")
                        append("Name: ${project.name}\n")
                        append("Created: ${Date(project.createdAt)}\n")
                        append("Modified: ${Date(project.modifiedAt)}\n")
                        append("Images: ${project.imageEntries.size}\n")
                        append("Albums: ${project.albums.size}\n")
                        append("Smart Albums: ${project.smartAlbums.size}\n")
                    }
                    zos.write(manifest.toByteArray())
                    zos.closeEntry()
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export project", e)
                false
            }
        }
}

// ── 扩展函数 ──────────────────────────────────────────────────────

/**
 * DamImageEntry 转 ImageFile
 */
private fun DamImageEntry.toImageFile(): ImageFile {
    return ImageFile(
        path = path,
        fileName = fileName,
        folderPath = folderPath,
        isRaw = isRaw,
        width = width,
        height = height,
        fileSize = fileSize,
        dateModified = dateModified,
        rating = rating,
        colorLabel = colorLabel,
        tags = tags,
        thumbnailPath = thumbnailPath,
        virtualCopyOf = virtualCopyOf,
        lastEdited = lastEdited,
    )
}
