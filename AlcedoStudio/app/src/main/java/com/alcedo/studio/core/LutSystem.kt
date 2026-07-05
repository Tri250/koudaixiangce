package com.alcedo.studio.core

import android.content.Context
import android.net.Uri
import com.alcedo.studio.data.model.Adjustments
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class CubeLutParser {

    fun parse(inputStream: java.io.InputStream): Lut3D? {
        return try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()

            var size = 33
            var title = ""
            val data = mutableListOf<FloatArray>()
            var inData = false

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                when {
                    trimmed.startsWith("TITLE", ignoreCase = true) -> {
                        title = trimmed.substringAfter("\"").substringBefore("\"")
                    }
                    trimmed.startsWith("LUT_3D_SIZE", ignoreCase = true) -> {
                        size = trimmed.substringAfter(" ").trim().toIntOrNull() ?: 33
                    }
                    trimmed.startsWith("LUT_1D_SIZE", ignoreCase = true) -> {
                        // 1D LUT, skip for now
                    }
                    trimmed.startsWith("DOMAIN_MIN", ignoreCase = true) ||
                    trimmed.startsWith("DOMAIN_MAX", ignoreCase = true) -> {
                        // Skip domain bounds
                    }
                    else -> {
                        val parts = trimmed.split(Regex("\\s+"))
                        if (parts.size >= 3) {
                            try {
                                val r = parts[0].toFloat()
                                val g = parts[1].toFloat()
                                val b = parts[2].toFloat()
                                data.add(floatArrayOf(r, g, b))
                                inData = true
                            } catch (_: NumberFormatException) {
                            }
                        }
                    }
                }
            }

            if (data.size == size * size * size) {
                Lut3D(
                    size = size,
                    data = data.toTypedArray(),
                    title = title
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class Lut3D(
    val size: Int,
    val data: Array<FloatArray>,
    val title: String = ""
) {
    fun apply(rgb: FloatArray, intensity: Float = 1f): FloatArray {
        val r = rgb[0].coerceIn(0f, 1f)
        val g = rgb[1].coerceIn(0f, 1f)
        val b = rgb[2].coerceIn(0f, 1f)

        val sizeM1 = size - 1
        val rIdx = r * sizeM1
        val gIdx = g * sizeM1
        val bIdx = b * sizeM1

        val r0 = rIdx.toInt().coerceIn(0, sizeM1)
        val g0 = gIdx.toInt().coerceIn(0, sizeM1)
        val b0 = bIdx.toInt().coerceIn(0, sizeM1)

        val r1 = (r0 + 1).coerceIn(0, sizeM1)
        val g1 = (g0 + 1).coerceIn(0, sizeM1)
        val b1 = (b0 + 1).coerceIn(0, sizeM1)

        val rFrac = rIdx - r0
        val gFrac = gIdx - g0
        val bFrac = bIdx - b0

        val c000 = data[indexOf(r0, g0, b0)]
        val c100 = data[indexOf(r1, g0, b0)]
        val c010 = data[indexOf(r0, g1, b0)]
        val c110 = data[indexOf(r1, g1, b0)]
        val c001 = data[indexOf(r0, g0, b1)]
        val c101 = data[indexOf(r1, g0, b1)]
        val c011 = data[indexOf(r0, g1, b1)]
        val c111 = data[indexOf(r1, g1, b1)]

        val c00 = ColorMath.mix(c000, c100, rFrac)
        val c10 = ColorMath.mix(c010, c110, rFrac)
        val c01 = ColorMath.mix(c001, c101, rFrac)
        val c11 = ColorMath.mix(c011, c111, rFrac)

        val c0 = ColorMath.mix(c00, c10, gFrac)
        val c1 = ColorMath.mix(c01, c11, gFrac)

        val result = ColorMath.mix(c0, c1, bFrac)

        return if (intensity < 1f) {
            ColorMath.mix(rgb, result, intensity)
        } else {
            result
        }
    }

    private fun indexOf(r: Int, g: Int, b: Int): Int {
        return r + g * size + b * size * size
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Lut3D) return false
        return size == other.size && data.contentDeepEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + data.contentDeepHashCode()
        return result
    }
}

class LutLibraryManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val lutDir: File
        get() = File(context.filesDir, "luts")

    init {
        if (!lutDir.exists()) {
            lutDir.mkdirs()
        }
    }

    fun getLutList(): List<LutEntry> {
        val jsonFile = File(lutDir, "luts.json")
        return if (jsonFile.exists()) {
            try {
                val jsonString = jsonFile.readText()
                json.decodeFromString<List<LutEntry>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            getBuiltInLuts()
        }
    }

    private fun getBuiltInLuts(): List<LutEntry> {
        return listOf(
            LutEntry(
                id = "kodak_portra_400",
                name = "Kodak Portra 400",
                category = "胶片模拟",
                isBuiltIn = true
            ),
            LutEntry(
                id = "kodak_ektar_100",
                name = "Kodak Ektar 100",
                category = "胶片模拟",
                isBuiltIn = true
            ),
            LutEntry(
                id = "fuji_superia_400",
                name = "Fuji Superia 400",
                category = "胶片模拟",
                isBuiltIn = true
            ),
            LutEntry(
                id = "fuji_velvia_50",
                name = "Fuji Velvia 50",
                category = "胶片模拟",
                isBuiltIn = true
            ),
            LutEntry(
                id = "agfa_vista_400",
                name = "Agfa Vista 400",
                category = "胶片模拟",
                isBuiltIn = true
            ),
        )
    }

    fun loadLut(lutId: String): Lut3D? {
        val lutFile = File(lutDir, "$lutId.cube")
        return if (lutFile.exists()) {
            lutFile.inputStream().use {
                CubeLutParser().parse(it)
            }
        } else {
            generateBuiltInLut(lutId)
        }
    }

    private fun generateBuiltInLut(lutId: String): Lut3D? {
        val adjustments = when (lutId) {
            "kodak_portra_400" -> Adjustments(
                saturation = -8f,
                contrast = 5f,
                filmId = lutId
            )
            "kodak_ektar_100" -> Adjustments(
                saturation = 15f,
                contrast = 10f,
                filmId = lutId
            )
            "fuji_superia_400" -> Adjustments(
                saturation = 5f,
                greenMagenta = -3f,
                filmId = lutId
            )
            "fuji_velvia_50" -> Adjustments(
                saturation = 25f,
                contrast = 15f,
                filmId = lutId
            )
            "agfa_vista_400" -> Adjustments(
                saturation = 10f,
                temperature = -5f,
                filmId = lutId
            )
            else -> return null
        }

        return generateLutFromAdjustments(adjustments, 33)
    }

    private fun generateLutFromAdjustments(adjustments: Adjustments, size: Int): Lut3D {
        val data = Array(size * size * size) { FloatArray(3) }
        val sizeM1 = size - 1

        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val idx = r + g * size + b * size * size
                    var rgb = floatArrayOf(
                        r.toFloat() / sizeM1,
                        g.toFloat() / sizeM1,
                        b.toFloat() / sizeM1
                    )

                    rgb = ColorMath.adjustSaturation(rgb, 1f + adjustments.saturation / 100f)
                    val contrast = 1f + adjustments.contrast / 100f
                    rgb[0] = ColorMath.contrast(rgb[0], contrast)
                    rgb[1] = ColorMath.contrast(rgb[1], contrast)
                    rgb[2] = ColorMath.contrast(rgb[2], contrast)

                    if (adjustments.temperature != 0f) {
                        val tempFactor = 1f + adjustments.temperature / 100f * 0.3f
                        rgb[0] = ColorMath.clamp(rgb[0] * tempFactor)
                        rgb[2] = ColorMath.clamp(rgb[2] / tempFactor)
                    }

                    if (adjustments.greenMagenta != 0f) {
                        val gmShift = adjustments.greenMagenta / 100f * 0.1f
                        rgb[1] = ColorMath.clamp(rgb[1] + gmShift)
                    }

                    data[idx] = rgb
                }
            }
        }

        return Lut3D(size = size, data = data, title = adjustments.filmId)
    }

    fun importLut(uri: Uri, name: String): LutEntry? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val lut = CubeLutParser().parse(inputStream) ?: return null

            val lutId = "imported_${System.currentTimeMillis()}"
            val lutFile = File(lutDir, "$lutId.cube")

            context.contentResolver.openInputStream(uri)?.use { input ->
                lutFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val entry = LutEntry(
                id = lutId,
                name = name,
                category = "导入",
                isBuiltIn = false
            )

            saveLutList(getLutList() + entry)
            entry
        } catch (e: Exception) {
            null
        }
    }

    private fun saveLutList(list: List<LutEntry>) {
        try {
            val jsonFile = File(lutDir, "luts.json")
            val jsonString = json.encodeToString(list)
            jsonFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteLut(lutId: String): Boolean {
        val lutFile = File(lutDir, "$lutId.cube")
        if (lutFile.exists()) {
            lutFile.delete()
        }
        val list = getLutList().filter { it.id != lutId }
        saveLutList(list)
        return true
    }

    fun getCategories(): List<String> {
        return getLutList().map { it.category }.distinct().sorted()
    }
}

@Serializable
data class LutEntry(
    val id: String,
    val name: String,
    val category: String = "custom",
    val isBuiltIn: Boolean = false,
    val usageCount: Int = 0,
    val isFavorite: Boolean = false,
)

object FilmLutGenerator {

    fun generateFilmLut(filmId: String, size: Int = 33): Lut3D {
        val filmParams = getFilmParams(filmId)
        val data = Array(size * size * size) { FloatArray(3) }
        val sizeM1 = size - 1

        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val idx = r + g * size + b * size * size
                    var rgb = floatArrayOf(
                        r.toFloat() / sizeM1,
                        g.toFloat() / sizeM1,
                        b.toFloat() / sizeM1
                    )

                    rgb[0] = ColorMath.clamp(rgb[0] * filmParams.rMult)
                    rgb[1] = ColorMath.clamp(rgb[1] * filmParams.gMult)
                    rgb[2] = ColorMath.clamp(rgb[2] * filmParams.bMult)

                    val contrast = 1f + filmParams.contrast
                    rgb[0] = ColorMath.contrast(rgb[0], contrast)
                    rgb[1] = ColorMath.contrast(rgb[1], contrast)
                    rgb[2] = ColorMath.contrast(rgb[2], contrast)

                    val sat = 1f + filmParams.saturation
                    rgb = ColorMath.adjustSaturation(rgb, sat)

                    data[idx] = rgb
                }
            }
        }

        return Lut3D(size = size, data = data, title = filmId)
    }

    private data class FilmParams(
        val rMult: Float, val gMult: Float, val bMult: Float,
        val contrast: Float, val saturation: Float
    )

    private fun getFilmParams(filmId: String): FilmParams = when (filmId) {
        "kodak_portra_400" -> FilmParams(1.05f, 1.02f, 0.98f, 0.05f, -0.08f)
        "kodak_ektar_100" -> FilmParams(1.08f, 1.03f, 0.95f, 0.1f, 0.15f)
        "fuji_superia_400" -> FilmParams(0.98f, 1.05f, 1.08f, 0.03f, 0.05f)
        "fuji_velvia_50" -> FilmParams(1.1f, 1.08f, 0.9f, 0.15f, 0.25f)
        "agfa_vista_400" -> FilmParams(0.95f, 1.02f, 1.1f, 0.08f, 0.1f)
        "bw_ilford_hp5" -> FilmParams(1f, 1f, 1f, 0.12f, -1f)
        else -> FilmParams(1f, 1f, 1f, 0f, 0f)
    }
}
