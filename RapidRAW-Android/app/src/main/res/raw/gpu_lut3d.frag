#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform sampler3D uLutTexture;
uniform float uLutIntensity;

vec3 tetrahedralInterpolation(sampler3D lut, vec3 color) {
    float lutSize = 33.0;
    float scale = (lutSize - 1.0) / lutSize;
    float offset = 0.5 / lutSize;

    vec3 scaledColor = color * scale + offset;
    vec3 cellCoord = scaledColor * (lutSize - 1.0);
    vec3 cell0 = floor(cellCoord);
    vec3 frac = cellCoord - cell0;
    vec3 f1 = 1.0 - frac;

    vec3 i0 = cell0 / (lutSize - 1.0);
    vec3 i1 = (cell0 + 1.0) / (lutSize - 1.0);

    vec3 c000 = texture(lut, vec3(i0.x, i0.y, i0.z)).rgb;
    vec3 c100 = texture(lut, vec3(i1.x, i0.y, i0.z)).rgb;
    vec3 c010 = texture(lut, vec3(i0.x, i1.y, i0.z)).rgb;
    vec3 c110 = texture(lut, vec3(i1.x, i1.y, i0.z)).rgb;
    vec3 c001 = texture(lut, vec3(i0.x, i0.y, i1.z)).rgb;
    vec3 c101 = texture(lut, vec3(i1.x, i0.y, i1.z)).rgb;
    vec3 c011 = texture(lut, vec3(i0.x, i1.y, i1.z)).rgb;
    vec3 c111 = texture(lut, vec3(i1.x, i1.y, i1.z)).rgb;

    vec3 result;
    if (frac.x > frac.y) {
        if (frac.y > frac.z) {
            result = c000 + (c100 - c000) * frac.x + (c110 - c100) * frac.y + (c111 - c110) * frac.z;
        } else if (frac.x > frac.z) {
            result = c000 + (c100 - c000) * frac.x + (c111 - c101) * frac.y + (c101 - c100) * frac.z;
        } else {
            result = c000 + (c111 - c011) * frac.x + (c010 - c000) * frac.y + (c011 - c010) * frac.z;
        }
    } else {
        if (frac.z > frac.y) {
            result = c000 + (c110 - c010) * frac.x + (c010 - c000) * frac.y + (c111 - c110) * frac.z;
        } else if (frac.z > frac.x) {
            result = c000 + (c011 - c001) * frac.x + (c111 - c011) * frac.y + (c001 - c000) * frac.z;
        } else {
            result = c000 + (c010 - c000) * frac.x + (c110 - c010) * frac.y + (c111 - c110) * frac.z;
        }
    }

    return result;
}

void main() {
    vec3 color = texture(uTexture, vTexCoord).rgb;
    vec3 lutColor = tetrahedralInterpolation(uLutTexture, color);
    vec3 result = mix(color, lutColor, uLutIntensity);
    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}