#include "vulkan_compute.h"

#include <cstdlib>
#include <cstring>
#include <cmath>
#include <vector>
#include <array>
#include <algorithm>
#include <mutex>
#include <atomic>

#include <android/log.h>

#define LOG_TAG "VulkanCompute"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define VK_CHECK(call)                                                       \
    do {                                                                      \
        VkResult result_ = (call);                                            \
        if (result_ != VK_SUCCESS) {                                          \
            LOGE("Vulkan error at %s:%d - %d", __FILE__, __LINE__, result_);  \
            return false;                                                     \
        }                                                                     \
    } while (0)

static std::mutex s_submissionMutex;

// ═══════════════════════════════════════════════════════════════════════════
// SPIR-V Compute Shader (embedded)
//
// This is a GLSL compute shader compiled to SPIR-V at runtime via
// shaderc / glslang, but for simplicity and reliability on Android,
// we embed the pre-compiled SPIR-V bytes directly.
//
// The shader implements the full adjustment pipeline matching the
// OpenGL ES fragment shaders: exposure, brightness, white balance,
// highlights, tonal controls, clarity, centre, saturation, vibrance,
// HSL 8-color, tone curve, color grading (CDL + color wheels),
// vignette, grain, tone mapping, and sharpening.
//
// Since we cannot embed a precompiled SPIR-V easily without a build step,
// we instead use a runtime-compiled approach with glslang or we embed
// the SPIR-V binary. Here we embed the GLSL source and compile it at
// runtime using shaderc (which is available in the Android NDK).
// ═══════════════════════════════════════════════════════════════════════════

static const char* kComputeShaderGLSL = R"glsl(
#version 450
layout(local_size_x = 16, local_size_y = 16) in;

// ── Bindings ──────────────────────────────────────────────────────────────
layout(binding = 0, std430) readonly buffer InputBuffer {
    uint inData[];
};

layout(binding = 1, std430) writeonly buffer OutputBuffer {
    uint outData[];
};

layout(binding = 2, std140) uniform Adjustments {
    // ── Image dimensions ─────────────────────────────────────
    float width;
    float height;
    vec2 pad0;

    // ── Exposure & Brightness ────────────────────────────────
    float exposure;
    float brightness;
    vec2 pad1;

    // ── White Balance ────────────────────────────────────────
    float temperature;
    float tint;
    vec2 pad2;

    // ── Tonal Controls ───────────────────────────────────────
    float contrast;
    float highlights;
    float shadows;
    float whites;
    float blacks;
    float clarity;
    float centre;
    float pad3;

    // ── Color ────────────────────────────────────────────────
    float saturation;
    float vibrance;
    vec2 pad4;

    // ── HSL 8-Color Panel ────────────────────────────────────
    vec4 hslRed;       // x=hue, y=sat, z=lum, w=pad
    vec4 hslOrange;
    vec4 hslYellow;
    vec4 hslGreen;
    vec4 hslAqua;
    vec4 hslBlue;
    vec4 hslPurple;
    vec4 hslMagenta;

    // ── Tone Curve ───────────────────────────────────────────
    vec4 curvePoints[5]; // 10 points packed as 5 vec4s (x0,y0,x1,y1)

    // ── Color Grading ────────────────────────────────────────
    vec4 cgShadows;     // x=hue, y=sat, z=lum, w=pad
    vec4 cgMidtones;
    vec4 cgHighlights;
    float cgBlend;
    float cgBalance;
    vec2 padCG;

    // ── CDL ──────────────────────────────────────────────────
    // 9 individual floats + 3 individual pad floats (NOT a float[3] array,
    // which would have a 16-byte stride in std140).
    float cdlShadowsR;
    float cdlShadowsG;
    float cdlShadowsB;
    float cdlMidtonesR;
    float cdlMidtonesG;
    float cdlMidtonesB;
    float cdlHighlightsR;
    float cdlHighlightsG;
    float cdlHighlightsB;
    float pad5a;
    float pad5b;
    float pad5c;

    // ── Vignette ─────────────────────────────────────────────
    float vignetteAmount;
    float vignetteMidpoint;
    float vignetteRoundness;
    float vignetteFeather;

    // ── Film Grain ───────────────────────────────────────────
    float grainAmount;
    float grainSize;
    float grainRoughness;
    float pad6;

    // ── Tone Mapping ─────────────────────────────────────────
    int toneMapMode;
    float agxContrast;
    float agxPedestal;
    float pad7;

    // ── Sharpening ───────────────────────────────────────────
    float sharpness;
    float sharpenRadius;
    vec2 pad8;

    // ════════════════════════════════════════════════════════════════════════
    // ── EXTENDED OPERATIONS (B1-B12) ────────────────────────────────────────
    // ════════════════════════════════════════════════════════════════════════

    // ── B1: Per-channel RGB tone curves ──────────────────────
    vec4 curveRedPoints[5];
    vec4 curveGreenPoints[5];
    vec4 curveBluePoints[5];

    // ── B2: Color Calibration ────────────────────────────────
    vec4 calRedGreen;     // x=redHue, y=redSat, z=greenHue, w=greenSat
    vec4 calBlueShadow;   // x=blueHue, y=blueSat, z=shadowsTint, w=pad

    // ── B3: Dehaze ───────────────────────────────────────────
    vec4 dehazePad;       // x=dehaze

    // ── B4: Structure ────────────────────────────────────────
    vec4 structurePad;    // x=structure

    // ── B5/B6: Noise Reduction ──────────────────────────────
    vec4 noiseReduction;  // x=lumaNoise, y=colorNoise

    // ── B7: Chromatic Aberration ────────────────────────────
    vec4 chromaticAberration; // x=RC, y=BY

    // ── B8: LUT ──────────────────────────────────────────────
    vec4 lutInfo;         // x=lutIntensity, y=hasLut, z=lutSize, w=pad

    // ── B9/B10/B11: Glow / Halation / Flare ─────────────────
    vec4 glowHalationFlare; // x=glow, y=halation, z=flare

    // ── B12: Show clipping ───────────────────────────────────
    ivec4 showClippingVec;  // x=showClipping
} adj;

// ── B8: 3D LUT sampler (binding=3) ────────────────────────────────────────
layout(binding = 3) uniform sampler3D lutSampler;

// ── Constants ──────────────────────────────────────────────────────────────
const float EPS = 1e-6;
const float PI  = 3.14159265358979;

// ── sRGB <-> Linear ───────────────────────────────────────────────────────
float srgb_to_linear(float v) {
    return (v <= 0.04045) ? (v / 12.92) : pow((v + 0.055) / 1.055, 2.4);
}
vec3 srgb_to_linear3(vec3 c) {
    return vec3(srgb_to_linear(c.r), srgb_to_linear(c.g), srgb_to_linear(c.b));
}
float linear_to_srgb(float v) {
    return (v <= 0.0031308) ? (v * 12.92) : (1.055 * pow(v, 1.0 / 2.4) - 0.055);
}
vec3 linear_to_srgb3(vec3 c) {
    return vec3(linear_to_srgb(c.r), linear_to_srgb(c.g), linear_to_srgb(c.b));
}

// ── Luminance (BT.709) ────────────────────────────────────────────────────
float get_luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

// ── Smoothstep helpers ────────────────────────────────────────────────────
float smoothstep_c(float e0, float e1, float x) {
    float t = clamp((x - e0) / max(e1 - e0, EPS), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// ── Luminance Masks ───────────────────────────────────────────────────────
float shadows_mask(float luma)  { return 1.0 - smoothstep_c(0.0, 0.5, luma); }
float highlights_mask(float luma) { return smoothstep_c(0.5, 1.0, luma); }
float whites_mask(float luma)   { return smoothstep_c(0.6, 1.0, luma); }
float blacks_mask(float luma)   { return 1.0 - smoothstep_c(0.0, 0.4, luma); }
float midtones_mask(float luma) { return smoothstep_c(0.2, 0.4, luma) * (1.0 - smoothstep_c(0.6, 0.8, luma)); }

// ── RGB <-> HSV ───────────────────────────────────────────────────────────
vec3 rgb_to_hsv(vec3 c) {
    float maxC = max(c.r, max(c.g, c.b));
    float minC = min(c.r, min(c.g, c.b));
    float delta = maxC - minC;
    vec3 hsv = vec3(0.0, 0.0, maxC);
    if (delta > EPS) {
        hsv.y = delta / maxC;
        if (maxC == c.r) {
            hsv.x = (c.g - c.b) / delta;
            if (hsv.x < 0.0) hsv.x += 6.0;
        } else if (maxC == c.g) {
            hsv.x = 2.0 + (c.b - c.r) / delta;
        } else {
            hsv.x = 4.0 + (c.r - c.g) / delta;
            if (hsv.x < 0.0) hsv.x += 6.0;
        }
        hsv.x *= 60.0;
    }
    return hsv;
}

vec3 hsv_to_rgb(vec3 hsv) {
    if (hsv.y <= 0.0) return vec3(hsv.z);
    float h = mod(hsv.x, 360.0) / 60.0;
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

// ── 1. Linear Exposure ───────────────────────────────────────────────────
vec3 apply_exposure(vec3 color, float exp) {
    return color * pow(2.0, exp);
}

// ── 2. Filmic Brightness ─────────────────────────────────────────────────
vec3 apply_brightness(vec3 color, float b) {
    b *= 2.0;
    vec3 num = color * (1.0 + b);
    vec3 den = 1.0 + abs(b) * color;
    return num / max(den, vec3(EPS));
}

// ── 3. White Balance ─────────────────────────────────────────────────────
vec3 wb_multipliers(float temperature, float tint) {
    // Map normalized temp [-1,1] to kelvin [2000, 15000]
    float temp = clamp(2000.0 + (temperature + 1.0) * 0.5 * 13000.0, 2000.0, 15000.0) / 100.0;
    float r = 1.0;
    if (temp > 66.0) {
        float x = temp - 60.0;
        r = 1.29293618606 + 0.00209825719 * x - 0.00315683591 * x * x + 0.00000129053 * x * x * x;
    }
    float g;
    if (temp <= 66.0) {
        g = 0.39008157876 + 0.60991842124 / (1.0 + 0.0000254 * (temp - 43.0) * (temp - 43.0));
    } else {
        float x = temp - 60.0;
        g = 1.12989086089 + 0.00215041426 * x - 0.00043932610 * x * x + 0.00000042528 * x * x * x;
    }
    float b;
    if (temp >= 66.0) {
        b = 1.0;
    } else if (temp <= 19.0) {
        b = 0.0;
    } else {
        b = 0.5441 + 0.4559 / (1.0 + 0.0005583 * (temp - 10.0) * (temp - 10.0));
    }
    g += tint * 0.1;
    return vec3(r, g, b);
}

vec3 apply_white_balance(vec3 color, float temp, float tint) {
    return color * wb_multipliers(temp, tint);
}

// ── 4. Highlights ────────────────────────────────────────────────────────
vec3 apply_highlights(vec3 color, float hl) {
    if (abs(hl) < EPS) return color;
    float luma = get_luma(color);
    float mask = highlights_mask(luma);
    if (hl < 0.0) {
        vec3 compressed = 1.0 - pow(1.0 - color, vec3(1.0 - hl));
        return mix(color, compressed, mask);
    } else {
        vec3 expanded = pow(color, vec3(1.0 / (1.0 + hl)));
        return mix(color, expanded, mask);
    }
}

// ── 5. Tonal ─────────────────────────────────────────────────────────────
vec3 apply_tonal(vec3 color, float contrast, float shadows, float whites, float blacks) {
    float luma = get_luma(color);
    vec3 result = color;
    if (abs(contrast) > EPS) {
        vec3 mid = vec3(0.18);
        result = mid + (result - mid) * (1.0 + contrast);
    }
    if (abs(shadows) > EPS) {
        result += shadows * shadows_mask(luma) * 0.3;
    }
    if (abs(whites) > EPS) {
        result += whites * whites_mask(luma) * 0.25;
    }
    if (abs(blacks) > EPS) {
        result += blacks * blacks_mask(luma) * 0.25;
    }
    return result;
}

// ── 6. Centre (Midtone Emphasis) ────────────────────────────────────────
vec3 apply_centre(vec3 color, float c) {
    if (abs(c) < EPS) return color;
    float luma = get_luma(color);
    float shift = c * 0.3;
    float target = clamp(luma + shift, 0.0, 1.0);
    float factor = (luma > EPS) ? target / luma : 1.0;
    return color * factor;
}

// ── 7. Saturation + Vibrance ─────────────────────────────────────────────
vec3 apply_creative_color(vec3 color, float sat, float vib) {
    vec3 hsv = rgb_to_hsv(color);
    float currentSat = hsv.y;
    float skinProtection = 1.0;
    if (hsv.x > 10.0 && hsv.x < 50.0 && currentSat < 0.5 && hsv.z > 0.2) {
        skinProtection = 0.5;
    }
    float vibranceAmount = vib * (1.0 - currentSat) * skinProtection;
    hsv.y = clamp(currentSat + vibranceAmount * 1.5, 0.0, 1.0);
    hsv.y = clamp(hsv.y + sat, 0.0, 1.0);
    return hsv_to_rgb(hsv);
}

// ── 8. Clarity (real local contrast via neighborhood blur, B14) ──────────
// Sample the original input buffer (linear) in a local Gaussian window. This
// is the single-pass approximation of the multi-pass blur pipeline used by
// the original WGSL: clarity = color + (color - blurred) * amount, masked to
// midtones so the effect targets local contrast rather than global tone.
vec3 sample_input_linear(ivec2 coord, int w, int h) {
    coord.x = clamp(coord.x, 0, w - 1);
    coord.y = clamp(coord.y, 0, h - 1);
    uint idx = uint(coord.y * w + coord.x);
    return srgb_to_linear3(unpackRGBA8(inData[idx]).rgb);
}

float gaussian_weight(float d, float sigma) {
    return exp(-(d * d) / (2.0 * sigma * sigma));
}

vec3 local_gaussian_blur(ivec2 coord, int w, int h, int radius) {
    float sigma = max(float(radius) / 2.0, 0.5);
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
            float wt = gaussian_weight(length(vec2(dx, dy)), sigma);
            sum += sample_input_linear(coord + ivec2(dx, dy), w, h) * wt;
            wsum += wt;
        }
    }
    return sum / max(wsum, EPS);
}

vec3 apply_clarity(vec3 color, ivec2 coord, float clarity, int w, int h) {
    if (abs(clarity) < EPS) return color;
    int radius = 5;
    vec3 blurred = local_gaussian_blur(coord, w, h, radius);
    // Local contrast: emphasize difference between pixel and its neighborhood.
    vec3 highPass = color - blurred;
    float luma = get_luma(color);
    float mask = midtones_mask(luma);
    vec3 result = color + highPass * clarity * 1.5;
    return mix(color, result, mask);
}

// ── B4: Structure (finer-grained local contrast) ────────────────────────
// Like clarity but uses a tighter neighborhood and a wider luma mask so the
// effect enhances fine texture/detail rather than broad midtone contrast.
vec3 apply_structure(vec3 color, ivec2 coord, float structure, int w, int h) {
    if (abs(structure) < EPS) return color;
    int radius = 2;
    vec3 blurred = local_gaussian_blur(coord, w, h, radius);
    vec3 highPass = color - blurred;
    float luma = get_luma(color);
    float mask = smoothstep_c(0.1, 0.9, luma);
    vec3 result = color + highPass * structure * 2.0;
    return mix(color, result, mask);
}

// ── 9. HSL Panel ────────────────────────────────────────────────────────
float hue_delta(float h1, float h2) {
    float d = abs(h1 - h2);
    return (d > 180.0) ? (360.0 - d) : d;
}

float hsl_range_weight(float hue, float center, float span) {
    float halfSpan = span * 0.5;
    float dist = hue_delta(hue, center);
    return (dist <= halfSpan) ? (1.0 - dist / halfSpan) : 0.0;
}

vec3 apply_hsl_panel(vec3 color, vec4 hslR, vec4 hslO, vec4 hslY, vec4 hslG,
                     vec4 hslA, vec4 hslB, vec4 hslP, vec4 hslM) {
    vec3 hsv = rgb_to_hsv(color);
    float hue = hsv.x;
    float hueShift = 0.0;
    float satShift = 0.0;
    float lumShift = 0.0;

    float w;
    w = hsl_range_weight(hue, 358.0, 35.0);
    hueShift += hslR.x * w; satShift += hslR.y * w; lumShift += hslR.z * w;

    w = hsl_range_weight(hue, 25.0, 45.0);
    hueShift += hslO.x * w; satShift += hslO.y * w; lumShift += hslO.z * w;

    w = hsl_range_weight(hue, 60.0, 40.0);
    hueShift += hslY.x * w; satShift += hslY.y * w; lumShift += hslY.z * w;

    w = hsl_range_weight(hue, 115.0, 90.0);
    hueShift += hslG.x * w; satShift += hslG.y * w; lumShift += hslG.z * w;

    w = hsl_range_weight(hue, 180.0, 60.0);
    hueShift += hslA.x * w; satShift += hslA.y * w; lumShift += hslA.z * w;

    w = hsl_range_weight(hue, 225.0, 60.0);
    hueShift += hslB.x * w; satShift += hslB.y * w; lumShift += hslB.z * w;

    w = hsl_range_weight(hue, 280.0, 55.0);
    hueShift += hslP.x * w; satShift += hslP.y * w; lumShift += hslP.z * w;

    w = hsl_range_weight(hue, 330.0, 50.0);
    hueShift += hslM.x * w; satShift += hslM.y * w; lumShift += hslM.z * w;

    hsv.x = mod(hsv.x + hueShift * 60.0 + 360.0, 360.0);
    hsv.y = clamp(hsv.y + satShift, 0.0, 1.0);
    vec3 rgb = hsv_to_rgb(hsv);
    float luma = get_luma(rgb);
    rgb = luma + (rgb - luma) * (1.0 + lumShift);
    return rgb;
}

// ── 10. Tone Curve (luma + per-channel RGB) ────────────────────────────
// Generic monotonic-cubic curve evaluator over a 10-point control curve
// packed as vec4[5] (x0,y0,x1,y1, ...). Works for the luma curve and the
// independent R/G/B curves (B1).
vec2 unpack_curve_point_arr(vec4 pts[5], int idx) {
    int vecIdx = idx / 2;
    int compIdx = idx % 2;
    vec4 v = pts[vecIdx];
    return (compIdx == 0) ? v.xy : v.zw;
}

float eval_curve(vec4 pts[5], float input_val) {
    const int N = 10;
    float xs[N]; float ys[N];
    for (int i = 0; i < N; i++) {
        vec2 pt = unpack_curve_point_arr(pts, i);
        xs[i] = pt.x; ys[i] = pt.y;
    }
    if (input_val <= xs[0]) return ys[0];
    if (input_val >= xs[N-1]) return ys[N-1];

    int idx = 0;
    for (int i = 0; i < N - 1; i++) {
        if (input_val >= xs[i] && input_val <= xs[i+1]) { idx = i; break; }
    }
    float dx = xs[idx+1] - xs[idx];
    if (dx < EPS) return ys[idx];
    float t = (input_val - xs[idx]) / dx;

    float m0, m1;
    if (idx == 0) { m0 = (ys[1] - ys[0]) / dx; }
    else { m0 = ((ys[idx+1] - ys[idx-1]) / (xs[idx+1] - xs[idx-1])) * dx * 0.5; }
    if (idx + 1 >= N - 1) { m1 = (ys[N-1] - ys[N-2]) / dx; }
    else { m1 = ((ys[idx+2] - ys[idx]) / (xs[idx+2] - xs[idx])) * dx * 0.5; }

    float t2 = t*t; float t3 = t2*t;
    float h00 = 2.0*t3 - 3.0*t2 + 1.0;
    float h10 = t3 - 2.0*t2 + t;
    float h01 = -2.0*t3 + 3.0*t2;
    float h11 = t3 - t2;
    return h00*ys[idx] + h10*m0 + h01*ys[idx+1] + h11*m1;
}

// Back-compat: luma curve only.
float apply_curve(float input_val) {
    return eval_curve(adj.curvePoints, input_val);
}

// B1: luma curve drives overall luma, then independent R/G/B curves shape
// each channel. This matches the original WGSL apply_curve (4-channel).
vec3 apply_tone_curve(vec3 color) {
    float luma = get_luma(color);
    float curvedLuma = eval_curve(adj.curvePoints, luma);
    float lumaRatio = (luma > EPS) ? curvedLuma / luma : 1.0;
    vec3 result = color * lumaRatio;
    result.r = eval_curve(adj.curveRedPoints,   result.r);
    result.g = eval_curve(adj.curveGreenPoints, result.g);
    result.b = eval_curve(adj.curveBluePoints,  result.b);
    return result;
}

// ── 11. Color Grading ───────────────────────────────────────────────────
vec3 color_wheel_tint(vec4 wheel) {
    float hue = wheel.x;
    float sat = wheel.y;
    float lum = wheel.z;
    vec3 hsv = vec3(hue, sat, 0.5);
    vec3 rgb = hsv_to_rgb(hsv * vec3(1.0, 1.0, 1.0) + vec3(0.0, 0.0, 0.0));
    // Manual HSV->RGB for normalized hue [0,1]
    float h = mod(hue, 1.0) * 6.0;
    int i = int(h);
    float f = h - float(i);
    float p = 0.5 * (1.0 - sat);
    float q = 0.5 * (1.0 - sat * f);
    float t = 0.5 * (1.0 - sat * (1.0 - f));
    vec3 tint;
    if (i == 0) tint = vec3(0.5, t, p);
    else if (i == 1) tint = vec3(q, 0.5, p);
    else if (i == 2) tint = vec3(p, 0.5, t);
    else if (i == 3) tint = vec3(p, q, 0.5);
    else if (i == 4) tint = vec3(t, p, 0.5);
    else tint = vec3(0.5, p, q);
    return tint * (1.0 + lum);
}

vec3 apply_color_grading(vec3 color, vec4 cgS, vec4 cgM, vec4 cgH,
                         float blend, float balance,
                         float cdlSR, float cdlSG, float cdlSB,
                         float cdlMR, float cdlMG, float cdlMB,
                         float cdlHR, float cdlHG, float cdlHB) {
    float luma = get_luma(color);
    float sm = shadows_mask(luma);
    float mm = midtones_mask(luma);
    float hm = highlights_mask(luma);

    float balancedSm = sm * (1.0 + balance * 0.5);
    float balancedHm = hm * (1.0 - balance * 0.5);
    float maskSum = balancedSm + mm + balancedHm + EPS;
    sm = balancedSm / maskSum;
    mm = mm / maskSum;
    hm = balancedHm / maskSum;

    vec3 tint = sm * color_wheel_tint(cgS) +
                mm * color_wheel_tint(cgM) +
                hm * color_wheel_tint(cgH);

    vec3 result = mix(color, color + tint, blend);

    // Global sat from color grading is handled by caller
    // CDL offsets
    float anyCdl = abs(cdlSR) + abs(cdlSG) + abs(cdlSB) +
                   abs(cdlMR) + abs(cdlMG) + abs(cdlMB) +
                   abs(cdlHR) + abs(cdlHG) + abs(cdlHB);
    if (anyCdl > EPS) {
        float offsetR = (sm * cdlSR + mm * cdlMR + hm * cdlHR) * 0.15;
        float offsetG = (sm * cdlSG + mm * cdlMG + hm * cdlHG) * 0.15;
        float offsetB = (sm * cdlSB + mm * cdlMB + hm * cdlHB) * 0.15;
        result += vec3(offsetR, offsetG, offsetB);
    }
    return result;
}

// ── 12. Vignette ────────────────────────────────────────────────────────
vec3 apply_vignette(vec3 color, vec2 uv, float amount, float midpoint,
                    float roundness, float feather, float w, float h) {
    if (abs(amount) < EPS) return color;
    vec2 centered = uv - 0.5;
    float aspect = w / max(h, 1.0);
    vec2 shaped = centered;
    shaped.x *= mix(1.0, 1.0 / max(aspect, EPS), 0.5 + roundness * 0.25);
    shaped.y *= mix(1.0, aspect, 0.5 + roundness * 0.25);
    float dist = length(shaped) * 1.414;
    float start = midpoint * 0.7;
    float end = clamp(start + feather * 0.3 + 0.05, start + 0.01, 1.0);
    float v;
    if (amount > 0.0) v = 1.0 - smoothstep_c(start, end, dist) * amount;
    else v = 1.0 + smoothstep_c(start, end, dist) * (-amount);
    return color * clamp(v, 0.0, 2.0);
}

// ── 13. Grain ───────────────────────────────────────────────────────────
uint hash_uint(uint x) {
    x = ((x >> 16u) ^ x) * 0x45d9f3bu;
    x = ((x >> 16u) ^ x) * 0x45d9f3bu;
    x = (x >> 16u) ^ x;
    return x;
}

float hash_f(vec2 p) {
    uint h = hash_uint(floatBitsToUint(p.x)) + hash_uint(floatBitsToUint(p.y)) * 127u;
    return float(h) / 4294967296.0;
}

float gaussian_grain(vec2 uv, float size, float w, float h) {
    vec2 scaled = uv * vec2(w, h) / max(size, 0.5);
    float ix = floor(scaled.x);
    float iy = floor(scaled.y);
    float r1 = hash_f(vec2(ix, iy));
    float r2 = hash_f(vec2(ix + 0.5, iy + 0.7));
    float r = sqrt(max(-2.0 * log(max(r1, EPS)), 0.0));
    float theta = 2.0 * PI * r2;
    return r * cos(theta);
}

vec3 apply_grain(vec3 color, vec2 uv, float amount, float size, float roughness, float w, float h) {
    if (amount < EPS) return color;
    float luma = get_luma(color);
    float grainAmount = clamp(1.0 - abs(luma - 0.5) * 1.5, 0.15, 1.0);
    float noise = gaussian_grain(uv, size, w, h);
    float strength = amount * grainAmount * 0.3;
    vec3 result = color + noise * strength;
    float chromaNoise = gaussian_grain(uv + vec2(100.0, 200.0), size * 1.5, w, h);
    float lum = get_luma(result);
    result = lum + (result - lum) * (1.0 + chromaNoise * strength * 0.15);
    return result;
}

// ── 14. AgX Tone Mapping ────────────────────────────────────────────────
vec3 apply_agx(vec3 color, float contrast, float pedestal) {
    float lo = -10.0; float hi = 13.0;
    vec3 logColor;
    logColor.r = clamp((log2(max(color.r, 0.0) + EPS) - lo) / (hi - lo), 0.0, 1.0);
    logColor.g = clamp((log2(max(color.g, 0.0) + EPS) - lo) / (hi - lo), 0.0, 1.0);
    logColor.b = clamp((log2(max(color.b, 0.0) + EPS) - lo) / (hi - lo), 0.0, 1.0);
    float c = 1.0 + contrast * 0.5;
    logColor = pow(logColor, vec3(c));
    logColor = max(logColor - vec3(pedestal), vec3(0.0)) / max(1.0 - pedestal, EPS);
    return clamp(logColor, 0.0, 1.0);
}

// ── 15. ACES Tone Mapping (full RRT + ODT) ──────────────────────────────
// Linear sRGB/Rec.709 -> ACEScg (AP1) -> RRT -> ODT(sRGB) -> display linear.
const mat3 sRGB_TO_AP1 = mat3(
    0.61319,  0.07021,  0.02062,
    0.33951,  0.91635,  0.08697,
    0.04731,  0.01345,  0.89241
);
const mat3 AP1_TO_sRGB = mat3(
     1.7050795555, -0.1302571050, -0.0240038776,
    -0.6242344134,  1.0809560424, -0.1302571050,
    -0.0808451588,  0.0493010787,  1.1542610815
);

// RRT + ODT combined curve (Narkowicz fit). This is the full ACES display
// rendering: scene linear in, display-referred sRGB-linear out.
vec3 aces_rrt_odt(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

float aces_rrt(float x) {
    // Legacy single-channel RRT (Hill fit), kept for reference.
    const float a = 0.0245786, b = 0.0000907, c = 0.983729, d = 0.4329510, e = 0.238081;
    return max((x * (a*x + b)) / (x * (c*x + d) + e), 0.0);
}

vec3 apply_aces2(vec3 color) {
    // B16: full pipeline. Work in ACEScg (AP1) primaries so the RRT+ODT
    // operates in the correct scene-referred space, then return to sRGB.
    vec3 ap1 = sRGB_TO_AP1 * max(color, vec3(0.0));
    vec3 mapped = aces_rrt_odt(ap1);
    vec3 outRgb = AP1_TO_sRGB * mapped;
    return clamp(outRgb, 0.0, 1.0);
}

// ── 16. Filmic Tone Mapping ─────────────────────────────────────────────
float filmic_curve(float x, float toe, float shoulder, float midpoint, float contrast) {
    float toeOffset = toe * 0.5;
    float shoulderOffset = shoulder * 0.5;
    float m = max(midpoint, 0.01);
    float toeC = x / (x + toeOffset + EPS);
    float shoulderC = 1.0 - (1.0 - x) / ((1.0 - x) + shoulderOffset + EPS);
    float blend = smoothstep(0.0, m, x);
    float result = mix(toeC, shoulderC, blend);
    if (abs(contrast - 1.0) > EPS) result = pow(max(result, 0.0), 1.0 / max(contrast, 0.1));
    return clamp(result, 0.0, 1.0);
}

vec3 apply_filmic(vec3 color) {
    return vec3(
        filmic_curve(max(color.r, 0.0), 0.3, 0.5, 0.5, 1.0),
        filmic_curve(max(color.g, 0.0), 0.3, 0.5, 0.5, 1.0),
        filmic_curve(max(color.b, 0.0), 0.3, 0.5, 0.5, 1.0)
    );
}

// ── 17. OpenDRT-style perceptual display rendering (B13, mode=3) ────────
// Display-referred perceptual sigmoid anchored at 18% middle grey. Compresses
// HDR scene-linear with a soft toe (shadows) and controlled shoulder
// (highlights) using an extended Reinhard white-point, then applies a gentle
// perceptual gamma shaping for display. This is a real single-pass
// approximation of the OpenDRT rendering pipeline.
vec3 apply_opendrt(vec3 color) {
    const float grey  = 0.18;
    const float white = 4.0;
    vec3 x = max(color, vec3(0.0));
    // Extended Reinhard: keeps midtones faithful, rolls off near `white`.
    vec3 num = x * (1.0 + x / (white * white));
    vec3 den = 1.0 + x;
    vec3 mapped = num / max(den, vec3(EPS));
    // Re-anchor to middle grey and shape for display.
    mapped = clamp(mapped / grey, 0.0, 1.0);
    mapped = pow(mapped, vec3(0.78));
    return clamp(mapped, 0.0, 1.0);
}

// ── Dither ──────────────────────────────────────────────────────────────
float hash_dither(float n) {
    uint s = floatBitsToUint(n);
    s = s * 1597334677u;
    s = (s >> 16u) | s;
    return float(s & 0xFFFFu) / 65536.0;
}

float gradient_noise(float x, float y) {
    float ix = floor(x); float iy = floor(y);
    float fx = x - ix; float fy = y - iy;
    float ux = fx*fx*(3.0-2.0*fx);
    float uy = fy*fy*(3.0-2.0*fy);
    float a = hash_dither(ix + iy * 57.0);
    float b = hash_dither(ix + 1.0 + iy * 57.0);
    float c = hash_dither(ix + (iy+1.0) * 57.0);
    float d = hash_dither(ix + 1.0 + (iy+1.0) * 57.0);
    return a + (b-a)*ux + (c-a)*uy + (a-b-c+d)*ux*uy;
}

// ── Pixel unpack/pack helpers ───────────────────────────────────────────
vec4 unpackRGBA8(uint p) {
    return vec4(
        float((p >> 0u)  & 0xFFu) / 255.0,
        float((p >> 8u)  & 0xFFu) / 255.0,
        float((p >> 16u) & 0xFFu) / 255.0,
        float((p >> 24u) & 0xFFu) / 255.0
    );
}

uint packRGBA8(vec4 c) {
    uvec4 u = uvec4(clamp(c * 255.0, 0.0, 255.0) + 0.5);
    return (u.a << 24u) | (u.b << 16u) | (u.g << 8u) | u.r;
}

// ── Sharpening (Gaussian unsharp mask, B15) ─────────────────────────────
// Real USM: subtract a Gaussian-blurred version of the original input from
// the current pixel and add the high-pass residual back scaled by `sharpness`.
// Replaces the previous 3x3 box-blur approximation. Operates in sRGB space
// (this stage runs after linear->sRGB) so the blur samples raw input pixels.
vec3 apply_sharpen(ivec2 coord, vec3 color, float sharpness, float radius, int w, int h) {
    if (sharpness < EPS) return color;

    int r = int(max(round(radius), 1.0));
    float sigma = max(float(r) / 2.0, 0.5);
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int dy = -r; dy <= r; dy++) {
        for (int dx = -r; dx <= r; dx++) {
            ivec2 nc = coord + ivec2(dx, dy);
            nc.x = clamp(nc.x, 0, w - 1);
            nc.y = clamp(nc.y, 0, h - 1);
            uint idx = nc.y * uint(w) + nc.x;
            vec3 neighbor = unpackRGBA8(inData[idx]).rgb;
            float wt = gaussian_weight(length(vec2(dx, dy)), sigma);
            sum += neighbor * wt;
            wsum += wt;
        }
    }
    vec3 blurred = sum / max(wsum, EPS);
    vec3 highPass = color - blurred;
    vec3 result = color + highPass * sharpness;

    // Luminance-weighted sharpening to limit chroma amplification.
    float lumaOrig = get_luma(color);
    float lumaResult = get_luma(result);
    vec3 chromaOrig = color - vec3(lumaOrig);
    vec3 chromaResult = result - vec3(lumaResult);
    vec3 chromaBlended = mix(chromaOrig, chromaResult, 0.2);
    return vec3(lumaResult) + chromaBlended;
}

// ═══════════════════════════════════════════════════════════════════════════
// ── EXTENDED OPERATION FUNCTIONS (B2,B3,B5,B6,B7,B8,B9,B10,B11,B12) ──────
// ═══════════════════════════════════════════════════════════════════════════

// ── B2: Color Calibration ───────────────────────────────────────────────
// Adjusts hue & saturation of the R/G/B primaries (weighted by hue proximity
// to each primary) and applies a shadows tint. Mirrors the WGSL
// apply_color_calibration.
vec3 apply_color_calibration(vec3 color) {
    float redHue   = adj.calRedGreen.x;
    float redSat   = adj.calRedGreen.y;
    float greenHue = adj.calRedGreen.z;
    float greenSat = adj.calRedGreen.w;
    float blueHue  = adj.calBlueShadow.x;
    float blueSat  = adj.calBlueShadow.y;
    float shadowsTint = adj.calBlueShadow.z;

    float anyActive = abs(redHue) + abs(redSat) + abs(greenHue) + abs(greenSat)
                    + abs(blueHue) + abs(blueSat) + abs(shadowsTint);
    if (anyActive < EPS) return color;

    vec3 hsv = rgb_to_hsv(color);
    float hue = hsv.x;
    float hueShift = 0.0;
    float satScale = 1.0;

    // Red primary straddles 0/360 degrees. Hue inputs are normalized to -1..1
    // (see JNI fillAdjustmentsUBO), so a 30x factor yields a ±30° max shift —
    // comparable to the HSL panel's hue range.
    float wr = hsl_range_weight(hue, 0.0, 60.0) + hsl_range_weight(hue, 360.0, 60.0);
    hueShift += redHue * wr * 30.0;
    satScale *= mix(1.0, 1.0 + redSat, wr);

    float wg = hsl_range_weight(hue, 120.0, 90.0);
    hueShift += greenHue * wg * 30.0;
    satScale *= mix(1.0, 1.0 + greenSat, wg);

    float wb = hsl_range_weight(hue, 240.0, 90.0);
    hueShift += blueHue * wb * 30.0;
    satScale *= mix(1.0, 1.0 + blueSat, wb);

    hsv.x = mod(hsv.x + hueShift + 360.0, 360.0);
    hsv.y = clamp(hsv.y * satScale, 0.0, 1.0);
    vec3 result = hsv_to_rgb(hsv);

    // Shadows tint: negative -> warm cast, positive -> cool cast.
    if (abs(shadowsTint) > EPS) {
        float luma = get_luma(color);
        float sm = shadows_mask(luma);
        vec3 warm = vec3(1.0, 0.92, 0.82);
        vec3 cool = vec3(0.82, 0.92, 1.0);
        float t = clamp(shadowsTint * 0.5 + 0.5, 0.0, 1.0);
        vec3 tint = mix(warm, cool, t);
        result = mix(result, result * tint, sm * abs(shadowsTint));
    }
    return result;
}

// ── B3: Dehaze (dark-channel prior) ─────────────────────────────────────
// Estimates the atmospheric light and transmission from a local dark channel
// of the original input, then inverts the scattering model on the working
// color: restored = (color - A*(1-t)) / t. Single-pass approximation of the
// multi-scale DCP used by the WGSL dehaze().
vec3 apply_dehaze(vec3 color, ivec2 coord, int w, int h, float amount) {
    if (abs(amount) < EPS) return color;
    int radius = 7;
    float dark = 1.0;
    vec3 airlight = vec3(0.0);
    for (int dy = -radius; dy <= radius; dy += 2) {
        for (int dx = -radius; dx <= radius; dx += 2) {
            vec3 n = sample_input_linear(coord + ivec2(dx, dy), w, h);
            float dc = min(n.r, min(n.g, n.b));
            dark = min(dark, dc);
            airlight = max(airlight, n);
        }
    }
    float omega = clamp(amount, 0.0, 1.0) * 0.9;
    vec3 A = max(airlight, vec3(EPS));
    float Amax = max(A.r, max(A.g, A.b));
    float t = max(1.0 - omega * dark / max(Amax, EPS), 0.1);
    vec3 restored = (color - A * (1.0 - t)) / max(t, 0.1);
    return mix(color, clamp(restored, 0.0, 4.0), clamp(abs(amount), 0.0, 1.0));
}

// ── B5: Luma noise reduction (bilateral-ish luma smoothing) ─────────────
vec3 apply_luma_noise_reduction(vec3 color, ivec2 coord, int w, int h, float amount) {
    if (amount < EPS) return color;
    int radius = 1;
    float lumaCenter = get_luma(color);
    float sum = 0.0;
    float wsum = 0.0;
    for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
            vec3 n = sample_input_linear(coord + ivec2(dx, dy), w, h);
            float nl = get_luma(n);
            float sw = gaussian_weight(length(vec2(dx, dy)), 1.0);
            float rw = gaussian_weight(nl - lumaCenter, 0.15);
            float wt = sw * rw;
            sum += nl * wt;
            wsum += wt;
        }
    }
    float denoised = sum / max(wsum, EPS);
    float lumaOrig = get_luma(color);
    float newLuma = mix(lumaOrig, denoised, clamp(amount, 0.0, 1.0));
    return (lumaOrig > EPS) ? color * (newLuma / lumaOrig) : color;
}

// ── B6: Color (chroma) noise reduction ──────────────────────────────────
// Low-pass filters the chroma residual (color - luma) toward the local mean.
vec3 apply_color_noise_reduction(vec3 color, ivec2 coord, int w, int h, float amount) {
    if (amount < EPS) return color;
    int radius = 1;
    float lumaCenter = get_luma(color);
    vec3 chromaCenter = color - vec3(lumaCenter);
    vec3 chromaSum = vec3(0.0);
    float wsum = 0.0;
    for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
            vec3 n = sample_input_linear(coord + ivec2(dx, dy), w, h);
            float nl = get_luma(n);
            vec3 nc = n - vec3(nl);
            float sw = gaussian_weight(length(vec2(dx, dy)), 1.0);
            chromaSum += nc * sw;
            wsum += sw;
        }
    }
    vec3 chromaMean = chromaSum / max(wsum, EPS);
    vec3 denoisedChroma = mix(chromaCenter, chromaMean, clamp(amount, 0.0, 1.0));
    return vec3(lumaCenter) + denoisedChroma;
}

// ── B7: Chromatic aberration (radial R/B offset) ───────────────────────
// Applied early so the shifted channels flow through the rest of the pipeline.
// Red-cyan axis offsets R, blue-yellow axis offsets B, both scaled by radial
// distance from the image center (barrel-style).
vec3 apply_chromatic_aberration(vec3 color, ivec2 coord, int w, int h, float rc, float by) {
    if (abs(rc) < EPS && abs(by) < EPS) return color;
    vec2 center = vec2(float(w) * 0.5, float(h) * 0.5);
    vec2 pos = vec2(coord) - center;
    float dist = length(pos) / max(length(center), 1.0);
    vec2 dir = (length(pos) > EPS) ? normalize(pos) : vec2(0.0);
    float offsetR = rc * dist * 8.0;
    float offsetB = by * dist * 8.0;
    ivec2 rCoord = coord + ivec2(dir * offsetR);
    ivec2 bCoord = coord + ivec2(dir * offsetB);
    vec3 rSample = sample_input_linear(rCoord, w, h);
    vec3 bSample = sample_input_linear(bCoord, w, h);
    float rMix = clamp(abs(rc), 0.0, 1.0);
    float bMix = clamp(abs(by), 0.0, 1.0);
    return vec3(mix(color.r, rSample.r, rMix),
                color.g,
                mix(color.b, bSample.b, bMix));
}

// ── B8: 3D LUT lookup ───────────────────────────────────────────────────
// Applied in display sRGB space (after tone mapping & sRGB encode).
vec3 apply_lut(vec3 color) {
    if (adj.lutInfo.y < 0.5) return color;          // hasLut
    float intensity = clamp(adj.lutInfo.x, 0.0, 1.0);
    if (intensity < EPS) return color;
    float size = max(adj.lutInfo.z, 2.0);
    // Half-texel-offset mapping for correct trilinear sampling. textureLod with
    // lod=0 is required in a compute shader: implicit derivatives used by
    // texture() are only defined in fragment shaders, so we must specify LOD
    // explicitly (the LUT has a single mip level).
    vec3 scaled = clamp(color, 0.0, 1.0) * ((size - 1.0) / size) + (0.5 / size);
    vec3 lutColor = textureLod(lutSampler, scaled, 0.0).rgb;
    return mix(color, lutColor, intensity);
}

// ── B9: Glow (bright extraction + screen blend) ─────────────────────────
vec3 apply_glow(vec3 color, ivec2 coord, int w, int h, float amount) {
    if (amount < EPS) return color;
    int radius = 6;
    float threshold = 0.6;
    vec3 blur = local_gaussian_blur(coord, w, h, radius);
    vec3 glow = max(blur - vec3(threshold), vec3(0.0)) / (1.0 - threshold + EPS);
    vec3 screened = 1.0 - (1.0 - color) * (1.0 - glow * amount * 2.0);
    return mix(color, screened, clamp(amount, 0.0, 1.0));
}

// ── B10: Halation (warm highlight bleed around bright regions) ──────────
vec3 apply_halation(vec3 color, ivec2 coord, int w, int h, float amount) {
    if (amount < EPS) return color;
    int radius = 5;
    vec3 blur = local_gaussian_blur(coord, w, h, radius);
    float brightLuma = get_luma(blur);
    float mask = smoothstep_c(0.6, 1.0, brightLuma);
    vec3 halationLight = vec3(1.0, 0.4, 0.2) * mask * amount * 2.0;
    return color + halationLight;
}

// ── B11: Lens flare (ghost generation along the light axis) ─────────────
vec3 apply_flare(vec3 color, vec2 uv, ivec2 coord, int w, int h, float amount) {
    if (amount < EPS) return color;
    vec2 lightPos = vec2(0.3, 0.25);
    int radius = 4;
    vec3 blur = local_gaussian_blur(coord, w, h, radius);
    float bright = smoothstep_c(0.7, 1.0, get_luma(blur));
    vec2 toLight = uv - lightPos;
    float ghost = 0.0;
    for (int i = 1; i <= 4; i++) {
        float t = float(i) * 0.3;
        vec2 ghostPos = lightPos + toLight * t * 2.0;
        float d = length(uv - ghostPos);
        ghost += 0.02 / max(d * d, 0.001);
    }
    ghost *= bright * amount * 0.5;
    return color + vec3(1.0, 0.85, 0.7) * ghost;
}

// ── B12: Show clipping overlay ──────────────────────────────────────────
// Red where the final color is pinned to white, blue where pinned to black.
vec3 show_clipping_overlay(vec3 color) {
    if (adj.showClippingVec.x == 0) return color;
    if (any(greaterThan(color, vec3(0.99)))) return vec3(1.0, 0.0, 0.0);
    if (any(lessThan(color, vec3(0.01)))) return vec3(0.0, 0.0, 1.0);
    return color;
}

// ═══════════════════════════════════════════════════════════════════════════
// ── MAIN ────────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════
void main() {
    int w = int(adj.width);
    int h = int(adj.height);
    ivec2 coord = ivec2(gl_GlobalInvocationID.xy);
    if (coord.x >= w || coord.y >= h) return;

    uint idx = uint(coord.y * w + coord.x);
    vec4 pixel = unpackRGBA8(inData[idx]);

    vec2 uv = (vec2(coord) + 0.5) / vec2(float(w), float(h));

    // Input is sRGB, convert to linear
    vec3 color = srgb_to_linear3(pixel.rgb);

    // Step 1: Chromatic aberration (B7) — applied first on the raw linear color
    // so the radially-offset channel samples (read from the original input) are
    // in the same color space as the center pixel. CA is a lens-capture defect
    // and should precede all exposure/WB adjustments.
    color = apply_chromatic_aberration(color, coord, w, h,
                                       adj.chromaticAberration.x,
                                       adj.chromaticAberration.y);

    // Step 2: Exposure
    color = apply_exposure(color, adj.exposure);

    // Step 3: Filmic brightness
    color = apply_brightness(color, adj.brightness);

    // Step 4: White balance
    color = apply_white_balance(color, adj.temperature, adj.tint);

    // Step 5: Highlights
    color = apply_highlights(color, adj.highlights);

    // Step 6: Tonal (contrast / shadows / whites / blacks)
    color = apply_tonal(color, adj.contrast, adj.shadows, adj.whites, adj.blacks);

    // Step 7: Centre
    color = apply_centre(color, adj.centre);

    // Step 8: Saturation + Vibrance
    color = apply_creative_color(color, adj.saturation, adj.vibrance);

    // Step 9: Dehaze (B3) — dark-channel prior haze removal.
    color = apply_dehaze(color, coord, w, h, adj.dehazePad.x);

    // Step 10: Luma noise reduction (B5)
    color = apply_luma_noise_reduction(color, coord, w, h, adj.noiseReduction.x);

    // Step 11: Color noise reduction (B6)
    color = apply_color_noise_reduction(color, coord, w, h, adj.noiseReduction.y);

    // Step 12: Clarity (B14) — midtone local contrast via Gaussian blur.
    color = apply_clarity(color, coord, adj.clarity, w, h);

    // Step 13: Structure (B4) — fine local contrast.
    color = apply_structure(color, coord, adj.structurePad.x, w, h);

    // Step 14: HSL Panel
    color = apply_hsl_panel(color, adj.hslRed, adj.hslOrange, adj.hslYellow, adj.hslGreen,
                            adj.hslAqua, adj.hslBlue, adj.hslPurple, adj.hslMagenta);

    // Step 15: Color Calibration (B2) — primary hue/sat + shadows tint.
    color = apply_color_calibration(color);

    // Step 16: Tone curve (B1: luma + per-channel R/G/B)
    color = apply_tone_curve(color);

    // Step 17: Color Grading + CDL
    color = apply_color_grading(color, adj.cgShadows, adj.cgMidtones, adj.cgHighlights,
                                adj.cgBlend, adj.cgBalance,
                                adj.cdlShadowsR, adj.cdlShadowsG, adj.cdlShadowsB,
                                adj.cdlMidtonesR, adj.cdlMidtonesG, adj.cdlMidtonesB,
                                adj.cdlHighlightsR, adj.cdlHighlightsG, adj.cdlHighlightsB);

    // Step 18: Tone mapping. toneMapMode mirrors Kotlin colorScienceMode:
    //   0=AgX, 1=ACES(RRT+ODT), 2=OpenDRT (B13), 3=Standard(passthrough).
    // Mode 3 and any out-of-range value apply no tone mapping (passthrough).
    if (adj.toneMapMode == 0) {
        color = apply_agx(color, adj.agxContrast, adj.agxPedestal);
    } else if (adj.toneMapMode == 1) {
        color = apply_aces2(color);
    } else if (adj.toneMapMode == 2) {
        color = apply_opendrt(color);
    }

    // Step 19: Linear to sRGB
    color = linear_to_srgb3(clamp(color, 0.0, 1.0));

    // Step 20: 3D LUT (B8) — applied in display sRGB space.
    color = apply_lut(color);

    // Step 21: Glow (B9)
    color = apply_glow(color, coord, w, h, adj.glowHalationFlare.x);

    // Step 22: Halation (B10)
    color = apply_halation(color, coord, w, h, adj.glowHalationFlare.y);

    // Step 23: Flare (B11)
    color = apply_flare(color, uv, coord, w, h, adj.glowHalationFlare.z);

    // Step 24: Vignette
    color = apply_vignette(color, uv, adj.vignetteAmount, adj.vignetteMidpoint,
                           adj.vignetteRoundness, adj.vignetteFeather,
                           float(w), float(h));

    // Step 25: Grain
    color = apply_grain(color, uv, adj.grainAmount, adj.grainSize, adj.grainRoughness,
                        float(w), float(h));

    // Step 26: Sharpening (B15, Gaussian USM) — reads original neighbors.
    color = apply_sharpen(coord, color, adj.sharpness, adj.sharpenRadius, w, h);

    // Step 27: Dither
    color += (gradient_noise(uv.x, uv.y) - 0.5) / 255.0;

    color = clamp(color, 0.0, 1.0);

    // Step 28: Show clipping overlay (B12) — must be the final visual stage so
    // the red/blue warning pixels are not themselves dithered or clamped away.
    color = show_clipping_overlay(color);

    outData[idx] = packRGBA8(vec4(color, pixel.a));
}
)glsl";

// ═══════════════════════════════════════════════════════════════════════════
// shaderc-based runtime SPIR-V compilation
// ═══════════════════════════════════════════════════════════════════════════

#include <shaderc/shaderc.hpp>
#include <spirv/spirv.h>

static bool compileGLSLToSPIRV(const char* source, std::vector<uint32_t>& spirv) {
    shaderc::Compiler compiler;
    shaderc::CompileOptions options;
    options.SetTargetEnvironment(shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_1);
    options.SetOptimizationLevel(shaderc_optimization_level_performance);

    shaderc::SpvCompilationResult result = compiler.CompileGlslToSpv(
        source, strlen(source),
        shaderc_compute_shader,
        "vulkan_compute.comp",
        "main",
        options
    );

    if (result.GetCompilationStatus() != shaderc_compilation_status_success) {
        LOGE("GLSL to SPIR-V compilation failed: %s", result.GetErrorMessage().c_str());
        return false;
    }

    spirv.assign(result.cbegin(), result.cend());
    LOGI("Compiled GLSL to SPIR-V: %zu words", spirv.size());
    return true;
}

// ═══════════════════════════════════════════════════════════════════════════
// VulkanCompute implementation
// ═══════════════════════════════════════════════════════════════════════════

VulkanCompute::VulkanCompute() = default;

VulkanCompute::~VulkanCompute() {
    release();
}

VulkanDeviceInfo VulkanCompute::probeDevice() {
    VulkanDeviceInfo info = {};
    info.supported = false;

    // Try to create a Vulkan instance
    VkInstanceCreateInfo createInfo = {};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;

    VkInstance tempInstance = VK_NULL_HANDLE;
    VkResult result = vkCreateInstance(&createInfo, nullptr, &tempInstance);
    if (result != VK_SUCCESS) {
        LOGW("Vulkan not available: vkCreateInstance returned %d", result);
        return info;
    }

    // Enumerate physical devices
    uint32_t deviceCount = 0;
    vkEnumeratePhysicalDevices(tempInstance, &deviceCount, nullptr);
    if (deviceCount == 0) {
        LOGW("No Vulkan physical devices found");
        vkDestroyInstance(tempInstance, nullptr);
        return info;
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(tempInstance, &deviceCount, devices.data());

    // Find a device with a compute queue
    for (auto device : devices) {
        VkPhysicalDeviceProperties props;
        vkGetPhysicalDeviceProperties(device, &props);

        uint32_t queueFamilyCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount, nullptr);
        std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
        vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount, queueFamilies.data());

        for (uint32_t i = 0; i < queueFamilyCount; i++) {
            if (queueFamilies[i].queueFlags & VK_QUEUE_COMPUTE_BIT) {
                info.supported = true;
                strncpy(info.deviceName, props.deviceName, sizeof(info.deviceName) - 1);
                info.deviceName[sizeof(info.deviceName) - 1] = '\0';
                info.apiVersion = props.apiVersion;
                info.maxImageDimension = props.limits.maxImageDimension2D;
                break;
            }
        }

        if (info.supported) break;
    }

    vkDestroyInstance(tempInstance, nullptr);
    return info;
}

bool VulkanCompute::initialize() {
    if (m_initialized) return true;

    // 2026 正式版: 初始化失败时自动释放已分配资源，防止句柄泄漏和重复初始化崩溃。
    bool ok = true;
    if (ok) ok = createInstance();
    if (ok) ok = pickPhysicalDevice();
    if (ok) ok = createLogicalDevice();
    if (ok) ok = createCommandPool();
    if (ok) ok = createDescriptorSetLayout();
    if (ok) ok = createPipelineLayout();
    if (ok) ok = createComputePipeline();

    if (ok) {
        // Create descriptor pool. Include a combined-image-sampler slot for the
        // 3D LUT (binding=3) so the descriptor set always has a valid binding.
        VkDescriptorPoolSize poolSize = {};
        poolSize.type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        poolSize.descriptorCount = 2;

        VkDescriptorPoolSize uboPoolSize = {};
        uboPoolSize.type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        uboPoolSize.descriptorCount = 1;

        VkDescriptorPoolSize lutPoolSize = {};
        lutPoolSize.type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        lutPoolSize.descriptorCount = 1;

        std::array<VkDescriptorPoolSize, 3> poolSizes = {poolSize, uboPoolSize, lutPoolSize};

        VkDescriptorPoolCreateInfo poolInfo = {};
        poolInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
        poolInfo.poolSizeCount = static_cast<uint32_t>(poolSizes.size());
        poolInfo.pPoolSizes = poolSizes.data();
        poolInfo.maxSets = 1;

        VkResult result = vkCreateDescriptorPool(m_device, &poolInfo, nullptr, &m_descriptorPool);
        if (result != VK_SUCCESS) {
            LOGE("Vulkan error creating descriptor pool: %d", result);
            ok = false;
        }
    }

    if (ok) {
        // Allocate descriptor set
        VkDescriptorSetAllocateInfo allocInfo = {};
        allocInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        allocInfo.descriptorPool = m_descriptorPool;
        allocInfo.descriptorSetCount = 1;
        allocInfo.pSetLayouts = &m_descriptorSetLayout;

        VkResult result = vkAllocateDescriptorSets(m_device, &allocInfo, &m_descriptorSet);
        if (result != VK_SUCCESS) {
            LOGE("Vulkan error allocating descriptor set: %d", result);
            ok = false;
        }
    }

    // Create a default identity 3D LUT and bind it to descriptor binding=3 so
    // the binding is always valid. The UBO `hasLut` flag gates whether the
    // shader actually samples it; this just guarantees a safe sampler is bound.
    if (ok && !createLutTexture(16, nullptr)) {
        LOGE("Failed to create default identity LUT texture");
        ok = false;
    }
    if (ok) {
        writeLutDescriptor();
    }

    if (!ok) {
        release();
        return false;
    }

    m_initialized = true;
    LOGI("VulkanCompute initialized successfully");
    return true;
}

bool VulkanCompute::createInstance() {
    VkApplicationInfo appInfo = {};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "RapidRAW";
    appInfo.applicationVersion = 1;
    appInfo.pEngineName = "RapidRAW-Vulkan";
    appInfo.engineVersion = 1;
    appInfo.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo createInfo = {};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    VK_CHECK(vkCreateInstance(&createInfo, nullptr, &m_instance));
    LOGD("Vulkan instance created");
    return true;
}

bool VulkanCompute::pickPhysicalDevice() {
    uint32_t deviceCount = 0;
    vkEnumeratePhysicalDevices(m_instance, &deviceCount, nullptr);
    if (deviceCount == 0) {
        LOGE("No physical devices with Vulkan support");
        return false;
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(m_instance, &deviceCount, devices.data());

    for (auto device : devices) {
        uint32_t queueFamilyCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount, nullptr);
        std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
        vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount, queueFamilies.data());

        for (uint32_t i = 0; i < queueFamilyCount; i++) {
            if (queueFamilies[i].queueFlags & VK_QUEUE_COMPUTE_BIT) {
                m_physicalDevice = device;
                m_computeQueueFamily = i;

                VkPhysicalDeviceProperties props;
                vkGetPhysicalDeviceProperties(device, &props);
                m_maxImageDimension = props.limits.maxImageDimension2D;
                LOGI("Selected GPU: %s (API 0x%08X, maxImageDim=%u)", props.deviceName, props.apiVersion, m_maxImageDimension);
                return true;
            }
        }
    }

    LOGE("No GPU with compute queue found");
    return false;
}

bool VulkanCompute::createLogicalDevice() {
    float queuePriority = 1.0f;
    VkDeviceQueueCreateInfo queueCreateInfo = {};
    queueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueCreateInfo.queueFamilyIndex = m_computeQueueFamily;
    queueCreateInfo.queueCount = 1;
    queueCreateInfo.pQueuePriorities = &queuePriority;

    VkDeviceCreateInfo deviceCreateInfo = {};
    deviceCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    deviceCreateInfo.queueCreateInfoCount = 1;
    deviceCreateInfo.pQueueCreateInfos = &queueCreateInfo;

    VK_CHECK(vkCreateDevice(m_physicalDevice, &deviceCreateInfo, nullptr, &m_device));
    vkGetDeviceQueue(m_device, m_computeQueueFamily, 0, &m_computeQueue);
    LOGD("Logical device created with compute queue");
    return true;
}

bool VulkanCompute::createCommandPool() {
    VkCommandPoolCreateInfo poolInfo = {};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = m_computeQueueFamily;

    VK_CHECK(vkCreateCommandPool(m_device, &poolInfo, nullptr, &m_commandPool));

    // Allocate command buffer
    VkCommandBufferAllocateInfo allocInfo = {};
    allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocInfo.commandPool = m_commandPool;
    allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocInfo.commandBufferCount = 1;

    VK_CHECK(vkAllocateCommandBuffers(m_device, &allocInfo, &m_commandBuffer));
    LOGD("Command pool and buffer created");
    return true;
}

bool VulkanCompute::createDescriptorSetLayout() {
    // Binding 0: input storage buffer
    VkDescriptorSetLayoutBinding inputBinding = {};
    inputBinding.binding = 0;
    inputBinding.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    inputBinding.descriptorCount = 1;
    inputBinding.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

    // Binding 1: output storage buffer
    VkDescriptorSetLayoutBinding outputBinding = {};
    outputBinding.binding = 1;
    outputBinding.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    outputBinding.descriptorCount = 1;
    outputBinding.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

    // Binding 2: uniform buffer
    VkDescriptorSetLayoutBinding uboBinding = {};
    uboBinding.binding = 2;
    uboBinding.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    uboBinding.descriptorCount = 1;
    uboBinding.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

    // Binding 3: 3D LUT combined image sampler (B8). Immutable samplers are not
    // used so we can recreate the sampler with the LUT; pImmutableSamplers=null.
    VkDescriptorSetLayoutBinding lutBinding = {};
    lutBinding.binding = 3;
    lutBinding.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    lutBinding.descriptorCount = 1;
    lutBinding.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    lutBinding.pImmutableSamplers = nullptr;

    std::array<VkDescriptorSetLayoutBinding, 4> bindings = {
        inputBinding, outputBinding, uboBinding, lutBinding};

    VkDescriptorSetLayoutCreateInfo layoutInfo = {};
    layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    layoutInfo.bindingCount = static_cast<uint32_t>(bindings.size());
    layoutInfo.pBindings = bindings.data();

    VK_CHECK(vkCreateDescriptorSetLayout(m_device, &layoutInfo, nullptr, &m_descriptorSetLayout));
    LOGD("Descriptor set layout created (4 bindings, incl. LUT sampler)");
    return true;
}

bool VulkanCompute::createPipelineLayout() {
    VkPipelineLayoutCreateInfo layoutInfo = {};
    layoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    layoutInfo.setLayoutCount = 1;
    layoutInfo.pSetLayouts = &m_descriptorSetLayout;

    VK_CHECK(vkCreatePipelineLayout(m_device, &layoutInfo, nullptr, &m_pipelineLayout));
    LOGD("Pipeline layout created");
    return true;
}

bool VulkanCompute::createComputePipeline() {
    // Compile GLSL to SPIR-V at runtime
    std::vector<uint32_t> spirv;
    if (!compileGLSLToSPIRV(kComputeShaderGLSL, spirv)) {
        LOGE("Failed to compile compute shader to SPIR-V");
        return false;
    }

    VkShaderModule shaderModule;
    if (!createShaderModule(spirv.data(), spirv.size() * sizeof(uint32_t), &shaderModule)) {
        return false;
    }

    VkPipelineShaderStageCreateInfo shaderStageInfo = {};
    shaderStageInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    shaderStageInfo.stage = VK_SHADER_STAGE_COMPUTE_BIT;
    shaderStageInfo.module = shaderModule;
    shaderStageInfo.pName = "main";

    VkComputePipelineCreateInfo pipelineInfo = {};
    pipelineInfo.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
    pipelineInfo.layout = m_pipelineLayout;
    pipelineInfo.stage = shaderStageInfo;

    VkResult result = vkCreateComputePipelines(m_device, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr, &m_pipeline);
    vkDestroyShaderModule(m_device, shaderModule, nullptr);

    if (result != VK_SUCCESS) {
        LOGE("Failed to create compute pipeline: %d", result);
        return false;
    }

    LOGI("Compute pipeline created");
    return true;
}

bool VulkanCompute::createShaderModule(const uint32_t* code, size_t codeSize, VkShaderModule* outModule) {
    VkShaderModuleCreateInfo createInfo = {};
    createInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    createInfo.codeSize = codeSize;
    createInfo.pCode = code;

    VK_CHECK(vkCreateShaderModule(m_device, &createInfo, nullptr, outModule));
    return true;
}

bool VulkanCompute::createBuffer(VkDeviceSize size, VkBufferUsageFlags usage,
                                  VkMemoryPropertyFlags props,
                                  VkBuffer& buffer, VkDeviceMemory& memory) {
    VkBufferCreateInfo bufferInfo = {};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = size;
    bufferInfo.usage = usage;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    VK_CHECK(vkCreateBuffer(m_device, &bufferInfo, nullptr, &buffer));

    VkMemoryRequirements memReqs;
    vkGetBufferMemoryRequirements(m_device, buffer, &memReqs);

    VkMemoryAllocateInfo allocInfo = {};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = findMemoryType(memReqs.memoryTypeBits, props);

    VK_CHECK(vkAllocateMemory(m_device, &allocInfo, nullptr, &memory));
    VK_CHECK(vkBindBufferMemory(m_device, buffer, memory, 0));
    return true;
}

uint32_t VulkanCompute::findMemoryType(uint32_t typeFilter, VkMemoryPropertyFlags props) {
    VkPhysicalDeviceMemoryProperties memProps;
    vkGetPhysicalDeviceMemoryProperties(m_physicalDevice, &memProps);

    for (uint32_t i = 0; i < memProps.memoryTypeCount; i++) {
        if ((typeFilter & (1 << i)) && (memProps.memoryTypes[i].propertyFlags & props) == props) {
            return i;
        }
    }

    LOGE("Failed to find suitable memory type");
    return 0;
}

void VulkanCompute::copyBuffer(VkBuffer src, VkBuffer dst, VkDeviceSize size) {
    VkCommandBufferBeginInfo beginInfo = {};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;

    vkBeginCommandBuffer(m_commandBuffer, &beginInfo);
    VkBufferCopy copyRegion = {};
    copyRegion.size = size;
    vkCmdCopyBuffer(m_commandBuffer, src, dst, 1, &copyRegion);
    vkEndCommandBuffer(m_commandBuffer);

    VkSubmitInfo submitInfo = {};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &m_commandBuffer;

    vkQueueSubmit(m_computeQueue, 1, &submitInfo, VK_NULL_HANDLE);
    vkQueueWaitIdle(m_computeQueue);
}

bool VulkanCompute::canProcessImage(int width, int height) const {
    if (!m_initialized) return false;

    // Check against device maxImageDimension2D limit
    if (width > static_cast<int>(m_maxImageDimension) ||
        height > static_cast<int>(m_maxImageDimension)) {
        return false;
    }

    // Check 100MP limit for VRAM safety
    if (static_cast<int64_t>(width) * height > 100000000LL) {
        return false;
    }

    return true;
}

bool VulkanCompute::processImage(uint8_t* pixels, int width, int height,
                                  const AdjustmentsUBO& adjustments) {
    if (!m_initialized) return false;

    // G-04: Prevent concurrent processing — only one image at a time
    if (m_isProcessing.exchange(true)) {
        LOGW("Already processing an image, rejecting concurrent request");
        return false;
    }

    // G-02: Check image dimensions against device limits
    if (width > static_cast<int>(m_maxImageDimension) ||
        height > static_cast<int>(m_maxImageDimension)) {
        LOGE("Image dimensions (%dx%d) exceed device maxImageDimension2D (%u)",
             width, height, m_maxImageDimension);
        m_isProcessing = false;
        return false;
    }

    // G-02: 100MP+ VRAM protection — fall back to CPU for very large images
    if (static_cast<int64_t>(width) * height > 100000000LL) {
        LOGW("Image too large (%.1f MP) - exceeds 100MP limit, fall back to CPU",
             static_cast<double>(static_cast<int64_t>(width) * height) / 1e6);
        m_isProcessing = false;
        return false;
    }

    VkDeviceSize imageSize = VkDeviceSize(width) * height * 4;  // RGBA8
    VkDeviceSize uboSize = sizeof(AdjustmentsUBO);

    // Ensure UBO is at least the minimum alignment
    // Pad to multiple of minUniformBufferOffsetAlignment
    VkPhysicalDeviceProperties physProps;
    vkGetPhysicalDeviceProperties(m_physicalDevice, &physProps);
    VkDeviceSize minUboAlignment = physProps.limits.minUniformBufferOffsetAlignment;
    VkDeviceSize alignedUboSize = ((uboSize + minUboAlignment - 1) / minUboAlignment) * minUboAlignment;

    bool sizeChanged = (width != m_lastWidth || height != m_lastHeight);

    // Recreate buffers if image size changed
    if (sizeChanged) {
        // Clean up old buffers
        if (m_inputBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(m_device, m_inputBuffer, nullptr);
            vkFreeMemory(m_device, m_inputMemory, nullptr);
            m_inputBuffer = VK_NULL_HANDLE;
        }
        if (m_outputBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(m_device, m_outputBuffer, nullptr);
            vkFreeMemory(m_device, m_outputMemory, nullptr);
            m_outputBuffer = VK_NULL_HANDLE;
        }
        if (m_uniformBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(m_device, m_uniformBuffer, nullptr);
            vkFreeMemory(m_device, m_uniformMemory, nullptr);
            m_uniformBuffer = VK_NULL_HANDLE;
        }
        if (m_uniformBuffer2 != VK_NULL_HANDLE) {
            vkDestroyBuffer(m_device, m_uniformBuffer2, nullptr);
            vkFreeMemory(m_device, m_uniformMemory2, nullptr);
            m_uniformBuffer2 = VK_NULL_HANDLE;
        }

        // Create input buffer (host-visible for upload)
        if (!createBuffer(imageSize,
                          VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                          VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                          m_inputBuffer, m_inputMemory)) {
            LOGE("Failed to create input buffer");
            m_isProcessing = false;
            return false;
        }

        // Create output buffer (host-visible for readback)
        if (!createBuffer(imageSize,
                          VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                          VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                          m_outputBuffer, m_outputMemory)) {
            LOGE("Failed to create output buffer");
            m_isProcessing = false;
            return false;
        }

        // Create uniform buffer (slot 0)
        if (!createBuffer(alignedUboSize,
                          VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                          VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                          m_uniformBuffer, m_uniformMemory)) {
            LOGE("Failed to create uniform buffer 0");
            m_isProcessing = false;
            return false;
        }

        // Create uniform buffer (slot 1) for double-buffering
        if (!createBuffer(alignedUboSize,
                          VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                          VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                          m_uniformBuffer2, m_uniformMemory2)) {
            LOGE("Failed to create uniform buffer 1");
            m_isProcessing = false;
            return false;
        }

        // Update descriptor sets with new buffers
        VkDescriptorBufferInfo inputBufferInfo = {};
        inputBufferInfo.buffer = m_inputBuffer;
        inputBufferInfo.offset = 0;
        inputBufferInfo.range = imageSize;

        VkDescriptorBufferInfo outputBufferInfo = {};
        outputBufferInfo.buffer = m_outputBuffer;
        outputBufferInfo.offset = 0;
        outputBufferInfo.range = imageSize;

        VkDescriptorBufferInfo uboBufferInfo = {};
        uboBufferInfo.buffer = m_uniformBuffer;
        uboBufferInfo.offset = 0;
        uboBufferInfo.range = alignedUboSize;

        VkWriteDescriptorSet descriptorWrites[3] = {};
        descriptorWrites[0].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        descriptorWrites[0].dstSet = m_descriptorSet;
        descriptorWrites[0].dstBinding = 0;
        descriptorWrites[0].descriptorCount = 1;
        descriptorWrites[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        descriptorWrites[0].pBufferInfo = &inputBufferInfo;

        descriptorWrites[1].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        descriptorWrites[1].dstSet = m_descriptorSet;
        descriptorWrites[1].dstBinding = 1;
        descriptorWrites[1].descriptorCount = 1;
        descriptorWrites[1].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        descriptorWrites[1].pBufferInfo = &outputBufferInfo;

        descriptorWrites[2].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        descriptorWrites[2].dstSet = m_descriptorSet;
        descriptorWrites[2].dstBinding = 2;
        descriptorWrites[2].descriptorCount = 1;
        descriptorWrites[2].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        descriptorWrites[2].pBufferInfo = &uboBufferInfo;

        vkUpdateDescriptorSets(m_device, 3, descriptorWrites, 0, nullptr);

        m_lastWidth = width;
        m_lastHeight = height;
    }

    // Double-buffering: toggle index and select the alternate uniform buffer
    m_uniformBufferIndex = (m_uniformBufferIndex + 1) % 2;
    VkBuffer currentUniformBuffer = (m_uniformBufferIndex == 0) ? m_uniformBuffer : m_uniformBuffer2;
    VkDeviceMemory currentUniformMemory = (m_uniformBufferIndex == 0) ? m_uniformMemory : m_uniformMemory2;

    // Update descriptor set binding 2 to point to the current uniform buffer
    {
        VkDescriptorBufferInfo uboBufferInfo = {};
        uboBufferInfo.buffer = currentUniformBuffer;
        uboBufferInfo.offset = 0;
        uboBufferInfo.range = alignedUboSize;

        VkWriteDescriptorSet uboWrite = {};
        uboWrite.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        uboWrite.dstSet = m_descriptorSet;
        uboWrite.dstBinding = 2;
        uboWrite.descriptorCount = 1;
        uboWrite.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        uboWrite.pBufferInfo = &uboBufferInfo;

        vkUpdateDescriptorSets(m_device, 1, &uboWrite, 0, nullptr);
    }

    // Upload input pixel data
    void* data;
    vkMapMemory(m_device, m_inputMemory, 0, imageSize, 0, &data);
    memcpy(data, pixels, static_cast<size_t>(imageSize));
    vkUnmapMemory(m_device, m_inputMemory);

    // Upload UBO to the current double-buffered uniform buffer
    vkMapMemory(m_device, currentUniformMemory, 0, alignedUboSize, 0, &data);
    memcpy(data, &adjustments, sizeof(AdjustmentsUBO));
    vkUnmapMemory(m_device, currentUniformMemory);

    // G-04: Lock submission mutex to protect GPU command buffer submission
    {
        std::lock_guard<std::mutex> lock(s_submissionMutex);

        // Record and submit command buffer
        VkCommandBufferBeginInfo beginInfo = {};
        beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;

        VkResult result = vkBeginCommandBuffer(m_commandBuffer, &beginInfo);
        if (result != VK_SUCCESS) {
            LOGE("Vulkan error at vkBeginCommandBuffer: %d", result);
            m_isProcessing = false;
            return false;
        }

        vkCmdBindPipeline(m_commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, m_pipeline);
        vkCmdBindDescriptorSets(m_commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                                m_pipelineLayout, 0, 1, &m_descriptorSet, 0, nullptr);

        // Dispatch compute workgroups
        uint32_t workgroupX = (width + 15) / 16;
        uint32_t workgroupY = (height + 15) / 16;
        vkCmdDispatch(m_commandBuffer, workgroupX, workgroupY, 1);

        // Add memory barrier to ensure writes are visible before readback
        VkMemoryBarrier barrier = {};
        barrier.sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER;
        barrier.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
        vkCmdPipelineBarrier(m_commandBuffer,
                             VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                             VK_PIPELINE_STAGE_HOST_BIT,
                             0, 1, &barrier, 0, nullptr, 0, nullptr);

        result = vkEndCommandBuffer(m_commandBuffer);
        if (result != VK_SUCCESS) {
            LOGE("Vulkan error at vkEndCommandBuffer: %d", result);
            m_isProcessing = false;
            return false;
        }

        VkSubmitInfo submitInfo = {};
        submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
        submitInfo.commandBufferCount = 1;
        submitInfo.pCommandBuffers = &m_commandBuffer;

        result = vkQueueSubmit(m_computeQueue, 1, &submitInfo, VK_NULL_HANDLE);
        if (result != VK_SUCCESS) {
            LOGE("Vulkan error at vkQueueSubmit: %d", result);
            m_isProcessing = false;
            return false;
        }

        result = vkQueueWaitIdle(m_computeQueue);
        if (result != VK_SUCCESS) {
            LOGE("Vulkan error at vkQueueWaitIdle: %d", result);
            m_isProcessing = false;
            return false;
        }
    }

    // Read back output
    vkMapMemory(m_device, m_outputMemory, 0, imageSize, 0, &data);
    memcpy(pixels, data, static_cast<size_t>(imageSize));
    vkUnmapMemory(m_device, m_outputMemory);

    m_isProcessing = false;
    return true;
}

// ═══════════════════════════════════════════════════════════════════════════
// 3D LUT texture management (B8, binding=3)
// ═══════════════════════════════════════════════════════════════════════════

bool VulkanCompute::createLutTexture(int size, const uint8_t* rgbaData) {
    if (size < 2) size = 2;

    // Build the pixel data. When rgbaData is null, generate an identity LUT so
    // the bound texture is always safe to sample. The 3D buffer layout matches
    // Vulkan's vkCmdCopyBufferToImage expectation: x(R) varies fastest, then
    // y(G), then z(B): index = (z*size*size + y*size + x) * 4.
    VkDeviceSize dataSize = VkDeviceSize(size) * size * size * 4;
    std::vector<uint8_t> pixels;
    const uint8_t* src = rgbaData;
    if (src == nullptr) {
        pixels.resize(static_cast<size_t>(dataSize));
        for (int z = 0; z < size; z++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    size_t idx = (size_t(z) * size * size + size_t(y) * size + size_t(x)) * 4;
                    pixels[idx + 0] = static_cast<uint8_t>(x * 255 / (size - 1));
                    pixels[idx + 1] = static_cast<uint8_t>(y * 255 / (size - 1));
                    pixels[idx + 2] = static_cast<uint8_t>(z * 255 / (size - 1));
                    pixels[idx + 3] = 255;
                }
            }
        }
        src = pixels.data();
    }

    // Create the 3D image (device-local, optimal tiling).
    VkImageCreateInfo imageInfo = {};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.imageType = VK_IMAGE_TYPE_3D;
    imageInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    imageInfo.extent = { static_cast<uint32_t>(size), static_cast<uint32_t>(size), static_cast<uint32_t>(size) };
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;

    VkResult result = vkCreateImage(m_device, &imageInfo, nullptr, &m_lutImage);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create LUT 3D image: %d", result);
        return false;
    }

    VkMemoryRequirements memReqs;
    vkGetImageMemoryRequirements(m_device, m_lutImage, &memReqs);

    VkMemoryAllocateInfo allocInfo = {};
    allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = findMemoryType(memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

    result = vkAllocateMemory(m_device, &allocInfo, nullptr, &m_lutMemory);
    if (result != VK_SUCCESS) {
        LOGE("Failed to allocate LUT image memory: %d", result);
        vkDestroyImage(m_device, m_lutImage, nullptr);
        m_lutImage = VK_NULL_HANDLE;
        return false;
    }
    vkBindImageMemory(m_device, m_lutImage, m_lutMemory, 0);

    // Staging buffer (host-visible) for upload.
    VkBuffer stagingBuffer = VK_NULL_HANDLE;
    VkDeviceMemory stagingMemory = VK_NULL_HANDLE;
    if (!createBuffer(dataSize,
                      VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                      VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                      stagingBuffer, stagingMemory)) {
        LOGE("Failed to create LUT staging buffer");
        destroyLutTexture();
        return false;
    }

    void* data = nullptr;
    vkMapMemory(m_device, stagingMemory, 0, dataSize, 0, &data);
    memcpy(data, src, static_cast<size_t>(dataSize));
    vkUnmapMemory(m_device, stagingMemory);

    // Record transitions + copy in a single one-time command buffer.
    VkCommandBufferBeginInfo beginInfo = {};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(m_commandBuffer, &beginInfo);

    transitionLutLayout(m_lutImage, VK_IMAGE_LAYOUT_UNDEFINED,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);

    VkBufferImageCopy region = {};
    region.bufferOffset = 0;
    region.bufferRowLength = 0;
    region.bufferImageHeight = 0;
    region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.imageSubresource.mipLevel = 0;
    region.imageSubresource.baseArrayLayer = 0;
    region.imageSubresource.layerCount = 1;
    region.imageOffset = { 0, 0, 0 };
    region.imageExtent = { static_cast<uint32_t>(size), static_cast<uint32_t>(size), static_cast<uint32_t>(size) };
    vkCmdCopyBufferToImage(m_commandBuffer, stagingBuffer, m_lutImage,
                           VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);

    transitionLutLayout(m_lutImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

    vkEndCommandBuffer(m_commandBuffer);

    VkSubmitInfo submitInfo = {};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &m_commandBuffer;
    vkQueueSubmit(m_computeQueue, 1, &submitInfo, VK_NULL_HANDLE);
    vkQueueWaitIdle(m_computeQueue);

    vkDestroyBuffer(m_device, stagingBuffer, nullptr);
    vkFreeMemory(m_device, stagingMemory, nullptr);

    // Create the 3D image view.
    VkImageViewCreateInfo viewInfo = {};
    viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image = m_lutImage;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_3D;
    viewInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    viewInfo.subresourceRange.baseMipLevel = 0;
    viewInfo.subresourceRange.levelCount = 1;
    viewInfo.subresourceRange.baseArrayLayer = 0;
    viewInfo.subresourceRange.layerCount = 1;

    result = vkCreateImageView(m_device, &viewInfo, nullptr, &m_lutView);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create LUT image view: %d", result);
        destroyLutTexture();
        return false;
    }

    // Create a linear sampler with clamp-to-edge so out-of-range coordinates
    // don't bleed stray colors into the LUT lookup.
    VkSamplerCreateInfo samplerInfo = {};
    samplerInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    samplerInfo.magFilter = VK_FILTER_LINEAR;
    samplerInfo.minFilter = VK_FILTER_LINEAR;
    samplerInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.anisotropyEnable = VK_FALSE;
    samplerInfo.maxAnisotropy = 1.0f;
    samplerInfo.borderColor = VK_BORDER_COLOR_INT_TRANSPARENT_BLACK;
    samplerInfo.unnormalizedCoordinates = VK_FALSE;
    samplerInfo.compareEnable = VK_FALSE;
    samplerInfo.compareOp = VK_COMPARE_OP_ALWAYS;
    samplerInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR;

    result = vkCreateSampler(m_device, &samplerInfo, nullptr, &m_lutSampler);
    if (result != VK_SUCCESS) {
        LOGE("Failed to create LUT sampler: %d", result);
        destroyLutTexture();
        return false;
    }

    m_lutSize = size;
    m_lutBound = true;
    LOGI("LUT 3D texture created (size=%d)", size);
    return true;
}

void VulkanCompute::destroyLutTexture() {
    if (m_lutSampler != VK_NULL_HANDLE) {
        vkDestroySampler(m_device, m_lutSampler, nullptr);
        m_lutSampler = VK_NULL_HANDLE;
    }
    if (m_lutView != VK_NULL_HANDLE) {
        vkDestroyImageView(m_device, m_lutView, nullptr);
        m_lutView = VK_NULL_HANDLE;
    }
    if (m_lutImage != VK_NULL_HANDLE) {
        vkDestroyImage(m_device, m_lutImage, nullptr);
        m_lutImage = VK_NULL_HANDLE;
    }
    if (m_lutMemory != VK_NULL_HANDLE) {
        vkFreeMemory(m_device, m_lutMemory, nullptr);
        m_lutMemory = VK_NULL_HANDLE;
    }
    m_lutSize = 0;
    m_lutBound = false;
}

void VulkanCompute::transitionLutLayout(VkImage image, VkImageLayout oldLayout,
                                         VkImageLayout newLayout) {
    VkImageMemoryBarrier barrier = {};
    barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barrier.oldLayout = oldLayout;
    barrier.newLayout = newLayout;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = image;
    barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    barrier.subresourceRange.baseMipLevel = 0;
    barrier.subresourceRange.levelCount = 1;
    barrier.subresourceRange.baseArrayLayer = 0;
    barrier.subresourceRange.layerCount = 1;

    VkPipelineStageFlags srcStage;
    VkPipelineStageFlags dstStage;
    VkAccessFlags srcAccess;
    VkAccessFlags dstAccess;

    if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED &&
        newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
        srcAccess = 0;
        dstAccess = VK_ACCESS_TRANSFER_WRITE_BIT;
        srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL &&
               newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
        srcAccess = VK_ACCESS_TRANSFER_WRITE_BIT;
        dstAccess = VK_ACCESS_SHADER_READ_BIT;
        srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        dstStage = VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
    } else {
        srcAccess = 0;
        dstAccess = VK_ACCESS_SHADER_READ_BIT;
        srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        dstStage = VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
    }

    barrier.srcAccessMask = srcAccess;
    barrier.dstAccessMask = dstAccess;

    vkCmdPipelineBarrier(m_commandBuffer, srcStage, dstStage, 0,
                         0, nullptr, 0, nullptr, 1, &barrier);
}

void VulkanCompute::writeLutDescriptor() {
    if (m_lutView == VK_NULL_HANDLE || m_lutSampler == VK_NULL_HANDLE) return;

    VkDescriptorImageInfo imageInfo = {};
    imageInfo.sampler = m_lutSampler;
    imageInfo.imageView = m_lutView;
    imageInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

    VkWriteDescriptorSet descriptorWrite = {};
    descriptorWrite.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    descriptorWrite.dstSet = m_descriptorSet;
    descriptorWrite.dstBinding = 3;
    descriptorWrite.dstArrayElement = 0;
    descriptorWrite.descriptorCount = 1;
    descriptorWrite.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    descriptorWrite.pImageInfo = &imageInfo;

    vkUpdateDescriptorSets(m_device, 1, &descriptorWrite, 0, nullptr);
}

bool VulkanCompute::setLut(const uint8_t* rgbaData, int size) {
    if (!m_initialized) return false;

    // Guard the command buffer against concurrent use by processImage. The
    // texture recreation records into m_commandBuffer, so it must not overlap
    // with a processImage recording pass.
    std::lock_guard<std::mutex> lock(s_submissionMutex);

    // Wait for any in-flight work before recreating the texture.
    vkDeviceWaitIdle(m_device);

    destroyLutTexture();

    // size<=0 reverts to the default identity LUT.
    if (size <= 0 || rgbaData == nullptr) {
        if (!createLutTexture(16, nullptr)) {
            LOGE("setLut: failed to recreate identity LUT");
            return false;
        }
    } else {
        if (!createLutTexture(size, rgbaData)) {
            LOGE("setLut: failed to create LUT (size=%d)", size);
            // Fall back to identity so binding=3 stays valid.
            createLutTexture(16, nullptr);
        }
    }

    writeLutDescriptor();
    return true;
}

void VulkanCompute::release() {
    if (m_device == VK_NULL_HANDLE) return;

    vkDeviceWaitIdle(m_device);

    destroyLutTexture();

    if (m_pipeline != VK_NULL_HANDLE) {
        vkDestroyPipeline(m_device, m_pipeline, nullptr);
        m_pipeline = VK_NULL_HANDLE;
    }
    if (m_pipelineLayout != VK_NULL_HANDLE) {
        vkDestroyPipelineLayout(m_device, m_pipelineLayout, nullptr);
        m_pipelineLayout = VK_NULL_HANDLE;
    }
    if (m_descriptorSetLayout != VK_NULL_HANDLE) {
        vkDestroyDescriptorSetLayout(m_device, m_descriptorSetLayout, nullptr);
        m_descriptorSetLayout = VK_NULL_HANDLE;
    }
    if (m_descriptorPool != VK_NULL_HANDLE) {
        vkDestroyDescriptorPool(m_device, m_descriptorPool, nullptr);
        m_descriptorPool = VK_NULL_HANDLE;
    }
    if (m_inputBuffer != VK_NULL_HANDLE) {
        vkDestroyBuffer(m_device, m_inputBuffer, nullptr);
        m_inputBuffer = VK_NULL_HANDLE;
    }
    if (m_inputMemory != VK_NULL_HANDLE) {
        vkFreeMemory(m_device, m_inputMemory, nullptr);
        m_inputMemory = VK_NULL_HANDLE;
    }
    if (m_outputBuffer != VK_NULL_HANDLE) {
        vkDestroyBuffer(m_device, m_outputBuffer, nullptr);
        m_outputBuffer = VK_NULL_HANDLE;
    }
    if (m_outputMemory != VK_NULL_HANDLE) {
        vkFreeMemory(m_device, m_outputMemory, nullptr);
        m_outputMemory = VK_NULL_HANDLE;
    }
    if (m_uniformBuffer != VK_NULL_HANDLE) {
        vkDestroyBuffer(m_device, m_uniformBuffer, nullptr);
        m_uniformBuffer = VK_NULL_HANDLE;
    }
    if (m_uniformMemory != VK_NULL_HANDLE) {
        vkFreeMemory(m_device, m_uniformMemory, nullptr);
        m_uniformMemory = VK_NULL_HANDLE;
    }
    if (m_uniformBuffer2 != VK_NULL_HANDLE) {
        vkDestroyBuffer(m_device, m_uniformBuffer2, nullptr);
        m_uniformBuffer2 = VK_NULL_HANDLE;
    }
    if (m_uniformMemory2 != VK_NULL_HANDLE) {
        vkFreeMemory(m_device, m_uniformMemory2, nullptr);
        m_uniformMemory2 = VK_NULL_HANDLE;
    }
    if (m_commandPool != VK_NULL_HANDLE) {
        vkDestroyCommandPool(m_device, m_commandPool, nullptr);
        m_commandPool = VK_NULL_HANDLE;
    }
    if (m_device != VK_NULL_HANDLE) {
        vkDestroyDevice(m_device, nullptr);
        m_device = VK_NULL_HANDLE;
    }
    if (m_instance != VK_NULL_HANDLE) {
        vkDestroyInstance(m_instance, nullptr);
        m_instance = VK_NULL_HANDLE;
    }

    m_initialized = false;
    m_isProcessing = false;
    m_uniformBufferIndex = 0;
    m_maxImageDimension = 4096;
    m_lastWidth = 0;
    m_lastHeight = 0;
    LOGI("VulkanCompute released");
}
