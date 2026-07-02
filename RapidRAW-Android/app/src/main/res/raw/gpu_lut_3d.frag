#version 300 es
precision highp float;

// ── 3D LUT Application Fragment Shader ────────────────────────────────
// Applies a 3D color lookup table (LUT) to the input image.
// The LUT is a 3D texture (GL_TEXTURE_3D) with trilinear filtering.
// Common sizes: 17x17x17, 33x33x33, 65x65x65.

// Input image texture
uniform sampler2D uTexture;   // Source image

// 3D LUT texture
uniform sampler3D uLutTexture; // 3D LUT for color transformation

// ── Controls ──────────────────────────────────────────────────────────
uniform float uLutIntensity;   // 0.0 .. 1.0, blend between original and LUT result
uniform float uLutSize;        // LUT size (e.g. 17, 33, 65). Default: 33.0

// Interpolated from vertex shader
in vec2 vTexCoord;
out vec4 fragColor;

// ── Constants ──────────────────────────────────────────────────────────
const float EPS = 1e-6;

// ═══════════════════════════════════════════════════════════════════════
// ── MAIN ─────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;

    // Skip if intensity is negligible
    if (uLutIntensity < EPS) {
        fragColor = vec4(color, 1.0);
        return;
    }

    // Clamp input color to [0, 1] before LUT lookup
    vec3 lutInput = clamp(color, 0.0, 1.0);

    // Scale and bias coordinates for correct edge sampling with trilinear
    // filtering. For a LUT of size N, we want to sample at positions
    // (0.5/N) to ((N-0.5)/N) to avoid bleeding at the boundary.
    float size = max(uLutSize, 2.0);  // Ensure minimum size of 2
    float halfTexel = 0.5 / size;
    float scale = 1.0 - 2.0 * halfTexel; // = (size - 1.0) / size
    vec3 lutCoord = lutInput * scale + halfTexel;

    // Sample the 3D LUT with hardware trilinear interpolation
    vec3 lutColor = texture(uLutTexture, lutCoord).rgb;

    // Blend between original and LUT result based on intensity
    vec3 result = mix(color, lutColor, uLutIntensity);

    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
