#version 300 es
precision highp float;

// ── Film Grain Fragment Shader ────────────────────────────────────────
// Realistic film grain simulation using Poisson disk sampling for spatial
// distribution and Gaussian distribution for intensity values.
// Grain is more visible in shadows and midtones, matching real film behavior.

// Input image texture
uniform sampler2D uTexture;   // Source image

// ── Grain Controls ────────────────────────────────────────────────────
uniform float uGrain;         // 0.0 .. 1.0, overall grain intensity
uniform float uGrainSize;     // 0.5 .. 3.0, grain clump size (larger = coarser grain)
uniform float uGrainRoughness;// 0.0 .. 1.0, grain edge roughness (0 = soft, 1 = harsh)

uniform vec2 uResolution;     // Image dimensions in pixels

// Interpolated from vertex shader
in vec2 vTexCoord;
out vec4 fragColor;

// ── Constants ──────────────────────────────────────────────────────────
const float PI = 3.14159265358979;
const float EPS = 1e-6;

// ── Luminance (BT.709) ────────────────────────────────────────────────

float get_luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// ── Hash Functions ─────────────────────────────────────────────────────
// Integer hash for generating pseudo-random values from coordinates.

uint hash_uint(uint x) {
    x = ((x >> 16u) ^ x) * 0x45d9f3bu;
    x = ((x >> 16u) ^ x) * 0x45d9f3bu;
    x = (x >> 16u) ^ x;
    return x;
}

float hash_float(vec2 p) {
    uint h = hash_uint(floatBitsToUint(p.x)) + hash_uint(floatBitsToUint(p.y)) * 127u;
    return float(h) / 4294967296.0;
}

// ── Poisson Disk Grain Pattern ────────────────────────────────────────
// Generates a spatially-varying grain pattern using Poisson-disk-like
// distribution. Each "grain clump" is placed at a hashed position,
// and its influence falls off with distance.

float poisson_grain(vec2 uv, float size) {
    // Scale UV by grain size to control clump spacing
    vec2 scaledUV = uv * uResolution / max(size, 0.5);

    // Find the integer grid cell
    vec2 cell = floor(scaledUV);
    vec2 local = scaledUV - cell;

    // Sample 4 candidate positions within the cell (rotated grid for Poisson-like distribution)
    float minDist = 1.0;
    float grainValue = 0.0;

    // Golden angle rotation for Poisson disk approximation
    const float GOLDEN_ANGLE = 2.39996323;

    for (int i = 0; i < 4; i++) {
        // Hash-based position within cell
        float angle = float(i) * GOLDEN_ANGLE;
        vec2 offset = vec2(cos(angle), sin(angle)) * 0.5 + 0.5;

        vec2 grainPos = offset;
        grainPos.x += hash_float(cell + vec2(float(i), 0.0)) * 0.5;
        grainPos.y += hash_float(cell + vec2(0.0, float(i))) * 0.5;
        grainPos = fract(grainPos);

        float dist = length(local - grainPos);
        if (dist < minDist) {
            minDist = dist;
            // Gaussian intensity based on hash
            float rnd = hash_float(cell * 7.0 + vec2(float(i)));
            // Box-Muller transform for approximate Gaussian
            float r = sqrt(max(-2.0 * log(max(rnd, EPS)), 0.0));
            float theta = 2.0 * PI * hash_float(cell * 13.0 + vec2(float(i) + 0.5));
            grainValue = r * cos(theta);
        }
    }

    return grainValue;
}

// ── Gaussian Grain (simpler, smoother) ────────────────────────────────

float gaussian_grain(vec2 uv, float size) {
    vec2 scaledUV = uv * uResolution / max(size, 0.5);
    float ix = floor(scaledUV.x);
    float iy = floor(scaledUV.y);

    float rnd1 = hash_float(vec2(ix, iy));
    float rnd2 = hash_float(vec2(ix + 0.5, iy + 0.7));

    // Box-Muller transform for Gaussian distribution
    float r = sqrt(max(-2.0 * log(max(rnd1, EPS)), 0.0));
    float theta = 2.0 * PI * rnd2;
    return r * cos(theta);
}

// ═══════════════════════════════════════════════════════════════════════
// ── MAIN ─────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;

    // Skip if grain intensity is negligible
    if (uGrain < EPS) {
        fragColor = vec4(color, 1.0);
        return;
    }

    float luma = get_luma(color);

    // Grain is more visible in shadows and midtones (matching real film)
    // Shadows: high visibility; Midtones: moderate; Highlights: reduced
    float grainAmount = 1.0 - abs(luma - 0.5) * 1.5;
    grainAmount = clamp(grainAmount, 0.15, 1.0);

    // Blend between Poisson disk (structured) and Gaussian (smooth) grain
    // Roughness controls the mix: 0 = smooth Gaussian, 1 = structured Poisson
    float poissonNoise = poisson_grain(vTexCoord, uGrainSize);
    float gaussianNoise = gaussian_grain(vTexCoord, uGrainSize);
    float noise = mix(gaussianNoise, poissonNoise, uGrainRoughness);

    // Scale noise by grain amount and intensity
    float grainStrength = uGrain * grainAmount * 0.3;

    // Apply noise to all channels (luminance grain, minimal chroma grain)
    vec3 grainResult = color + noise * grainStrength;

    // Add slight chroma grain (much weaker than luma grain)
    float chromaNoise = gaussian_grain(vTexCoord + vec2(100.0, 200.0), uGrainSize * 1.5);
    float chromaStrength = grainStrength * 0.15; // chroma grain is much weaker
    float lum = get_luma(grainResult);
    grainResult = lum + (grainResult - lum) * (1.0 + chromaNoise * chromaStrength);

    fragColor = vec4(clamp(grainResult, 0.0, 1.0), 1.0);
}
