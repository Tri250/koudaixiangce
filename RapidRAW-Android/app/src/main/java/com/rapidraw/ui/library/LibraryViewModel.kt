package com.rapidraw.ui.library

import android.app.Application
import android.content.ContentUris
import android.database.Cursor
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rapidraw.data.model.ImageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOrder {
    DATE,
    RATING,
    NAME,
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val contentResolver = application.contentResolver

    private val _images = MutableStateFlow<List<ImageFile>>(emptyList())
    val images: StateFlow<List<ImageFile>> = _images.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _filterRaw = MutableStateFlow(false)
    val filterRaw: StateFlow<Boolean> = _filterRaw.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _thumbnails = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<String, Bitmap>> = _thumbnails.asStateFlow()

    private val _selectedImages = MutableStateFlow<Set<String>>(emptySet())
    val selectedImages: StateFlow<Set<String>> = _selectedImages.asStateFlow()

    private val SUPPORTED_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "tiff", "tif",
        "dng", "cr2", "cr3", "nef", "arw", "raf", "orf", "rw2", "pef", "srw",
    )

    init {
        loadImages(null)
    }

    fun loadImages(folder: String?) {
        _selectedFolder.value = folder
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val imageList = queryImages(folder)
            val folderSet = mutableSetOf<String>()

            for (image in imageList) {
                val parentFolder = image.folderPath
                if (parentFolder.isNotEmpty()) {
                    folderSet.add(parentFolder)
                }
            }

            _folders.value = folderSet.toList().sorted()
            applyFilters(imageList)
            _isLoading.value = false
        }
    }

    private suspend fun queryImages(folder: String?): List<ImageFile> =
        withContext(Dispatchers.IO) {
            val images = mutableListOf<ImageFile>()

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
            )

            val selectionBuilder = StringBuilder()
            val selectionArgs = mutableListOf<String>()

            val extensionConditions = SUPPORTED_EXTENSIONS.map { ext ->
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE '%.$ext'"
            } + SUPPORTED_EXTENSIONS.map { ext ->
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE '%.${ext.uppercase()}'"
            }
            selectionBuilder.append("(${extensionConditions.joinToString(" OR ")})")

            if (folder != null) {
                selectionBuilder.append(" AND ${MediaStore.Images.Media.DATA} LIKE ?")
                selectionArgs.add("$folder/%")
            }

            val sortOrderClause = when (_sortOrder.value) {
                SortOrder.DATE -> "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                SortOrder.RATING -> "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                SortOrder.NAME -> "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            }

            val cursor: Cursor? = contentResolver.query(
                collection,
                projection,
                selectionBuilder.toString(),
                selectionArgs.toTypedArray(),
                sortOrderClause,
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (it.moveToNext()) {
                    val path = it.getString(dataColumn) ?: continue
                    val fileName = it.getString(nameColumn) ?: continue
                    val extension = fileName.substringAfterLast('.', "").lowercase()

                    if (extension !in SUPPORTED_EXTENSIONS) continue

                    val folderPath = path.substringBeforeLast('/', "")
                    val isRaw = extension in ImageFile.RAW_EXTENSIONS

                    images.add(
                        ImageFile(
                            path = path,
                            fileName = fileName,
                            folderPath = folderPath,
                            isRaw = isRaw,
                            width = if (widthColumn >= 0) it.getInt(widthColumn) else 0,
                            height = if (heightColumn >= 0) it.getInt(heightColumn) else 0,
                            fileSize = if (sizeColumn >= 0) it.getLong(sizeColumn) else 0L,
                            dateModified = if (dateColumn >= 0) it.getLong(dateColumn) * 1000L else 0L,
                            rating = 0,
                        )
                    )
                }
            }

            images
        }

    fun loadThumbnails() {
        viewModelScope.launch(Dispatchers.IO) {
            val thumbnailMap = mutableMapOf<String, Bitmap>()
            val currentImages = _images.value

            for (image in currentImages) {
                try {
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        image.path.hashCode().toLong().let { if (it > 0) it else -it }
                    )

                    val thumbnail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentResolver.loadThumbnail(
                            uri,
                            android.util.Size(256, 256),
                            null,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Thumbnails.getThumbnail(
                            contentResolver,
                            image.path.hashCode().toLong().let { if (it > 0) it else -it },
                            MediaStore.Images.Thumbnails.MINI_KIND,
                            null,
                        )
                    }

                    if (thumbnail != null) {
                        thumbnailMap[image.path] = thumbnail
                    }
                } catch (_: Exception) {
                    // Skip failed thumbnails
                }
            }

            _thumbnails.value = thumbnailMap
        }
    }

    fun searchImages(query: String) {
        _searchQuery.value = query
        viewModelScope.launch(Dispatchers.IO) {
            val allImages = queryImages(_selectedFolder.value)
            applyFilters(allImages)
        }
    }

    fun toggleSortOrder() {
        _sortOrder.value = when (_sortOrder.value) {
            SortOrder.DATE -> SortOrder.RATING
            SortOrder.RATING -> SortOrder.NAME
            SortOrder.NAME -> SortOrder.DATE
        }
        viewModelScope.launch(Dispatchers.IO) {
            val allImages = queryImages(_selectedFolder.value)
            applyFilters(allImages)
        }
    }

    fun toggleFilterRaw() {
        _filterRaw.value = !_filterRaw.value
        viewModelScope.launch(Dispatchers.IO) {
            val allImages = queryImages(_selectedFolder.value)
            applyFilters(allImages)
        }
    }

    fun toggleImageSelection(path: String) {
        _selectedImages.update { current ->
            if (path in current) {
                current - path
            } else {
                current + path
            }
        }
    }

    fun clearSelection() {
        _selectedImages.value = emptySet()
    }

    private fun applyFilters(allImages: List<ImageFile>) {
        var filtered = allImages

        if (_filterRaw.value) {
            filtered = filtered.filter { it.isRaw }
        }

        val query = _searchQuery.value.trim()
        if (query.isNotEmpty()) {
            val lowerQuery = query.lowercase()
            filtered = filtered.filter { it.fileName.lowercase().contains(lowerQuery) }
        }

        filtered = when (_sortOrder.value) {
            SortOrder.DATE -> filtered.sortedByDescending { it.dateModified }
            SortOrder.RATING -> filtered.sortedByDescending { it.rating }
            SortOrder.NAME -> filtered.sortedBy { it.fileName.lowercase() }
        }

        _images.value = filtered
    }
}
