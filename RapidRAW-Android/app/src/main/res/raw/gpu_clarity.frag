#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform float uClarity;
uniform float uDehaze;

vec3 largeRadiusBlur(sampler2D tex, vec2 uv, vec2 texelSize) {
    vec3 result = vec3(0.0);
    float totalWeight = 0.0;

    float largeRadius = 15.0;

    for (int y = -7; y <= 7; y++) {
        for (int x = -7; x <= 7; x++) {
            float fx = float(x);
            float fy = float(y);
            vec2 offset = vec2(fx, fy) * texelSize * 2.0;
            float dist = length(vec2(fx, fy));
            float weight = exp(-0.5 * (dist * dist) / (largeRadius * largeRadius));
            result += texture(tex, uv + offset).rgb * weight;
            totalWeight += weight;
        }
    }

    return result / max(totalWeight, 0.0001);
}

float rgbToLuminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec2 texelSize = 1.0 / uResolution;

    vec3 original = texture(uTexture, vTexCoord).rgb;
    vec3 blurred = largeRadiusBlur(uTexture, vTexCoord, texelSize);

    float origLuma = rgbToLuminance(original);
    float blurLuma = rgbToLuminance(blurred);

    float clarityAmount = uClarity * 0.5;
    float clarityMask = 1.0 - abs(origLuma - blurLuma) * 2.0;

    vec3 clarityResult = original + (original - blurred) * clarityAmount * clarityMask;

    float dehazeAmount = uDehaze * 0.5;
    float hazeMask = 1.0 - origLuma;
    vec3 dehaze = original - origLuma * dehazeAmount;
    vec3 dehazeResult = dehaze / max(1.0 - dehazeAmount * hazeMask * 0.5, 0.01);

    float dehazeWeight = clamp(uDehaze, 0.0, 1.0);
    vec3 result = mix(clarityResult, dehazeResult, dehazeWeight);

    float clarityWeight = clamp(uClarity, 0.0, 1.0);
    result = mix(original, result, clarityWeight + dehazeWeight);

    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}