package com.alcedo.studio.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class FaceDetector(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val faceDir: File
        get() = File(context.filesDir, "faces")

    init {
        if (!faceDir.exists()) {
            faceDir.mkdirs()
        }
    }

    fun detectFaces(bitmap: Bitmap): List<FaceDetectionResult> {
        val faces = mutableListOf<FaceDetectionResult>()

        try {
            val detector = android.media.FaceDetector(bitmap.width, bitmap.height, 10)
            val faceArray = arrayOfNulls<android.media.FaceDetector.Face>(10)
            val faceCount = detector.findFaces(bitmap, faceArray)

            for (i in 0 until faceCount) {
                val face = faceArray[i] ?: continue
                val midPoint = android.graphics.PointF()
                face.getMidPoint(midPoint)

                val eyesDistance = face.eyesDistance()
                val confidence = face.confidence()

                val left = (midPoint.x - eyesDistance).toInt()
                val top = (midPoint.y - eyesDistance).toInt()
                val right = (midPoint.x + eyesDistance).toInt()
                val bottom = (midPoint.y + eyesDistance * 1.5f).toInt()

                val bounds = Rect(
                    left.coerceAtLeast(0),
                    top.coerceAtLeast(0),
                    right.coerceAtMost(bitmap.width),
                    bottom.coerceAtMost(bitmap.height)
                )

                faces.add(
                    FaceDetectionResult(
                        id = UUID.randomUUID().toString(),
                        bounds = bounds,
                        confidence = confidence,
                        eyesDistance = eyesDistance,
                        pose = FacePose(
                            eulerX = face.pose(android.media.FaceDetector.Face.EULER_X),
                            eulerY = face.pose(android.media.FaceDetector.Face.EULER_Y),
                            eulerZ = face.pose(android.media.FaceDetector.Face.EULER_Z)
                        )
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return faces
    }

    fun extractFaceEmbedding(bitmap: Bitmap, faceRect: Rect): FloatArray? {
        return try {
            val faceBitmap = Bitmap.createBitmap(
                bitmap,
                faceRect.left,
                faceRect.top,
                faceRect.width(),
                faceRect.height()
            )

            val resized = Bitmap.createScaledBitmap(faceBitmap, 96, 96, true)

            val pixels = IntArray(96 * 96)
            resized.getPixels(pixels, 0, 96, 0, 0, 96, 96)

            val embedding = FloatArray(128)
            for (i in embedding.indices) {
                val pixelIndex = (i * 3) % pixels.size
                val pixel = pixels[pixelIndex]
                embedding[i] = ((pixel and 0xFF) / 255f) * 2 - 1
            }

            normalizeEmbedding(embedding)

            if (faceBitmap != resized && faceBitmap != bitmap) {
                faceBitmap.recycle()
            }
            if (resized != bitmap) {
                resized.recycle()
            }

            embedding
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeEmbedding(embedding: FloatArray) {
        var norm = 0f
        for (value in embedding) {
            norm += value * value
        }
        norm = kotlin.math.sqrt(norm)
        if (norm > 0f) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }
    }

    fun compareFaces(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) return 0f

        var dotProduct = 0f
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
        }

        return dotProduct.coerceIn(0f, 1f)
    }

    fun areSamePerson(embedding1: FloatArray, embedding2: FloatArray, threshold: Float = 0.6f): Boolean {
        return compareFaces(embedding1, embedding2) >= threshold
    }
}

class FaceAlbumManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val faceDetector = FaceDetector(context)

    private val albumDir: File
        get() = File(context.filesDir, "face_albums")

    init {
        if (!albumDir.exists()) {
            albumDir.mkdirs()
        }
    }

    fun getAlbums(): List<FaceAlbum> {
        val albumFile = File(albumDir, "albums.json")
        return if (albumFile.exists()) {
            try {
                json.decodeFromString<List<FaceAlbum>>(albumFile.readText())
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun saveAlbums(albums: List<FaceAlbum>) {
        try {
            val albumFile = File(albumDir, "albums.json")
            albumFile.writeText(json.encodeToString(albums))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createAlbum(name: String, coverPhotoUri: String? = null): FaceAlbum {
        val album = FaceAlbum(
            id = UUID.randomUUID().toString(),
            name = name,
            coverPhotoUri = coverPhotoUri,
            photoUris = mutableListOf(),
            createdAt = System.currentTimeMillis()
        )
        val albums = getAlbums().toMutableList()
        albums.add(album)
        saveAlbums(albums)
        return album
    }

    fun addPhotoToAlbum(albumId: String, photoUri: String) {
        val albums = getAlbums().toMutableList()
        val index = albums.indexOfFirst { it.id == albumId }
        if (index >= 0) {
            val album = albums[index]
            if (!album.photoUris.contains(photoUri)) {
                val updatedPhotos = album.photoUris.toMutableList()
                updatedPhotos.add(photoUri)
                albums[index] = album.copy(
                    photoUris = updatedPhotos,
                    updatedAt = System.currentTimeMillis()
                )
                saveAlbums(albums)
            }
        }
    }

    fun removePhotoFromAlbum(albumId: String, photoUri: String) {
        val albums = getAlbums().toMutableList()
        val index = albums.indexOfFirst { it.id == albumId }
        if (index >= 0) {
            val album = albums[index]
            val updatedPhotos = album.photoUris.filter { it != photoUri }
            albums[index] = album.copy(
                photoUris = updatedPhotos,
                updatedAt = System.currentTimeMillis()
            )
            saveAlbums(albums)
        }
    }

    fun renameAlbum(albumId: String, newName: String) {
        val albums = getAlbums().toMutableList()
        val index = albums.indexOfFirst { it.id == albumId }
        if (index >= 0) {
            albums[index] = albums[index].copy(name = newName)
            saveAlbums(albums)
        }
    }

    fun deleteAlbum(albumId: String) {
        val albums = getAlbums().filter { it.id != albumId }
        saveAlbums(albums)
    }

    fun getPhotosWithFaces(): List<PhotoWithFaces> {
        val faceDir = File(context.filesDir, "faces")
        return faceDir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<PhotoWithFaces>(file.readText())
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
    }

    fun savePhotoFaces(photoUri: String, faces: List<FaceDetectionResult>) {
        try {
            val photoWithFaces = PhotoWithFaces(
                photoUri = photoUri,
                faces = faces,
                analyzedAt = System.currentTimeMillis()
            )
            val file = File(faceDir, "${photoUri.hashCode()}.json")
            file.writeText(json.encodeToString(photoWithFaces))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clusterFaces(photos: List<PhotoWithFaces>, threshold: Float = 0.6f): List<FaceCluster> {
        val allFaces = photos.flatMap { photo ->
            photo.faces.map { face ->
                FacePhotoPair(photo.photoUri, face)
            }
        }

        val clusters = mutableListOf<FaceCluster>()
        val unclustered = allFaces.toMutableList()

        while (unclustered.isNotEmpty()) {
            val current = unclustered.removeAt(0)
            val cluster = FaceCluster(
                id = UUID.randomUUID().toString(),
                faces = mutableListOf(current)
            )

            val iterator = unclustered.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                val similarity = faceDetector.compareFaces(
                    current.face.embedding ?: FloatArray(128),
                    other.face.embedding ?: FloatArray(128)
                )
                if (similarity >= threshold) {
                    cluster.faces.add(other)
                    iterator.remove()
                }
            }

            clusters.add(cluster)
        }

        return clusters.sortedByDescending { it.faces.size }
    }
}

@Serializable
data class FaceDetectionResult(
    val id: String,
    val bounds: Rect,
    val confidence: Float = 1f,
    val eyesDistance: Float = 0f,
    val pose: FacePose = FacePose(),
    val embedding: FloatArray? = null,
    val personId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceDetectionResult) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

@Serializable
data class FacePose(
    val eulerX: Float = 0f,
    val eulerY: Float = 0f,
    val eulerZ: Float = 0f
)

@Serializable
data class FaceAlbum(
    val id: String,
    val name: String,
    val coverPhotoUri: String? = null,
    val photoUris: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class PhotoWithFaces(
    val photoUri: String,
    val faces: List<FaceDetectionResult>,
    val analyzedAt: Long
)

data class FacePhotoPair(
    val photoUri: String,
    val face: FaceDetectionResult
)

data class FaceCluster(
    val id: String,
    val faces: MutableList<FacePhotoPair>
)
