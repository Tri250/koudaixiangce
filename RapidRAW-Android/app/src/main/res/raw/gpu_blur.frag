#version 300 es
precision highp float;

// ── Gaussian Blur (Separable 2-Pass) Fragment Shader ──────────────────
// Implements a separable Gaussian blur for efficient two-pass rendering.
// Pass 1 (horizontal): blur along X axis, render to intermediate FBO.
// Pass 2 (vertical): blur along Y axis, render to final target.
//
// The kernel size is controlled by uBlurRadius (in pixels).
// Uses linear sampling optimization where possible.

// Input image texture
uniform sampler2D uTexture;   // Source image (pass 1: original; pass 2: horizontally blurred)

// ── Blur Controls ─────────────────────────────────────────────────────
uniform float uBlurRadius;    // 0.0 .. 20.0, blur radius in pixels
uniform float uBlurDirection; // 0.0 = horizontal, 1.0 = vertical (select pass)
uniform float uBlurQuality;   // 0.0 .. 1.0, quality (controls sample count)

uniform vec2 uResolution;     // Image dimensions in pixels (width, height)

// Interpolated from vertex shader
in vec2 vTexCoord;
out vec4 fragColor;

// ── Constants ──────────────────────────────────────────────────────────
const float EPS = 1e-6;
const int MAX_SAMPLES = 23; // Maximum half-kernel size (must be odd for center sample)

// ── Gaussian Weight Calculation ───────────────────────────────────────

float gaussian_weight(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma + EPS));
}

// ═══════════════════════════════════════════════════════════════════════
// ── MAIN ─────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;

    // Skip if blur radius is negligible
    if (uBlurRadius < 0.5) {
        fragColor = vec4(color, 1.0);
        return;
    }

    // Determine blur direction
    vec2 direction;
    if (uBlurDirection < 0.5) {
        direction = vec2(1.0, 0.0); // Horizontal pass
    } else {
        direction = vec2(0.0, 1.0); // Vertical pass
    }

    // Texel size for offset calculation
    vec2 texelSize = 1.0 / uResolution;

    // Sigma controls the spread of the Gaussian
    // A good rule of thumb: sigma ≈ radius / 2.5
    float sigma = max(uBlurRadius / 2.5, 0.5);

    // Number of samples based on quality setting
    // Higher quality = more samples = smoother but slower
    int halfKernel = int(uBlurRadius * (0.3 + uBlurQuality * 0.7));
    halfKernel = clamp(halfKernel, 1, MAX_SAMPLES);

    // Accumulate weighted samples
    vec3 sum = vec3(0.0);
    float weightSum = 0.0;

    // Center sample (full weight)
    float centerWeight = gaussian_weight(0.0, sigma);
    sum += color * centerWeight;
    weightSum += centerWeight;

    // Symmetric sampling: iterate from 1 to halfKernel
    for (int i = 1; i <= MAX_SAMPLES; i++) {
        if (i > halfKernel) break;

        float w = gaussian_weight(float(i), sigma);

        // Positive offset sample
        vec2 offset = direction * texelSize * float(i);
        vec3 samplePos = texture(uTexture, vTexCoord + offset).rgb;
        sum += samplePos * w;

        // Negative offset sample (symmetric)
        vec3 sampleNeg = texture(uTexture, vTexCoord - offset).rgb;
        sum += sampleNeg * w;

        weightSum += w * 2.0;
    }

    // Normalize by total weight
    vec3 result = sum / max(weightSum, EPS);

    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
