#version 300 es
precision highp float;

// ── Main Image Adjustments Fragment Shader ────────────────────────────
// Handles: exposure, brightness, contrast, white balance,
//          highlights/shadows, vibrance, saturation, clarity, tone curve
// Expects input as linear RGB (caller handles sRGB→linear conversion).

// Input image texture
uniform sampler2D uTexture;   // Source image (linear RGB)
uniform vec2 uResolution;     // Image dimensions in pixels (width, height)

// ── Exposure & Brightness ─────────────────────────────────────────────
uniform float uExposure;      // -5.0 .. 5.0, stops of exposure compensation
uniform float uBrightness;    // -1.0 .. 1.0, filmic brightness (midtone emphasis)

// ── White Balance ─────────────────────────────────────────────────────
uniform float uTemperature;   // 2000 .. 15000, color temperature in Kelvin
uniform float uTint;          // -1.0 .. 1.0, green-magenta tint offset

// ── Tonal Controls ────────────────────────────────────────────────────
uniform float uContrast;      // -1.0 .. 1.0, perceptual contrast
uniform float uHighlights;    // -1.0 .. 1.0, highlight compression/expansion
uniform float uShadows;       // -1.0 .. 1.0, shadow lift/crush
uniform float uWhites;        // -1.0 .. 1.0, white point adjustment
uniform float uBlacks;        // -1.0 .. 1.0, black point adjustment

// ── Color ─────────────────────────────────────────────────────────────
uniform float uSaturation;    // -1.0 .. 1.0, global saturation shift
uniform float uVibrance;      // -1.0 .. 1.0, smart saturation (less effect on already-saturated)

// ── Detail ────────────────────────────────────────────────────────────
uniform float uClarity;       // -1.0 .. 1.0, local contrast (midtone clarity)
uniform float uCentre;        // -1.0 .. 1.0, midtone emphasis / centre weight

// ── Tone Curve ────────────────────────────────────────────────────────
// 10 control points packed as 5 vec4s: (x0,y0,x1,y1) per vec4
uniform vec4 uCurvePoints[5];

// Interpolated from vertex shader
in vec2 vTexCoord;
out vec4 fragColor;

// ── Constants ──────────────────────────────────────────────────────────
const float PI = 3.14159265358979;
const float EPS = 1e-6;

// ── sRGB <-> Linear ───────────────────────────────────────────────────

float srgb_to_linear(float v) {
    return (v <= 0.04045) ? (v / 12.92) : pow((v + 0.055) / 1.055, 2.4);
}

vec3 srgb_to_linear(vec3 c) {
    return vec3(srgb_to_linear(c.r), srgb_to_linear(c.g), srgb_to_linear(c.b));
}

float linear_to_srgb(float v) {
    return (v <= 0.0031308) ? (v * 12.92) : (1.055 * pow(v, 1.0 / 2.4) - 0.055);
}

vec3 linear_to_srgb(vec3 c) {
    return vec3(linear_to_srgb(c.r), linear_to_srgb(c.g), linear_to_srgb(c.b));
}

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

float whites_mask(float luma) {
    return smoothstep_custom(0.6, 1.0, luma);
}

float blacks_mask(float luma) {
    return 1.0 - smoothstep_custom(0.0, 0.4, luma);
}

float midtones_mask(float luma) {
    return smoothstep_custom(0.2, 0.4, luma) * (1.0 - smoothstep_custom(0.6, 0.8, luma));
}

// ── Cubic Hermite Tone Curve ──────────────────────────────────────────

struct CurvePoint {
    float x;
    float y;
};

CurvePoint unpack_curve_point(int idx) {
    int vecIdx = idx / 2;
    int compIdx = idx % 2;
    vec4 v = uCurvePoints[vecIdx];
    if (compIdx == 0) {
        return CurvePoint(v.x, v.y);
    } else {
        return CurvePoint(v.z, v.w);
    }
}

float apply_curve(float input_val) {
    const int N = 10;
    CurvePoint pts[N];
    for (int i = 0; i < N; i++) {
        pts[i] = unpack_curve_point(i);
    }

    if (input_val <= pts[0].x) return pts[0].y;
    if (input_val >= pts[N - 1].x) return pts[N - 1].y;

    int idx = 0;
    for (int i = 0; i < N - 1; i++) {
        if (input_val >= pts[i].x && input_val <= pts[i + 1].x) {
            idx = i;
            break;
        }
    }

    float dx = pts[idx + 1].x - pts[idx].x;
    if (dx < EPS) return pts[idx].y;

    float t = (input_val - pts[idx].x) / dx;

    float m0, m1;
    if (idx == 0) {
        m0 = (pts[1].y - pts[0].y) / dx;
    } else {
        m0 = ((pts[idx + 1].y - pts[idx - 1].y) / (pts[idx + 1].x - pts[idx - 1].x)) * dx * 0.5;
    }
    if (idx + 1 >= N - 1) {
        m1 = (pts[N - 1].y - pts[N - 2].y) / dx;
    } else {
        m1 = ((pts[idx + 2].y - pts[idx].y) / (pts[idx + 2].x - pts[idx].x)) * dx * 0.5;
    }

    float t2 = t * t;
    float t3 = t2 * t;

    float h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
    float h10 = t3 - 2.0 * t2 + t;
    float h01 = -2.0 * t3 + 3.0 * t2;
    float h11 = t3 - t2;

    return h00 * pts[idx].y + h10 * m0 + h01 * pts[idx + 1].y + h11 * m1;
}

// ── 1. Linear Exposure ────────────────────────────────────────────────

vec3 apply_linear_exposure(vec3 color, float exposure) {
    return color * pow(2.0, exposure);
}

// ── 2. Filmic Brightness ──────────────────────────────────────────────

vec3 apply_filmic_brightness(vec3 color, float brightness) {
    float b = brightness * 2.0;
    vec3 numerator = color * (1.0 + b);
    vec3 denominator = 1.0 + abs(b) * color;
    return numerator / max(denominator, vec3(EPS));
}

// ── 3. White Balance ──────────────────────────────────────────────────

vec3 temperature_tint_multipliers(float temperature, float tint) {
    float temp = clamp(temperature, 2000.0, 15000.0) / 100.0;

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

vec3 apply_white_balance(vec3 color, float temperature, float tint) {
    return color * temperature_tint_multipliers(temperature, tint);
}

// ── 4. Highlights Adjustment ──────────────────────────────────────────

vec3 apply_highlights(vec3 color, float highlights) {
    if (abs(highlights) < EPS) return color;

    float luma = get_luma(color);
    float mask = highlights_mask(luma);

    vec3 result = color;
    if (highlights < 0.0) {
        vec3 compressed = 1.0 - pow(1.0 - color, vec3(1.0 - highlights));
        result = mix(color, compressed, mask);
    } else {
        vec3 expanded = pow(color, vec3(1.0 / (1.0 + highlights)));
        result = mix(color, expanded, mask);
    }

    return result;
}

// ── 5. Tonal Adjustments ──────────────────────────────────────────────

vec3 apply_tonal(vec3 color, float contrast, float shadows, float whites, float blacks) {
    float luma = get_luma(color);
    vec3 result = color;

    // Contrast around perceptual middle gray
    if (abs(contrast) > EPS) {
        float contrastPow = 1.0 + contrast;
        vec3 mid = vec3(0.18);
        result = mid + (result - mid) * contrastPow;
    }

    // Shadows lift/crush
    if (abs(shadows) > EPS) {
        float sm = shadows_mask(luma);
        result += shadows * sm * 0.3;
    }

    // Whites
    if (abs(whites) > EPS) {
        float wm = whites_mask(luma);
        result += whites * wm * 0.25;
    }

    // Blacks
    if (abs(blacks) > EPS) {
        float bm = blacks_mask(luma);
        result += blacks * bm * 0.25;
    }

    return result;
}

// ── 6. RGB <-> HSV ────────────────────────────────────────────────────

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

// ── 7. Saturation + Vibrance ──────────────────────────────────────────

vec3 apply_creative_color(vec3 color, float saturation, float vibrance) {
    vec3 hsv = rgb_to_hsv(color);
    float currentSat = hsv.y;

    // Skin tone protection
    float skinProtection = 1.0;
    if (hsv.x > 10.0 && hsv.x < 50.0 && currentSat < 0.5 && hsv.z > 0.2) {
        skinProtection = 0.5;
    }

    // Vibrance: less effect on already-saturated colors
    float vibranceAmount = vibrance * (1.0 - currentSat) * skinProtection;
    hsv.y = clamp(currentSat + vibranceAmount * 1.5, 0.0, 1.0);

    // Saturation
    hsv.y = clamp(hsv.y + saturation, 0.0, 1.0);

    return hsv_to_rgb(hsv);
}

// ── 8. Clarity (Local Contrast) ───────────────────────────────────────

vec3 sample_blur(vec2 uv, float radius) {
    vec2 texel = 1.0 / uResolution;
    vec3 sum = vec3(0.0);
    float count = 0.0;

    for (int x = -3; x <= 3; x++) {
        for (int y = -3; y <= 3; y++) {
            vec2 offset = vec2(float(x), float(y)) * texel * radius;
            sum += texture(uTexture, uv + offset).rgb;
            count += 1.0;
        }
    }

    return sum / count;
}

vec3 apply_clarity(vec3 color, vec2 uv) {
    if (abs(uClarity) < EPS) return color;

    vec3 blurred = sample_blur(uv, 5.0);
    vec3 highPass = color - blurred;
    return color + highPass * uClarity * 2.0;
}

// ── 9. Centre (Midtone Emphasis) ──────────────────────────────────────

vec3 apply_centre(vec3 color) {
    if (abs(uCentre) < EPS) return color;
    float luma = get_luma(color);
    float shift = uCentre * 0.3;
    float target = clamp(luma + shift, 0.0, 1.0);
    float factor = (luma > EPS) ? target / luma : 1.0;
    return color * factor;
}

// ── 10. Tone Curve ────────────────────────────────────────────────────

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

// ── Hash for dither ───────────────────────────────────────────────────

float hash_dither(float n) {
    uint s = floatBitsToUint(n);
    s = s * 1597334677u;
    s = (s >> 16u) | s;
    return float(s & 0xFFFFu) / 65536.0;
}

float gradient_noise(float x, float y) {
    float ix = floor(x);
    float iy = floor(y);
    float fx = x - ix;
    float fy = y - iy;

    float ux = fx * fx * (3.0 - 2.0 * fx);
    float uy = fy * fy * (3.0 - 2.0 * fy);

    float a = hash_dither(ix + iy * 57.0);
    float b = hash_dither(ix + 1.0 + iy * 57.0);
    float c = hash_dither(ix + (iy + 1.0) * 57.0);
    float d = hash_dither(ix + 1.0 + (iy + 1.0) * 57.0);

    return a + (b - a) * ux + (c - a) * uy + (a - b - c + d) * ux * uy;
}

// ═══════════════════════════════════════════════════════════════════════
// ── MAIN ─────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

void main() {
    vec2 uv = vTexCoord;

    // Sample input (assumed sRGB, convert to linear)
    vec3 original = texture(uTexture, uv).rgb;
    vec3 color = srgb_to_linear(original);

    // Step 1: Exposure
    color = apply_linear_exposure(color, uExposure);

    // Step 2: Filmic brightness
    color = apply_filmic_brightness(color, uBrightness);

    // Step 3: White balance
    color = apply_white_balance(color, uTemperature, uTint);

    // Step 4: Highlights
    color = apply_highlights(color, uHighlights);

    // Step 5: Tonal (contrast / shadows / whites / blacks)
    color = apply_tonal(color, uContrast, uShadows, uWhites, uBlacks);

    // Step 6: Centre
    color = apply_centre(color);

    // Step 7: Saturation + Vibrance
    color = apply_creative_color(color, uSaturation, uVibrance);

    // Step 8: Clarity (local contrast)
    color = apply_clarity(color, uv);

    // Step 9: Tone curve
    color = apply_tone_curve(color);

    // Step 10: Linear to sRGB
    color = linear_to_srgb(color);

    // Step 11: Dither to avoid banding
    color += (gradient_noise(uv.x, uv.y) - 0.5) / 255.0;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
