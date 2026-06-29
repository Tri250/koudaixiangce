#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform float uRedCyan;
uniform float uBlueYellow;

void main() {
    vec2 texelSize = 1.0 / uResolution;
    vec2 center = vec2(0.5, 0.5);
    vec2 direction = vTexCoord - center;

    float redOffset = uRedCyan * 0.01;
    float blueOffset = uBlueYellow * 0.01;

    vec2 redCoord = vTexCoord + direction * redOffset;
    vec2 greenCoord = vTexCoord;
    vec2 blueCoord = vTexCoord + direction * blueOffset;

    float r = texture(uTexture, redCoord).r;
    float g = texture(uTexture, greenCoord).g;
    float b = texture(uTexture, blueCoord).b;

    fragColor = vec4(r, g, b, 1.0);
}