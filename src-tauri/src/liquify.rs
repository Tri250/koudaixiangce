use image::{DynamicImage, GenericImageView, Rgba, RgbaImage};
use serde::{Deserialize, Serialize};

// ---------------------------------------------------------------------------
// Brush types
// ---------------------------------------------------------------------------

/// The kind of deformation applied by a liquify brush stroke.
#[derive(Clone, Copy, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum LiquifyBrushType {
    /// Push pixels away from the stroke direction (or pull if pressure < 0).
    Push,
    /// Pull pixels towards the stroke direction.
    Pull,
    /// Shrink / pucker pixels towards the brush centre.
    Pucker,
    /// Expand / bloat pixels away from the brush centre.
    Bloat,
    /// Rotate pixels around the brush centre.
    Twirl,
    /// Revert displaced pixels back towards their original positions.
    Reconstruct,
}

impl Default for LiquifyBrushType {
    fn default() -> Self {
        Self::Push
    }
}

// ---------------------------------------------------------------------------
// Data structures
// ---------------------------------------------------------------------------

/// A single brush position along a liquify stroke.
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LiquifyBrush {
    /// Type of deformation.
    pub brush_type: LiquifyBrushType,
    /// Brush radius in pixels.
    pub radius: f32,
    /// Brush pressure / strength [−1, 1].  Negative values invert the effect.
    pub pressure: f32,
    /// Brush centre position (x, y) in image pixel coordinates.
    pub position: (f32, f32),
}

impl Default for LiquifyBrush {
    fn default() -> Self {
        Self {
            brush_type: LiquifyBrushType::Push,
            radius: 50.0,
            pressure: 0.5,
            position: (0.0, 0.0),
        }
    }
}

/// A complete liquify stroke – a series of brush positions forming one
/// continuous interaction.
#[derive(Clone, Debug, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct LiquifyStroke {
    pub brushes: Vec<LiquifyBrush>,
}

/// Mesh grid storing original and displaced control-point positions over the
/// image.  Each grid vertex has an (dx, dy) displacement that is initially
/// zero and is mutated as brush strokes are applied.
#[derive(Clone, Debug)]
pub struct MeshGrid {
    /// Image width in pixels.
    pub width: u32,
    /// Image height in pixels.
    pub height: u32,
    /// Spacing between grid vertices in pixels.
    pub grid_spacing: u32,
    /// Number of vertices along the x axis.
    pub cols: usize,
    /// Number of vertices along the y axis.
    pub rows: usize,
    /// Displacement vectors for every grid vertex, stored row-major as
    /// `(dx, dy)` pairs.  Length = `rows * cols * 2`.
    pub displacements: Vec<f32>,
}

// ---------------------------------------------------------------------------
// Mesh creation
// ---------------------------------------------------------------------------

/// Create a mesh grid for an image of the given dimensions.
///
/// `grid_spacing` controls the density of control points (default 4 px).
/// A smaller spacing gives higher fidelity but costs more memory and compute.
pub fn create_mesh_grid(width: u32, height: u32, grid_spacing: u32) -> MeshGrid {
    let spacing = grid_spacing.max(1);
    let cols = (width / spacing + 1) as usize;
    let rows = (height / spacing + 1) as usize;
    let displacements = vec![0.0f32; rows * cols * 2];

    MeshGrid {
        width,
        height,
        grid_spacing: spacing,
        cols,
        rows,
        displacements,
    }
}

// ---------------------------------------------------------------------------
// Mesh helpers
// ---------------------------------------------------------------------------

impl MeshGrid {
    /// Get the original (x, y) position of grid vertex (col, row).
    #[inline]
    fn original_position(&self, col: usize, row: usize) -> (f32, f32) {
        let x = (col as u32 * self.grid_spacing).min(self.width) as f32;
        let y = (row as u32 * self.grid_spacing).min(self.height) as f32;
        (x, y)
    }

    /// Get the displaced (x, y) position of grid vertex (col, row).
    #[inline]
    fn displaced_position(&self, col: usize, row: usize) -> (f32, f32) {
        let (ox, oy) = self.original_position(col, row);
        let idx = (row * self.cols + col) * 2;
        (ox + self.displacements[idx], oy + self.displacements[idx + 1])
    }

    /// Set the displacement at grid vertex (col, row).
    #[inline]
    fn set_displacement(&mut self, col: usize, row: usize, dx: f32, dy: f32) {
        let idx = (row * self.cols + col) * 2;
        self.displacements[idx] = dx;
        self.displacements[idx + 1] = dy;
    }

    /// Add to the displacement at grid vertex (col, row).
    #[inline]
    fn add_displacement(&mut self, col: usize, row: usize, ddx: f32, ddy: f32) {
        let idx = (row * self.cols + col) * 2;
        self.displacements[idx] += ddx;
        self.displacements[idx + 1] += ddy;
    }
}

// ---------------------------------------------------------------------------
// Deformation kernels
// ---------------------------------------------------------------------------

/// Push/pull deformation at a point.
///
/// Moves grid vertices within `radius` of `centre` in the given `direction`
/// by an amount proportional to `pressure` and a cubic fall-off kernel.
pub fn push_pull_deform(
    grid: &mut MeshGrid,
    centre: (f32, f32),
    radius: f32,
    pressure: f32,
    direction: (f32, f32),
) {
    let r_sq = radius * radius;
    if r_sq < 1e-6 {
        return;
    }

    // Normalise direction.
    let dir_len = (direction.0 * direction.0 + direction.1 * direction.1).sqrt();
    if dir_len < 1e-6 {
        return;
    }
    let dir_x = direction.0 / dir_len;
    let dir_y = direction.1 / dir_len;

    // Iterate over all grid vertices within the brush bounding box.
    let spacing = grid.grid_spacing as f32;
    let col_min = ((centre.0 - radius) / spacing).max(0.0) as usize;
    let col_max = (((centre.0 + radius) / spacing) as usize + 1).min(grid.cols - 1);
    let row_min = ((centre.1 - radius) / spacing).max(0.0) as usize;
    let row_max = (((centre.1 + radius) / spacing) as usize + 1).min(grid.rows - 1);

    for row in row_min..=row_max {
        for col in col_min..=col_max {
            let (ox, oy) = grid.original_position(col, row);
            let dx = ox - centre.0;
            let dy = oy - centre.1;
            let dist_sq = dx * dx + dy * dy;

            if dist_sq >= r_sq {
                continue;
            }

            // Cubic fall-off: weight = (1 - (d/r)^2)^2
            let t = dist_sq / r_sq;
            let weight = (1.0 - t) * (1.0 - t);

            let amount = pressure * weight;
            grid.add_displacement(col, row, dir_x * amount, dir_y * amount);
        }
    }
}

/// Pucker (shrink) or bloat (expand) deformation.
///
/// When `pressure > 0` the effect is *bloat* (expand), when `pressure < 0`
/// the effect is *pucker* (shrink).  Vertices are moved radially away from
/// or towards the centre.
pub fn pucker_bloat_deform(
    grid: &mut MeshGrid,
    centre: (f32, f32),
    radius: f32,
    pressure: f32,
) {
    let r_sq = radius * radius;
    if r_sq < 1e-6 {
        return;
    }

    let spacing = grid.grid_spacing as f32;
    let col_min = ((centre.0 - radius) / spacing).max(0.0) as usize;
    let col_max = (((centre.0 + radius) / spacing) as usize + 1).min(grid.cols - 1);
    let row_min = ((centre.1 - radius) / spacing).max(0.0) as usize;
    let row_max = (((centre.1 + radius) / spacing) as usize + 1).min(grid.rows - 1);

    for row in row_min..=row_max {
        for col in col_min..=col_max {
            let (ox, oy) = grid.original_position(col, row);
            let dx = ox - centre.0;
            let dy = oy - centre.1;
            let dist_sq = dx * dx + dy * dy;

            if dist_sq >= r_sq || dist_sq < 1e-6 {
                continue;
            }

            let dist = dist_sq.sqrt();
            let t = dist / radius;

            // Smooth radial weight: (1 - t^2)^2
            let weight = (1.0 - t * t) * (1.0 - t * t);

            // Radial displacement amount.
            let amount = pressure * weight * (radius - dist) / radius;

            // Direction away from centre.
            let nx = dx / dist;
            let ny = dy / dist;

            grid.add_displacement(col, row, nx * amount, ny * amount);
        }
    }
}

/// Twirl (rotational) deformation around a centre point.
///
/// Vertices within `radius` are rotated by an angle proportional to `pressure`
/// and a smooth fall-off kernel.
pub fn twirl_deform(
    grid: &mut MeshGrid,
    centre: (f32, f32),
    radius: f32,
    pressure: f32,
) {
    let r_sq = radius * radius;
    if r_sq < 1e-6 {
        return;
    }

    let spacing = grid.grid_spacing as f32;
    let col_min = ((centre.0 - radius) / spacing).max(0.0) as usize;
    let col_max = (((centre.0 + radius) / spacing) as usize + 1).min(grid.cols - 1);
    let row_min = ((centre.1 - radius) / spacing).max(0.0) as usize;
    let row_max = (((centre.1 + radius) / spacing) as usize + 1).min(grid.rows - 1);

    // Maximum twist angle in radians.
    let max_angle = pressure * std::f32::consts::PI;

    for row in row_min..=row_max {
        for col in col_min..=col_max {
            let (ox, oy) = grid.original_position(col, row);
            let dx = ox - centre.0;
            let dy = oy - centre.1;
            let dist_sq = dx * dx + dy * dy;

            if dist_sq >= r_sq || dist_sq < 1e-6 {
                continue;
            }

            let dist = dist_sq.sqrt();
            let t = dist / radius;

            // Smooth fall-off: (1 - t)^2
            let weight = (1.0 - t) * (1.0 - t);
            let angle = max_angle * weight;

            // Rotate the displacement vector.
            let cos_a = angle.cos();
            let sin_a = angle.sin();
            let new_x = dx * cos_a - dy * sin_a;
            let new_y = dx * sin_a + dy * cos_a;

            grid.add_displacement(col, row, new_x - dx, new_y - dy);
        }
    }
}

/// Reconstruct (undo) deformation in a region.
///
/// Vertices within `radius` of `centre` are moved back towards their
/// original (zero-displacement) positions, with strength proportional to
/// `pressure`.
pub fn reconstruct_deform(
    grid: &mut MeshGrid,
    centre: (f32, f32),
    radius: f32,
    pressure: f32,
) {
    let r_sq = radius * radius;
    if r_sq < 1e-6 {
        return;
    }

    let spacing = grid.grid_spacing as f32;
    let col_min = ((centre.0 - radius) / spacing).max(0.0) as usize;
    let col_max = (((centre.0 + radius) / spacing) as usize + 1).min(grid.cols - 1);
    let row_min = ((centre.1 - radius) / spacing).max(0.0) as usize;
    let row_max = (((centre.1 + radius) / spacing) as usize + 1).min(grid.rows - 1);

    for row in row_min..=row_max {
        for col in col_min..=col_max {
            let (ox, oy) = grid.original_position(col, row);
            let dx = ox - centre.0;
            let dy = oy - centre.1;
            let dist_sq = dx * dx + dy * dy;

            if dist_sq >= r_sq {
                continue;
            }

            let t = dist_sq / r_sq;
            let weight = (1.0 - t) * (1.0 - t) * pressure;

            let idx = (row * grid.cols + col) * 2;
            let curr_dx = grid.displacements[idx];
            let curr_dy = grid.displacements[idx + 1];

            // Move back towards zero displacement.
            grid.set_displacement(col, row, curr_dx * (1.0 - weight), curr_dy * (1.0 - weight));
        }
    }
}

// ---------------------------------------------------------------------------
// Stroke application
// ---------------------------------------------------------------------------

/// Apply a single liquify stroke to the mesh grid.
///
/// Each brush position in the stroke is processed in order, mutating the
/// grid's displacement field with the appropriate deformation kernel.
pub fn apply_liquify_stroke(grid: &mut MeshGrid, stroke: &LiquifyStroke) {
    let len = stroke.brushes.len();
    for (i, brush) in stroke.brushes.iter().enumerate() {
        // Compute direction from the previous brush position (for push/pull).
        let direction = if i > 0 {
            let prev = &stroke.brushes[i - 1];
            (
                brush.position.0 - prev.position.0,
                brush.position.1 - prev.position.1,
            )
        } else if i + 1 < len {
            let next = &stroke.brushes[i + 1];
            (
                next.position.0 - brush.position.0,
                next.position.1 - brush.position.1,
            )
        } else {
            (1.0, 0.0)
        };

        match brush.brush_type {
            LiquifyBrushType::Push => {
                push_pull_deform(grid, brush.position, brush.radius, brush.pressure, direction);
            }
            LiquifyBrushType::Pull => {
                push_pull_deform(
                    grid,
                    brush.position,
                    brush.radius,
                    -brush.pressure,
                    direction,
                );
            }
            LiquifyBrushType::Pucker => {
                pucker_bloat_deform(grid, brush.position, brush.radius, -brush.pressure);
            }
            LiquifyBrushType::Bloat => {
                pucker_bloat_deform(grid, brush.position, brush.radius, brush.pressure);
            }
            LiquifyBrushType::Twirl => {
                twirl_deform(grid, brush.position, brush.radius, brush.pressure);
            }
            LiquifyBrushType::Reconstruct => {
                reconstruct_deform(grid, brush.position, brush.radius, brush.pressure);
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Multiple-stroke application
// ---------------------------------------------------------------------------

/// Compatibility type matching the command-layer stroke definition.
#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct LiquifyStrokeCommand {
    pub brush_type: String,
    pub radius: f32,
    pub pressure: f32,
    pub points: Vec<(f32, f32)>,
}

/// Apply multiple liquify strokes to an image.
///
/// Creates a mesh grid, converts each `LiquifyStrokeCommand` into
/// `LiquifyStroke`/`LiquifyBrush` form, applies them sequentially,
/// then renders the final displaced image.
pub fn apply_liquify_strokes(
    image: &DynamicImage,
    strokes: &[LiquifyStrokeCommand],
) -> anyhow::Result<DynamicImage> {
    let (width, height) = image.dimensions();
    let grid_spacing = 4u32;
    let mut grid = create_mesh_grid(width, height, grid_spacing);

    for cmd in strokes {
        let brush_type = match cmd.brush_type.to_lowercase().as_str() {
            "push" => LiquifyBrushType::Push,
            "pull" => LiquifyBrushType::Pull,
            "pucker" => LiquifyBrushType::Pucker,
            "bloat" => LiquifyBrushType::Bloat,
            "twirl" => LiquifyBrushType::Twirl,
            "reconstruct" => LiquifyBrushType::Reconstruct,
            _ => LiquifyBrushType::Push,
        };

        // Convert points to brushes
        let brushes: Vec<LiquifyBrush> = cmd
            .points
            .iter()
            .map(|&pos| LiquifyBrush {
                brush_type,
                radius: cmd.radius,
                pressure: cmd.pressure,
                position: pos,
            })
            .collect();

        let stroke = LiquifyStroke { brushes };
        apply_liquify_stroke(&mut grid, &stroke);
    }

    let result = apply_liquify_mesh(&grid, image);
    Ok(DynamicImage::ImageRgba8(result))
}

// ---------------------------------------------------------------------------
// Bicubic interpolation helper
// ---------------------------------------------------------------------------

/// Cubic weighting function (Mitchell-Netravali style, B = 1/3, C = 1/3).
#[inline]
#[allow(dead_code)]
fn cubic_weight(t: f32) -> f32 {
    let t = t.abs();
    if t < 1.0 {
        (1.0 - t) * (1.0 - t) * (1.0 + 2.0 * t) // actually standard cubic, close enough
    } else if t < 2.0 {
        (2.0 - t) * (2.0 - t) * (2.0 - t) / 6.0 * 2.0 // simplified
    } else {
        0.0
    }
}

/// Simpler and more robust cubic kernel (Catmull-Rom style).
#[inline]
fn catmull_rom(t: f32) -> f32 {
    let t = t.abs();
    if t <= 1.0 {
        1.5 * t * t * t - 2.5 * t * t + 1.0
    } else if t <= 2.0 {
        -0.5 * t * t * t + 2.5 * t * t - 4.0 * t + 2.0
    } else {
        0.0
    }
}

/// Bicubic interpolation on a 2D displacement field sampled at the grid
/// vertex level.  Returns the interpolated (dx, dy) at fractional grid
/// coordinate (fx, fy).
fn bicubic_interpolate_displacement(
    grid: &MeshGrid,
    fx: f32,
    fy: f32,
) -> (f32, f32) {
    let ix = fx.floor() as i32;
    let iy = fy.floor() as i32;
    let tx = fx - ix as f32;
    let ty = fy - iy as f32;

    let mut sum_dx = 0.0f32;
    let mut sum_dy = 0.0f32;

    for j in -1..=2i32 {
        let wy = catmull_rom(ty - j as f32);
        for i in -1..=2i32 {
            let wx = catmull_rom(tx - i as f32);
            let w = wx * wy;

            let col = (ix + i).clamp(0, grid.cols as i32 - 1) as usize;
            let row = (iy + j).clamp(0, grid.rows as i32 - 1) as usize;
            let idx = (row * grid.cols + col) * 2;
            sum_dx += grid.displacements[idx] * w;
            sum_dy += grid.displacements[idx + 1] * w;
        }
    }

    (sum_dx, sum_dy)
}

// ---------------------------------------------------------------------------
// Final rendering
// ---------------------------------------------------------------------------

/// Render the final warped image from the mesh grid displacement using
/// inverse mapping with bilinear interpolation.
///
/// For each output pixel (x, y) we look up the displacement at that position
/// via the mesh grid (with bicubic interpolation for smooth results), then
/// compute the source pixel as `(x + dx, y + dy)` and sample the original
/// image with bilinear interpolation.
pub fn apply_liquify_mesh(grid: &MeshGrid, original: &DynamicImage) -> RgbaImage {
    let (width, height) = (grid.width, grid.height);
    let mut output = RgbaImage::new(width, height);

    let spacing = grid.grid_spacing as f32;
    let orig_rgba = original.to_rgba8();

    for y in 0..height {
        for x in 0..width {
            // Fractional grid coordinates.
            let fx = x as f32 / spacing;
            let fy = y as f32 / spacing;

            // Look up displacement.
            let (dx, dy) = bicubic_interpolate_displacement(grid, fx, fy);

            // Source pixel (inverse mapping).
            let src_x = x as f32 + dx;
            let src_y = y as f32 + dy;

            // Bilinear sampling from the original image.
            let pixel = bilinear_sample(&orig_rgba, src_x, src_y, width, height);
            output.put_pixel(x, y, pixel);
        }
    }

    output
}

/// Bilinear sampling of an RGBA image at fractional coordinates.
///
/// Coordinates outside the image are clamped to the edge.
fn bilinear_sample(
    img: &RgbaImage,
    x: f32,
    y: f32,
    width: u32,
    height: u32,
) -> Rgba<u8> {
    let x0 = x.floor().clamp(0.0, (width - 1) as f32) as u32;
    let y0 = y.floor().clamp(0.0, (height - 1) as f32) as u32;
    let x1 = (x0 + 1).min(width - 1);
    let y1 = (y0 + 1).min(height - 1);

    let fx = (x - x0 as f32).clamp(0.0, 1.0);
    let fy = (y - y0 as f32).clamp(0.0, 1.0);

    let p00 = img.get_pixel(x0, y0);
    let p10 = img.get_pixel(x1, y0);
    let p01 = img.get_pixel(x0, y1);
    let p11 = img.get_pixel(x1, y1);

    let w00 = (1.0 - fx) * (1.0 - fy);
    let w10 = fx * (1.0 - fy);
    let w01 = (1.0 - fx) * fy;
    let w11 = fx * fy;

    let r = (p00[0] as f32 * w00 + p10[0] as f32 * w10 + p01[0] as f32 * w01 + p11[0] as f32 * w11)
        .round()
        .clamp(0.0, 255.0) as u8;
    let g = (p00[1] as f32 * w00 + p10[1] as f32 * w10 + p01[1] as f32 * w01 + p11[1] as f32 * w11)
        .round()
        .clamp(0.0, 255.0) as u8;
    let b = (p00[2] as f32 * w00 + p10[2] as f32 * w10 + p01[2] as f32 * w01 + p11[2] as f32 * w11)
        .round()
        .clamp(0.0, 255.0) as u8;
    let a = (p00[3] as f32 * w00 + p10[3] as f32 * w10 + p01[3] as f32 * w01 + p11[3] as f32 * w11)
        .round()
        .clamp(0.0, 255.0) as u8;

    Rgba([r, g, b, a])
}
