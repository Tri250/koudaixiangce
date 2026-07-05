#version 300 es
precision highp float;

// ── HSL Per-Channel Adjustment Fragment Shader ────────────────────────
// Adjusts Hue, Saturation, and Luminance independently for 8 color
// ranges: Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta.
// Uses smooth weighting with configurable span for each range.

// Input image texture
uniform sampler2D uTexture;   // Source image (linear RGB expected)

// ── HSL 8-Color Panel ────────────────────────────────────────────────
// Each color channel has Hue, Saturation, Luminance controls.
// Hue shifts are in [-1, 1] (mapped to degrees in hue space).
// Saturation shifts are in [-1, 1] (additive).
// Luminance shifts are in [-1, 1] (multiplicative around luma).

uniform float uHueRed;        // -1.0 .. 1.0
uniform float uSatRed;        // -1.0 .. 1.0
uniform float uLumRed;        // -1.0 .. 1.0
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

// Interpolated from vertex shader
in vec2 vTexCoord;
out vec4 fragColor;

// ── Constants ──────────────────────────────────────────────────────────
const float EPS = 1e-6;

// ── RGB <-> HSV ───────────────────────────────────────────────────────

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

// ── Luminance (BT.709) ────────────────────────────────────────────────

float get_luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// ── Hue Distance (circular) ───────────────────────────────────────────

float hue_delta(float h1, float h2) {
    float d = abs(h1 - h2);
    return (d > 180.0) ? (360.0 - d) : d;
}

// ── HSL Range Weight ──────────────────────────────────────────────────
// Returns smooth [0,1] weight for how much a hue belongs to a color range.
// center: hue angle in degrees, span: full angular width of the range.

float hsl_range_weight(float hue, float center, float span) {
    float halfSpan = span * 0.5;
    float dist = hue_delta(hue, center);
    return (dist <= halfSpan) ? (1.0 - dist / halfSpan) : 0.0;
}

// ── Apply HSL Panel ───────────────────────────────────────────────────

vec3 apply_hsl_panel(vec3 color) {
    vec3 hsv = rgb_to_hsv(color);
    float hue = hsv.x;

    // Color ranges: (center degrees, span degrees)
    // Red(358, 35), Orange(25, 45), Yellow(60, 40), Green(115, 90),
    // Aqua(180, 60), Blue(225, 60), Purple(280, 55), Magenta(330, 50)
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

    // Apply hue shift (scaled to degrees)
    hsv.x = mod(hsv.x + hueShift * 60.0 + 360.0, 360.0);

    // Apply saturation shift
    hsv.y = clamp(hsv.y + satShift, 0.0, 1.0);

    // Apply luminance shift (perceptual: keep luma, scale chroma)
    vec3 rgb = hsv_to_rgb(hsv);
    float luma = get_luma(rgb);
    rgb = luma + (rgb - luma) * (1.0 + lumShift);

    return rgb;
}

// ═══════════════════════════════════════════════════════════════════════
// ── MAIN ─────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;

    color = apply_hsl_panel(color);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
