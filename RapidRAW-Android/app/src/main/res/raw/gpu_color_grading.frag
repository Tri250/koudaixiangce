#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform vec3 uShadows;
uniform vec3 uMidtones;
uniform vec3 uHighlights;
uniform float uBlend;

float rgbToLuminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 ascCdl(vec3 color, vec3 slope, vec3 offset, vec3 power) {
    vec3 result = color * slope + offset;
    result = pow(clamp(result, 0.0, 1.0), power);
    return result;
}

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;

    vec3 shadowsColor = ascCdl(color, uShadows, vec3(0.0), vec3(1.0));
    vec3 midtonesColor = ascCdl(color, vec3(1.0), uMidtones, vec3(1.0));
    vec3 highlightsColor = ascCdl(color, uHighlights, vec3(0.0), vec3(1.0));

    float luminance = rgbToLuminance(color);

    float shadowWeight = 1.0 - smoothstep(0.1, 0.5, luminance);
    float highlightWeight = smoothstep(0.5, 0.9, luminance);
    float midtoneWeight = 1.0 - shadowWeight - highlightWeight;

    vec3 graded = shadowsColor * shadowWeight + midtonesColor * midtoneWeight + highlightsColor * highlightWeight;

    vec3 result = mix(color, graded, uBlend);
    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}