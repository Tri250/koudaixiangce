#version 300 es
precision highp float;

uniform sampler2D uTexture;       // Input image
uniform sampler2D uBlurTexture;   // Pre-blurred version of input
uniform int uEffectType;          // 0=none, 1=glow, 2=halation, 3=lens_flare

// Glow params
uniform float uGlowAmount;        // 0..1
uniform float uGlowThreshold;     // 0..1
uniform float uGlowWarmShift;     // 0..0.1
uniform float uGlowHighlightProtection; // 0..1

// Halation params
uniform float uHalationAmount;    // 0..1
uniform vec3 uHalationCoreColor;  // RGB (1.0, 0.15, 0.03)
uniform vec3 uHalationEdgeColor;  // RGB (1.0, 0.32, 0.10)
uniform float uHalationBrightnessAdapt; // 0..1
uniform float uHalationDesaturation;    // 0..1

// Lens Flare params
uniform float uFlareAmount;       // 0..1
uniform vec2 uFlareLightPos;      // normalized (0..1, 0..1)
uniform int uFlareGhostCount;
uniform int uFlareStreakCount;
uniform float uFlareChromaticOffset;

in vec2 vTexCoord;
out vec4 fragColor;

// Glow: screen blend blurred bright pixels with warm shift
vec3 applyGlow(vec3 base, vec3 blurred, float luminance) {
    // Extract bright pixels from blur
    vec3 glowLayer = blurred * step(uGlowThreshold, luminance);
    // Warm shift
    glowLayer.r *= (1.0 + uGlowWarmShift);
    glowLayer.b *= (1.0 - uGlowWarmShift);
    // Screen blend
    vec3 screened = 1.0 - (1.0 - base) * (1.0 - glowLayer * uGlowAmount);
    // Highlight protection
    float protection = smoothstep(uGlowHighlightProtection, 1.0, luminance);
    return mix(screened, base, protection * 0.7);
}

// Halation: add colored blurred layer based on luminance
vec3 applyHalation(vec3 base, vec3 blurred, float luminance) {
    // Mix core and edge colors based on luminance
    vec3 halationColor = mix(uHalationEdgeColor, uHalationCoreColor, smoothstep(0.15, 0.5, luminance));
    // Modulate by blur and luminance
    float strength = uHalationAmount * (uHalationBrightnessAdapt + (1.0 - uHalationBrightnessAdapt) * luminance);
    vec3 halationLayer = blurred * halationColor * strength;
    // Desaturate halation layer
    float halationLuma = dot(halationLayer, vec3(0.2126, 0.7152, 0.0722));
    halationLayer = mix(halationLayer, vec3(halationLuma), uHalationDesaturation);
    // Additive blend
    return base + halationLayer;
}

// Lens Flare: generate ghost elements and streaks
vec3 applyLensFlare(vec3 base, vec2 uv) {
    vec2 dir = uv - uFlareLightPos;
    float dist = length(dir);
    vec2 normDir = dist > 0.001 ? dir / dist : vec2(0.0);

    vec3 flare = vec3(0.0);

    // Ghost elements along the line from light to center
    for (int i = 0; i < 12; i++) {
        if (i >= uFlareGhostCount) break;
        float t = float(i + 1) / float(uFlareGhostCount + 1);
        vec2 ghostPos = uFlareLightPos + dir * t * 2.0;
        float ghostDist = length(uv - ghostPos);
        float ghostSize = 0.03 + 0.02 * float(i) / float(uFlareGhostCount);
        // Chromatic offset per ghost
        vec3 ghostColor = vec3(
            exp(-ghostDist * ghostDist / (2.0 * ghostSize * ghostSize * (1.0 + uFlareChromaticOffset * float(i)))),
            exp(-ghostDist * ghostDist / (2.0 * ghostSize * ghostSize)),
            exp(-ghostDist * ghostDist / (2.0 * ghostSize * ghostSize * (1.0 - uFlareChromaticOffset * float(i))))
        );
        // Anamorphic stretch (0.5x horizontal squeeze)
        ghostColor *= 1.5;
        flare += ghostColor * (0.5 / float(uFlareGhostCount));
    }

    // Streaks radiating from light source
    for (int i = 0; i < 12; i++) {
        if (i >= uFlareStreakCount) break;
        float angle = float(i) * 3.14159265 / float(uFlareStreakCount);
        vec2 streakDir = vec2(cos(angle), sin(angle));
        float streakDot = abs(dot(normDir, streakDir));
        float streakIntensity = pow(streakDot, 64.0) * exp(-dist * 3.0);
        // Alternating chromatic colors
        vec3 streakColor = (mod(float(i), 2.0) < 1.0) ?
            vec3(1.0, 0.9, 0.7) : vec3(0.7, 0.9, 1.0);
        flare += streakColor * streakIntensity * (0.3 / float(uFlareStreakCount));
    }

    // Light source core
    float coreDist = length(uv - uFlareLightPos);
    float coreIntensity = exp(-coreDist * coreDist / 0.002);
    flare += vec3(1.0, 0.95, 0.8) * coreIntensity;

    // Anamorphic horizontal stretch
    flare *= vec3(1.5, 1.0, 0.8);

    return base + flare * uFlareAmount;
}

void main() {
    vec3 base = texture(uTexture, vTexCoord).rgb;
    vec3 blurred = texture(uBlurTexture, vTexCoord).rgb;
    float luminance = dot(base, vec3(0.2126, 0.7152, 0.0722));

    vec3 result = base;

    if (uEffectType == 1) {
        result = applyGlow(base, blurred, luminance);
    } else if (uEffectType == 2) {
        result = applyHalation(base, blurred, luminance);
    } else if (uEffectType == 3) {
        result = applyLensFlare(base, vTexCoord);
    }

    fragColor = vec4(result, 1.0);
}
