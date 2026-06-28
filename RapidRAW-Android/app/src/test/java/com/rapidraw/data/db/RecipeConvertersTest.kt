package com.rapidraw.data.db

import org.junit.Assert.*
import org.junit.Test

class RecipeConvertersTest {

    private val converters = RecipeConverters()

    @Test
    fun fromFloatList_convertsToCommaString() {
        val list = listOf(1f, 2.5f, 3.75f)
        val result = converters.fromFloatList(list)
        assertEquals("1.0,2.5,3.75", result)
    }

    @Test
    fun toFloatList_convertsFromCommaString() {
        val value = "1.0,2.5,3.75"
        val result = converters.toFloatList(value)
        assertEquals(listOf(1f, 2.5f, 3.75f), result)
    }

    @Test
    fun toFloatList_emptyString_returnsEmptyList() {
        val result = converters.toFloatList("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun toFloatList_invalidValues_defaultsToZero() {
        val result = converters.toFloatList("1.0,abc,3.0")
        assertEquals(listOf(1f, 0f, 3f), result)
    }

    @Test
    fun roundTrip_preservesValues() {
        val original = listOf(0.1f, 0.2f, 0.3f, 0.4f)
        val serialized = converters.fromFloatList(original)
        val deserialized = converters.toFloatList(serialized)
        assertEquals(original, deserialized)
    }
}
