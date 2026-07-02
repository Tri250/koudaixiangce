package com.rapidraw.data.model

import org.junit.Assert.*
import org.junit.Test

class ImageFileTest {

    @Test
    fun isRawFile_recognizesCommonRawExtensions() {
        assertTrue(ImageFile.isRawFile("/sdcard/IMG_1234.dng"))
        assertTrue(ImageFile.isRawFile("/sdcard/IMG_1234.CR2"))
        assertTrue(ImageFile.isRawFile("/sdcard/IMG_1234.arw"))
        assertTrue(ImageFile.isRawFile("/sdcard/IMG_1234.NEF"))
        assertTrue(ImageFile.isRawFile("/sdcard/IMG_1234.RAF"))
    }

    @Test
    fun isRawFile_returnsFalseForJpegAndPng() {
        assertFalse(ImageFile.isRawFile("/sdcard/IMG_1234.jpg"))
        assertFalse(ImageFile.isRawFile("/sdcard/IMG_1234.jpeg"))
        assertFalse(ImageFile.isRawFile("/sdcard/IMG_1234.png"))
        assertFalse(ImageFile.isRawFile("/sdcard/IMG_1234.heic"))
    }

    @Test
    fun isRawFile_handlesPathsWithoutExtension() {
        assertFalse(ImageFile.isRawFile("/sdcard/no_extension"))
    }

    @Test
    fun imageFile_defaultsAreSane() {
        val image = ImageFile(
            path = "/sdcard/IMG.jpg",
            fileName = "IMG.jpg",
            folderPath = "/sdcard",
            isRaw = false,
        )
        assertEquals(0, image.width)
        assertEquals(0, image.height)
        assertEquals(0L, image.fileSize)
        assertEquals(0, image.rating)
        assertEquals(ColorLabel.NONE, image.colorLabel)
        assertTrue(image.tags.isEmpty())
    }
}
