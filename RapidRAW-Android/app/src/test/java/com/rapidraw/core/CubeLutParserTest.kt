package com.rapidraw.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class CubeLutParserTest {

    private fun createIdentityCube(size: Int): String {
        val sb = StringBuilder()
        sb.append("TITLE \"Identity\"")
        sb.appendLine()
        sb.append("LUT_3D_SIZE $size")
        sb.appendLine()
        sb.append("DOMAIN_MIN 0.0 0.0 0.0")
        sb.appendLine()
        sb.append("DOMAIN_MAX 1.0 1.0 1.0")
        sb.appendLine()

        val step = 1f / (size - 1)
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    sb.append("${r * step} ${g * step} ${b * step}")
                    sb.appendLine()
                }
            }
        }
        return sb.toString()
    }

    @Test
    fun parse_valid3DLut_parsesCorrectly() {
        val parser = CubeLutParser()
        val cubeContent = createIdentityCube(3)
        val lut = parser.parse(ByteArrayInputStream(cubeContent.toByteArray()))

        assertNotNull(lut)
        assertEquals(3, lut?.size)
        assertEquals(3 * 3 * 3 * 3, lut?.data?.size)
    }

    @Test
    fun parse_nullInput_returnsNull() {
        val parser = CubeLutParser()
        assertNull(parser.parse(null))
    }

    @Test
    fun parse_emptyData_returnsNull() {
        val parser = CubeLutParser()
        assertNull(parser.parse(ByteArrayInputStream("".toByteArray())))
    }

    @Test
    fun parse_1DLut_returnsNull() {
        val parser = CubeLutParser()
        val content = """
            LUT_1D_SIZE 3
            0.0 0.0 0.0
            0.5 0.5 0.5
            1.0 1.0 1.0
        """.trimIndent()
        assertNull(parser.parse(ByteArrayInputStream(content.toByteArray())))
    }

    @Test
    fun parse_incompleteData_returnsNull() {
        val parser = CubeLutParser()
        val content = """
            LUT_3D_SIZE 3
            0.0 0.0 0.0
        """.trimIndent()
        assertNull(parser.parse(ByteArrayInputStream(content.toByteArray())))
    }

    @Test
    fun lut3DSample_identity_returnsSameValue() {
        val parser = CubeLutParser()
        val lut = parser.parse(ByteArrayInputStream(createIdentityCube(3).toByteArray()))!!

        val sampled = lut.sample(0.5f, 0.5f, 0.5f)
        assertEquals(0.5f, sampled.first, 0.01f)
        assertEquals(0.5f, sampled.second, 0.01f)
        assertEquals(0.5f, sampled.third, 0.01f)
    }

    @Test
    fun lut3DSample_clampsOutsideRange() {
        val parser = CubeLutParser()
        val lut = parser.parse(ByteArrayInputStream(createIdentityCube(3).toByteArray()))!!

        val sampled = lut.sample(1.5f, -0.5f, 0.5f)
        assertTrue(sampled.first in 0f..1f)
        assertTrue(sampled.second in 0f..1f)
        assertTrue(sampled.third in 0f..1f)
    }

    @Test
    fun lutToRgbaBuffer_hasCorrectSize() {
        val parser = CubeLutParser()
        val lut = parser.parse(ByteArrayInputStream(createIdentityCube(2).toByteArray()))!!
        val buffer = parser.lutToRgbaBuffer(lut)

        assertEquals(2 * 2 * 2 * 4, buffer.size)
    }

    @Test
    fun parse_fromFile() {
        val parser = CubeLutParser()
        val tempFile = File.createTempFile("identity", ".cube")
        tempFile.writeText(createIdentityCube(3))

        val lut = parser.parseFile(tempFile)
        assertNotNull(lut)
        assertEquals(3, lut?.size)

        tempFile.delete()
    }

    @Test
    fun parse_overSizedLut_returnsNull() {
        val parser = CubeLutParser()
        // 创建一个超过 MAX_LUT_SIZE 的 LUT 头
        val content = """
            LUT_3D_SIZE 65
            0.0 0.0 0.0
        """.trimIndent()
        assertNull(parser.parse(ByteArrayInputStream(content.toByteArray())))
    }

    @Test
    fun parse_invalidSize_returnsNull() {
        val parser = CubeLutParser()
        val content = """
            LUT_3D_SIZE 1
            0.0 0.0 0.0
        """.trimIndent()
        assertNull(parser.parse(ByteArrayInputStream(content.toByteArray())))
    }
}
