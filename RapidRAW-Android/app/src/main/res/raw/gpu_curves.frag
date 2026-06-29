#version 300 es
precision highp float;

// ── RGB / Luma Curves Fragment Shader ─────────────────────────────────
// Applies user-defined tone curves to the image using 1D LUT texture
// sampling. Supports Master (luma), Red, Green, Blue channel curves.
//
// Two modes of operation:
//   1. If uCurveLut is provided (256x1 LUT textures), sample directly.
//   2. If curve control points are provided, evaluate Catmull-Rom splines.

// Input image texture
uniform sampler2D uTexture;   // Source image (linear RGB expected)

// ── 1D LUT textures for curve evaluation ──────────────────────────────
// Each is a 256x1 RGBA texture mapping input channel to output.
// If all are bound, this shader uses direct LUT sampling (fast path).
uniform sampler2D uCurveLutMaster;  // Master/Luma curve LUT
uniform sampler2D uCurveLutRed;     // Red channel curve LUT
uniform sampler2D uCurveLutGreen;   // Green channel curve LUT
uniform sampler2D uCurveLutBlue;    // Blue channel curve LUT

// ── Curve Control Points (fallback when LUT textures not available) ───
// 12 points per channel packed as 6 vec4s: (x0,y0,x1,y1) per vec4
uniform vec4 uRedCurve[6];     // Red channel control points
uniform vec4 uGreenCurve[6];   // Green channel control points
uniform vec4 uBlueCurve[6];    // Blue channel control points
uniform vec4 uMasterCurve[6];  // Master/Luma curve control points

// ── Mode switch ───────────────────────────────────────────────────────
uniform float uUseLut;         // 1.0 = use 1D LUT textures, 0.0 = use control points

// Interpolated from vertex shader
in vec2 vTexCoord;
out vec4 fragColor;

// ── Constants ──────────────────────────────────────────────────────────
const float EPS = 1e-6;

// ── Luminance (BT.709) ────────────────────────────────────────────────

float get_luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// ── Catmull-Rom Spline Evaluation ─────────────────────────────────────
// Evaluates a curve defined by 12 control points (packed in 6 vec4s)
// at a given input value.

float apply_curve_points(float x, vec4 curve[6]) {
    x = clamp(x, 0.0, 1.0);

    // Unpack vec4 array to flat point arrays
    float xs[12];
    float ys[12];
    for (int i = 0; i < 6; i++) {
        xs[i * 2] = curve[i].x;
        ys[i * 2] = curve[i].y;
        xs[i * 2 + 1] = curve[i].z;
        ys[i * 2 + 1] = curve[i].w;
    }

    // Clamp to endpoints
    if (x <= xs[0]) return ys[0];
    if (x >= xs[11]) return ys[11];

    // Find the segment containing x
    int idx = 0;
    for (int i = 0; i < 11; i++) {
        if (x >= xs[i] && x <= xs[i + 1]) {
            idx = i;
            break;
        }
    }

    float dx = xs[idx + 1] - xs[idx];
    if (dx < EPS) return ys[idx];

    float t = (x - xs[idx]) / dx;

    // Catmull-Rom tangents
    float m0, m1;
    if (idx == 0) {
        m0 = (ys[1] - ys[0]) / max(xs[1] - xs[0], EPS);
    } else {
        m0 = ((ys[idx + 1] - ys[idx - 1]) / max(xs[idx + 1] - xs[idx - 1], EPS)) * dx * 0.5;
    }
    if (idx >= 10) {
        m1 = (ys[11] - ys[10]) / max(xs[11] - xs[10], EPS);
    } else {
        m1 = ((ys[idx + 2] - ys[idx]) / max(xs[idx + 2] - xs[idx], EPS)) * dx * 0.5;
    }

    // Hermite basis functions
    float t2 = t * t;
    float t3 = t2 * t;
    float h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
    float h10 = t3 - 2.0 * t2 + t;
    float h01 = -2.0 * t3 + 3.0 * t2;
    float h11 = t3 - t2;

    return h00 * ys[idx] + h10 * m0 + h01 * ys[idx + 1] + h11 * m1;
}

// ── 1D LUT Sampling ───────────────────────────────────────────────────
// Sample a 256x1 LUT texture. texCoord.x = input value [0,1].

float sample_1d_lut(sampler2D lut, float value) {
    value = clamp(value, 0.0, 1.0);
    // 256-entry LUT: offset by half-texel for correct interpolation
    float coord = (value * 255.0 + 0.5) / 256.0;
    return texture(lut, vec2(coord, 0.5)).r;
}

// ═══════════════════════════════════════════════════════════════════════
// ── MAIN ─────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;

    if (uUseLut > 0.5) {
        // ── Fast path: sample 1D LUT textures ──

        // Master/Luma curve: apply as luma ratio
        float luma = get_luma(color);
        float curvedLuma = sample_1d_lut(uCurveLutMaster, luma);
        float lumaRatio = (luma > EPS) ? curvedLuma / luma : 1.0;
        vec3 result = color * lumaRatio;

        // Per-channel curves
        result.r = sample_1d_lut(uCurveLutRed, result.r);
        result.g = sample_1d_lut(uCurveLutGreen, result.g);
        result.b = sample_1d_lut(uCurveLutBlue, result.b);

        color = result;
    } else {
        // ── Fallback path: evaluate Catmull-Rom from control points ──

        // Master curve as luma ratio
        float luma = get_luma(color);
        float curvedLuma = apply_curve_points(luma, uMasterCurve);
        float lumaRatio = (luma > EPS) ? curvedLuma / luma : 1.0;
        vec3 result = color * lumaRatio;

        // Per-channel curves
        result.r = apply_curve_points(result.r, uRedCurve);
        result.g = apply_curve_points(result.g, uGreenCurve);
        result.b = apply_curve_points(result.b, uBlueCurve);

        color = result;
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
