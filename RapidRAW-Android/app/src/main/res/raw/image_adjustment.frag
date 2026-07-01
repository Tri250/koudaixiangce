#version 300 es
precision highp float;

// ── Input ──────────────────────────────────────────────────────────────
uniform sampler2D uTexture;
uniform vec2 uResolution;

// ── Adjustment Uniforms ────────────────────────────────────────────────
// Exposure & Brightness
uniform float uExposure;       // -5.0 .. 5.0
uniform float uBrightness;     // -1.0 .. 1.0

// White Balance
uniform float uTemperature;    // 2000 .. 15000
uniform float uTint;           // -1.0 .. 1.0

// Tonal
uniform float uContrast;       // -1.0 .. 1.0
uniform float uHighlights;     // -1.0 .. 1.0
uniform float uShadows;        // -1.0 .. 1.0
uniform float uWhites;         // -1.0 .. 1.0
uniform float uBlacks;         // -1.0 .. 1.0

// Color
uniform float uSaturation;     // -1.0 .. 1.0
uniform float uVibrance;       // -1.0 .. 1.0

// HSL 8-color panel
uniform float uHueRed;         // -1.0 .. 1.0
uniform float uSatRed;         // -1.0 .. 1.0
uniform float uLumRed;         // -1.0 .. 1.0
uniform float uHueOrange;
uniform float uSatOrange;
uniform float uLumOrange;
uniform float uHueYellow;
uniform float uSatYellow;
uniform float uLumYellow;
uniform float uHueGreen;
uniform float uSatGreen;
uniform float uLumGreen;
uniform float uHueAqua;
uniform float uSatAqua;
uniform float uLumAqua;
uniform float uHueBlue;
uniform float uSatBlue;
uniform float uLumBlue;
uniform float uHuePurple;
uniform float uSatPurple;
uniform float uLumPurple;
uniform float uHueMagenta;
uniform float uSatMagenta;
uniform float uLumMagenta;

// Tone Curve (10 control points, x then y)
uniform vec4 uCurvePoints[5]; // 10 points packed as (x0,y0,x1,y1) per vec4

// Color Grading
uniform vec3 uColorGradingShadows;
uniform vec3 uColorGradingMidtones;
uniform vec3 uColorGradingHighlights;
uniform float uColorGradingBlend;    // 0.0 .. 1.0
uniform float uColorGradingGlobalSat; // -1.0 .. 1.0

// Color Calibration
uniform float uCalibRedHue;
uniform float uCalibRedSat;
uniform float uCalibGreenHue;
uniform float uCalibGreenSat;
uniform float uCalibBlueHue;
uniform float uCalibBlueSat;

// Detail
uniform float uSharpness;     // 0.0 .. 4.0
uniform float uClarity;       // -1.0 .. 1.0
uniform float uStructure;     // -1.0 .. 1.0

// Effects
uniform float uDehaze;        // -1.0 .. 1.0
uniform float uVignette;      // -1.0 .. 1.0
uniform float uGrain;         // 0.0 .. 1.0
uniform float uGrainSize;     // 0.5 .. 3.0

// Chromatic Aberration (dual-axis)
uniform float uChromaticAberrationRedCyan;   // -1.0 .. 1.0
uniform float uChromaticAberrationBlueYellow; // -1.0 .. 1.0

// Tone Mapping / Color Science
uniform int uColorScienceMode;        // 0=agx, 1=aces2, 2=opendrt, 3=standard (matches ColorScience.Mode ordinal)
uniform float uAgXEnabled;            // deprecated, use uColorScienceMode
uniform float uAgXContrast;           // 0.0 .. 1.0
uniform float uAgXPedestal;           // 0.0 .. 0.5
uniform int uAces2DisplayColorSpace;  // 0=sRGB, 1=P3, 2=Rec2020
uniform int uAces2Eotf;               // 0=sRGB, 1=PQ, 2=Gamma22
uniform float uAces2PeakLuminance;    // 100.0 .. 10000.0
uniform int uOpenDrtDisplayColorSpace;// 0=sRGB, 1=P3, 2=Rec2020
uniform int uOpenDrtEotf;             // 0=sRGB, 1=PQ, 2=Gamma22
uniform float uOpenDrtPeakLuminance;  // 100.0 .. 10000.0

// Debug
uniform float uClippingPreview; // 0.0 or 1.0

// ── Film Simulation Uniforms ──────────────────────────────────────────
uniform float uFilmIntensity;       // 0.0 - 1.0, film simulation blend amount
uniform float uHighlightRollOff;    // 0.0 - 1.0, how quickly highlights fade
uniform float uShadowLift;          // 0.0 - 1.0, shadow detail preservation
uniform float uDrCompression;       // 0.0 - 1.0, dynamic range compression
uniform float uFilmRedShift;        // -1.0 - 1.0, film color shift red
uniform float uFilmGreenShift;      // -1.0 - 1.0
uniform float uFilmBlueShift;       // -1.0 - 1.0
uniform float uFilmSaturation;      // -1.0 - 1.0, film saturation modifier
uniform float uFilmContrast;        // -1.0 - 1.0, film contrast modifier
uniform float uFilmGrainAmount;     // 0.0 - 1.0
uniform float uFilmGrainSize;       // 0.0 - 1.0
uniform float uFilmGrainRoughness;  // 0.0 - 1.0
uniform vec2 uFilmCurve[6];         // x=input, y=output, 6 control points

// ── Additional Adjustment Uniforms ────────────────────────────────────
uniform float uGreenMagenta;        // -1.0 - 1.0, green-magenta tint axis
uniform float uSoftGlow;            // 0.0 - 1.0, soft glow/bloom intensity
uniform float uToneLevel;           // -1.0 - 1.0, combined tone/brightness control

// ── Flow Mask & LUT ──────────────────────────────────────────────────
uniform sampler2D uMaskTexture;     // Flow mask alpha texture
uniform float uMaskIntensity;       // 0.0 - 1.0
uniform sampler3D uLutTexture;      // 3D LUT for color grading
uniform float uLutIntensity;        // 0.0 - 1.0

// ── Missing Fields Fix ────────────────────────────────────────────────
uniform float uLumaNoiseReduction;  // 0.0 - 1.0
uniform float uColorNoiseReduction; // 0.0 - 1.0
uniform float uCentre;              // -1.0 - 1.0
uniform float uVignetteMidpoint;    // 0.0 - 1.0
uniform float uVignetteRoundness;   // -1.0 - 1.0
uniform float uVignetteFeather;     // 0.0 - 1.0
uniform float uGrainRoughness;      // 0.0 - 1.0
uniform float uGlowAmount;          // 0.0 - 1.0
uniform float uHalationAmount;      // 0.0 - 1.0
uniform float uFlareAmount;         // 0.0 - 1.0
uniform float uColorGradingBalance; // -1.0 - 1.0
uniform float uColorCalibrationShadowsTint; // -1.0 - 1.0

// CDL Color Grading (per-channel R/G/B offsets for Lift/Gamma/Gain)
uniform float uCdlShadowsR;      // -1.0 - 1.0
uniform float uCdlShadowsG;
uniform float uCdlShadowsB;
uniform float uCdlMidtonesR;
uniform float uCdlMidtonesG;
uniform float uCdlMidtonesB;
uniform float uCdlHighlightsR;
uniform float uCdlHighlightsG;
uniform float uCdlHighlightsB;

// Blur-based creative effects
uniform float uBlurGlow;         // 0.0 - 1.0
uniform float uBlurHalation;     // 0.0 - 1.0
uniform float uRotation;            // -180 - 180
uniform int uOrientationSteps;      // 0 - 3
uniform float uFlipHorizontal;      // 0.0 or 1.0
uniform float uFlipVertical;        // 0.0 or 1.0
uniform float uCropAspectRatio;     // 0.0 = off
uniform float uTransformDistortion; // -1.0 - 1.0
uniform float uTransformVertical;   // -1.0 - 1.0
uniform float uTransformHorizontal; // -1.0 - 1.0
uniform float uTransformRotate;     // -45 .. 45
uniform float uTransformAspect;     // -1.0 - 1.0
uniform float uTransformScale;      // 0.1 - 2.0
uniform float uTransformXOffset;    // -1.0 - 1.0
uniform float uTransformYOffset;    // -1.0 - 1.0

// ── Perspective & Tint & Edge Light & Color Range ──────────────────────
uniform float uPerspectiveVertical;
uniform float uPerspectiveHorizontal;
uniform float uPerspectiveRotate;
uniform float uPerspectiveScale;
uniform float uPerspectiveAspect;
uniform float uShadowsTintHue;
uniform float uShadowsTintSaturation;
uniform float uHighlightsTintHue;
uniform float uHighlightsTintSaturation;
uniform float uEdgeLightAmount;
uniform float uEdgeLightHue;
uniform float uEdgeLightSaturation;
uniform float uColorRangeHue;
uniform float uColorRangeWidth;
uniform float uColorRangeSatAdjust;
uniform float uColorRangeLumAdjust;

// Lens Correction
uniform float uLensDistortion;      // -1.0 - 1.0
uniform float uLensVignette;        // -1.0 - 1.0
uniform float uLensTca;             // -1.0 - 1.0
uniform float uLensFocalLength;     // 1.0 - 1000.0

uniform vec4 uRedCurve[6];          // RGB red curve points (x0,y0,x1,y1)
uniform vec4 uGreenCurve[6];        // RGB green curve points
uniform vec4 uBlueCurve[6];         // RGB blue curve points

in vec2 vTexCoord;
out vec4 fragColor;

// ── Constants ──────────────────────────────────────────────────────────
const float PI = 3.14159265358979;
const float EPS = 1e-6;

// ── sRGB ↔ Linear ─────────────────────────────────────────────────────

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

// ── RGB ↔ HSV ─────────────────────────────────────────────────────────

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
        hsv.x *= 60.0; // degrees [0, 360)
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

// ── Luma ───────────────────────────────────────────────────────────────

float get_luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// ── Smoothstep helpers ─────────────────────────────────────────────────

float smoothstep_custom(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// ── Luminance Masks ───────────────────────────────────────────────────

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

// ── Cubic Hermite Interpolation (User Tone Curve) ─────────────────────

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
    // 10 control points
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

    // Catmull-Rom tangents
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

// ── 2. Filmic Brightness (Rational Curve) ─────────────────────────────

vec3 apply_filmic_exposure(vec3 color, float brightness) {
    // Filmic rational curve with midtone emphasis
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

    // Apply tint (green-magenta shift)
    // tint is already normalized to [-1, 1] by GpuPipeline
    float tintFactor = tint;
    g += tintFactor * 0.1;

    return vec3(r, g, b);
}

vec3 apply_white_balance(vec3 color, float temperature, float tint) {
    vec3 mult = temperature_tint_multipliers(temperature, tint);
    return color * mult;
}

// ── 4. Highlights Adjustment ──────────────────────────────────────────

vec3 apply_highlights_adjustment(vec3 color, float highlights) {
    float luma = get_luma(color);
    float mask = highlights_mask(luma);

    if (abs(highlights) < EPS) return color;

    // Compress or expand highlights
    vec3 result = color;
    if (highlights < 0.0) {
        // Compress: pull highlights down with a power function
        vec3 compressed = 1.0 - pow(1.0 - color, vec3(1.0 - highlights));
        result = mix(color, compressed, mask);
    } else {
        // Expand: push highlights up
        vec3 expanded = pow(color, vec3(1.0 / (1.0 + highlights)));
        result = mix(color, expanded, mask);
    }

    return result;
}

// ── 5. Tonal Adjustments (Shadows/Whites/Blacks/Contrast) ─────────────

vec3 apply_tonal_adjustments(vec3 color, float contrast, float shadows,
                             float whites, float blacks) {
    float luma = get_luma(color);
    vec3 result = color;

    // Contrast (perceptual gamma curve)
    if (abs(contrast) > EPS) {
        float contrastPow = 1.0 + contrast;
        // Apply around midpoint 0.18 (perceptual middle gray)
        vec3 mid = vec3(0.18);
        result = mid + (result - mid) * contrastPow;
    }

    // Shadows
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

// ── 6. Creative Color (Saturation + Vibrance) ─────────────────────────

vec3 apply_creative_color(vec3 color, float saturation, float vibrance) {
    float luma = get_luma(color);
    vec3 hsv = rgb_to_hsv(color);
    float currentSat = hsv.y;

    // Vibrance: less effect on already-saturated colors
    // Skin tone protection: reduce vibrance for skin-like hues
    float skinProtection = 1.0;
    if (hsv.x > 10.0 && hsv.x < 50.0 && currentSat < 0.5 && hsv.z > 0.2) {
        skinProtection = 0.5;
    }

    float vibranceAmount = vibrance * (1.0 - currentSat) * skinProtection;
    hsv.y = clamp(currentSat + vibranceAmount * 1.5, 0.0, 1.0);

    // Saturation
    hsv.y = clamp(hsv.y + saturation, 0.0, 1.0);

    return hsv_to_rgb(hsv);
}

// ── 7. HSL 8-Color Panel ──────────────────────────────────────────────

float hue_delta(float h1, float h2) {
    float d = abs(h1 - h2);
    return (d > 180.0) ? (360.0 - d) : d;
}

float hsl_range_weight(float hue, float center, float span) {
    float halfSpan = span * 0.5;
    float dist = hue_delta(hue, center);
    return (dist <= halfSpan) ? (1.0 - dist / halfSpan) : 0.0;
}

vec3 apply_hsl_panel(vec3 color) {
    vec3 hsv = rgb_to_hsv(color);
    float hue = hsv.x;

    // HSL ranges: (center, span)
    // Red(358,35), Orange(25,45), Yellow(60,40), Green(115,90),
    // Aqua(180,60), Blue(225,60), Purple(280,55), Magenta(330,50)

    float hueShift = 0.0;
    float satShift = 0.0;
    float lumShift = 0.0;

    // Red
    float w = hsl_range_weight(hue, 358.0, 35.0);
    hueShift += uHueRed * w;
    satShift += uSatRed * w;
    lumShift += uLumRed * w;

    // Orange
    w = hsl_range_weight(hue, 25.0, 45.0);
    hueShift += uHueOrange * w;
    satShift += uSatOrange * w;
    lumShift += uLumOrange * w;

    // Yellow
    w = hsl_range_weight(hue, 60.0, 40.0);
    hueShift += uHueYellow * w;
    satShift += uSatYellow * w;
    lumShift += uLumYellow * w;

    // Green
    w = hsl_range_weight(hue, 115.0, 90.0);
    hueShift += uHueGreen * w;
    satShift += uSatGreen * w;
    lumShift += uLumGreen * w;

    // Aqua
    w = hsl_range_weight(hue, 180.0, 60.0);
    hueShift += uHueAqua * w;
    satShift += uSatAqua * w;
    lumShift += uLumAqua * w;

    // Blue
    w = hsl_range_weight(hue, 225.0, 60.0);
    hueShift += uHueBlue * w;
    satShift += uSatBlue * w;
    lumShift += uLumBlue * w;

    // Purple
    w = hsl_range_weight(hue, 280.0, 55.0);
    hueShift += uHuePurple * w;
    satShift += uSatPurple * w;
    lumShift += uLumPurple * w;

    // Magenta
    w = hsl_range_weight(hue, 330.0, 50.0);
    hueShift += uHueMagenta * w;
    satShift += uSatMagenta * w;
    lumShift += uLumMagenta * w;

    hsv.x = mod(hsv.x + hueShift * 60.0 + 360.0, 360.0);
    hsv.y = clamp(hsv.y + satShift, 0.0, 1.0);

    vec3 rgb = hsv_to_rgb(hsv);
    float luma = get_luma(rgb);
    rgb = luma + (rgb - luma) * (1.0 + lumShift);

    return rgb;
}

// ── 8. Color Grading ──────────────────────────────────────────────────

vec3 apply_color_grading(vec3 color) {
    float luma = get_luma(color);

    float sm = shadows_mask(luma);
    float mm = midtones_mask(luma);
    float hm = highlights_mask(luma);

    // Apply balance: shift weight between shadows and highlights
    float balance = uColorGradingBalance;
    float balancedSm = sm * (1.0 + balance * 0.5);
    float balancedHm = hm * (1.0 - balance * 0.5);

    // Normalize masks
    float maskSum = balancedSm + mm + balancedHm + EPS;
    sm = balancedSm / maskSum;
    mm = mm / maskSum;
    hm = balancedHm / maskSum;

    vec3 tint = sm * uColorGradingShadows +
                mm * uColorGradingMidtones +
                hm * uColorGradingHighlights;

    vec3 result = mix(color, color + tint, uColorGradingBlend);

    // Global saturation from color grading
    if (abs(uColorGradingGlobalSat) > EPS) {
        float lum = get_luma(result);
        result = lum + (result - lum) * (1.0 + uColorGradingGlobalSat);
    }

    return result;
}

// ── 9. Color Calibration (RGB Primary Matrix) ────────────────────────

vec3 apply_color_calibration(vec3 color) {
    // Build 3x3 hue/saturation rotation matrix for RGB primaries
    // Each primary can have its hue rotated and saturation scaled

    float rh = uCalibRedHue * 60.0;   // convert to degrees
    float rs = 1.0 + uCalibRedSat;
    float gh = uCalibGreenHue * 60.0;
    float gs = 1.0 + uCalibGreenSat;
    float bh = uCalibBlueHue * 60.0;
    float bs = 1.0 + uCalibBlueSat;

    // Convert each primary to HSV, adjust, convert back
    vec3 redPrimary = hsv_to_rgb(vec3(mod(rh, 360.0), clamp(rs, 0.0, 2.0), 1.0));
    vec3 greenPrimary = hsv_to_rgb(vec3(mod(120.0 + gh, 360.0), clamp(gs, 0.0, 2.0), 1.0));
    vec3 bluePrimary = hsv_to_rgb(vec3(mod(240.0 + bh, 360.0), clamp(bs, 0.0, 2.0), 1.0));

    // Build color matrix
    mat3 calibrationMatrix = mat3(
        redPrimary.r, redPrimary.g, redPrimary.b,
        greenPrimary.r, greenPrimary.g, greenPrimary.b,
        bluePrimary.r, bluePrimary.g, bluePrimary.b
    );

    return calibrationMatrix * color;
}

// ── Hash & Gradient Noise ──────────────────────────────────────────────

float hash(float n) {
    // Integer hash
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

    float a = hash(ix + iy * 57.0);
    float b = hash(ix + 1.0 + iy * 57.0);
    float c = hash(ix + (iy + 1.0) * 57.0);
    float d = hash(ix + 1.0 + (iy + 1.0) * 57.0);

    return a + (b - a) * ux + (c - a) * uy + (a - b - c + d) * ux * uy;
}

// ── Dither ─────────────────────────────────────────────────────────────

float dither(vec2 coord) {
    return gradient_noise(coord.x, coord.y) - 0.5;
}

// ── 10. Local Contrast (Clarity/Structure) ────────────────────────────

// Simple box blur for local contrast estimation
vec3 sample_blur(vec2 uv, float radius) {
    vec2 texel = 1.0 / uResolution;
    vec3 sum = vec3(0.0);
    float count = 0.0;

    int samples = 3;
    for (int x = -samples; x <= samples; x++) {
        for (int y = -samples; y <= samples; y++) {
            vec2 offset = vec2(float(x), float(y)) * texel * radius;
            sum += texture(uTexture, uv + offset).rgb;
            count += 1.0;
        }
    }

    return sum / count;
}

vec3 apply_local_contrast(vec3 color, vec2 uv) {
    if (abs(uClarity) < EPS && abs(uStructure) < EPS) return color;

    float blurRadius = 5.0;
    vec3 blurred = sample_blur(uv, blurRadius);

    vec3 result = color;

    // Clarity: local contrast (difference from blurred)
    if (abs(uClarity) > EPS) {
        vec3 highPass = color - blurred;
        result += highPass * uClarity * 2.0;
    }

    // Structure: finer local contrast
    if (abs(uStructure) > EPS) {
        vec3 fineBlurred = sample_blur(uv, 2.0);
        vec3 fineHighPass = color - fineBlurred;
        result += fineHighPass * uStructure * 1.5;
    }

    return result;
}

// ── 11. Sharpness (Unsharp Mask) ──────────────────────────────────────

vec3 apply_sharpness(vec3 color, vec2 uv) {
    if (uSharpness < EPS) return color;

    vec2 texel = 1.0 / uResolution;
    float radius = 1.0;

    // Sample neighbors for unsharp mask
    vec3 sum = vec3(0.0);
    sum += texture(uTexture, uv + vec2(-radius, 0.0) * texel).rgb;
    sum += texture(uTexture, uv + vec2(radius, 0.0) * texel).rgb;
    sum += texture(uTexture, uv + vec2(0.0, -radius) * texel).rgb;
    sum += texture(uTexture, uv + vec2(0.0, radius) * texel).rgb;
    sum += texture(uTexture, uv + vec2(-radius, -radius) * texel).rgb * 0.707;
    sum += texture(uTexture, uv + vec2(radius, -radius) * texel).rgb * 0.707;
    sum += texture(uTexture, uv + vec2(-radius, radius) * texel).rgb * 0.707;
    sum += texture(uTexture, uv + vec2(radius, radius) * texel).rgb * 0.707;
    float weightSum = 4.0 + 4.0 * 0.707;
    vec3 blurred = sum / weightSum;

    vec3 highPass = color - blurred;
    return color + highPass * uSharpness;
}

// ── 12. Chromatic Aberration (Dual-Axis) ──────────────────────────────

vec3 apply_chromatic_aberration(vec3 color, vec2 uv) {
    bool hasCa = abs(uChromaticAberrationRedCyan) > EPS || abs(uChromaticAberrationBlueYellow) > EPS;
    if (!hasCa) return color;

    vec2 dir = uv - 0.5;
    float dist = length(dir);

    // Red-Cyan axis: offset red channel outward/inward
    vec2 offsetR = dir * dist * uChromaticAberrationRedCyan * 0.02;
    // Blue-Yellow axis: offset blue channel outward/inward (opposite direction)
    vec2 offsetB = dir * dist * uChromaticAberrationBlueYellow * 0.02;

    // Re-sample original texture with separate CA offsets
    float r = texture(uTexture, uv + offsetR).r;
    float b = texture(uTexture, uv - offsetB).b;

    // Blend with processed color
    float blendR = clamp(abs(uChromaticAberrationRedCyan), 0.0, 1.0);
    float blendB = clamp(abs(uChromaticAberrationBlueYellow), 0.0, 1.0);
    vec3 result = color;
    result.r = mix(color.r, srgb_to_linear(r), blendR);
    result.b = mix(color.b, srgb_to_linear(b), blendB);

    return result;
}

// ── 13. Dehaze ────────────────────────────────────────────────────────

vec3 apply_dehaze(vec3 color) {
    if (abs(uDehaze) < EPS) return color;

    float luma = get_luma(color);

    // Estimate haze amount based on how close to white the darkest areas are
    float hazeEstimate = smoothstep_custom(0.0, 0.8, luma);

    if (uDehaze > 0.0) {
        // Remove haze: subtract estimated atmospheric light
        vec3 atmosphericLight = vec3(0.85, 0.88, 0.92);
        float transmission = 1.0 - hazeEstimate * uDehaze;
        transmission = max(transmission, 0.1);
        vec3 result = (color - atmosphericLight * hazeEstimate * uDehaze) / transmission;
        return max(result, vec3(0.0));
    } else {
        // Add haze
        vec3 atmosphericLight = vec3(0.85, 0.88, 0.92);
        float amount = -uDehaze;
        return mix(color, atmosphericLight, amount * hazeEstimate);
    }
}

// ── 14. Vignette ──────────────────────────────────────────────────────

vec3 apply_vignette(vec3 color, vec2 uv) {
    if (abs(uVignette) < EPS) return color;

    vec2 centered = uv - 0.5;
    float dist = length(centered) * 1.414; // normalize to [0,1] at corners

    // Apply roundness
    float aspect = 1.0 + uVignetteRoundness * 0.5;
    float shapedDist = dist * aspect;

    // Midpoint and feather
    float start = uVignetteMidpoint * 0.7;
    float end = start + uVignetteFeather * 0.3 + 0.05;

    float vignetteAmount;
    if (uVignette > 0.0) {
        // Darken edges
        vignetteAmount = 1.0 - smoothstep_custom(start, clamp(end, start + 0.01, 1.0), shapedDist) * uVignette;
    } else {
        // Brighten edges (inverse vignette)
        vignetteAmount = 1.0 + smoothstep_custom(start, clamp(end, start + 0.01, 1.0), shapedDist) * uVignette;
    }

    return color * vignetteAmount;
}

// ── 15. Film Grain (Legacy) ───────────────────────────────────────────

vec3 apply_grain(vec3 color, vec2 uv) {
    if (uGrain < EPS) return color;

    float noise = gradient_noise(uv.x * uGrainSize * uResolution.x,
                                  uv.y * uGrainSize * uResolution.y);
    noise = (noise - 0.5) * uGrain * 0.3;

    // Roughness modulates grain sharpness
    if (uGrainRoughness > EPS) {
        noise *= (0.5 + uGrainRoughness * 0.5);
    }

    // Grain is more visible in midtones
    float luma = get_luma(color);
    float grainAmount = 1.0 - abs(luma - 0.5) * 1.5;
    grainAmount = clamp(grainAmount, 0.2, 1.0);

    return color + noise * grainAmount;
}

// ── 16. Tone Curve ────────────────────────────────────────────────────

vec3 apply_tone_curve(vec3 color) {
    // Apply curve per-channel then luma
    float luma = get_luma(color);

    // Luma curve
    float curvedLuma = apply_curve(luma);
    float lumaRatio = (luma > EPS) ? curvedLuma / luma : 1.0;

    // Blend between direct curve and luma-guided curve
    vec3 result = color * lumaRatio;

    // Per-channel curves for color control
    result.r = apply_curve(result.r);
    result.g = apply_curve(result.g);
    result.b = apply_curve(result.b);

    return result;
}

// ── 17. AgX Tone Mapping ──────────────────────────────────────────────

vec3 agx_default_contrast(float lo, float mid, float hi, float t) {
    // AgX log encoding
    float v = log2(t + EPS);
    v = (v - lo) / (hi - lo);
    v = clamp(v, 0.0, 1.0);
    // Apply contrast S-curve
    float contrast = 1.0 + uAgXContrast * 0.5;
    v = pow(v, contrast);
    return v;
}

vec3 apply_agx_tonemap(vec3 color) {
    // AgX log encoding parameters
    float lo = -10.0;
    float hi = 13.0;
    float mid = 1.5;

    vec3 logColor;
    logColor.r = agx_default_contrast(lo, mid, hi, color.r);
    logColor.g = agx_default_contrast(lo, mid, hi, color.g);
    logColor.b = agx_default_contrast(lo, mid, hi, color.b);

    // Apply AgX gamut mapping
    vec3 result = logColor;

    // Add pedestal
    result = max(result - uAgXPedestal, vec3(0.0)) / (1.0 - uAgXPedestal);

    return result;
}

// ── ACES 2.0 Tone Mapping ─────────────────────────────────────────────

vec3 aces_mat_xyz_to_ap0 = vec3(0.9525524, 0.0, 0.0000937);
vec3 aces_mat_xyz_to_ap1 = vec3(1.0520915, 0.0, -0.0520915);

vec3 srgb_to_ap0(vec3 c) {
    // sRGB -> XYZ -> ACES AP0 (ACES2065-1)
    // Simplified Bradford adaptation + matrix
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
    return xyz_to_ap0 * (srgb_to_xyz * c);
}

vec3 ap0_to_srgb(vec3 c) {
    // ACES AP0 -> XYZ -> sRGB
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
    return xyz_to_srgb * (ap0_to_xyz * c);
}

float aces_rrt(float x) {
    // Simplified ACES 2.0 RRT (Reference Rendering Transform)
    // Segment-wise S-curve with film-like shoulder and toe
    const float a = 0.0245786;
    const float b = 0.0000907;
    const float c = 0.983729;
    const float d = 0.4329510;
    const float e = 0.238081;
    float y = (x * (a * x + b)) / (x * (c * x + d) + e);
    return max(y, 0.0);
}

vec3 apply_aces2_tonemap(vec3 color) {
    // Step 1: Convert to ACES AP0 working space
    vec3 ap0 = srgb_to_ap0(color);

    // Step 2: Apply RRT per-channel (simplified)
    vec3 rrt;
    rrt.r = aces_rrt(max(ap0.r, 0.0));
    rrt.g = aces_rrt(max(ap0.g, 0.0));
    rrt.b = aces_rrt(max(ap0.b, 0.0));

    // Step 3: ODT (Output Device Transform) - simplified sRGB ODT
    // Scale to target display peak luminance
    float peakScale = 100.0 / max(uAces2PeakLuminance, 100.0);
    vec3 scaled = rrt * peakScale;

    // Step 4: Convert back to display space
    vec3 result = ap0_to_srgb(scaled);

    // Gamut compression: handle out-of-gamut colors
    float maxVal = max(result.r, max(result.g, result.b));
    if (maxVal > 1.0) {
        float gamutComp = 1.0 + log(maxVal) * 0.2;
        result = result / gamutComp;
    }

    return clamp(result, 0.0, 1.0);
}

// ── OpenDRT Tone Mapping ──────────────────────────────────────────────

vec3 apply_opendrt_tonemap(vec3 color) {
    // OpenDRT v1.0 - Jed Smith
    // Perceptually uniform tone mapping with excellent shadow detail

    // Step 1: Convert to AP1 (working space)
    mat3 srgb_to_ap1 = mat3(
        0.6130974, 0.3395231, 0.0473795,
        0.0701937, 0.9163539, 0.0134524,
        0.0206156, 0.1095698, 0.8698145
    );
    vec3 ap1 = srgb_to_ap1 * color;

    // Step 2: Tonescale (perceptual luminance compression)
    float Lw = max(uOpenDrtPeakLuminance / 100.0, 1.0); // scene white relative to 100 nit
    float Lp = 1.0; // display peak (normalized)

    // OpenDRT tonescale: smooth highlight compression
    vec3 tonescaled;
    for (int i = 0; i < 3; i++) {
        float x = max(ap1[i], 0.0);
        // Sigmoidal compression with film-like toe
        float t = x / (x + Lw * 0.18); // normalized
        float toe = pow(t, 1.0 / 2.4); // perceptual toe
        float shoulder = 1.0 - pow(1.0 - t, 2.0); // highlight shoulder
        float blend = smoothstep(0.0, 1.0, t);
        tonescaled[i] = mix(toe, shoulder, blend) * Lp;
    }

    // Step 3: Convert back to sRGB
    mat3 ap1_to_srgb = mat3(
        1.7050505, -0.6217921, -0.0832584,
        -0.1302564, 1.1408049, -0.0105485,
        -0.0240033, -0.1289689, 1.1529723
    );
    vec3 result = ap1_to_srgb * tonescaled;

    // Shadow rolloff (OpenDRT signature: lifted blacks)
    result = max(result, 0.0);
    float shadowLift = 0.003; // minimal black level
    result = result + shadowLift * (1.0 - result);

    return clamp(result, 0.0, 1.0);
}

// ── 18. Clipping Visualization ────────────────────────────────────────

vec3 apply_clipping_preview(vec3 color) {
    if (uClippingPreview < 0.5) return color;

    vec3 result = color;
    // Red for highlight clipping
    if (color.r > 1.0 || color.g > 1.0 || color.b > 1.0) {
        result = vec3(1.0, 0.0, 0.0);
    }
    // Blue for shadow clipping
    if (color.r < 0.0 || color.g < 0.0 || color.b < 0.0) {
        result = vec3(0.0, 0.0, 1.0);
    }

    return result;
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Tone Level (影调) ────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_tone_level(vec3 color) {
    if (abs(uToneLevel) < 0.001) return color;
    // Positive = brighter (lift shadows and midtones)
    // Negative = darker (compress highlights)
    float lift = uToneLevel * 0.3;
    float gamma = 1.0 - uToneLevel * 0.3;
    gamma = max(0.3, gamma);
    color += lift * 0.2;
    color = pow(max(color, vec3(0.0)), vec3(gamma));
    return color;
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Green-Magenta Tint ──────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_green_magenta(vec3 color) {
    if (abs(uGreenMagenta) < 0.001) return color;
    // Green-Magenta axis shift
    // Magenta = reduce green, increase red+blue
    // Green = increase green, reduce red+blue
    float amount = uGreenMagenta * 0.12;
    color.r += amount * 0.5;
    color.g -= amount * 0.8;
    color.b += amount * 0.3;
    return max(color, vec3(0.0));
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Soft Glow ───────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_soft_glow(vec3 color, vec2 uv) {
    if (uSoftGlow < 0.001) return color;
    // Extract highlights
    float lum = get_luma(color);
    vec3 highlights = color * smoothstep(0.4, 0.9, lum);
    // Simple 9x9 blur approximation for glow
    vec2 texel = 1.0 / uResolution;
    vec3 blur = vec3(0.0);
    float weights = 0.0;
    for (int dx = -4; dx <= 4; dx++) {
        for (int dy = -4; dy <= 4; dy++) {
            float w = 1.0 / (1.0 + float(dx * dx + dy * dy) * 0.1);
            blur += texture(uTexture, uv + vec2(float(dx), float(dy)) * texel * 3.0).rgb * w;
            weights += w;
        }
    }
    blur /= weights;
    // Extract high luminance from blur
    float blurLum = get_luma(blur);
    vec3 glow = blur * smoothstep(0.4, 0.9, blurLum);
    // Additive blend with reduced opacity
    color = mix(color, color + glow * 0.4, uSoftGlow);
    return color;
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Film Tone Curve (6 control points, Cubic Hermite) ──────────
// ═══════════════════════════════════════════════════════════════════════

float apply_film_curve_channel(float x) {
    // Normalize input to 0-1 range
    x = clamp(x, 0.0, 1.0);

    // Find the segment
    for (int i = 0; i < 5; i++) {
        float x0 = uFilmCurve[i].x / 255.0;
        float x1 = uFilmCurve[i + 1].x / 255.0;
        if (x >= x0 && x <= x1) {
            float y0 = uFilmCurve[i].y / 255.0;
            float y1 = uFilmCurve[i + 1].y / 255.0;

            // Catmull-Rom tangents
            float m0, m1;
            if (i == 0) {
                m0 = (y1 - y0) / (x1 - x0);
            } else {
                float yPrev = uFilmCurve[i - 1].y / 255.0;
                float xPrev = uFilmCurve[i - 1].x / 255.0;
                m0 = (y1 - yPrev) / (x1 - xPrev) * 0.5;
            }
            if (i == 4) {
                m1 = (y1 - y0) / (x1 - x0);
            } else {
                float yNext = uFilmCurve[i + 2].y / 255.0;
                float xNext = uFilmCurve[i + 2].x / 255.0;
                m1 = (yNext - y0) / (xNext - x0) * 0.5;
            }

            float t = (x - x0) / max(x1 - x0, 0.001);
            float t2 = t * t;
            float t3 = t2 * t;

            // Hermite interpolation
            float h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
            float h10 = t3 - 2.0 * t2 + t;
            float h01 = -2.0 * t3 + 3.0 * t2;
            float h11 = t3 - t2;

            return h00 * y0 + h10 * (x1 - x0) * m0 + h01 * y1 + h11 * (x1 - x0) * m1;
        }
    }
    return x;
}

vec3 apply_film_curve(vec3 color) {
    // Apply the same curve to R, G, B channels using the 6 control points
    color.r = apply_film_curve_channel(color.r);
    color.g = apply_film_curve_channel(color.g);
    color.b = apply_film_curve_channel(color.b);
    return color;
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Film Grain (Realistic) ──────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_film_grain(vec3 color, vec2 uv) {
    if (uFilmGrainAmount < 0.001) return color;
    float lum = get_luma(color);
    // Grain more visible in shadows
    float shadowWeight = 1.0 - lum * 0.5;
    // Variable grain size: use different hash frequencies
    float size = max(1.0, uFilmGrainSize * 8.0 + 1.0);
    vec2 grainUV = uv * uResolution / size;
    float noise = hash(grainUV.x + hash(grainUV.y)) * 2.0 - 1.0;
    // Roughness: more rough = sharper noise edges
    float roughNoise = mix(noise * noise * sign(noise), noise, uFilmGrainRoughness);
    float grainStrength = uFilmGrainAmount * shadowWeight * 0.15;
    return color + roughNoise * grainStrength;
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Film Simulation ─────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_film_simulation(vec3 color, vec2 uv) {
    // 1. Apply film color shift in linear space
    color.r += uFilmRedShift * 0.08;
    color.g += uFilmGreenShift * 0.08;
    color.b += uFilmBlueShift * 0.08;

    // 2. Apply film saturation modifier
    float lum = get_luma(color);
    vec3 desat = vec3(lum);
    color = mix(desat, color, 1.0 + uFilmSaturation);

    // 3. Apply film contrast modifier
    color = (color - 0.5) * (1.0 + uFilmContrast * 0.5) + 0.5;

    // 4. Apply shadow lift (raise shadow values)
    float shadowMask = 1.0 - smoothstep(0.0, 0.3, lum);
    color += shadowMask * uShadowLift * 0.15;

    // 5. Apply highlight rolloff
    float highlightMask = smoothstep(0.7, 1.0, lum);
    float rolloff = 1.0 - highlightMask * uHighlightRollOff * 0.3;
    color *= rolloff;

    // 6. Apply dynamic range compression
    if (uDrCompression > 0.0) {
        float compressed = atan(color.r * uDrCompression * 3.0) / (PI * 0.5);
        float compressedG = atan(color.g * uDrCompression * 3.0) / (PI * 0.5);
        float compressedB = atan(color.b * uDrCompression * 3.0) / (PI * 0.5);
        vec3 compressedV = vec3(compressed, compressedG, compressedB);
        float t = uDrCompression;
        color = mix(color, compressedV, t);
    }

    // 7. Apply film tone curve (cubic Hermite through 6 points)
    color = apply_film_curve(color);

    // 8. Apply film grain
    color = apply_film_grain(color, uv);

    return color;
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Centre (midtone emphasis) ────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_centre(vec3 color) {
    if (abs(uCentre) < EPS) return color;
    float luma = get_luma(color);
    float shift = uCentre * 0.3;
    float target = clamp(luma + shift, 0.0, 1.0);
    float factor = (luma > EPS) ? target / luma : 1.0;
    return color * factor;
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Noise Reduction (placeholder) ────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_noise_reduction(vec3 color) {
    if (uLumaNoiseReduction < EPS && uColorNoiseReduction < EPS) return color;
    float lum = get_luma(color);
    vec3 chroma = color - vec3(lum);
    // Chroma damping: reduce color noise
    float chromaDamp = 1.0 - uColorNoiseReduction * 0.5;
    vec3 newChroma = chroma * chromaDamp;
    // Luma noise reduction: bilateral-weighted spatial filter
    vec2 texel = 1.0 / uResolution;
    vec3 n1 = texture(uTexture, vTexCoord + vec2(texel.x, 0.0)).rgb;
    vec3 n2 = texture(uTexture, vTexCoord - vec2(texel.x, 0.0)).rgb;
    vec3 n3 = texture(uTexture, vTexCoord + vec2(0.0, texel.y)).rgb;
    vec3 n4 = texture(uTexture, vTexCoord - vec2(0.0, texel.y)).rgb;
    float lum1 = get_luma(n1);
    float lum2 = get_luma(n2);
    float lum3 = get_luma(n3);
    float lum4 = get_luma(n4);
    // Bilateral weights: closer luminance = higher weight
    float sigma = 0.1 + uLumaNoiseReduction * 0.3;
    float w1 = exp(-((lum1 - lum) * (lum1 - lum)) / (2.0 * sigma * sigma));
    float w2 = exp(-((lum2 - lum) * (lum2 - lum)) / (2.0 * sigma * sigma));
    float w3 = exp(-((lum3 - lum) * (lum3 - lum)) / (2.0 * sigma * sigma));
    float w4 = exp(-((lum4 - lum) * (lum4 - lum)) / (2.0 * sigma * sigma));
    float wSum = w1 + w2 + w3 + w4;
    float neighborLum = (lum1 * w1 + lum2 * w2 + lum3 * w3 + lum4 * w4) / max(wSum, EPS);
    float spatialBlend = uLumaNoiseReduction * 0.6;
    float newLum = mix(lum, neighborLum, spatialBlend);
    return vec3(newLum) + newChroma;
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Glow ─────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_glow(vec3 color) {
    if (uGlowAmount < 0.001) return color;
    float lum = get_luma(color);
    float bloom = lum * max(uGlowAmount, uBlurGlow) * 0.3;
    return color + vec3(bloom);
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Halation ─────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_halation(vec3 color) {
    if (uHalationAmount < 0.001 && uBlurHalation < 0.001) return color;
    float lum = get_luma(color);
    float highlightMask = smoothstep_custom(0.5, 1.0, lum);
    float halation = highlightMask * max(uHalationAmount, uBlurHalation) * 0.4;
    return vec3(color.r + halation, color.g + halation * 0.3, color.b + halation * 0.1);
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Flare ────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_flare(vec3 color) {
    if (uFlareAmount < 0.001) return color;
    vec3 flareColor = vec3(0.9, 0.85, 0.8);
    float blend = uFlareAmount * 0.25;
    return mix(color, flareColor, blend);
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: RGB Curves ───────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

float apply_rgb_curve_channel(float x, vec4 curve[6]) {
    x = clamp(x, 0.0, 1.0);
    // Flatten vec4 array to points
    float xs[12];
    float ys[12];
    for (int i = 0; i < 6; i++) {
        xs[i * 2] = curve[i].x;
        ys[i * 2] = curve[i].y;
        xs[i * 2 + 1] = curve[i].z;
        ys[i * 2 + 1] = curve[i].w;
    }
    // Clamp
    if (x <= xs[0]) return ys[0];
    if (x >= xs[11]) return ys[11];
    // Find segment
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
    if (idx == 0) m0 = (ys[1] - ys[0]) / dx;
    else m0 = ((ys[idx + 1] - ys[idx - 1]) / (xs[idx + 1] - xs[idx - 1])) * dx * 0.5;
    if (idx >= 10) m1 = (ys[11] - ys[10]) / dx;
    else m1 = ((ys[idx + 2] - ys[idx]) / (xs[idx + 2] - xs[idx])) * dx * 0.5;
    float t2 = t * t;
    float t3 = t2 * t;
    float h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
    float h10 = t3 - 2.0 * t2 + t;
    float h01 = -2.0 * t3 + 3.0 * t2;
    float h11 = t3 - t2;
    return h00 * ys[idx] + h10 * m0 + h01 * ys[idx + 1] + h11 * m1;
}

vec3 apply_rgb_curves(vec3 color) {
    color.r = apply_rgb_curve_channel(color.r, uRedCurve);
    color.g = apply_rgb_curve_channel(color.g, uGreenCurve);
    color.b = apply_rgb_curve_channel(color.b, uBlueCurve);
    return color;
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Color Calibration Shadows Tint ───────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_color_calibration_shadows_tint(vec3 color) {
    if (abs(uColorCalibrationShadowsTint) < EPS) return color;
    float luma = get_luma(color);
    float sMask = shadows_mask(luma);
    float tint = uColorCalibrationShadowsTint * 0.15 * sMask;
    return vec3(color.r + tint, color.g - tint * 0.5, color.b + tint);
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: CDL Color Grading (Lift/Gamma/Gain per-channel offsets) ────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_cdl_grading(vec3 color) {
    // Check if any CDL parameter is non-zero
    float anyCdl = abs(uCdlShadowsR) + abs(uCdlShadowsG) + abs(uCdlShadowsB) +
                   abs(uCdlMidtonesR) + abs(uCdlMidtonesG) + abs(uCdlMidtonesB) +
                   abs(uCdlHighlightsR) + abs(uCdlHighlightsG) + abs(uCdlHighlightsB);
    if (anyCdl < EPS) return color;

    float luma = get_luma(color);

    // Compute smoothstep masks for shadows, midtones, highlights
    float sm = shadows_mask(luma);
    float mm = midtones_mask(luma);
    float hm = highlights_mask(luma);

    // Normalize masks so they sum to 1
    float maskSum = sm + mm + hm + EPS;
    sm /= maskSum;
    mm /= maskSum;
    hm /= maskSum;

    // Apply per-channel CDL offsets (Lift for shadows, Gamma for midtones, Gain for highlights)
    float offsetR = (sm * uCdlShadowsR + mm * uCdlMidtonesR + hm * uCdlHighlightsR) * 0.15;
    float offsetG = (sm * uCdlShadowsG + mm * uCdlMidtonesG + hm * uCdlHighlightsG) * 0.15;
    float offsetB = (sm * uCdlShadowsB + mm * uCdlMidtonesB + hm * uCdlHighlightsB) * 0.15;

    return clamp(color + vec3(offsetR, offsetG, offsetB), 0.0, 1.0);
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Perspective Correction ────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_perspective_correction(vec3 color) {
    // Perspective correction is applied via UV remapping in the vertex shader or main().
    // This stub exists for pipeline consistency; actual perspective warp requires
    // multi-pass rendering or vertex displacement, which is handled in the Kotlin layer.
    return color;
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Local Tint (Shadow/Highlight tinting) ────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_local_tint(vec3 color) {
    if (uShadowsTintSaturation < EPS && uHighlightsTintSaturation < EPS) return color;
    float lum = get_luma(color);
    // Shadow tint: blend toward the target hue in shadows
    if (uShadowsTintSaturation > EPS && lum < 0.5) {
        float shWeight = (1.0 - lum * 2.0) * uShadowsTintSaturation;
        float targetR = abs(mod(uShadowsTintHue * 6.0, 2.0) - 1.0);
        float targetG = abs(mod((uShadowsTintHue + 0.333) * 6.0, 2.0) - 1.0);
        float targetB = abs(mod((uShadowsTintHue + 0.667) * 6.0, 2.0) - 1.0);
        vec3 tint = vec3(targetR, targetG, targetB);
        color = mix(color, color * (1.0 + tint * shWeight * 0.3), shWeight * 0.5);
    }
    // Highlight tint: blend toward the target hue in highlights
    if (uHighlightsTintSaturation > EPS && lum > 0.5) {
        float hlWeight = ((lum - 0.5) * 2.0) * uHighlightsTintSaturation;
        float targetR = abs(mod(uHighlightsTintHue * 6.0, 2.0) - 1.0);
        float targetG = abs(mod((uHighlightsTintHue + 0.333) * 6.0, 2.0) - 1.0);
        float targetB = abs(mod((uHighlightsTintHue + 0.667) * 6.0, 2.0) - 1.0);
        vec3 tint = vec3(targetR, targetG, targetB);
        color = mix(color, color * (1.0 + tint * hlWeight * 0.3), hlWeight * 0.5);
    }
    return color;
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Edge Light ───────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_edge_light(vec3 color) {
    if (uEdgeLightAmount < EPS) return color;
    vec2 texel = 1.0 / uResolution;
    float lum = get_luma(color);
    float lumN = get_luma(texture(uTexture, vTexCoord + vec2(0.0, texel.y)).rgb);
    float lumS = get_luma(texture(uTexture, vTexCoord - vec2(0.0, texel.y)).rgb);
    float lumE = get_luma(texture(uTexture, vTexCoord + vec2(texel.x, 0.0)).rgb);
    float lumW = get_luma(texture(uTexture, vTexCoord - vec2(texel.x, 0.0)).rgb);
    float edge = abs(lum - lumN) + abs(lum - lumS) + abs(lum - lumE) + abs(lum - lumW);
    edge = clamp(edge * 4.0, 0.0, 1.0);
    float rimFactor = edge * uEdgeLightAmount / 100.0;
    // Apply colored rim light
    float targetR = abs(mod(uEdgeLightHue * 6.0, 2.0) - 1.0) * uEdgeLightSaturation;
    float targetG = abs(mod((uEdgeLightHue + 0.333) * 6.0, 2.0) - 1.0) * uEdgeLightSaturation;
    float targetB = abs(mod((uEdgeLightHue + 0.667) * 6.0, 2.0) - 1.0) * uEdgeLightSaturation;
    vec3 rimColor = vec3(targetR, targetG, targetB) * 0.5 + 0.5;
    return mix(color, color + rimColor * rimFactor, rimFactor);
}

// ═══════════════════════════════════════════════════════════════════════
// ── NEW: Color Range (Selective hue adjustment) ───────────────────────
// ═══════════════════════════════════════════════════════════════════════

vec3 apply_color_range(vec3 color) {
    if (abs(uColorRangeSatAdjust) < EPS && abs(uColorRangeLumAdjust) < EPS) return color;
    // Get pixel hue and check if it falls within the selected range
    vec3 hsv = rgb_to_hsv(color);
    float pixelHue = hsv.x / 360.0; // normalize to 0-1 range (rgb_to_hsv returns degrees)
    float centerHue = uColorRangeHue; // 0-1 range
    float halfWidth = uColorRangeWidth * 0.5; // 0-1 range
    float hueDist = abs(pixelHue - centerHue);
    hueDist = min(hueDist, 1.0 - hueDist); // Wrap around
    float mask = smoothstep(halfWidth, halfWidth * 0.5, hueDist);
    // Apply sat/lum adjustments within the range
    hsv.y += uColorRangeSatAdjust * mask;
    hsv.z += uColorRangeLumAdjust * mask;
    hsv.y = clamp(hsv.y, 0.0, 1.0);
    hsv.z = clamp(hsv.z, 0.0, 1.0);
    return hsv_to_rgb(hsv);
}

// ═══════════════════════════════════════════════════════════════════════
// ── Oklab Perceptual Color Space (Björn Ottosson) ───────────────────────
// Ported from ZenFilters Rust pipeline for perceptual color grading.
// Oklab is perceptually uniform: equal steps in oklab ≈ equal perceptual steps.
// ═══════════════════════════════════════════════════════════════════════

// ── Oklab Uniform Parameters ──────────────────────────────────────────────
uniform float uOklabHueShift;      // -1.0 .. 1.0, rotate hue in oklab space
uniform float uOklabSaturation;    // -1.0 .. 1.0, scale chroma in oklab space  
uniform float uOklabChroma;        // -1.0 .. 1.0, add/subtract chroma in oklab space
uniform float uOklabLightness;     // -1.0 .. 1.0, scale lightness in oklab space
uniform float uOklabContrast;      // -1.0 .. 1.0, S-curve contrast in L channel
uniform float uOklabTextureAmount; // 0.0 .. 1.0, blend texture-preserving amount

// ── Oklab ↔ sRGB Conversions ─────────────────────────────────────────────

vec3 srgb_to_linear_srgb(vec3 c) {
    // Assumes input is already linear RGB (from earlier srgb_to_linear call)
    return c;
}

vec3 oklab_to_srgb(vec3 lab) {
    // Oklab (L, a, b) → linear sRGB
    // Step 1: Oklab → LMS
    float l_ = lab.x + 0.3963377774 * lab.y + 0.2158037573 * lab.z;
    float m_ = lab.x - 0.1055613458 * lab.y - 0.0638541728 * lab.z;
    float s_ = lab.x - 0.0894841775 * lab.y - 1.2914855480 * lab.z;

    // Step 2: Cube → LMS nonlinear
    float l = l_ * l_ * l_;
    float m = m_ * m_ * m_;
    float s = s_ * s_ * s_;

    // Step 3: LMS → linear sRGB
    return vec3(
        +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    );
}

vec3 srgb_to_oklab(vec3 rgb) {
    // linear sRGB → Oklab (L, a, b)
    // Step 1: linear sRGB → LMS
    float l = 0.4122214708 * rgb.r + 0.5363325363 * rgb.g + 0.0514459929 * rgb.b;
    float m = 0.2119034982 * rgb.r + 0.6806995451 * rgb.g + 0.1073969566 * rgb.b;
    float s = 0.0883024619 * rgb.r + 0.2817188376 * rgb.g + 0.6299787005 * rgb.b;

    // Step 2: Cube root → LMS nonlinear (perceptually uniform)
    float l_ = pow(max(l, EPS), 1.0 / 3.0);
    float m_ = pow(max(m, EPS), 1.0 / 3.0);
    float s_ = pow(max(s, EPS), 1.0 / 3.0);

    // Step 3: LMS → Oklab
    return vec3(
        0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
        1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
        0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
    );
}

// ── Oklab LCh (Lightness, Chroma, Hue angle) ──────────────────────────────

vec3 oklab_to_lch(vec3 lab) {
    // Oklab (L, a, b) → LCh (L, C, h)
    float C = length(lab.yz);
    float h = atan(lab.z, lab.y); // [-PI, PI]
    return vec3(lab.x, C, h);
}

vec3 oklab_lch_to_lab(vec3 lch) {
    // LCh (L, C, h) → Oklab (L, a, b)
    return vec3(lch.x, lch.y * cos(lch.z), lch.y * sin(lch.z));
}

// ── Hue Rotation in Oklab ───────────────────────────────────────────────
// Based on Björn Ottosson's optimal hue rotation matrix.
// amount: -1.0..1.0 (0 = no change)
vec3 oklab_hue_rotate(vec3 lab, float amount) {
    if (abs(amount) < EPS) return lab;
    vec3 lch = oklab_to_lch(lab);
    float hue = lch.z + amount * 3.14159265; // rotate by amount radians
    return oklab_lch_to_lab(vec3(lch.x, lch.y, hue));
}

// ── Skin Tone Protection ───────────────────────────────────────────────────
// Protects skin tones during saturation/chroma changes.
// Returns a weight [0..1] where 1 = definitely skin, 0 = definitely not.
float oklab_skin_weight(vec3 lab) {
    float hue = atan(lab.z, lab.y); // [-PI, PI]
    float chroma = length(lab.yz);
    // Skin hue range in oklab: roughly 0.5-1.3 radians (orange-red range)
    // Also depends on moderate chroma and L > 0.3
    float hueDist = abs(mod(hue - 0.8, 2.0 * 3.14159265) - 3.14159265);
    float hueWeight = smoothstep(1.3, 0.5, hueDist);
    float chromaWeight = smoothstep(0.0, 0.15, chroma) * smoothstep(0.5, 0.1, chroma);
    float lWeight = smoothstep(0.2, 0.5, lab.x) * smoothstep(1.0, 0.6, lab.x);
    return clamp(hueWeight * chromaWeight * lWeight, 0.0, 1.0);
}

// ── Saturation (skin-protected) ───────────────────────────────────────────
// Scale chroma while preserving lightness and protecting skin tones.
vec3 oklab_saturation(vec3 lab, float sat, float skinProtection) {
    if (abs(sat) < EPS) return lab;
    float currentSat = length(lab.yz);
    if (currentSat < EPS) return lab;
    
    // Saturation: scale chroma proportionally
    // sat = -1.0 → desaturate to gray
    // sat = 1.0 → double chroma
    float chromaScale = 1.0 + sat;
    float skinWeight = skinProtection > 0.5 ? oklab_skin_weight(lab) : 0.0;
    
    float newChroma = currentSat * chromaScale;
    float chromaBlend = mix(newChroma, currentSat, skinWeight * abs(sat) * 0.7);
    
    float scale = (currentSat > EPS) ? (chromaBlend / currentSat) : 1.0;
    return vec3(lab.x, lab.y * scale, lab.z * scale);
}

// ── Chroma (absolute adjustment) ─────────────────────────────────────────
vec3 oklab_chroma(vec3 lab, float amount, float skinProtection) {
    if (abs(amount) < EPS) return lab;
    float currentChroma = length(lab.yz);
    float skinWeight = skinProtection > 0.5 ? oklab_skin_weight(lab) : 0.0;
    
    // amount: -1.0 = reduce to 25%, 0 = no change, 1.0 = increase to 4x
    float targetChroma = currentChroma * (1.0 + amount * 3.0);
    float blendedChroma = mix(targetChroma, currentChroma, skinWeight * abs(amount) * 0.5);
    
    float scale = (currentChroma > EPS) ? (blendedChroma / currentChroma) : 1.0;
    return vec3(lab.x, lab.y * scale, lab.z * scale);
}

// ── Lightness Scale ───────────────────────────────────────────────────────
vec3 oklab_lightness(vec3 lab, float amount) {
    if (abs(amount) < EPS) return lab;
    // amount: -1.0..1.0
    // S-curve lightness adjustment
    float L = lab.x;
    float mid = 0.5;
    float contrast = 1.0 + amount * 0.5;
    float newL = mid + (L - mid) * contrast;
    return vec3(newL, lab.y, lab.z);
}

// ── Oklab Contrast S-Curve ────────────────────────────────────────────────
vec3 oklab_contrast(vec3 lab, float amount) {
    if (abs(amount) < EPS) return lab;
    float L = lab.x;
    float mid = 0.5;
    // S-curve around mid gray
    float contrast = 1.0 + amount * 2.0;
    float newL = mid + (L - mid) * contrast;
    // Soft clamp to avoid out-of-range artifacts
    newL = clamp(newL, 0.0, 1.0);
    return vec3(newL, lab.y, lab.z);
}

// ── Texture-Preserving Oklab Adjust ───────────────────────────────────────
// Applies oklab adjustments while trying to preserve local texture detail.
vec3 oklab_texture_preserve(vec3 lab, vec2 uv) {
    if (uOklabTextureAmount < EPS) return lab;
    // Sample neighboring oklab values
    float eps = 1.0 / max(uResolution.x, uResolution.y) * 2.0;
    vec3 labL = srgb_to_oklab(srgb_to_linear(texture(uTexture, uv + vec2(-eps, 0.0))).rgb);
    vec3 labR = srgb_to_oklab(srgb_to_linear(texture(uTexture, uv + vec2(eps, 0.0))).rgb);
    vec3 labU = srgb_to_oklab(srgb_to_linear(texture(uTexture, uv + vec2(0.0, eps))).rgb);
    vec3 labD = srgb_to_oklab(srgb_to_linear(texture(uTexture, uv + vec2(0.0, -eps))).rgb);
    
    // Compute gradient magnitude in oklab space
    vec3 gradient = vec3(
        length(vec2(labL.y - lab.y, labU.z - lab.z)),
        length(vec2(labR.y - lab.y, labU.z - lab.z)),
        length(vec2(labL.x - lab.x, labR.x - lab.x))
    );
    float gradientMag = length(gradient);
    
    // Blend toward unmodified lab for high-gradient areas (edges/texture)
    float preserveWeight = smoothstep(0.01, 0.15, gradientMag) * uOklabTextureAmount;
    return mix(lab, lab, 1.0 - preserveWeight);
}

// ── Main Oklab Pipeline ────────────────────────────────────────────────────
vec3 apply_oklab_adjustments(vec3 color, vec2 uv) {
    // Convert linear RGB to Oklab
    vec3 lab = srgb_to_oklab(color);
    
    // Apply lightness/contrast adjustments (affects L only)
    lab = oklab_lightness(lab, uOklabLightness);
    lab = oklab_contrast(lab, uOklabContrast);
    
    // Apply chroma/saturation adjustments (affects a,b with skin protection)
    lab = oklab_saturation(lab, uOklabSaturation, 1.0);
    lab = oklab_chroma(lab, uOklabChroma, 1.0);
    
    // Apply hue rotation
    lab = oklab_hue_rotate(lab, uOklabHueShift);
    
    // Apply texture preservation
    lab = oklab_texture_preserve(lab, uv);
    
    // Convert back to linear RGB
    return oklab_to_srgb(lab);
}

// ═══════════════════════════════════════════════════════════════════════
// ── MAIN ─────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

void main() {
    vec2 uv = vTexCoord;

    // === Geometric Transform ===
    // 1. Flip
    if (uFlipHorizontal > 0.5) uv.x = 1.0 - uv.x;
    if (uFlipVertical > 0.5) uv.y = 1.0 - uv.y;

    // 2. Orientation rotation (90° multiples)
    if (uOrientationSteps == 1) uv = vec2(1.0 - uv.y, uv.x);
    else if (uOrientationSteps == 2) uv = vec2(1.0 - uv.x, 1.0 - uv.y);
    else if (uOrientationSteps == 3) uv = vec2(uv.y, 1.0 - uv.x);

    // 3. Fine rotation
    if (abs(uRotation) > 0.5) {
        vec2 center = uv - 0.5;
        float angleRad = radians(uRotation);
        float c = cos(angleRad);
        float s = sin(angleRad);
        uv = vec2(center.x * c - center.y * s, center.x * s + center.y * c) + 0.5;
    }

    // 4. Scale and offset
    if (uTransformScale > 0.0) {
        uv = (uv - 0.5) * (1.0 / max(uTransformScale, 0.1)) + 0.5;
        uv.x += uTransformXOffset * 0.01;
        uv.y += uTransformYOffset * 0.01;
    }

    // 4b. Aspect ratio
    if (abs(uTransformAspect) > EPS) {
        uv = (uv - 0.5);
        uv.x *= (1.0 + uTransformAspect * 0.5);
        uv += 0.5;
    }

    // 4c. Transform rotate (perspective panel fine rotation, degrees)
    if (abs(uTransformRotate) > 0.5) {
        vec2 center = uv - 0.5;
        float angleRad = radians(uTransformRotate);
        float c = cos(angleRad);
        float s = sin(angleRad);
        uv = vec2(center.x * c - center.y * s, center.x * s + center.y * c) + 0.5;
    }

    // 5. Perspective / distortion
    if (abs(uTransformDistortion) > EPS || abs(uTransformVertical) > EPS || abs(uTransformHorizontal) > EPS) {
        vec2 centered = uv - 0.5;
        float dist = length(centered);
        float r2 = dist * dist;
        float k = uTransformDistortion * 0.3;
        float radial = 1.0 + k * r2;
        vec2 persp = centered;
        persp.x += uTransformHorizontal * centered.y * centered.y * 0.3;
        persp.y += uTransformVertical * centered.x * centered.x * 0.3;
        uv = persp / radial + 0.5;
    }

    // 6. Lens correction
    vec3 originalLinear;
    {
        vec2 sampleUv = uv;
        if (abs(uLensDistortion) > EPS || abs(uLensVignette) > EPS || abs(uLensTca) > EPS) {
            vec2 centered = sampleUv - 0.5;
            float dist = length(centered);
            float r2 = dist * dist;
            // Focal length factor: shorter focal length = stronger effects
            float focalFactor = 50.0 / max(uLensFocalLength, 1.0);

            // Distortion correction
            float k1 = uLensDistortion * 0.15 * focalFactor;
            float radial = 1.0 + k1 * r2;
            vec2 uvCorrected = centered / radial + 0.5;

            // TCA: separate R/B channel radial offsets
            float tca = uLensTca * 0.02 * focalFactor;
            float tcaR = tca * r2;
            float tcaB = -tca * r2;
            vec2 centeredCorr = uvCorrected - 0.5;
            vec2 uvR = centeredCorr * (1.0 + tcaR) + 0.5;
            vec2 uvB = centeredCorr * (1.0 + tcaB) + 0.5;

            float r = texture(uTexture, uvR).r;
            float g = texture(uTexture, uvCorrected).g;
            float b = texture(uTexture, uvB).b;
            originalLinear = vec3(r, g, b);

            // Vignette correction: brighten edges
            float vignetteCorr = 1.0 + uLensVignette * 0.5 * r2 * focalFactor;
            originalLinear *= vignetteCorr;

            // Update uv for any downstream use
            uv = uvCorrected;
        } else {
            originalLinear = texture(uTexture, sampleUv).rgb;
        }
    }

    // Store original for film intensity blending
    vec3 noFilmColor = originalLinear;

    // === Step 1: sRGB to Linear (if input is sRGB) ===
    vec3 color = srgb_to_linear(originalLinear);

    // === Step 2: Tone level (影调) ===
    color = apply_tone_level(color);

    // === Step 3: Linear exposure ===
    color = apply_linear_exposure(color, uExposure);

    // === Step 4: Filmic brightness ===
    color = apply_filmic_exposure(color, uBrightness);

    // === Step 5: White balance ===
    color = apply_white_balance(color, uTemperature, uTint);

    // === Step 6: Green-Magenta tint ===
    color = apply_green_magenta(color);

    // === Step 6b: Noise reduction ===
    color = apply_noise_reduction(color);

    // === Step 6c: Local tint (shadow/highlight tinting) ===
    color = apply_local_tint(color);

    // === Step 6d: Edge light ===
    color = apply_edge_light(color);

    // === Step 6e: Color range (selective hue adjustment) ===
    color = apply_color_range(color);

    // === Step 7: Highlights adjustment ===
    color = apply_highlights_adjustment(color, uHighlights);

    // === Step 8: Tonal adjustments (contrast/shadows/whites/blacks) ===
    color = apply_tonal_adjustments(color, uContrast, uShadows, uWhites, uBlacks);

    // === Step 8b: Centre (midtone emphasis) ===
    color = apply_centre(color);

    // === Step 9: Creative color (saturation + vibrance) ===
    color = apply_creative_color(color, uSaturation, uVibrance);

    // === Step 10: HSL 8-color panel ===
    color = apply_hsl_panel(color);

    // === Step 11: Color grading ===
    color = apply_color_grading(color);

    // === Step 12: Color calibration ===
    color = apply_color_calibration(color);

    // === Step 12b: Color calibration shadows tint ===
    color = apply_color_calibration_shadows_tint(color);

    // === Step 12c: CDL color grading (Lift/Gamma/Gain per-channel offsets) ===
    color = apply_cdl_grading(color);

    // === Step 12d: Oklab Perceptual Color Space Adjustments ===
    // ZenFilters Oklab pipeline: hue rotation, saturation, chroma, lightness, contrast
    // in perceptually uniform oklab space with skin tone protection
    if (abs(uOklabHueShift) > EPS || abs(uOklabSaturation) > EPS ||
        abs(uOklabChroma) > EPS || abs(uOklabLightness) > EPS ||
        abs(uOklabContrast) > EPS) {
        color = apply_oklab_adjustments(color, uv);
    }

    // === Step 13: Local contrast (clarity/structure) ===
    color = apply_local_contrast(color, uv);

    // === Step 14: Sharpness ===
    color = apply_sharpness(color, uv);

    // === Step 15: Chromatic aberration ===
    color = apply_chromatic_aberration(color, uv);

    // === Step 16: Dehaze ===
    color = apply_dehaze(color);

    // === Step 17: Soft glow ===
    color = apply_soft_glow(color, uv);

    // === Step 18: Vignette ===
    color = apply_vignette(color, uv);

    // === Step 19: Creative light effects ===
    color = apply_glow(color);
    color = apply_halation(color);
    color = apply_flare(color);

    // === Step 20: Film simulation (complete: curve + grain + color + DR) ===
    vec3 filmColor = apply_film_simulation(color, uv);

    // === Step 20: Blend film with non-film based on intensity ===
    color = mix(color, filmColor, uFilmIntensity);

    // === Step 21: User tone curves ===
    color = apply_tone_curve(color);

    // === Step 21b: RGB curves ===
    color = apply_rgb_curves(color);

    // === Step 22: Color Science Tone Mapping ===
    // Mode ordinal matches ColorScience.Mode: 0=AGX, 1=ACES_2, 2=OPEN_DRT, 3=STANDARD
    if (uColorScienceMode == 0 || uAgXEnabled > 0.5) {
        color = apply_agx_tonemap(color);
    } else if (uColorScienceMode == 1) {
        color = apply_aces2_tonemap(color);
    } else if (uColorScienceMode == 2) {
        color = apply_opendrt_tonemap(color);
    }

    // === Step 23: Linear to sRGB ===
    color = linear_to_srgb(color);

    // === Step 24: Dither ===
    color += (gradient_noise(uv.x, uv.y) - 0.5) / 255.0;

    // === Step 25: LUT color grading ===
    if (uLutIntensity > 0.001) {
        // Proper 3D LUT sampling with edge padding
        vec3 lutCoord = clamp(color, 0.0, 1.0) * 0.9375 + 0.03125; // scale for 32-size LUT edge
        vec3 lutColor = texture(uLutTexture, lutCoord).rgb;
        color = mix(color, lutColor, uLutIntensity);
    }

    // === Step 26: Flow Mask blending ===
    if (uMaskIntensity > 0.001) {
        float maskAlpha = texture(uMaskTexture, vTexCoord).a * uMaskIntensity;
        vec3 original = noFilmColor;
        color = mix(original, color, maskAlpha);
    }

    // === Step 27: Clipping visualization ===
    if (uClippingPreview > 0.5) {
        color = apply_clipping_preview(color);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
