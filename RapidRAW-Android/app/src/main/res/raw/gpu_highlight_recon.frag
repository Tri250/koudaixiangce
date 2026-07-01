#version 300 es
precision highp float;

uniform sampler2D uTexture;
uniform float uThreshold;        // clipping threshold
uniform int uMethod;              // 0=clip, 1=reconstruct, 2=color_blend
uniform float uBlendRadius;      // boundary blend radius in pixels

in vec2 vTexCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(uTexture, vTexCoord);

    if (uMethod == 0) {
        // Simple clip
        color.rgb = clamp(color.rgb, 0.0, uThreshold);
    } else if (uMethod == 1 || uMethod == 2) {
        // For GPU, apply soft compression above threshold
        // Full reconstruction requires multi-pass; this is the single-pass approximation
        float maxC = max(color.r, max(color.g, color.b));
        if (maxC > uThreshold) {
            float excess = maxC - uThreshold;
            float compression = uThreshold + excess * (1.0 - exp(-excess * 2.0)) / (excess + 0.5);
            float ratio = compression / max(maxC, 0.001);
            color.rgb *= mix(1.0, ratio, smoothstep(uThreshold, uThreshold + 0.5, maxC));
        }
    }

    fragColor = color;
}
