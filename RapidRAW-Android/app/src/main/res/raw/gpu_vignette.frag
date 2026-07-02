#version 300 es
precision highp float;

// ── Vignette Effect Fragment Shader ───────────────────────────────────
// Applies a configurable vignette (darkening/brightening of edges) with
// controls for amount, midpoint, roundness, and feather.

// Input image texture
uniform sampler2D uTexture;   // Source image

// ── Vignette Controls ─────────────────────────────────────────────────
uniform float uVignette;          // -1.0 .. 1.0, positive = darken edges, negative = brighten
uniform float uVignetteMidpoint;  // 0.0 .. 1.0, center of vignette falloff (0 = center, 1 = edge)
uniform float uVignetteRoundness; // -1.0 .. 1.0, shape of vignette (negative = rectangular, positive = round)
uniform float uVignetteFeather;   // 0.0 .. 1.0, softness of vignette edge transition

uniform vec2 uResolution;         // Image dimensions in pixels (width, height)

// Interpolated from vertex shader
in vec2 vTexCoord;
out vec4 fragColor;

// ── Constants ──────────────────────────────────────────────────────────
const float EPS = 1e-6;

// ── Smoothstep helpers ────────────────────────────────────────────────

float smoothstep_custom(float e0, float e1, float x) {
    float t = clamp((x - e0) / max(e1 - e0, EPS), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// ═══════════════════════════════════════════════════════════════════════
// ── MAIN ─────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;

    // Skip if vignette amount is negligible
    if (abs(uVignette) < EPS) {
        fragColor = vec4(color, 1.0);
        return;
    }

    // Compute distance from center, normalized so corners are ~1.0
    vec2 centered = vTexCoord - 0.5;

    // Apply roundness: adjust aspect ratio for elliptical vignette
    // Positive roundness = round, negative = more rectangular
    float aspect = uResolution.x / max(uResolution.y, 1.0);
    vec2 shapedCenter = centered;
    shapedCenter.x *= mix(1.0, 1.0 / max(aspect, EPS), 0.5 + uVignetteRoundness * 0.25);
    shapedCenter.y *= mix(1.0, aspect, 0.5 + uVignetteRoundness * 0.25);

    float dist = length(shapedCenter) * 1.414; // normalize to [0,1] at corners

    // Midpoint: controls where the vignette falloff begins
    // Higher midpoint = larger unaffected center area
    float start = uVignetteMidpoint * 0.7;

    // Feather: controls how gradually the vignette transitions
    // Higher feather = softer, wider transition
    float end = start + uVignetteFeather * 0.3 + 0.05;
    end = clamp(end, start + 0.01, 1.0);

    // Compute vignette multiplier
    float vignetteAmount;
    if (uVignette > 0.0) {
        // Darken edges
        vignetteAmount = 1.0 - smoothstep_custom(start, end, dist) * uVignette;
    } else {
        // Brighten edges (inverse vignette / light leak effect)
        vignetteAmount = 1.0 + smoothstep_custom(start, end, dist) * (-uVignette);
    }

    // Clamp to prevent extreme brightening
    vignetteAmount = clamp(vignetteAmount, 0.0, 2.0);

    fragColor = vec4(clamp(color * vignetteAmount, 0.0, 1.0), 1.0);
}
