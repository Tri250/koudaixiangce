package com.rawlab.android

import android.content.Context
import org.json.JSONArray

data class MasterRecipe(val id: String, val name: String, val subtitle: String, val summary: String,
    val lutFileName: String, val exposure: Float, val shadows: Float, val highlights: Float,
    val contrast: Float, val saturation: Float, val tint: Float, val hsl: HslSettings) {
    companion object {
        fun load(context: Context): List<MasterRecipe> {
            val rows = JSONArray(context.assets.open("master-recipes.json").bufferedReader().use { it.readText() })
            val recipes = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val mixer = row.getJSONObject("hsl")
                val values = listOf("hue", "saturation", "lightness").flatMap { axis ->
                    val array = mixer.getJSONArray(axis)
                    require(array.length() == 8)
                    (0 until 8).map { array.getDouble(it).toFloat() }
                }
                MasterRecipe(row.getString("id"), row.getString("name"), row.getString("subtitle"),
                    row.getString("summary"), row.getString("lutFileName"), row.getDouble("exposureDelta").toFloat(),
                    row.getDouble("shadowsDelta").toFloat() / 100, row.getDouble("highlightsDelta").toFloat() / 100,
                    row.getDouble("contrastDelta").toFloat() / 100, row.getDouble("saturationDelta").toFloat() / 100,
                    row.getDouble("tintDelta").toFloat(), HslSettings(values))
            }
            require(recipes.size == 14 && recipes.map { it.id }.distinct().size == 14)
            return recipes
        }
    }
}
