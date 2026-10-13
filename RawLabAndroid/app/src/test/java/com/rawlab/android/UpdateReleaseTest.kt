package com.rawlab.android

import org.junit.Assert.*
import org.junit.Test

class UpdateReleaseTest {
    private fun release(tag: String, draft: Boolean = false, preview: Boolean = false,
                        assets: List<String> = listOf("RawLab-Android-0.5.0.apk")) =
        UpdateRelease.select(tag, draft, preview, "Notes", assets, "0.4.0")

    @Test fun comparesVersionsNumericallyAndNeverDowngrades() {
        assertEquals("0.10.0", release("v0.10.0")?.version)
        assertNull(release("v0.4"))
        assertNull(release("v0.3.9"))
        assertEquals("https://github.com/Tri250/koudaixiangce/releases/tag/v0.4.1", release("v0.4.1")?.url)
    }

    @Test fun rejectsInvalidTagsAndIgnoresUnpublishedOrOtherPlatformReleases() {
        for (tag in listOf("v0.5.0-beta", "v0.5.0/evil", "bad", "0.-1.0")) {
            assertThrows(IllegalArgumentException::class.java) { release(tag) }
        }
        assertNull(release("v0.5.0", draft = true))
        assertNull(release("v0.5.0", preview = true))
        assertNull(release("v0.5.0", assets = listOf("RawLab-Mac-arm64.zip")))
    }

    @Test fun unparseableInstalledVersionStillOffersUpdateWhileEqualSuppresses() {
        assertNotNull(UpdateRelease.select("v0.6.0", false, false, "Notes",
            listOf("RawLab-Android-0.6.0.apk"), "unknown"))
        assertNull(UpdateRelease.select("v0.6.0", false, false, "Notes",
            listOf("RawLab-Android-0.6.0.apk"), "0.6.0"))
        assertNull(UpdateRelease.select("v0.6.0", false, false, "Notes",
            listOf("RawLab-Android-0.6.0.apk"), "0.7.0"))
    }

    @Test fun apkAssetNamesMatchCaseInsensitively() {
        assertNotNull(UpdateRelease.select("v0.6.0", false, false, "Notes",
            listOf("rawlab-android-0.6.0.apk"), "0.5.0"))
        assertNotNull(UpdateRelease.select("v0.6.0", false, false, "Notes",
            listOf("RawLab-Mac-arm64.zip", "RAWLAB-ANDROID-0.6.0.APK"), "0.5.0"))
        assertNull(UpdateRelease.select("v0.6.0", false, false, "Notes",
            listOf("RawLab-Mac-arm64.zip"), "0.5.0"))
    }

    @Test fun manualChecksBypassOptOutAndDailyInterval() {
        assertTrue(UpdateRelease.shouldCheck(true, false, 100, 101))
        assertFalse(UpdateRelease.shouldCheck(false, false, 0, 100000))
        assertFalse(UpdateRelease.shouldCheck(false, true, 100, 101))
        assertTrue(UpdateRelease.shouldCheck(false, true, 100, 86500))
        assertTrue(UpdateRelease.shouldCheck(false, true, 200, 100))
    }
}
