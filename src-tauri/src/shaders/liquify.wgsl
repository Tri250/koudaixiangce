// Liquify mesh warp shader
// Applies displacement field from a mesh grid to warp the image
// Uses inverse mapping with bilinear interpolation

struct LiquifyParams {
    image_width: f32,
    image_height: f32,
    grid_spacing: f32,
    grid_cols: f32,
    grid_rows: f32,
    padding: f32,
}

@group(0) @binding(0) var<uniform> params: LiquifyParams;
@group(0) @binding(1) var<storage, read> displacement: array<f32>; // dx,dy pairs for each grid point
@group(0) @binding(2) var<storage, read> input_image: array<f32>;  // RGBA input
@group(0) @binding(3) var<storage, read_write> output_image: array<f32>; // RGBA output

// Bilinear interpolation of displacement field
fn bilinear_interp(x: f32, y: f32) -> vec2<f32> {
    let grid_x = x / params.grid_spacing;
    let grid_y = y / params.grid_spacing;
    let x0 = i32(floor(grid_x));
    let y0 = i32(floor(grid_y));
    let x1 = x0 + 1;
    let y1 = y0 + 1;
    let fx = grid_x - f32(x0);
    let fy = grid_y - f32(y0);

    // Get displacement at 4 corners (clamped to grid bounds)
    let cols = i32(params.grid_cols);
    let rows = i32(params.grid_rows);
    let cx0 = clamp(x0, 0, cols - 1);
    let cy0 = clamp(y0, 0, rows - 1);
    let cx1 = clamp(x1, 0, cols - 1);
    let cy1 = clamp(y1, 0, rows - 1);

    let d00 = vec2<f32>(displacement[(cy0 * cols + cx0) * 2], displacement[(cy0 * cols + cx0) * 2 + 1]);
    let d10 = vec2<f32>(displacement[(cy0 * cols + cx1) * 2], displacement[(cy0 * cols + cx1) * 2 + 1]);
    let d01 = vec2<f32>(displacement[(cy1 * cols + cx0) * 2], displacement[(cy1 * cols + cx0) * 2 + 1]);
    let d11 = vec2<f32>(displacement[(cy1 * cols + cx1) * 2], displacement[(cy1 * cols + cx1) * 2 + 1]);

    return mix(mix(d00, d10, fx), mix(d01, d11, fx), fy);
}

@compute @workgroup_size(16, 16)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    let x = gid.x;
    let y = gid.y;
    if (x >= u32(params.image_width) || y >= u32(params.image_height)) {
        return;
    }

    // Get displacement at current pixel
    let disp = bilinear_interp(f32(x), f32(y));

    // Inverse mapping: find source pixel
    let src_x = f32(x) - disp.x;
    let src_y = f32(y) - disp.y;

    // Bilinear sample from input image
    let w = params.image_width;
    let h = params.image_height;

    let sx0 = i32(clamp(floor(src_x), 0.0, w - 1.0));
    let sy0 = i32(clamp(floor(src_y), 0.0, h - 1.0));
    let sx1 = min(sx0 + 1, i32(w) - 1);
    let sy1 = min(sy0 + 1, i32(h) - 1);
    let fx = src_x - floor(src_x);
    let fy = src_y - floor(src_y);

    let idx00 = (sy0 * i32(w) + sx0) * 4;
    let idx10 = (sy0 * i32(w) + sx1) * 4;
    let idx01 = (sy1 * i32(w) + sx0) * 4;
    let idx11 = (sy1 * i32(w) + sx1) * 4;

    let out_idx = (y * u32(w) + x) * 4;
    for (var c = 0u; c < 4u; c++) {
        let v00 = input_image[idx00 + c];
        let v10 = input_image[idx10 + c];
        let v01 = input_image[idx01 + c];
        let v11 = input_image[idx11 + c];
        output_image[out_idx + c] = mix(mix(v00, v10, fx), mix(v01, v11, fx), fy);
    }
}
