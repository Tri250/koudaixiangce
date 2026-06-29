#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform int uMode;
uniform float uAgXContrast;
uniform float uAgXPedestal;
uniform float uExposure;
uniform float uGamma;

// ACES Filmic Tone Mapping
vec3 acesToneMap(vec3 color) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((color * (a * color + b)) / (color * (c * color + d) + e), 0.0, 1.0);
}

// Reinhard Tone Mapping
vec3 reinhardToneMap(vec3 color) {
    return color / (color + vec3(1.0));
}

// Reinhard Extended (Filmic)
vec3 reinhardExtendedToneMap(vec3 color, float maxWhite) {
    vec3 numerator = color * (1.0 + color / (maxWhite * maxWhite));
    return numerator / (1.0 + color);
}

// Filmic Tone Mapping (Uncharted 2 based)
vec3 filmicToneMap(vec3 color) {
    float shoulderStrength = 0.22;
    float linearStrength = 0.3;
    float linearAngle = 0.1;
    float toeStrength = 0.2;
    float toeNumerator = 0.01;
    float toeDenominator = 0.3;
    float linearWhite = 11.2;

    vec3 x = max(color - 0.004, 0.0);
    vec3 result = (x * (shoulderStrength * x + linearAngle * linearStrength) + toeStrength * toeNumerator) /
                  (x * (shoulderStrength * x + linearStrength) + toeStrength * toeDenominator);
    return result;
}

// AgX Tone Mapping
vec3 agxDefaultContrast(vec3 x) {
    vec3 x2 = x * x;
    vec3 x4 = x2 * x2;

    return 15.5 * x4 * x2
         - 40.14 * x4 * x
         + 31.96 * x4
         - 6.868 * x2 * x
         + 0.4298 * x2
         + 0.1191 * x
         - 0.00232;
}

vec3 agxToneMap(vec3 color, float contrast, float pedestal) {
    float range = 12.0;
    float slope = 1.0;
    float power = 1.0;
    float sat = 1.0;

    vec3 x = color;
    x = clamp(x, 0.0, range);

    float c = clamp(contrast, 0.0, 2.0);
    float p = clamp(pedestal, 0.0, 2.0);

    vec3 y = agxDefaultContrast(x);

    vec3 result = pow(y * slope + p, vec3(power));
    result = clamp(result, 0.0, 1.0);

    float luma = dot(result, vec3(0.2126, 0.7152, 0.0722));
    vec3 desat = vec3(luma);
    result = mix(desat, result, sat);

    return result;
}

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;

    color *= uExposure;

    vec3 toneMapped;

    if (uMode == 0) {
        toneMapped = agxToneMap(color, uAgXContrast, uAgXPedestal);
    } else if (uMode == 1) {
        toneMapped = acesToneMap(color);
    } else if (uMode == 2) {
        toneMapped = reinhardToneMap(color);
    } else {
        toneMapped = filmicToneMap(color);
    }

    toneMapped = pow(clamp(toneMapped, 0.0, 1.0), vec3(1.0 / uGamma));

    fragColor = vec4(toneMapped, 1.0);
}