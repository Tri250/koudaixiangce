package com.rapidraw.corepackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlinpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.powpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Realpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtractedpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock haspackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-cpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 *package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask frompackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract maskpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: outpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-bpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scalepackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gammapackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    //package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ─────────────────────────────────package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id:package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurvepackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portrapackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portrapackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30fpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly coolpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superiapackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20fpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W withpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50fpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – userpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromIdpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find {package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    //package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ─────────────────────────────────────────package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (leftpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coordspackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically anpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skippackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: androidpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock presetpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        valpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOMpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOMpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Floatpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-bpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Booleanpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negativepackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the givenpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @parampackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instancepackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap:package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val wpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmappackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = samplepackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto whitepackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        valpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance)package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range,package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        }package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1fpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gammapackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock ==package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.copackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStockpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStockpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        valpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0,package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i inpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[ipackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val rIn = Color.red(argb)package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val rIn = Color.red(argb) / 255f
            val gIn = Color.green(argpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val rIn = Color.red(argb) / 255f
            val gIn = Color.green(argb) / 255f
            val bIn = Colorpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val rIn = Color.red(argb) / 255f
            val gIn = Color.green(argb) / 255f
            val bIn = Color.blue(argb) / 255f

            // Subtract maskpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val rIn = Color.red(argb) / 255f
            val gIn = Color.green(argb) / 255f
            val bIn = Color.blue(argb) / 255f

            // Subtract mask & normalise to [0,1]
            valpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val rIn = Color.red(argb) / 255f
            val gIn = Color.green(argb) / 255f
            val bIn = Color.blue(argb) / 255f

            // Subtract mask & normalise to [0,1]
            val rAdj = normalise(rIn, mask[package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val rIn = Color.red(argb) / 255f
            val gIn = Color.green(argb) / 255f
            val bIn = Color.blue(argb) / 255f

            // Subtract mask & normalise to [0,1]
            val rAdj = normalise(rIn, mask[0], range[0], range[1])package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val rIn = Color.red(argb) / 255f
            val gIn = Color.green(argb) / 255f
            val bIn = Color.blue(argb) / 255f

            // Subtract mask & normalise to [0,1]
            val rAdj = normalise(rIn, mask[0], range[0], range[1])
            val gAdj = normalise(gIn, maskpackage com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val rIn = Color.red(argb) / 255f
            val gIn = Color.green(argb) / 255f
            val bIn = Color.blue(argb) / 255f

            // Subtract mask & normalise to [0,1]
            val rAdj = normalise(rIn, mask[0], range[0], range[1])
            val gAdj = normalise(gIn, mask[1], range[2], range[3package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val rIn = Color.red(argb) / 255f
            val gIn = Color.green(argb) / 255f
            val bIn = Color.blue(argb) / 255f

            // Subtract mask & normalise to [0,1]
            val rAdj = normalise(rIn, mask[0], range[0], range[1])
            val gAdj = normalise(gIn, mask[1], range[2], range[3])
            val bAdj = normalise(bIn,package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val rIn = Color.red(argb) / 255f
            val gIn = Color.green(argb) / 255f
            val bIn = Color.blue(argb) / 255f

            // Subtract mask & normalise to [0,1]
            val rAdj = normalise(rIn, mask[0], range[0], range[1])
            val gAdj = normalise(gIn, mask[1], range[2], range[3])
            val bAdj = normalise(bIn, mask[2], range[4], range[package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative inversion engine.
 *
 * Real color negative film has an orange mask (unexposed dye layer) that must be
 * subtracted before simple channel inversion, and each film stock has a characteristic
 * tone curve (gamma / S-curve) that shapes the final result.
 *
 * Processing pipeline:
 *   1. Sample orange mask from unexposed border region
 *   2. Subtract mask per-channel, then normalize to [0, 1]
 *   3. Invert each channel: out = 1 − in
 *   4. Auto white-balance (find white-point from brightest pixels, scale channels)
 *   5. Apply film-stock characteristic gamma / S-curve
 *   6. Clamp to [0, 1]
 */
object NegativeConverter {

    // ── Film stock presets ────────────────────────────────────────

    enum class FilmStock(val id: String, val gamma: Float, val sCurveStrength: Float) {
        /** Kodak Portra – warm tones, very soft highlight roll-off */
        KODAK_PORTRA("kodak_portra", 0.55f, 0.30f),
        /** Fuji Superia – neutral, slightly cool */
        FUJI_SUPERIA("fuji_superia", 0.60f, 0.20f),
        /** Ilford HP5 – B&W with pronounced S-curve */
        ILFORD_HP5("ilford_hp5", 0.50f, 0.55f),
        /** Custom – user-supplied gamma, no extra S-curve */
        CUSTOM("custom", 0.55f, 0.0f);

        companion object {
            fun fromId(id: String): FilmStock =
                entries.find { it.id == id } ?: CUSTOM
        }
    }

    // ── Public API ───────────────────────────────────────────────

    data class Params(
        /** Rectangle (left, top, right, bottom) in pixel coords to sample the orange mask from.
         *  Typically an unexposed film border area. Null = skip mask subtraction. */
        val maskSampleRegion: android.graphics.Rect? = null,
        /** Film stock preset – determines gamma & S-curve shape */
        val filmStock: FilmStock = FilmStock.CUSTOM,
        /** User-defined gamma when filmStock == CUSTOM (ignored otherwise) */
        val customGamma: Float = 0.55f,
        /** Automatically white-balance after inversion */
        val autoWhiteBalance: Boolean = true,
    )

    /**
     * Convert a color-negative image to a positive image in-place on the given Bitmap.
     *
     * @param bitmap  ARGB_8888 bitmap of the scanned negative
     * @param params  conversion parameters
     * @return the same bitmap instance, modified in-place
     */
    fun convertNegative(bitmap: Bitmap, params: Params): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        // 1. Sample orange mask
        val mask = sampleMask(bitmap, params.maskSampleRegion)

        // 2. Compute min/max after mask subtraction (for normalisation)
        val range = computeRangeAfterMaskSubtraction(bitmap, mask)

        // 3. Auto white-balance: find white point from brightest pixels
        val whitePoint = if (params.autoWhiteBalance) {
            findWhitePoint(bitmap, mask, range, topFraction = 0.005f)
        } else {
            floatArrayOf(1f, 1f, 1f)
        }

        // 4. Choose gamma
        val gamma = if (params.filmStock == FilmStock.CUSTOM) {
            params.customGamma.coerceIn(0.2f, 1.0f)
        } else {
            params.filmStock.gamma
        }
        val sCurve = params.filmStock.sCurveStrength

        // 5. Process every pixel
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val argb = pixels[i]
            val rIn = Color.red(argb) / 255f
            val gIn = Color.green(argb) / 255f
            val bIn = Color.blue(argb) / 255f

            // Subtract mask & normalise to [0,1]
            val rAdj = normalise(rIn, mask[0], range[0], range[1])
            val gAdj = normalise(gIn, mask[1], range[2], range[3])
            val bAdj = normalise(bIn, mask[2], range[4], range[5])

            // Invert
            var r = 1f