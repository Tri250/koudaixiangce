#version 300 es
precision highp float;

// ── Mask Compositing and Blending Fragment Shader ─────────────────────
// Composites a processed image with the original using a mask texture.
// Supports multiple blend modes and mask inversion.
//
// This shader is used at the end of the GPU pipeline to apply flow masks,
// gradient masks, brush masks, and luminosity masks.

// ── Textures ──────────────────────────────────────────────────────────
uniform sampler2D uTexture;       // Processed image (result of GPU pipeline)
uniform sampler2D uOriginalTexture; // Original unprocessed image
uniform sampler2D uMaskTexture;   // Mask texture (grayscale in alpha or red channel)

// ── Blend Controls ────────────────────────────────────────────────────
uniform float uMaskIntensity;     // 0.0 .. 1.0, overall mask blend intensity
uniform float uMaskInvert;        // 0.0 or 1.0, invert mask (white = processed, black = original)
uniform int uBlendMode;           // 0=Normal, 1=Overlay, 2=SoftLight, 3=Multiply, 4=Screen

// ── Second processed layer (optional) ─────────────────────────────────
uniform float uSecondLayerOpacity; // 0.0 .. 1.0, opacity for second processed layer blend
uniform float uUseSecondLayer;     // 0.0 or 1.0, enable second layer compositing

// Interpolated from vertex shader
in vec2 vTexCoord;
out vec4 fragColor;

// ── Constants ──────────────────────────────────────────────────────────
const float EPS = 1e-6;

// ── Luminance (BT.709) ────────────────────────────────────────────────

float get_luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// ── Mask Sampling ─────────────────────────────────────────────────────
// Reads mask value from the mask texture. Supports both alpha and red
// channel masks, and handles inversion.

float sample_mask() {
    vec4 maskSample = texture(uMaskTexture, vTexCoord);

    // Use alpha channel if it contains data, otherwise fall back to red
    float maskValue = maskSample.a;
    if (maskValue < EPS && maskSample.r > EPS) {
        maskValue = maskSample.r;
    }

    // Invert if requested
    if (uMaskInvert > 0.5) {
        maskValue = 1.0 - maskValue;
    }

    return maskValue;
}

// ── Blend Modes ───────────────────────────────────────────────────────

vec3 blend_normal(vec3 base, vec3 blend) {
    return blend;
}

vec3 blend_overlay(vec3 base, vec3 blend) {
    // Overlay: multiply for darks, screen for lights
    vec3 result;
    result.r = (base.r < 0.5) ? (2.0 * base.r * blend.r) : (1.0 - 2.0 * (1.0 - base.r) * (1.0 - blend.r));
    result.g = (base.g < 0.5) ? (2.0 * base.g * blend.g) : (1.0 - 2.0 * (1.0 - base.g) * (1.0 - blend.g));
    result.b = (base.b < 0.5) ? (2.0 * base.b * blend.b) : (1.0 - 2.0 * (1.0 - base.b) * (1.0 - blend.b));
    return result;
}

vec3 blend_soft_light(vec3 base, vec3 blend) {
    // Soft light: gentle overlay-like effect
    vec3 result;
    result.r = (blend.r < 0.5) ?
        (base.r - (1.0 - 2.0 * blend.r) * base.r * (1.0 - base.r)) :
        (base.r + (2.0 * blend.r - 1.0) * ((sqrt(max(base.r, 0.0)) - base.r)));
    result.g = (blend.g < 0.5) ?
        (base.g - (1.0 - 2.0 * blend.g) * base.g * (1.0 - base.g)) :
        (base.g + (2.0 * blend.g - 1.0) * ((sqrt(max(base.g, 0.0)) - base.g)));
    result.b = (blend.b < 0.5) ?
        (base.b - (1.0 - 2.0 * blend.b) * base.b * (1.0 - base.b)) :
        (base.b + (2.0 * blend.b - 1.0) * ((sqrt(max(base.b, 0.0)) - base.b)));
    return result;
}

vec3 blend_multiply(vec3 base, vec3 blend) {
    return base * blend;
}

vec3 blend_screen(vec3 base, vec3 blend) {
    return 1.0 - (1.0 - base) * (1.0 - blend);
}

vec3 apply_blend_mode(vec3 base, vec3 blend, int mode) {
    if (mode == 0) return blend_normal(base, blend);
    else if (mode == 1) return blend_overlay(base, blend);
    else if (mode == 2) return blend_soft_light(base, blend);
    else if (mode == 3) return blend_multiply(base, blend);
    else if (mode == 4) return blend_screen(base, blend);
    return blend_normal(base, blend);
}

// ═══════════════════════════════════════════════════════════════════════
// ── MAIN ─────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

void main() {
    vec3 processed = texture(uTexture, vTexCoord).rgb;
    vec3 original = texture(uOriginalTexture, vTexCoord).rgb;

    // Sample the mask
    float maskValue = sample_mask();

    // Apply mask intensity to the mask value
    float effectiveMask = maskValue * uMaskIntensity;

    // Blend processed and original using the mask
    // mask=1.0 → show processed, mask=0.0 → show original
    vec3 result = mix(original, processed, effectiveMask);

    // Optional: Apply blend mode for the processed layer
    if (uBlendMode > 0) {
        vec3 blended = apply_blend_mode(original, processed, uBlendMode);
        result = mix(original, blended, effectiveMask);
    }

    // Optional: Second layer compositing
    if (uUseSecondLayer > 0.5 && uSecondLayerOpacity > EPS) {
        // The second layer is the difference between processed and original,
        // blended with opacity. This allows stacking multiple edit passes.
        vec3 secondLayer = processed - original;
        result += secondLayer * uSecondLayerOpacity;
    }

    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
