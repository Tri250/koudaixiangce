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
    float pad0[2];

    // ── Exposure & Brightness ────────────────────────────────
    float exposure;
    float brightness;
    float pad1[2];

    // ── White Balance ────────────────────────────────────────
    float temperature;
    float tint;
    float pad2[2];

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
    float pad4[2];

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
    float cdlShadowsR;
    float cdlShadowsG;
    float cdlShadowsB;
    float cdlMidtonesR;
    float cdlMidtonesG;
    float cdlMidtonesB;
    float cdlHighlightsR;
    float cdlHighlightsG;
    float cdlHighlightsB;
    float pad5[3];

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
    float pad8[2];
} adj;

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

// ── 8. Clarity (simplified - no texture sampling in compute) ─────────────
vec3 apply_clarity(vec3 color, float clarity) {
    if (abs(clarity) < EPS) return color;
    // Without texture sampling in compute, we approximate clarity
    // as a midtone contrast adjustment
    float luma = get_luma(color);
    float mask = midtones_mask(luma);
    vec3 mid = vec3(luma);
    vec3 result = mid + (color - mid) * (1.0 + clarity * 2.0);
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

// ── 10. Tone Curve ──────────────────────────────────────────────────────
vec2 unpack_curve_point(int idx) {
    int vecIdx = idx / 2;
    int compIdx = idx % 2;
    vec4 v = adj.curvePoints[vecIdx];
    if (compIdx == 0) return v.xy;
    else return v.zw;
}

float apply_curve(float input_val) {
    const int N = 10;
    float xs[N]; float ys[N];
    for (int i = 0; i < N; i++) {
        vec2 pt = unpack_curve_point(i);
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

vec3 apply_tone_curve(vec3 color) {
    float luma = get_luma(color);
    float curvedLuma = apply_curve(luma);
    float lumaRatio = (luma > EPS) ? curvedLuma / luma : 1.0;
    vec3 result = color * lumaRatio;
    result.r = apply_curve(result.r);
    result.g = apply_curve(result.g);
    result.b = apply_curve(result.b);
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

// ── 15. ACES Tone Mapping ───────────────────────────────────────────────
float aces_rrt(float x) {
    const float a = 0.0245786, b = 0.0000907, c = 0.983729, d = 0.4329510, e = 0.238081;
    return max((x * (a*x + b)) / (x * (c*x + d) + e), 0.0);
}

vec3 apply_aces2(vec3 color) {
    // Simplified ACES - apply RRT directly in sRGB
    vec3 rrt;
    rrt.r = aces_rrt(max(color.r, 0.0));
    rrt.g = aces_rrt(max(color.g, 0.0));
    rrt.b = aces_rrt(max(color.b, 0.0));
    return clamp(rrt, 0.0, 1.0);
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

// ── Sharpening (unsharp mask with local neighborhood) ───────────────────
vec3 apply_sharpen(ivec2 coord, vec3 color, float sharpness, float radius, int w, int h) {
    if (sharpness < EPS) return color;

    // Collect local blur (3x3 box blur as approximation for unsharp mask)
    vec3 sum = vec3(0.0);
    float count = 0.0;
    int r = int(max(radius, 0.5));
    for (int dy = -r; dy <= r; dy++) {
        for (int dx = -r; dx <= r; dx++) {
            ivec2 nc = coord + ivec2(dx, dy);
            nc.x = clamp(nc.x, 0, w - 1);
            nc.y = clamp(nc.y, 0, h - 1);
            uint idx = nc.y * uint(w) + nc.x;
            vec4 neighbor = unpackRGBA8(inData[idx]);
            sum += neighbor.rgb;
            count += 1.0;
        }
    }
    vec3 blurred = sum / max(count, 1.0);
    vec3 highPass = color - blurred;
    vec3 result = color + highPass * sharpness;

    // Luminance-weighted sharpening
    float lumaOrig = get_luma(color);
    float lumaResult = get_luma(result);
    vec3 chromaOrig = color - vec3(lumaOrig);
    vec3 chromaResult = result - vec3(lumaResult);
    vec3 chromaBlended = mix(chromaOrig, chromaResult, 0.2);
    return vec3(lumaResult) + chromaBlended;
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

    // Step 1: Exposure
    color = apply_exposure(color, adj.exposure);

    // Step 2: Filmic brightness
    color = apply_brightness(color, adj.brightness);

    // Step 3: White balance
    color = apply_white_balance(color, adj.temperature, adj.tint);

    // Step 4: Highlights
    color = apply_highlights(color, adj.highlights);

    // Step 5: Tonal (contrast / shadows / whites / blacks)
    color = apply_tonal(color, adj.contrast, adj.shadows, adj.whites, adj.blacks);

    // Step 6: Centre
    color = apply_centre(color, adj.centre);

    // Step 7: Saturation + Vibrance
    color = apply_creative_color(color, adj.saturation, adj.vibrance);

    // Step 8: Clarity
    color = apply_clarity(color, adj.clarity);

    // Step 9: HSL Panel
    color = apply_hsl_panel(color, adj.hslRed, adj.hslOrange, adj.hslYellow, adj.hslGreen,
                            adj.hslAqua, adj.hslBlue, adj.hslPurple, adj.hslMagenta);

    // Step 10: Tone curve
    color = apply_tone_curve(color);

    // Step 11: Color Grading + CDL
    color = apply_color_grading(color, adj.cgShadows, adj.cgMidtones, adj.cgHighlights,
                                adj.cgBlend, adj.cgBalance,
                                adj.cdlShadowsR, adj.cdlShadowsG, adj.cdlShadowsB,
                                adj.cdlMidtonesR, adj.cdlMidtonesG, adj.cdlMidtonesB,
                                adj.cdlHighlightsR, adj.cdlHighlightsG, adj.cdlHighlightsB);

    // Step 12: Tone mapping
    if (adj.toneMapMode == 0) {
        color = apply_agx(color, adj.agxContrast, adj.agxPedestal);
    } else if (adj.toneMapMode == 1) {
        color = apply_aces2(color);
    } else if (adj.toneMapMode == 2) {
        color = apply_filmic(color);
    }

    // Step 13: Linear to sRGB
    color = linear_to_srgb3(clamp(color, 0.0, 1.0));

    // Step 14: Vignette
    color = apply_vignette(color, uv, adj.vignetteAmount, adj.vignetteMidpoint,
                           adj.vignetteRoundness, adj.vignetteFeather,
                           float(w), float(h));

    // Step 15: Grain
    color = apply_grain(color, uv, adj.grainAmount, adj.grainSize, adj.grainRoughness,
                        float(w), float(h));

    // Step 16: Sharpening (requires reading neighbors from input buffer)
    // We sharpen on the sRGB output; read original neighbors for unsharp mask
    vec3 sharpened = apply_sharpen(coord, color, adj.sharpness, adj.sharpenRadius, w, h);
    color = sharpened;

    // Step 17: Dither
    color += (gradient_noise(uv.x, uv.y) - 0.5) / 255.0;

    outData[idx] = packRGBA8(vec4(clamp(color, 0.0, 1.0), pixel.a));
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
        // Create descriptor pool
        VkDescriptorPoolSize poolSize = {};
        poolSize.type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        poolSize.descriptorCount = 2;

        VkDescriptorPoolSize uboPoolSize = {};
        uboPoolSize.type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        uboPoolSize.descriptorCount = 1;

        std::array<VkDescriptorPoolSize, 2> poolSizes = {poolSize, uboPoolSize};

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

    std::array<VkDescriptorSetLayoutBinding, 3> bindings = {inputBinding, outputBinding, uboBinding};

    VkDescriptorSetLayoutCreateInfo layoutInfo = {};
    layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    layoutInfo.bindingCount = static_cast<uint32_t>(bindings.size());
    layoutInfo.pBindings = bindings.data();

    VK_CHECK(vkCreateDescriptorSetLayout(m_device, &layoutInfo, nullptr, &m_descriptorSetLayout));
    LOGD("Descriptor set layout created");
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

void VulkanCompute::release() {
    if (m_device == VK_NULL_HANDLE) return;

    vkDeviceWaitIdle(m_device);

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
