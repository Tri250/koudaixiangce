#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform float uAmount;
uniform float uSize;
uniform float uRoughness;
uniform float uTime;

float hash(vec2 p) {
    float h = dot(p, vec2(127.1, 311.7));
    return fract(sin(h) * 43758.5453);
}

float hash3D(vec3 p) {
    float h = dot(p, vec3(127.1, 311.7, 74.7));
    return fract(sin(h) * 43758.5453);
}

float grainNoise(vec2 uv, float time, float size, float roughness) {
    vec2 pixelCoord = uv * uResolution / size;
    vec2 cellIndex = floor(pixelCoord);
    vec2 cellFrac = fract(pixelCoord);

    float t = time * roughness;

    float n00 = hash3D(vec3(cellIndex + vec2(0.0, 0.0), t));
    float n10 = hash3D(vec3(cellIndex + vec2(1.0, 0.0), t));
    float n01 = hash3D(vec3(cellIndex + vec2(0.0, 1.0), t));
    float n11 = hash3D(vec3(cellIndex + vec2(1.0, 1.0), t));

    vec2 smoothFrac = cellFrac * cellFrac * (3.0 - 2.0 * cellFrac);

    float n0 = mix(n00, n10, smoothFrac.x);
    float n1 = mix(n01, n11, smoothFrac.x);
    float noise = mix(n0, n1, smoothFrac.y);

    return (noise - 0.5) * 2.0;
}

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;

    float grainSize = max(uSize, 0.1);
    float roughness = clamp(uRoughness, 0.0, 1.0);

    float grain = grainNoise(vTexCoord, uTime, grainSize, roughness);

    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float grainAmount = uAmount * (1.0 - luminance * 0.5) * 0.1;

    vec3 result = color + grain * grainAmount;
    result = clamp(result, 0.0, 1.0);

    fragColor = vec4(result, 1.0);
}