# iPad and Cross-Platform Color Controls

## Scope

Adapt the existing iOS editor to iPad window sizes. Across iOS, macOS, Android
and Windows, start unseen photos in embedded-preview exposure mode, add an
eight-channel HSL mixer, and add a one-shot Auto action to exposure. Preserve
existing saved exposure modes and all original RAW files. On 2026-10-11 the owner
also authorized committing, pushing, opening a PR to `development` and merging
it after verification. Release and deployment remain outside this request.

## Behavior

- Landscape iPad windows retain a large photo workspace beside an independently
  scrollable adjustment region; portrait and narrow windows put a bounded dock
  below the photo so a tall tablet does not squeeze the image into a narrow column.
  Support portrait, landscape and resized windows using available width, not
  a fixed device-name check. Import, comparison, zoom and export stay available.
- New settings, full resets and input-group resets use embedded-preview exposure matching. A single exposure-value reset retains the selected baseline. Old edit
  records keep their explicit mode. Missing/invalid embedded previews retain
  the core's scene-baseline fallback. The C API session default stays unchanged.
- HSL uses red, orange, yellow, green, aqua, blue, purple and magenta, in that
  order. Each channel has hue, saturation and lightness values in [-100,100].
  Hue maps to +/-30 degrees, saturation scales by 1 + value/100, and lightness
  moves by value/200. Smooth, circular interpolation between hue centers
  (0,30,60,120,180,240,270,300 degrees) avoids color-band seams. Achromatic
  pixels remain unchanged. Zero settings bypass exactly.
- HSL runs in display-sRGB after existing tone/color controls, before chroma
  denoise, detail and effects. HSL executes in Metal, GLES and D3D11 photo shaders,
  using the CPU implementation as the parity reference. GPU Auto falls back only
  on a backend failure or an already unsupported look; GPU Force requires actual
  GPU execution. Inactive HSL bypasses exactly. Request-v2 is unchanged.
- Auto meters the current RAW and white balance before user exposure and all
  display/look controls. A bounded spatial sample supplies linear-sRGB median
  luminance; target gray is .1845. Positive gains are limited so the 99.5th
  percentile remains at or below linear 4. The returned absolute user EV is
  clamped to [-4,4], not added to the current EV. An unusably dark/empty sample
  returns an error without changing settings. Repeated Auto is idempotent.
- HSL and the chosen EV participate in edit memory, copies, reset/default
  detection and frozen batch snapshots. Missing HSL fields decode as identity.
  Auto runs off the UI thread and stale results cannot replace edits on a new
  photo or overwrite settings changed since metering started.

## Shared Interfaces

`sony2fuji_hsl_config` contains `uint32_t version`, `uint32_t struct_size` and
three `float[8]` arrays named `hue`, `saturation`, `lightness`. Version is 1.
`sony2fuji_session_set_hsl(session, config)` validates all fields before changing
session state. `sony2fuji_session_estimate_auto_exposure(session, request,
float* exposure_ev)` validates and meters RAW or tagged RGB8/RGBA8 buffers,
using current RAW exposure mode and white balance without producing an image.

## Verification

Add core tests for identity, channel isolation, wrap continuity, grayscale,
validation/state retention, actual rendered pixels, CPU fallback, metering
direction/bounds/EV independence and a real RAW. Add platform model/persistence
regressions and compile affected clients where tools are available. Build and
capture iPhone and iPad simulator layouts, including rotation/narrow windows.
Report Windows/macOS host-tool limitations and real-device coverage honestly.
