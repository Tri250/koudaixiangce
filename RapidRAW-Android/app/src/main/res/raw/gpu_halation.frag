#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform float uAmount;
uniform float uSpread;

vec3 gaussianBlurRed(sampler2D tex, vec2 uv, vec2 texelSize, float spread) {
    vec3 result = vec3(0.0);
    float totalWeight = 0.0;

    int kernelRadius = int(ceil(spread));
    kernelRadius = clamp(kernelRadius, 1, 6);

    for (int y = -6; y <= 6; y++) {
        for (int x = -6; x <= 6; x++) {
            if (abs(x) > kernelRadius || abs(y) > kernelRadius) continue;
            vec2 offset = vec2(float(x), float(y)) * texelSize;
            float dist = length(vec2(float(x), float(y)));
            float weight = exp(-0.5 * (dist * dist) / (spread * spread));
            float red = texture(tex, uv + offset).r;
            result += vec3(red, 0.0, 0.0) * weight;
            totalWeight += weight;
        }
    }

    return result / max(totalWeight, 0.0001);
}

float rgbToLuminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec2 texelSize = 1.0 / uResolution;
    float spread = max(uSpread, 0.5);

    vec3 original = texture(uTexture, vTexCoord).rgb;

    vec3 blurredRed = gaussianBlurRed(uTexture, vTexCoord, texelSize, spread);

    float luminance = rgbToLuminance(original);
    float highlightMask = smoothstep(0.7, 1.0, luminance);

    vec3 halation = blurredRed * highlightMask * uAmount * 0.5;

    vec3 result = original + halation;
    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}