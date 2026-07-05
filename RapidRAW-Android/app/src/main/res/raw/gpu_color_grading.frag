#version 300 es
precision highp float;

// ── Color Grading Fragment Shader ─────────────────────────────────────
// Implements CDL (Color Decision List) lift/gamma/gain per-channel offsets
// and three-way color wheels for shadows, midtones, highlights.
// Also supports global saturation and luminance balance controls.

// Input image texture
uniform sampler2D uTexture;   // Source image (linear RGB)

// ── Color Wheel Tints ────────────────────────────────────────────────
// Each vec3 encodes (hue/360, saturation/100, luminance/100) for the
// respective tonal range color wheel.

uniform vec3 uColorGradingShadows;      // Shadows color wheel (hue, sat, lum)
uniform vec3 uColorGradingMidtones;     // Midtones color wheel (hue, sat, lum)
uniform vec3 uColorGradingHighlights;   // Highlights color wheel (hue, sat, lum)

// ── Blending & Balance ────────────────────────────────────────────────
uniform float uColorGradingBlend;       // 0.0 .. 1.0, overall tint blend amount
uniform float uColorGradingGlobalSat;   // -1.0 .. 1.0, global saturation adjustment
uniform float uColorGradingBalance;     // -1.0 .. 1.0, shadows/highlights balance shift

// ── CDL Lift/Gamma/Gain Per-Channel ───────────────────────────────────
// Lift = shadows offset, Gamma = midtones power, Gain = highlights scale.
// These are additive offsets per R/G/B per tonal range.

uniform float uCdlShadowsR;      // -1.0 .. 1.0, shadows red lift
uniform float uCdlShadowsG;      // -1.0 .. 1.0, shadows green lift
uniform float uCdlShadowsB;      // -1.0 .. 1.0, shadows blue lift
uniform float uCdlMidtonesR;     // -1.0 .. 1.0, midtones red gamma offset
uniform float uCdlMidtonesG;     // -1.0 .. 1.0, midtones green gamma offset
uniform float uCdlMidtonesB;     // -1.0 .. 1.0, midtones blue gamma offset
uniform float uCdlHighlightsR;   // -1.0 .. 1.0, highlights red gain
uniform float uCdlHighlightsG;   // -1.0 .. 1.0, highlights green gain
uniform float uCdlHighlightsB;   // -1.0 .. 1.0, highlights blue gain

// Interpolated from vertex shader
in vec2 vTexCoord;
out vec4 fragColor;

// ── Constants ──────────────────────────────────────────────────────────
const float EPS = 1e-6;

// ── Luminance (BT.709) ────────────────────────────────────────────────

float get_luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// ── Smoothstep helpers ────────────────────────────────────────────────

float smoothstep_custom(float e0, float e1, float x) {
    float t = clamp((x - e0) / max(e1 - e0, EPS), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// ── Luminance Masks ──────────────────────────────────────────────────

float shadows_mask(float luma) {
    return 1.0 - smoothstep_custom(0.0, 0.5, luma);
}

float highlights_mask(float luma) {
    return smoothstep_custom(0.5, 1.0, luma);
}

float midtones_mask(float luma) {
    return smoothstep_custom(0.2, 0.4, luma) * (1.0 - smoothstep_custom(0.6, 0.8, luma));
}

// ── HSV to RGB for color wheel tint generation ────────────────────────

vec3 hsv_to_rgb(vec3 hsv) {
    if (hsv.y <= 0.0) return vec3(hsv.z);

    float h = mod(hsv.x, 1.0) * 6.0;
    int i = int(h);
    float f = h - float(i);
    float p = hsv.z * (1.0 - hsv.y);
    float q = hsv.z * (1.0 - hsv.y * f);
    float t = hsv.z * (1.0 - hsv.y * (1.0 - f));

    if (i == 0) return vec3(hsv.z, t, p);
    else if (i == 1) return vec3(q, hsv.z, p);
    else if (i == 2) return vec3(p, hsv.z, t);
    else if (i == 3) return vec3(p, q, hsv.z);
    else if (i == 4) return vec3(t, p, hsv.z);
    else return vec3(hsv.z, p, q);
}

// ── Color Wheel Tint ──────────────────────────────────────────────────
// Converts (hue/360, sat/100, lum/100) into an RGB tint color

vec3 color_wheel_tint(vec3 wheel) {
    float hue = wheel.x;           // 0..1 (hue/360)
    float sat = wheel.y;           // 0..1 (sat/100)
    float lum = wheel.z;           // -1..1 (lum/100)
    return hsv_to_rgb(vec3(hue, sat, 0.5)) * (1.0 + lum);
}

// ═══════════════════════════════════════════════════════════════════════
// ── MAIN ─────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;
    float luma = get_luma(color);

    // ── Color Wheel Tinting ───────────────────────────────────────────

    float sm = shadows_mask(luma);
    float mm = midtones_mask(luma);
    float hm = highlights_mask(luma);

    // Apply balance: shift weight between shadows and highlights
    float balancedSm = sm * (1.0 + uColorGradingBalance * 0.5);
    float balancedHm = hm * (1.0 - uColorGradingBalance * 0.5);

    // Normalize masks
    float maskSum = balancedSm + mm + balancedHm + EPS;
    sm = balancedSm / maskSum;
    mm = mm / maskSum;
    hm = balancedHm / maskSum;

    // Compute weighted tint from all three color wheels
    vec3 tint = sm * color_wheel_tint(uColorGradingShadows) +
                mm * color_wheel_tint(uColorGradingMidtones) +
                hm * color_wheel_tint(uColorGradingHighlights);

    // Blend tint into image
    vec3 result = mix(color, color + tint, uColorGradingBlend);

    // ── Global Saturation from Color Grading ──────────────────────────

    if (abs(uColorGradingGlobalSat) > EPS) {
        float lum = get_luma(result);
        result = lum + (result - lum) * (1.0 + uColorGradingGlobalSat);
    }

    // ── CDL Lift/Gamma/Gain Per-Channel ───────────────────────────────

    float anyCdl = abs(uCdlShadowsR) + abs(uCdlShadowsG) + abs(uCdlShadowsB) +
                   abs(uCdlMidtonesR) + abs(uCdlMidtonesG) + abs(uCdlMidtonesB) +
                   abs(uCdlHighlightsR) + abs(uCdlHighlightsG) + abs(uCdlHighlightsB);

    if (anyCdl > EPS) {
        // Recompute masks for CDL (use balanced masks)
        float cdlSm = sm;
        float cdlMm = mm;
        float cdlHm = hm;

        // Per-channel CDL offsets (Lift for shadows, Gamma for midtones, Gain for highlights)
        float offsetR = (cdlSm * uCdlShadowsR + cdlMm * uCdlMidtonesR + cdlHm * uCdlHighlightsR) * 0.15;
        float offsetG = (cdlSm * uCdlShadowsG + cdlMm * uCdlMidtonesG + cdlHm * uCdlHighlightsG) * 0.15;
        float offsetB = (cdlSm * uCdlShadowsB + cdlMm * uCdlMidtonesB + cdlHm * uCdlHighlightsB) * 0.15;

        result += vec3(offsetR, offsetG, offsetB);
    }

    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
