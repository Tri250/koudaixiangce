#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform float uAmount;
uniform float uMidpoint;
uniform float uRoundness;
uniform float uFeather;

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;

    vec2 center = vec2(0.5, 0.5);
    float aspectRatio = uResolution.x / uResolution.y;
    vec2 distVec = (vTexCoord - center) * vec2(aspectRatio, 1.0);
    float dist = length(distVec);

    float midpoint = clamp(uMidpoint, 0.0, 1.0);
    float roundness = clamp(uRoundness, -1.0, 1.0);
    float feather = clamp(uFeather, 0.001, 1.0);

    float baseDist = dist * (1.0 + roundness * (dist * dist - dist));
    float vignette = smoothstep(midpoint + feather, midpoint - feather, baseDist);
    vignette = mix(1.0, vignette, uAmount);

    vec3 result = color * vignette;
    fragColor = vec4(result, 1.0);
}