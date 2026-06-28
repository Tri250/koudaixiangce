package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * .cube LUT 文件解析器
 * 支持标准 Adobe Cube LUT 格式（3D LUT）
 */
class CubeLutParser {

    data class Lut3D(
        val size: Int,           // 每维大小（如 33 = 33x33x33）
        val data: FloatArray,    // RGB interleaved: [R,G,B,R,G,B,...]
    )

    /**
     * 解析 .cube 文件
     */
    fun parse(file: File): Lut3D? = parse(file.inputStream())

    /**
     * 解析 .cube 文件输入流
     */
    fun parse(inputStream: java.io.InputStream?): Lut3D? {
        if (inputStream == null) return null
        val lines = inputStream.bufferedReader().readLines()
        var size = 0
        val values = mutableListOf<Float>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("LUT_3D_SIZE", ignoreCase = true) -> {
                    size = trimmed.substringAfter(" ").trim().toIntOrNull() ?: 0
                }
                trimmed.startsWith("LUT_1D_SIZE", ignoreCase = true) -> {
                    // 不支持 1D LUT
                    return null
                }
                trimmed.startsWith("#") || trimmed.startsWith("TITLE", ignoreCase = true) ||
                trimmed.startsWith("DOMAIN_MIN", ignoreCase = true) ||
                trimmed.startsWith("DOMAIN_MAX", ignoreCase = true) -> {
                    // 跳过注释和元数据
                }
                trimmed.isNotEmpty() -> {
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size == 3) {
                        parts.forEach { p ->
                            val v = p.toFloatOrNull()
                            if (v != null) values.add(v)
                        }
                    }
                }
            }
        }

        if (size == 0 || values.size != size * size * size * 3) return null
        return Lut3D(size, values.toFloatArray())
    }

    /**
     * 将 3D LUT 数据转换为 3D 纹理可用的格式
     * OpenGL ES 3.0 的 glTexImage3D 需要 R/G/B 分离或 RGBA 打包
     */
    fun lutToRgbaBuffer(lut: Lut3D): ByteArray {
        val size = lut.size
        val total = size * size * size
        val buffer = ByteArray(total * 4) // RGBA

        for (i in 0 until total) {
            val r = (lut.data[i * 3] * 255).toInt().coerceIn(0, 255)
            val g = (lut.data[i * 3 + 1] * 255).toInt().coerceIn(0, 255)
            val b = (lut.data[i * 3 + 2] * 255).toInt().coerceIn(0, 255)
            buffer[i * 4] = r.toByte()
            buffer[i * 4 + 1] = g.toByte()
            buffer[i * 4 + 2] = b.toByte()
            buffer[i * 4 + 3] = 0xFF.toByte()
        }
        return buffer
    }
}

/**
 * LUT 管理器：导入、存储、列表管理
 */
class LutManager(private val context: Context) {

    data class LutEntry(
        val id: String,
        val name: String,
        val category: String,
        val filePath: String,
        val size: Int,
        val isBuiltIn: Boolean,
    )

    private val _luts = MutableStateFlow<List<LutEntry>>(emptyList())
    val luts: StateFlow<List<LutEntry>> = _luts.asStateFlow()

    private val lutDir = File(context.filesDir, "luts").also { it.mkdirs() }
    private val parser = CubeLutParser()

    /**
     * 导入 .cube 文件
     */
    fun importLut(file: File): Boolean {
        val lut = parser.parse(file) ?: return false
        return importLut(lut, file.nameWithoutExtension)
    }

    /**
     * 从已解析的 Lut3D 导入
     */
    fun importLut(lut: CubeLutParser.Lut3D, name: String): Boolean {
        val id = "${System.currentTimeMillis()}_$name"
        val dest = File(lutDir, "$id.cube")
        dest.bufferedWriter().use { out ->
            out.write("TITLE \"$name\"\n")
            out.write("LUT_3D_SIZE ${lut.size}\n")
            out.write("DOMAIN_MIN 0.0 0.0 0.0\n")
            out.write("DOMAIN_MAX 1.0 1.0 1.0\n")
            val data = lut.data
            for (i in data.indices step 3) {
                out.write("${data[i]} ${data[i + 1]} ${data[i + 2]}\n")
            }
        }

        val entry = LutEntry(
            id = id,
            name = name,
            category = "用户导入",
            filePath = dest.absolutePath,
            size = lut.size,
            isBuiltIn = false,
        )
        _luts.value = _luts.value + entry
        return true
    }

    /**
     * 扫描目录导入所有 .cube 文件
     */
    fun scanDirectory(dir: File) {
        dir.listFiles { _, name -> name.endsWith(".cube", true) }
            ?.forEach { importLut(it) }
    }

    /**
     * 删除 LUT
     */
    fun deleteLut(id: String) {
        val entry = _luts.value.find { it.id == id } ?: return
        File(entry.filePath).delete()
        _luts.value = _luts.value.filterNot { it.id == id }
    }

    /**
     * 解析指定 LUT
     */
    fun loadLut(id: String): CubeLutParser.Lut3D? {
        val entry = _luts.value.find { it.id == id } ?: return null
        return parser.parse(File(entry.filePath))
    }
}
