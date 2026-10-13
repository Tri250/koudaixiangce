# Rendered DNG Export Verification

## Scope

The implementation is isolated on `feature/rendered-dng-export`, based on
`origin/development` at `12d6cc1`. The primary checkout and its unrelated edits
were not modified. Delivery uses a feature PR targeting `development`; merging
and release are outside the current authorization.

Rendered DNG bakes the FINAL display-sRGB image after the selected look, white
balance, exposure, tone/HSL, denoising, sharpening, vignette, grain and final
resize. It is not sensor RAW and does not preserve independently editable RawLab
controls. The shared encoder uses DNG 1.4 float32 RGB LinearRaw, inverse sRGB,
an output-referred D50 virtual profile and neutral Adobe settings.

Source capture EXIF, MakerNotes and sensor calibration are not copied. Existing
JPEG/PNG metadata handling is unchanged. Uncompressed storage is approximately
12 bytes per pixel. Classic TIFF outputs above the 4 GiB container limit are
rejected. Mobile DNG exports use files, not the photo library.

## Mac And Lightroom

The packaged Mac application exposes rendered DNG in single-export menus and
the batch format picker. A current-edit-memory photo was exported through the
actual single-export UI to both DNG and 16-bit PNG without changing its settings.

The focused real-RAW test also exercised nonzero adjustment categories, CPU and
forced Metal, original size and a 1536-pixel long edge. For each backend and size,
single and batch DNG files were byte-identical and source identity was unchanged.

The actual batch UI was then exercised with DNG, original size and an isolated
output folder. It reported one success and zero failures; the published DNG was
byte-identical to the current-settings single-export UI file. Only the validation
application was quit, and its owned test journal/look snapshot were moved out of
the default batch directory into ignored validation artifacts.

Lightroom Classic 14.1 imported two production DNG files using Add, without
Develop presets. Both were inspected in Develop and exported as uncompressed,
unresized, unsharpened 16-bit sRGB TIFFs. The file Software tags identify
`Adobe Photoshop Lightroom Classic 14.1 (Macintosh)`; ICC is
`sRGB IEC61966-2.1`. Export XMP retains zero exposure/tone/detail/effects controls
and a Linear curve rather than reapplying the baked RawLab adjustments.

| Reference | Dimensions | Max error / 65535 | Mean error / 65535 | Max error / RGB8 step |
| --- | --- | --- | --- | --- |
| Current Mac adjustments | 4608 x 3072 | 26 | 1.428045 | 0.101167 |
| Nonzero effects / forced Metal | 4608 x 3072 | 50 | 1.982398 | 0.194553 |

Both comparisons pass the acceptance threshold of one RGB8 step (257 units in
RGB16). This validates these actual Lightroom imports, not arbitrary import
presets or subsequent edits. Photoshop / Camera Raw is not installed and remains
unverified.

The Adobe DNG SDK 1.4 validator also completed without warnings/errors on a
production Metal DNG. Its sRGB RGB16 decode differed from the matching PNG by at
most 35/65535 (0.136187 RGB8 steps).

## Checks

Run from the repository root, with `RAWLAB_TEST_RAW` pointing to an external
full-resolution Bayer RAW when a fixture is required:

```sh
bash RawLabMac/build.sh
bash RawLabMac/tests/batch-export.sh
bash RawLabMac/tests/edit-model.sh
bash RawLabMac/tests/chroma-settings.sh
bash RawLabMac/tests/wavelet-settings.sh
bash RawLabMac/tests/denoise-model.sh "$RAWLAB_TEST_RAW"
bash RawLabMac/tests/adjustments.sh "$RAWLAB_TEST_RAW"
bash RawLabMac/tests/rendered-dng.sh "$RAWLAB_TEST_RAW" output/rendered-dng-validation
ctest --test-dir lutools/build-macos --output-on-failure
```

Cross-platform checks use the regular platform commands:

```sh
bash lutools/platform/ios/build-framework.sh
bash lutools/platform/ios/verify-framework.sh
bash RawLab/tests/batch-export.sh
# IOS_SIMULATOR_APP is the linked arm64 simulator RawLab.app.
RAWLAB_TEST_RAW="$RAWLAB_TEST_RAW" bash RawLab/tests/processor-export.sh "$IOS_SIMULATOR_APP" "$IOS_SIMULATOR_ID"
# From RawLabAndroid:
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Synthetic tests independently read tags and float pixels with libtiff. Storage
regressions cover unwritable paths, failed publication, replacement of an
existing output, invalid pixels and a POSIX mid-write file-size-limit failure.
Failed writes preserve the existing destination and remove owned staging files.
C ABI regressions require FINAL FILE output, reject DNG buffer/preview requests
and retain request layouts. The appended status `OUTPUT_IO_ERROR = 6` separates
DNG output-storage failure from source `IO_ERROR = 3`, so a bad source remains a
per-photo failure while a full/unwritable output stops the batch. Previous status
values and JPEG/PNG error behavior are unchanged.

An initial whole-core run with the Sony small-size RAW passed 18/21 checks.
`raw_noise`, `raw_reuse` and `raw_white_balance` failed identically on the
unchanged `development` baseline. All three pass with the full-resolution Bayer
fixture. No RAW processing algorithm was changed to mask those fixture failures.
The final whole-core run with the full-resolution fixture passed 21/21 checks.

The CLI was also corrected to map DNG extensions case-insensitively to format 4
rather than writing JPEG bytes under a DNG filename. System libtiff must be at
least the verified 4.7.1 version, or builds can select the pinned source dependency
with `SONY2FUJI_BUILD_TIFF_DEPS=ON`.

### iOS Publication Error Regressions

The production file writer now lives with the Foundation-only batch core so the
existing host suite exercises its real file moves and journal checkpoints.
After a successful publication, a failed checkpoint is classified as uncertain
rather than a normal per-photo failure. A regression temporarily makes the real
journal directory unavailable, restores it for the interrupted-state save, then
reloads and continues the job. The completed DNG is verified and neither rendered
nor replaced again.

Output-directory creation failures are converted to global storage errors in the
shared directory entry point used by both rendering and publication. Regressions
place a normal file at the job's directory path and verify both paths stop after
the first target, retain the second target as pending and preserve the blocker.
Both failures were reproduced before the fixes; the expanded batch suite and
simulator app build passed after the changes.

### iOS Confirmation Routing

Both DNG confirmation actions now check the output files directly, without
requesting Photos authorization or presenting the JPEG-only manual confirmation
dialog. The model also routes obsolete manual DNG confirmation calls through
file recovery rather than trusting a saved/unsaved choice. Reconciliation refreshes
the file export list and persists the checked state; missing outputs become
pending while existing outputs stay completed. Recovery also clears the resolved
checkpoint warning without clearing unresolved global storage errors.

`RawLab/tests/batch-publication.sh` compiles the actual iOS model and app sources
into a standalone simulator test process, using its own bundle with Photos access
explicitly denied. The regression reproduced the authorization failure before
the fix and now checks permission-free DNG recovery, unchanged output bytes,
post-open file deletion, refreshed export actions and journal state, as well as
the retained JPEG authorization and manual-confirmation behavior. It does not
inject a live checkpoint failure into the SwiftUI view; that state remains
covered by the real writer/journal regression in the batch core suite.

```sh
bash RawLab/tests/batch-publication.sh "$IOS_SIMULATOR_APP" "$IOS_SIMULATOR_ID"
```

## Platform Boundaries

- Mac final build and focused Swift/native tests have completed successfully.
  Both actual single and batch UI exports and the real-RAW batch runner were
  verified.
- Android explicit formats, JNI mapping, legacy journals and SAF file output are
  implemented. `:app:testDebugUnitTest`, `:app:lintDebug` and `:app:assembleDebug`
  passed, including arm64-v8a and x86_64 native builds. APK libtiff license
  packaging was checked. Physical-device UI/SAF and GLES rendering are not
  runtime-verified; no application was installed over the user's device app.
- iOS direct FINAL file rendering and persistent DNG batch file output are
  implemented. Device, arm64 simulator and x86_64 simulator framework builds and
  license checks passed. The simulator app linked successfully; synthetic
  processor/DNG/orientation/effects and batch recovery runtime tests passed.
  Completed DNG journals remain resumable until explicit dismissal, stale
  container output paths relocate and deleted outputs become pending again.
  A real full-resolution Bayer RAW was exported to an upright 85 x 128 DNG with
  nonzero WB, exposure, tone/HSL, look strength, denoising, sharpening, vignette
  and grain. Independently decoded float pixels, re-encoded to sRGB RGB8,
  differed from the matching iOS final buffer by at most one code value. CPU and
  forced-Metal DNGs differed by at most 0.189580 RGB8 steps. Device/UI
  document-picker validation remains unverified.
- Windows single/batch WPF integration, legacy journal tests and license
  packaging are implemented. Single export validates the selected format's
  extension and protects original-file aliases with a Windows file identity and
  a non-delete-sharing source handle during publication. There is no
  Windows/.NET/PowerShell toolchain on
  this Mac; Windows compilation and D3D11 runtime validation are unverified.

Photos, DNG/TIFF/PNG files, screenshots, private source paths and build products
are not part of the source changes. Local comparison metrics and artifacts live
only under ignored `output/` directories.

Normal write/publication failures clean up staging files. Abrupt process
termination can leave an unreferenced native staging file; it is not published
as a completed DNG and is not currently part of batch-journal cleanup.
