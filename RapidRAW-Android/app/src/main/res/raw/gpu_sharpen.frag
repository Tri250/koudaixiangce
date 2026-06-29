#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform float uAmount;
uniform float uRadius;
uniform float uThreshold;

const int MAX_KERNEL_SIZE = 9;

vec3 gaussianBlur(sampler2D tex, vec2 uv, vec2 texelSize, float radius) {
    vec3 result = vec3(0.0);
    float totalWeight = 0.0;

    int kernelRadius = int(ceil(radius));
    kernelRadius = clamp(kernelRadius, 1, 4);

    for (int y = -4; y <= 4; y++) {
        for (int x = -4; x <= 4; x++) {
            if (abs(x) > kernelRadius || abs(y) > kernelRadius) continue;
            vec2 offset = vec2(float(x), float(y)) * texelSize;
            float dist = length(vec2(float(x), float(y)));
            float weight = exp(-0.5 * (dist * dist) / (radius * radius));
            result += texture(tex, uv + offset).rgb * weight;
            totalWeight += weight;
        }
    }

    return result / max(totalWeight, 0.0001);
}

void main() {
    vec2 texelSize = 1.0 / uResolution;
    float radius = max(uRadius, 0.5);

    vec3 original = texture(uTexture, vTexCoord).rgb;
    vec3 blurred = gaussianBlur(uTexture, vTexCoord, texelSize, radius);

    vec3 detail = original - blurred;
    float detailMagnitude = length(detail);

    float threshold = max(uThreshold, 0.0);
    float mask = smoothstep(threshold, threshold + 0.01, detailMagnitude);

    vec3 sharpened = original + detail * uAmount * mask;
    fragColor = vec4(sharpened, 1.0);
}