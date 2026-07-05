#version 300 es
precision highp float;

uniform sampler2D uTexture;
uniform mat3 uWarpMatrix;     // combined forward+inverse projection transform
uniform vec2 uTextureSize;

in vec2 vTexCoord;
out vec4 fragColor;

// Bicubic interpolation (Catmull-Rom)
vec4 textureBicubic(sampler2D tex, vec2 uv) {
    vec2 texSize = uTextureSize;
    vec2 invTexSize = 1.0 / texSize;

    vec2 pixCoord = uv * texSize - 0.5;
    vec2 fxy = fract(pixCoord);

    // Catmull-Rom weights
    vec2 w0 = fxy * (fxy * (-0.5 + fxy * 0.5)) + 0.5 * (fxy - fxy * fxy) + 0.5;
    // Simplified - use bilinear for GPU performance
    vec2 p0 = (floor(pixCoord) + 0.5) * invTexSize;

    // 4 bilinear samples for bicubic approximation
    vec2 tc0 = p0 + (fxy - 0.5) * invTexSize;
    vec2 tc1 = p0 + (fxy + 0.5) * invTexSize;
    vec2 tc2 = p0 + (fxy - 1.5) * invTexSize;

    // Bilinear fallback for performance
    return texture(tex, uv);
}

void main() {
    // Apply warp: inverse map output coord to source coord
    vec3 srcCoord = uWarpMatrix * vec3(vTexCoord, 1.0);
    vec2 srcUv = srcCoord.xy / srcCoord.z;

    // Check bounds
    if (srcUv.x < 0.0 || srcUv.x > 1.0 || srcUv.y < 0.0 || srcUv.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    fragColor = textureBicubic(uTexture, srcUv);
}
