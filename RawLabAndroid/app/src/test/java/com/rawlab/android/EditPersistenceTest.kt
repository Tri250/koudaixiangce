package com.rawlab.android

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EditPersistenceTest {
    @Test fun failedReplacementKeepsLastDurableValueInMemory() {
        val root = Files.createTempDirectory("rawlab-atomic").toFile()
        try {
            val directory = File(root, "store")
            val store = EditStore(directory)
            val identity = PhotoIdentity("original")
            store.save(identity, EditSettings(exposure = .5f))
            assertTrue(directory.renameTo(File(root, "backup")))
            directory.writeText("blocked")
            assertThrows(Exception::class.java) { store.save(identity, EditSettings(exposure = 1f)) }
            assertEquals(.5f, store.load(identity)?.exposure)
        } finally { root.deleteRecursively() }
    }
    @Test fun uriIdentityRoundTripsAcrossStoreInstancesAndResets() {
        val directory = Files.createTempDirectory("rawlab-edits").toFile()
        try {
            val identity = PhotoIdentity("content://media/external/images/media/7")
            // 覆盖全部 12 个调整模块（强度/曝光/高光/阴影/对比度/S曲线/饱和度/色温/色调/降噪/暗角/颗粒），
            // 保证编辑记忆对每个模块都完整保存与恢复。
            val changed = EditSettings(film = "velvia", strength = 1.5f, exposure = .75f, contrast = .25f,
                exposureMode = 2, hsl = HslSettings().withValue(1, 0, -60f),
                highlights = .25f, shadows = -.35f, toneCurve = .4f, saturation = -.15f,
                customWb = true, temperature = 4200f, tint = 12f, sharpening = .5f,
                denoise = DenoiseSettings(true, 10f, 72f, 100f), displayChromaDenoise = 2,
                effects = PhotoEffectsSettings(vignetteAmount = -40f, vignetteMidpoint = 20f,
                    grainAmount = 65f, grainSize = 70f))
            EditStore(directory).save(identity, changed)
            val reopened = EditStore(directory)
            assertEquals(changed, reopened.load(identity))
            val file = File(directory, "edits.properties")
            val properties = java.util.Properties().also { file.inputStream().use(it::load) }
            val key = properties.stringPropertyNames().first { it.endsWith(".values") }
            assertEquals(49, properties.getProperty(key).split(',').size)
            properties.setProperty(key, properties.getProperty(key).split(',').take(24).joinToString(","))
            file.outputStream().use { properties.store(it, null) }
            val legacy = EditStore(directory).load(identity)!!
            assertEquals(0, legacy.exposureMode)
            assertTrue(legacy.hsl.isIdentity)
            assertNull(reopened.load(PhotoIdentity("content://media/external/images/media/8")))
            reopened.reset(identity)
            assertNull(EditStore(directory).load(identity))
        } finally { directory.deleteRecursively() }
    }

    @Test fun corruptedRecordIsSkippedWithoutBreakingOtherRecords() {
        val directory = Files.createTempDirectory("rawlab-edits").toFile()
        try {
            val good = PhotoIdentity("content://media/external/images/media/9")
            EditStore(directory).save(good, EditSettings(exposure = .5f))
            val file = File(directory, "edits.properties")
            val properties = java.util.Properties().also { file.inputStream().use(it::load) }
            val corruptKey = "record." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("content://corrupt".toByteArray()) + ".values"
            properties.setProperty(corruptKey, "not,a,float")
            file.outputStream().use { properties.store(it, null) }
            val reopened = EditStore(directory)
            assertEquals(.5f, reopened.load(good)?.exposure)
            assertNull(reopened.load(PhotoIdentity("content://corrupt")))
        } finally { directory.deleteRecursively() }
    }

    @Test fun sameBasenameAndFailedWriteDoNotCrossContaminate() {
        val directory = Files.createTempDirectory("rawlab-edits").toFile()
        try {
            val first = PhotoIdentity("content://one/IMG_0001.ARW")
            val second = PhotoIdentity("content://two/IMG_0001.ARW")
            EditStore(directory).save(first, EditSettings(exposure = .5f))
            EditStore(directory).save(second, EditSettings(exposure = -.5f))
            assertEquals(.5f, EditStore(directory).load(first)?.exposure)
            assertEquals(-.5f, EditStore(directory).load(second)?.exposure)

            val broken = File(directory, "not-a-directory")
            broken.writeText("occupied")
            assertThrows(Exception::class.java) { EditStore(File(broken, "child")).save(first, EditSettings()) }
        } finally { directory.deleteRecursively() }
    }
}
