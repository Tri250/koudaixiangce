#version 300 es
precision highp float;

// ── Tone Mapping Fragment Shader ──────────────────────────────────────
// Implements multiple tone mapping operators for HDR→LDR conversion:
//   - AgX (Blender's filmic tone mapper, excellent highlight rolloff)
//   - ACES 2.0 (Academy Color Encoding System, industry standard)
//   - Filmic (simple S-curve with adjustable shoulder/toe)
//
// Input is expected in linear RGB. Output is tone-mapped linear RGB
// (caller is responsible for sRGB encoding afterwards).

// Input image texture
uniform sampler2D uTexture;   // Source image (linear RGB, HDR values allowed)

// ── Tone Mapping Mode ─────────────────────────────────────────────────
// 0 = AgX, 1 = ACES 2.0, 2 = Filmic, 3 = Passthrough (no tonemap)
uniform int uToneMapMode;

// ── AgX Parameters ────────────────────────────────────────────────────
uniform float uAgXContrast;   // 0.0 .. 1.0, contrast boost in log domain
uniform float uAgXPedestal;   // 0.0 .. 0.5, black level pedestal (lifts shadows)

// ── ACES 2.0 Parameters ──────────────────────────────────────────────
uniform int uAces2DisplayColorSpace;  // 0=sRGB, 1=P3, 2=Rec2020
uniform int uAces2Eotf;               // 0=sRGB, 1=PQ, 2=Gamma22
uniform float uAces2PeakLuminance;    // 100.0 .. 10000.0, display peak nits

// ── Filmic Parameters ─────────────────────────────────────────────────
uniform float uFilmicToe;      // 0.0 .. 1.0, shadow toe strength
uniform float uFilmicShoulder; // 0.0 .. 1.0, highlight shoulder strength
uniform float uFilmicMidpoint; // 0.1 .. 1.0, midpoint of the S-curve
uniform float uFilmicContrast; // 0.5 .. 2.0, contrast of the S-curve

// Interpolated from vertex shader
in vec2 vTexCoord;
out vec4 fragColor;

// ── Constants ──────────────────────────────────────────────────────────
const float PI = 3.14159265358979;
const float EPS = 1e-6;

// ═══════════════════════════════════════════════════════════════════════
// ── AgX Tone Mapping ─────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_agx(vec3 color) {
    // AgX log encoding parameters
    float lo = -10.0;
    float hi = 13.0;

    // Per-channel log encoding
    vec3 logColor;
    logColor.r = clamp((log2(max(color.r, 0.0) + EPS) - lo) / (hi - lo), 0.0, 1.0);
    logColor.g = clamp((log2(max(color.g, 0.0) + EPS) - lo) / (hi - lo), 0.0, 1.0);
    logColor.b = clamp((log2(max(color.b, 0.0) + EPS) - lo) / (hi - lo), 0.0, 1.0);

    // Apply contrast S-curve in log domain
    float contrast = 1.0 + uAgXContrast * 0.5;
    logColor = pow(logColor, vec3(contrast));

    // Apply pedestal (black level lift)
    logColor = max(logColor - vec3(uAgXPedestal), vec3(0.0)) / max(1.0 - uAgXPedestal, EPS);

    // AgX gamut mapping: simple clamp to valid range
    // In a full implementation this would include the 3x3 matrix transform
    // for AgX output transform, but for mobile performance we use simplified version.
    logColor = clamp(logColor, 0.0, 1.0);

    return logColor;
}

// ═══════════════════════════════════════════════════════════════════════
// ── ACES 2.0 Tone Mapping ────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

// sRGB → XYZ → ACES AP0 matrix (simplified Bradford adaptation)
mat3 srgb_to_ap0_matrix() {
    mat3 srgb_to_xyz = mat3(
        0.4124564, 0.3575761, 0.1804375,
        0.2126729, 0.7151522, 0.0721750,
        0.0193339, 0.1191920, 0.9503041
    );
    mat3 xyz_to_ap0 = mat3(
        1.049811, 0.0, -0.0000975,
        -0.495903, 1.373313, 0.098240,
        0.0, 0.0, 0.991252
    );
    return xyz_to_ap0 * srgb_to_xyz;
}

mat3 ap0_to_srgb_matrix() {
    mat3 ap0_to_xyz = mat3(
        0.9525524, 0.0, 0.0000937,
        0.3430834, 0.7282532, -0.0713366,
        0.0, 0.0, 1.0087922
    );
    mat3 xyz_to_srgb = mat3(
        3.2404542, -1.5371385, -0.4985314,
        -0.9692660, 1.8760108, 0.0415560,
        0.0556434, -0.2040259, 1.0572252
    );
    return xyz_to_srgb * ap0_to_xyz;
}

// ACES RRT (Reference Rendering Transform) - simplified segment-wise S-curve
float aces_rrt(float x) {
    const float a = 0.0245786;
    const float b = 0.0000907;
    const float c = 0.983729;
    const float d = 0.4329510;
    const float e = 0.238081;
    float y = (x * (a * x + b)) / (x * (c * x + d) + e);
    return max(y, 0.0);
}

vec3 apply_aces2(vec3 color) {
    // Step 1: Convert to ACES AP0 working space
    mat3 toAp0 = srgb_to_ap0_matrix();
    vec3 ap0 = toAp0 * color;

    // Step 2: Apply RRT per-channel
    vec3 rrt;
    rrt.r = aces_rrt(max(ap0.r, 0.0));
    rrt.g = aces_rrt(max(ap0.g, 0.0));
    rrt.b = aces_rrt(max(ap0.b, 0.0));

    // Step 3: ODT - scale to target display peak luminance
    float peakScale = 100.0 / max(uAces2PeakLuminance, 100.0);
    vec3 scaled = rrt * peakScale;

    // Step 4: Convert back to display space
    mat3 toSrgb = ap0_to_srgb_matrix();
    vec3 result = toSrgb * scaled;

    // Gamut compression for out-of-gamut colors
    float maxVal = max(result.r, max(result.g, result.b));
    if (maxVal > 1.0) {
        float gamutComp = 1.0 + log(max(maxVal, EPS)) * 0.2;
        result = result / max(gamutComp, EPS);
    }

    return clamp(result, 0.0, 1.0);
}

// ═══════════════════════════════════════════════════════════════════════
// ── Filmic Tone Mapping (Simple S-Curve) ─────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

// Hable's filmic curve (Uncharted 2) with adjustable parameters
float filmic_curve(float x, float toe, float shoulder, float midpoint, float contrast) {
    // Toe and shoulder strengths control the shape of the curve
    // Higher toe = more shadow detail lifted
    // Higher shoulder = more highlight compression

    float toeOffset = toe * 0.5;
    float shoulderOffset = shoulder * 0.5;
    float m = max(midpoint, 0.01);

    // Apply toe: lift shadows
    float toeCurve = x / (x + toeOffset + EPS);

    // Apply shoulder: compress highlights
    float shoulderCurve = 1.0 - (1.0 - x) / ((1.0 - x) + shoulderOffset + EPS);

    // Blend between toe and shoulder based on luminance
    float blend = smoothstep(0.0, m, x);

    float result = mix(toeCurve, shoulderCurve, blend);

    // Apply contrast (power curve around midpoint)
    if (abs(contrast - 1.0) > EPS) {
        result = pow(max(result, 0.0), 1.0 / max(contrast, 0.1));
    }

    return clamp(result, 0.0, 1.0);
}

vec3 apply_filmic(vec3 color) {
    vec3 result;
    result.r = filmic_curve(max(color.r, 0.0), uFilmicToe, uFilmicShoulder, uFilmicMidpoint, uFilmicContrast);
    result.g = filmic_curve(max(color.g, 0.0), uFilmicToe, uFilmicShoulder, uFilmicMidpoint, uFilmicContrast);
    result.b = filmic_curve(max(color.b, 0.0), uFilmicToe, uFilmicShoulder, uFilmicMidpoint, uFilmicContrast);
    return result;
}

// ═══════════════════════════════════════════════════════════════════════
// ── MAIN ─────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;

    // Apply selected tone mapping operator
    if (uToneMapMode == 0) {
        // AgX
        color = apply_agx(color);
    } else if (uToneMapMode == 1) {
        // ACES 2.0
        color = apply_aces2(color);
    } else if (uToneMapMode == 2) {
        // Filmic
        color = apply_filmic(color);
    }
    // Mode 3 = passthrough (no tonemapping)

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
