// Sky replacement compositing shader
// Blends a new sky behind the foreground using a refined mask

struct SkyReplaceParams {
    width: f32,
    height: f32,
    feather: f32,
    color_match_strength: f32,
    horizon_adjust: f32,
}

@group(0) @binding(0) var<uniform> params: SkyReplaceParams;
@group(0) @binding(1) var<storage, read> foreground: array<f32>;  // Original image RGBA
@group(0) @binding(2) var<storage, read> sky_image: array<f32>;   // New sky RGBA
@group(0) @binding(3) var<storage, read> sky_mask: array<f32>;    // Sky mask 0-1
@group(0) @binding(4) var<storage, read_write> output: array<f32>; // Result RGBA

@compute @workgroup_size(16, 16)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    let x = gid.x;
    let y = gid.y;
    let w = u32(params.width);
    let h = u32(params.height);
    if (x >= w || y >= h) { return; }

    let idx = (y * w + x) * 4;
    let mask_idx = y * w + x;

    // Get mask value and apply horizon adjustment
    var alpha = sky_mask[mask_idx];
    let normalized_y = f32(y) / f32(h);
    alpha = alpha + params.horizon_adjust * 0.01 * (0.5 - normalized_y);
    alpha = clamp(alpha, 0.0, 1.0);

    // Apply feathering at edges using smoothstep
    if (params.feather > 0.0) {
        let edge_width = params.feather * 0.01;
        if (alpha > 0.0 && alpha < 1.0) {
            alpha = smoothstep(0.0, edge_width, alpha) * (1.0 - smoothstep(1.0 - edge_width, 1.0, alpha)) + smoothstep(1.0 - edge_width, 1.0, alpha);
        }
    }

    // Color match: blend sky color towards foreground color space
    let fg_r = foreground[idx];
    let fg_g = foreground[idx + 1];
    let fg_b = foreground[idx + 2];
    let sk_r = sky_image[idx];
    let sk_g = sky_image[idx + 1];
    let sk_b = sky_image[idx + 2];

    let cms = params.color_match_strength;
    let matched_sk_r = mix(sk_r, sk_r * (fg_r / max(sk_r, 0.001)), cms * 0.3);
    let matched_sk_g = mix(sk_g, sk_g * (fg_g / max(sk_g, 0.001)), cms * 0.3);
    let matched_sk_b = mix(sk_b, sk_b * (fg_b / max(sk_b, 0.001)), cms * 0.3);

    // Composite: foreground over sky using mask
    output[idx] = mix(matched_sk_r, fg_r, 1.0 - alpha);
    output[idx + 1] = mix(matched_sk_g, fg_g, 1.0 - alpha);
    output[idx + 2] = mix(matched_sk_b, fg_b, 1.0 - alpha);
    output[idx + 3] = 1.0;
}
