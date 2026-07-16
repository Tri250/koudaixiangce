// Skin Detection Compute Shader
//
// 4-method weighted fusion for robust skin-tone detection:
//   1. YCgCr color space (robust for Asian skin tones)
//   2. YCbCr color space (classic, Hsu 2002 region)
//   3. RGB ratio rules (Peer et al. 2003)
//   4. HSV hue/saturation/value
//
// Input:  RGBA texture (sRGB-encoded or linear RGB, controlled by params)
// Output: R8Unorm storage texture, R channel = skin confidence [0,1]
//
// All detection formulas operate on sRGB-encoded normalized [0,1] values,
// because the classic thresholds (R>95, Cb in [0.55,0.85], etc.) are
// calibrated for gamma-compressed sRGB. When the input is linear RGB
// (params.is_linear_rgb == 1), it is converted back to sRGB-encoded first.

struct SkinDetectionParams {
    threshold_low: f32,   // below this confidence -> 0.0
    threshold_high: f32,  // above this confidence -> 1.0
    weight_ycgcr: f32,
    weight_ycbcr: f32,
    weight_rgb: f32,
    weight_hsv: f32,
    is_linear_rgb: u32,   // 1 = input is linear RGB; 0 = input is sRGB encoded
    _pad0: u32,
}

@group(0) @binding(0) var src_texture: texture_2d<f32>;
@group(0) @binding(1) var dst_texture: texture_storage_2d<r8unorm, write>;
@group(0) @binding(2) var<uniform> params: SkinDetectionParams;

fn linear_to_srgb(c: vec3<f32>) -> vec3<f32> {
    let c_clamped = clamp(c, vec3<f32>(0.0), vec3<f32>(1.0));
    let cutoff = vec3<f32>(0.0031308);
    let a = vec3<f32>(0.055);
    let higher = (1.0 + a) * pow(c_clamped, vec3<f32>(1.0 / 2.4)) - a;
    let lower = c_clamped * 12.92;
    return select(higher, lower, c_clamped <= cutoff);
}

// Normalize input to sRGB-encoded [0,1] for the classic skin-detection formulas.
fn to_srgb_input(rgba: vec4<f32>) -> vec3<f32> {
    var rgb = clamp(rgba.rgb, vec3<f32>(0.0), vec3<f32>(1.0));
    if (params.is_linear_rgb == 1u) {
        rgb = linear_to_srgb(rgb);
    }
    return rgb;
}

// Smoothstep falloff outside a [lo, hi] box.
// Returns 1.0 inside the box, smoothly decaying to 0.0 across `band` outside.
fn smooth_box(v: f32, lo: f32, hi: f32, band: f32) -> f32 {
    let inner = min(v - lo, hi - v);
    if (inner >= 0.0) {
        return 1.0;
    }
    let dist = -inner;
    if (dist >= band) {
        return 0.0;
    }
    let t = 1.0 - dist / band;
    return t * t * (3.0 - 2.0 * t);
}

// Method 1: YCgCr color space (robust for Asian skin tones)
//   Y  =  0.25*R + 0.5*G + 0.25*B
//   Cg = -0.25*R + 0.5*G - 0.25*B
//   Cr =  0.5*R - 0.5*B
// Skin box: Cg in [-0.04, 0.04], Cr in [-0.04, 0.04].
// Probability = smooth distance from box boundary (joint on Cg and Cr).
fn detect_ycgcr(rgb: vec3<f32>) -> f32 {
    let cg = -0.25 * rgb.r + 0.5 * rgb.g - 0.25 * rgb.b;
    let cr = 0.5 * rgb.r - 0.5 * rgb.b;

    let cg_lo = -0.04;
    let cg_hi = 0.04;
    let cr_lo = -0.04;
    let cr_hi = 0.04;

    let band = 0.15;
    let p_cg = smooth_box(cg, cg_lo, cg_hi, band);
    let p_cr = smooth_box(cr, cr_lo, cr_hi, band);
    return p_cg * p_cr;
}

// Method 2: YCbCr color space (classic, Hsu 2002 region)
//   Y  =  0.299*R + 0.587*G + 0.114*B
//   Cb = -0.168736*R - 0.331264*G + 0.5*B + 0.5
//   Cr =  0.5*R - 0.418688*G - 0.081312*B + 0.5
// Skin box: Cb in [0.55, 0.85], Cr in [0.55, 0.85],
// combined with an elliptical boundary centered at (0.7, 0.7).
fn detect_ycbcr(rgb: vec3<f32>) -> f32 {
    let cb = -0.168736 * rgb.r - 0.331264 * rgb.g + 0.5 * rgb.b + 0.5;
    let cr = 0.5 * rgb.r - 0.418688 * rgb.g - 0.081312 * rgb.b + 0.5;

    let cb_lo = 0.55;
    let cb_hi = 0.85;
    let cr_lo = 0.55;
    let cr_hi = 0.85;

    let band = 0.08;
    let p_cb = smooth_box(cb, cb_lo, cb_hi, band);
    let p_cr = smooth_box(cr, cr_lo, cr_hi, band);
    let p_box = p_cb * p_cr;

    // Elliptical boundary centered at (0.7, 0.7), half-axes (0.15, 0.15).
    let ecx = cb - 0.7;
    let ecy = cr - 0.7;
    let ell = (ecx * ecx) / (0.15 * 0.15) + (ecy * ecy) / (0.15 * 0.15);

    var p_ell: f32;
    let ell_band = 0.3;
    if (ell <= 1.0 - ell_band) {
        p_ell = 1.0;
    } else if (ell >= 1.0) {
        p_ell = 0.0;
    } else {
        let t = (1.0 - ell) / ell_band;
        p_ell = t * t * (3.0 - 2.0 * t);
    }

    return p_box * p_ell;
}

// Method 3: RGB ratio rules (Peer et al. 2003, 8-bit sRGB scale)
//   c1: R > 95
//   c2: R > G > B
//   c3: max(R,G,B) - min(R,G,B) > 15
//   c4: |R - G| > 15
// All four satisfied -> 1.0, otherwise 0.25 * satisfied_count.
fn detect_rgb(rgb: vec3<f32>) -> f32 {
    // Scale normalized [0,1] sRGB to 8-bit [0,255].
    let r = rgb.r * 255.0;
    let g = rgb.g * 255.0;
    let b = rgb.b * 255.0;

    let c1 = r > 95.0;
    let c2 = (r > g) && (g > b);
    let mx = max(max(r, g), b);
    let mn = min(min(r, g), b);
    let c3 = (mx - mn) > 15.0;
    let c4 = abs(r - g) > 15.0;

    var count: f32 = 0.0;
    if c1 { count = count + 1.0; }
    if c2 { count = count + 1.0; }
    if c3 { count = count + 1.0; }
    if c4 { count = count + 1.0; }

    if c1 && c2 && c3 && c4 {
        return 1.0;
    }
    return count * 0.25;
}

// Method 4: HSV (hue near red/orange, moderate saturation, sufficient value)
//   V = max(R,G,B)
//   S = (max-min)/max   (if max > 0)
//   H = standard hexagonal hue in [0,1]
// Skin: H in [0, 0.12] ∪ [0.92, 1.0], S in [0.1, 0.6], V > 0.2.
// Gaussian falloff around hue and saturation centers.
fn detect_hsv(rgb: vec3<f32>) -> f32 {
    let mx = max(max(rgb.r, rgb.g), rgb.b);
    let mn = min(min(rgb.r, rgb.g), rgb.b);
    let delta = mx - mn;

    let v = mx;
    if (v <= 0.0001) {
        return 0.0;
    }

    // Value gate with soft falloff above 0.2.
    if (v <= 0.2) {
        return 0.0;
    }
    let p_v = clamp((v - 0.2) / 0.3, 0.0, 1.0);

    let s = delta / mx;
    // Saturation gate: skin S in [0.1, 0.6], Gaussian around center 0.35.
    if (s < 0.1 || s > 0.6) {
        return 0.0;
    }
    let s_center = 0.35;
    let s_sigma = 0.18;
    let p_s = exp(-((s - s_center) * (s - s_center)) / (2.0 * s_sigma * s_sigma));

    // Hue computation (hexagonal, normalized to [0,1]).
    var h: f32;
    if (delta < 0.0001) {
        h = 0.0;
    } else if (mx == rgb.r) {
        h = (rgb.g - rgb.b) / delta;
        if (h < 0.0) { h = h + 6.0; }
        h = h / 6.0;
    } else if (mx == rgb.g) {
        h = (rgb.b - rgb.r) / delta + 2.0;
        h = h / 6.0;
    } else {
        h = (rgb.r - rgb.g) / delta + 4.0;
        h = h / 6.0;
    }

    // Hue: skin H in [0, 0.12] ∪ [0.92, 1.0]. Centers at 0.06 and 0.96.
    // Use circular distance to account for hue wraparound.
    let h_a = 0.06;
    let h_b = 0.96;
    let da = min(abs(h - h_a), 1.0 - abs(h - h_a));
    let db = min(abs(h - h_b), 1.0 - abs(h - h_b));
    let d_min = min(da, db);
    let h_sigma = 0.09;
    let p_h = exp(-(d_min * d_min) / (2.0 * h_sigma * h_sigma));

    return p_s * p_h * p_v;
}

@compute @workgroup_size(16, 16)
fn detect_skin(@builtin(global_invocation_id) id: vec3<u32>) {
    let dims = textureDimensions(src_texture);
    if (id.x >= dims.x || id.y >= dims.y) {
        return;
    }

    let texel = textureLoad(src_texture, vec2<i32>(id.xy), 0);
    let rgb = to_srgb_input(texel);

    let p_ycgcr = detect_ycgcr(rgb);
    let p_ycbcr = detect_ycbcr(rgb);
    let p_rgb = detect_rgb(rgb);
    let p_hsv = detect_hsv(rgb);

    let w_sum = params.weight_ycgcr + params.weight_ycbcr
              + params.weight_rgb + params.weight_hsv;
    let w_safe = max(w_sum, 0.0001);
    let weighted = (params.weight_ycgcr * p_ycgcr
                  + params.weight_ycbcr * p_ycbcr
                  + params.weight_rgb * p_rgb
                  + params.weight_hsv * p_hsv) / w_safe;

    // Threshold with smooth band: >= high -> 1.0, <= low -> 0.0,
    // smoothstep interpolation in between to avoid hard edges.
    var mask: f32;
    if (weighted >= params.threshold_high) {
        mask = 1.0;
    } else if (weighted <= params.threshold_low) {
        mask = 0.0;
    } else {
        let span = max(params.threshold_high - params.threshold_low, 0.0001);
        let t = (weighted - params.threshold_low) / span;
        mask = t * t * (3.0 - 2.0 * t);
    }

    textureStore(dst_texture, vec2<i32>(id.xy), vec4<f32>(mask, 0.0, 0.0, 1.0));
}
