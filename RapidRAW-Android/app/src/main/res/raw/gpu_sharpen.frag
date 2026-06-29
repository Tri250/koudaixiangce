#version 300 es
precision highp float;

// ── Unsharp Mask Sharpening Fragment Shader ───────────────────────────
// Implements unsharp mask (USM) sharpening: subtract a blurred version
// from the original, then add the difference back scaled by strength.
// This is the standard sharpening approach used in photo editing.

// Input image texture
uniform sampler2D uTexture;   // Source image

// ── Sharpen Controls ──────────────────────────────────────────────────
uniform float uSharpness;     // 0.0 .. 4.0, sharpening intensity (0 = no sharpening)
uniform float uRadius;        // 0.5 .. 3.0, blur radius for unsharp mask (default: 1.0)

uniform vec2 uResolution;     // Image dimensions in pixels (width, height)

// Interpolated from vertex shader
in vec2 vTexCoord;
out vec4 fragColor;

// ── Constants ──────────────────────────────────────────────────────────
const float EPS = 1e-6;

// ── Luminance (BT.709) ────────────────────────────────────────────────

float get_luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// ── Gaussian Blur (5x5 kernel) ────────────────────────────────────────
// Approximation of a Gaussian blur for the unsharp mask low-pass filter.
// Uses a fixed 5x5 kernel with normalized weights.

vec3 gaussian_blur_5x5(vec2 uv, float radius) {
    vec2 texel = 1.0 / uResolution;

    // 5x5 Gaussian kernel weights (sigma ~1.0, pre-normalized)
    // Row 0 and 4: 1  4  7  4  1
    // Row 1 and 3: 4 16 26 16  4
    // Row 2:       7 26 41 26  7
    // Sum = 273
    const float kernel[25] = float[25](
        1.0,  4.0,  7.0,  4.0,  1.0,
        4.0, 16.0, 26.0, 16.0,  4.0,
        7.0, 26.0, 41.0, 26.0,  7.0,
        4.0, 16.0, 26.0, 16.0,  4.0,
        1.0,  4.0,  7.0,  4.0,  1.0
    );

    vec3 sum = vec3(0.0);
    float weightSum = 0.0;

    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            int idx = (y + 2) * 5 + (x + 2);
            float w = kernel[idx];
            vec2 offset = vec2(float(x), float(y)) * texel * radius;
            sum += texture(uTexture, uv + offset).rgb * w;
            weightSum += w;
        }
    }

    return sum / max(weightSum, EPS);
}

// ── 3x3 Laplacian High-Pass ───────────────────────────────────────────
// Alternative high-pass filter using a 3x3 Laplacian kernel.
// Produces sharper but potentially noisier results.

vec3 laplacian_highpass(vec2 uv, float radius) {
    vec2 texel = 1.0 / uResolution;
    vec3 center = texture(uTexture, uv).rgb;

    // Sample 4-connected neighbors
    vec3 top    = texture(uTexture, uv + vec2(0.0, -1.0) * texel * radius).rgb;
    vec3 bottom = texture(uTexture, uv + vec2(0.0,  1.0) * texel * radius).rgb;
    vec3 left   = texture(uTexture, uv + vec2(-1.0, 0.0) * texel * radius).rgb;
    vec3 right  = texture(uTexture, uv + vec2( 1.0, 0.0) * texel * radius).rgb;

    // Sample diagonal neighbors (reduced weight)
    vec3 tl = texture(uTexture, uv + vec2(-1.0, -1.0) * texel * radius).rgb;
    vec3 tr = texture(uTexture, uv + vec2( 1.0, -1.0) * texel * radius).rgb;
    vec3 bl = texture(uTexture, uv + vec2(-1.0,  1.0) * texel * radius).rgb;
    vec3 br = texture(uTexture, uv + vec2( 1.0,  1.0) * texel * radius).rgb;

    // Laplacian: center * 8 - sum(neighbors), with diagonals at 0.707 weight
    vec3 neighbors = top + bottom + left + right +
                     (tl + tr + bl + br) * 0.707;
    float diagWeight = 4.0 * 0.707;
    float weightSum = 4.0 + diagWeight;

    return center - neighbors / weightSum;
}

// ═══════════════════════════════════════════════════════════════════════
// ── MAIN ─────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;

    // Skip if sharpness is negligible
    if (uSharpness < EPS) {
        fragColor = vec4(color, 1.0);
        return;
    }

    float radius = max(uRadius, 0.5);

    // Method: Unsharp Mask
    // 1. Create blurred (low-pass) version
    vec3 blurred = gaussian_blur_5x5(vTexCoord, radius);

    // 2. Compute high-pass = original - blurred
    vec3 highPass = color - blurred;

    // 3. Add high-pass back, scaled by sharpness
    vec3 result = color + highPass * uSharpness;

    // Optional: luminance-weighted sharpening to reduce color halos
    // Sharpen primarily in luminance, less in chroma
    float lumaOrig = get_luma(color);
    float lumaResult = get_luma(result);
    vec3 chromaOrig = color - vec3(lumaOrig);
    vec3 chromaResult = result - vec3(lumaResult);

    // Blend: 80% luminance sharpening, 20% chroma sharpening
    vec3 chromaBlended = mix(chromaOrig, chromaResult, 0.2);
    result = vec3(lumaResult) + chromaBlended;

    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
