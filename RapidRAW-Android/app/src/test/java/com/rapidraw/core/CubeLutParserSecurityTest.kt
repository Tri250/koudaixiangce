package com.rapidraw.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class CubeLutParserSecurityTest {

    private val parser = CubeLutParser()

    @Test
    fun `parse rejects oversized declared 3D LUT`() {
        val content = buildString {
            appendLine("LUT_3D_SIZE 129")
            appendLine("1.0 1.0 1.0")
        }
        val result = parser.parse(ByteArrayInputStream(content.toByteArray()))
        assertNull(result?.lut3D)
    }

    @Test
    fun `parse accepts valid small 3D LUT`() {
        val size = 2
        val content = buildString {
            appendLine("LUT_3D_SIZE $size")
            repeat(size * size * size) {
                appendLine("${it.toFloat() / (size * size * size)} 0.0 0.0")
            }
        }
        val result = parser.parse(ByteArrayInputStream(content.toByteArray()))
        assertNotNull(result)
        assertEquals(size, result!!.lut3D!!.size)
    }
}
