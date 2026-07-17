// Skin smoothing shader
// Applies bilateral-filter-based smoothing weighted by a skin mask

struct SkinSmoothParams {
    width: f32,
    height: f32,
    radius: f32,
    spatial_sigma: f32,
    range_sigma: f32,
    strength: f32,
    texture_preserve: f32,
}

@group(0) @binding(0) var<uniform> params: SkinSmoothParams;
@group(0) @binding(1) var<storage, read> input_image: array<f32>;
@group(0) @binding(2) var<storage, read> skin_mask: array<f32>; // 0.0-1.0 skin probability
@group(0) @binding(3) var<storage, read_write> output_image: array<f32>;

fn gaussian(x: f32, sigma: f32) -> f32 {
    return exp(-0.5 * x * x / (sigma * sigma));
}

@compute @workgroup_size(8, 8)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    let x = gid.x;
    let y = gid.y;
    let w = u32(params.width);
    let h = u32(params.height);
    if (x >= w || y >= h) { return; }

    let center_idx = (y * w + x) * 4;
    let center_r = input_image[center_idx];
    let center_g = input_image[center_idx + 1];
    let center_b = input_image[center_idx + 2];
    let mask_val = skin_mask[y * w + x];

    if (mask_val < 0.01) {
        // Not skin, keep original
        output_image[center_idx] = center_r;
        output_image[center_idx + 1] = center_g;
        output_image[center_idx + 2] = center_b;
        output_image[center_idx + 3] = input_image[center_idx + 3];
        return;
    }

    // Bilateral filter
    let r = i32(params.radius);
    var sum_r = 0.0; var sum_g = 0.0; var sum_b = 0.0; var weight_sum = 0.0;

    for (var dy = -r; dy <= r; dy++) {
        for (var dx = -r; dx <= r; dx++) {
            let nx = clamp(i32(x) + dx, 0, i32(w) - 1);
            let ny = clamp(i32(y) + dy, 0, i32(h) - 1);
            let n_idx = (ny * i32(w) + nx) * 4;

            let nr = input_image[n_idx];
            let ng = input_image[n_idx + 1];
            let nb = input_image[n_idx + 2];

            let spatial_dist = f32(dx * dx + dy * dy);
            let range_dist = (nr - center_r) * (nr - center_r) + (ng - center_g) * (ng - center_g) + (nb - center_b) * (nb - center_b);

            let w_spatial = gaussian(spatial_dist, params.spatial_sigma);
            let w_range = gaussian(range_dist, params.range_sigma);
            let w_mask = skin_mask[ny * i32(w) + nx];
            let weight = w_spatial * w_range * w_mask;

            sum_r += nr * weight;
            sum_g += ng * weight;
            sum_b += nb * weight;
            weight_sum += weight;
        }
    }

    if (weight_sum > 0.0) {
        let smooth_r = sum_r / weight_sum;
        let smooth_g = sum_g / weight_sum;
        let smooth_b = sum_b / weight_sum;

        // Blend between smoothed and original based on strength and mask
        let blend = params.strength * mask_val;
        output_image[center_idx] = mix(center_r, smooth_r, blend);
        output_image[center_idx + 1] = mix(center_g, smooth_g, blend);
        output_image[center_idx + 2] = mix(center_b, smooth_b, blend);
    } else {
        output_image[center_idx] = center_r;
        output_image[center_idx + 1] = center_g;
        output_image[center_idx + 2] = center_b;
    }
    output_image[center_idx + 3] = input_image[center_idx + 3];
}
