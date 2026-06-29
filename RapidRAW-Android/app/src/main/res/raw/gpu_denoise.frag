#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform float uSigmaSpatial;
uniform float uSigmaRange;

const int KERNEL_RADIUS = 2;
const int KERNEL_SIZE = 5;

void main() {
    vec2 texelSize = 1.0 / uResolution;
    vec3 centerColor = texture(uTexture, vTexCoord).rgb;
    vec3 sum = vec3(0.0);
    float totalWeight = 0.0;

    float spatialSigma = max(uSigmaSpatial, 0.1);
    float rangeSigma = max(uSigmaRange, 0.01);

    for (int y = -KERNEL_RADIUS; y <= KERNEL_RADIUS; y++) {
        for (int x = -KERNEL_RADIUS; x <= KERNEL_RADIUS; x++) {
            vec2 offset = vec2(float(x), float(y)) * texelSize;
            vec3 sampleColor = texture(uTexture, vTexCoord + offset).rgb;

            float spatialDist = length(vec2(float(x), float(y)));
            float spatialWeight = exp(-0.5 * (spatialDist * spatialDist) / (spatialSigma * spatialSigma));

            float rangeDist = length(sampleColor - centerColor);
            float rangeWeight = exp(-0.5 * (rangeDist * rangeDist) / (rangeSigma * rangeSigma));

            float weight = spatialWeight * rangeWeight;
            sum += sampleColor * weight;
            totalWeight += weight;
        }
    }

    vec3 result = sum / max(totalWeight, 0.0001);
    fragColor = vec4(result, 1.0);
}