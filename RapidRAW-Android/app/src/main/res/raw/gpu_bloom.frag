#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform float uAmount;
uniform float uThreshold;
uniform float uRadius;

vec3 gaussianBlur(sampler2D tex, vec2 uv, vec2 texelSize, float radius) {
    vec3 result = vec3(0.0);
    float totalWeight = 0.0;

    int kernelRadius = int(ceil(radius));
    kernelRadius = clamp(kernelRadius, 1, 6);

    for (int y = -6; y <= 6; y++) {
        for (int x = -6; x <= 6; x++) {
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

float rgbToLuminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec2 texelSize = 1.0 / uResolution;
    float radius = max(uRadius, 0.5);

    vec3 original = texture(uTexture, vTexCoord).rgb;

    float luminance = rgbToLuminance(original);
    float brightPass = smoothstep(uThreshold, uThreshold + 0.1, luminance);
    vec3 brightAreas = original * brightPass;

    vec3 bloom = gaussianBlur(uTexture, vTexCoord, texelSize, radius);
    float bloomLuma = rgbToLuminance(bloom);
    bloom *= smoothstep(uThreshold, uThreshold + 0.1, bloomLuma);

    vec3 result = original + bloom * uAmount * 0.5;
    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}