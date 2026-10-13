package com.rawlab.android

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageResourcesTest {
    @Test fun allSixLanguagesSelectNativeResources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = mapOf("en" to "Language", "zh-Hans" to "语言", "de" to "Sprache",
            "fr" to "Langue", "ja" to "言語", "es" to "Idioma")
        val hue = mapOf("en" to "Hue", "zh-Hans" to "色相", "de" to "Farbton",
            "fr" to "Teinte", "ja" to "色相", "es" to "Matiz")
        val recipes = mapOf("en" to "Master recipes", "zh-Hans" to "大师配方", "de" to "Master-Rezepte",
            "fr" to "Recettes maîtres", "ja" to "マスターレシピ", "es" to "Recetas maestras")
        expected.forEach { (language, value) ->
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(language))
            val localized = context.createConfigurationContext(config)
            assertEquals(value, localized.getString(R.string.language))
            assertEquals(hue[language], localized.getString(R.string.hsl_hue))
            assertEquals(recipes[language], localized.getString(R.string.master_recipes))
            localized.getString(R.string.vignette_summary, 1.0, 50.0, 0.0, 50.0, 0.0)
            localized.getString(R.string.grain_summary, 1.0, 25.0, 50.0)
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(LanguageChoice.resolve("system", listOf("pt-BR"))))
        assertEquals("Language", context.createConfigurationContext(config).getString(R.string.language))
    }
}
