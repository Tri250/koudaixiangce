package com.rapidraw.core

import android.content.Context
import android.content.SharedPreferences
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.FilmSimulation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class UserPreferenceLearning(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("rapidraw_prefs", Context.MODE_PRIVATE)

    data class UserPreferenceProfile(
        val portraitOffsets: Map<String, Float>,
        val landscapeOffsets: Map<String, Float>,
        val nightOffsets: Map<String, Float>,
        val foodOffsets: Map<String, Float>,
        val generalOffsets: Map<String, Float>,
        val favoriteFilmId: String?,
        val avgFilmIntensity: Float,
        val editCount: Int,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun recordAdjustment(scene: SceneType, adjustments: Adjustments) {
        val key = "edits_${scene.name.lowercase()}"
        val existing = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        existing.add(json.encodeToString(adjustments))
        prefs.edit().putStringSet(key, existing).apply()
    }

    fun learn(): UserPreferenceProfile {
        val scenes = listOf(SceneType.PORTRAIT, SceneType.LANDSCAPE, SceneType.NIGHT, SceneType.FOOD, SceneType.GENERAL)
        val offsets = mutableMapOf<String, Map<String, Float>>()
        var totalEdits = 0

        scenes.forEach { scene ->
            val key = "edits_${scene.name.lowercase()}"
            val edits = prefs.getStringSet(key, emptySet()) ?: emptySet()
            totalEdits += edits.size

            if (edits.size >= 3) {
                // Calculate average adjustments for this scene
                val avg = calculateAverageAdjustments(edits)
                offsets[scene.name.lowercase()] = avg
            }
        }

        // Find favorite film
        val filmCounts = mutableMapOf<String, Int>()
        scenes.forEach { scene ->
            val key = "edits_${scene.name.lowercase()}"
            prefs.getStringSet(key, emptySet())?.forEach { editJson ->
                try {
                    val adj = json.decodeFromString<Adjustments>(editJson)
                    if (adj.filmId.isNotEmpty()) {
                        filmCounts[adj.filmId] = (filmCounts[adj.filmId] ?: 0) + 1
                    }
                } catch (_: Exception) { Log.w(TAG, "Failed to read user preferences") }
            }
        }
        val favoriteFilm = filmCounts.maxByOrNull { it.value }?.key

        val avgIntensity = if (totalEdits > 0) {
            scenes.flatMap { scene ->
                val key = "edits_${scene.name.lowercase()}"
                prefs.getStringSet(key, emptySet())?.mapNotNull { editJson ->
                    try {
                        json.decodeFromString<Adjustments>(editJson).filmIntensity
                    } catch (_: Exception) { null }
                } ?: emptyList()
            }.average().toFloat()
        } else 1f

        return UserPreferenceProfile(
            portraitOffsets = offsets["portrait"] ?: emptyMap(),
            landscapeOffsets = offsets["landscape"] ?: emptyMap(),
            nightOffsets = offsets["night"] ?: emptyMap(),
            foodOffsets = offsets["food"] ?: emptyMap(),
            generalOffsets = offsets["general"] ?: emptyMap(),
            favoriteFilmId = favoriteFilm,
            avgFilmIntensity = avgIntensity,
            editCount = totalEdits,
        )
    }

    fun personalizedOptimize(
        baseAdjustments: Adjustments,
        scene: SceneType,
        profile: UserPreferenceProfile
    ): Adjustments {
        val offsets = when (scene) {
            SceneType.PORTRAIT -> profile.portraitOffsets
            SceneType.LANDSCAPE -> profile.landscapeOffsets
            SceneType.NIGHT -> profile.nightOffsets
            SceneType.FOOD -> profile.foodOffsets
            else -> profile.generalOffsets
        }

        if (offsets.isEmpty()) return baseAdjustments

        // Apply learned offsets with 50% weight (gradual learning)
        var result = baseAdjustments
        offsets.forEach { (key, offset) ->
            val current = when (key) {
                "exposure" -> result.exposure
                "contrast" -> result.contrast
                "saturation" -> result.saturation
                "temperature" -> result.temperature
                "clarity" -> result.clarity
                "vibrance" -> result.vibrance
                "highlights" -> result.highlights
                "shadows" -> result.shadows
                "dehaze" -> result.dehaze
                "sharpness" -> result.sharpness
                else -> 0f
            }
            val blended = current + offset * 0.5f
            result = result.copyByField(key, blended)
        }

        // Apply favorite film if user consistently uses one
        if (profile.favoriteFilmId != null && profile.editCount > 5) {
            FilmSimulation.getById(profile.favoriteFilmId)?.let { film ->
                result = result.withFilmSimulation(film)
                result = result.copy(filmIntensity = profile.avgFilmIntensity)
            }
        }

        return result
    }

    private fun calculateAverageAdjustments(editJsons: Set<String>): Map<String, Float> {
        val values = mutableMapOf<String, MutableList<Float>>()

        editJsons.forEach { jsonStr ->
            try {
                val adj = json.decodeFromString<Adjustments>(jsonStr)
                values.getOrPut("exposure") { mutableListOf() }.add(adj.exposure)
                values.getOrPut("contrast") { mutableListOf() }.add(adj.contrast)
                values.getOrPut("saturation") { mutableListOf() }.add(adj.saturation)
                values.getOrPut("temperature") { mutableListOf() }.add(adj.temperature)
                values.getOrPut("clarity") { mutableListOf() }.add(adj.clarity)
                values.getOrPut("vibrance") { mutableListOf() }.add(adj.vibrance)
                values.getOrPut("highlights") { mutableListOf() }.add(adj.highlights)
                values.getOrPut("shadows") { mutableListOf() }.add(adj.shadows)
                values.getOrPut("dehaze") { mutableListOf() }.add(adj.dehaze)
                values.getOrPut("sharpness") { mutableListOf() }.add(adj.sharpness)
            } catch (_: Exception) { Log.w(TAG, "Failed to read user preferences") }
        }

        return values.mapValues { it.value.average().toFloat() }
    }

    fun clearHistory() {
        prefs.edit().clear().apply()
    }
}
