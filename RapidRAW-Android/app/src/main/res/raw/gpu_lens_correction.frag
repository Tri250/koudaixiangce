#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform float uDistortion;
uniform float uVignette;
uniform float uTca;
uniform float uFocalLength;

void main() {
    vec2 center = vec2(0.5, 0.5);
    float aspectRatio = uResolution.x / uResolution.y;

    vec2 uv = vTexCoord - center;
    float r = length(uv);
    float r2 = r * r;
    float r4 = r2 * r2;
    float r6 = r4 * r2;

    float focalLength = max(uFocalLength, 0.1);
    float k1 = uDistortion / focalLength;

    float distortion = 1.0 + k1 * r2;
    vec2 undistortedUV = center + uv * distortion;

    float vignetteAmount = uVignette;
    float vignette = 1.0 - r2 * vignetteAmount * 0.5;
    vignette = clamp(vignette, 0.0, 1.0);

    float tca = uTca * 0.01;
    float rDist = 1.0 + (k1 + tca) * r2;
    float bDist = 1.0 + (k1 - tca) * r2;

    vec2 redUV = center + uv * rDist;
    vec2 blueUV = center + uv * bDist;

    float rChannel = texture(uTexture, redUV).r;
    float gChannel = texture(uTexture, undistortedUV).g;
    float bChannel = texture(uTexture, blueUV).b;

    vec3 color = vec3(rChannel, gChannel, bChannel);
    color *= vignette;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}