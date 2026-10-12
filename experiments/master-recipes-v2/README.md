# Master Recipe LUT Refinement v2

This experiment refines the existing 14 authored master looks. It does not recover photographers' original recipes or train an authenticated photographer-style model.

## Method

1. Read user-provided reference collections, orient images and convert embedded ICC profiles to display-sRGB. Untagged RGB is explicitly assumed sRGB. Reject unsupported profiles, monochrome images and near-identical 64px proxies.
2. Split supplied series together, or use capture day where present. When neither exists, split only after proxy deduplication. This does not guarantee that different crops or photographs from one shoot are separated by scene; these splits are internal diagnostics, not an independent style-accuracy benchmark.
3. Strictly load the existing CanonCGT SSL checkpoint. Canonicalize each reference separately, producing **model-generated pseudo input/reference pairs**, not real before/after supervision. No user RAW enters the model or fitting path.
4. Fit a 9-grid smooth correction, bounded to 0.04 per RGB channel, to each original 65-grid look. Regularize against the authored table and its gray/skin probes. Use identical gamut/tone processing for training and validation. Select by validation pseudo loss plus preservation terms, never by test results.
5. For a prior with more than 0.5% adjacent luma drops larger than 0.03, smooth its residual once with a separable `[0.25, 0.5, 0.25]` kernel. At 65-grid this only affects very close input colors. Do not enforce monotonic luma across all single-channel changes: those also change hue.
6. Repair gray-axis tone, add a 1% identity-luma contribution and compress chroma to the legal gamut with 0.001 headroom, instead of clipping individual channels. Preserve intentional warm/cool gray grading.
7. Reject candidates with mean table change above 0.015, maximum change above 0.08, gray-probe change above 0.035, or decreasing gray tone. Reject a run if no candidate qualifies. Export one fixed 65-grid display-sRGB/BT.709 `referenceStyle` CUBE, using the existing `hamada_neural.cube` axis/interpolation/export implementation and its 0.02 representation-error gate.

The six preset UI controls are not inputs to this script and are not baked again. The IDs, filenames and UI settings stay unchanged. A 3D LUT cannot reproduce local lighting, halation, grain, composition, semantic masks or source-specific exposure correction.

## Runtime

The script requires the existing `hamada_neural` research package and an already-deployed CanonCGT repository/checkpoint, plus PyTorch, Pillow with LittleCMS, NumPy and PyYAML. It installs nothing and does not modify either upstream package. RawLab ships only the generated CUBE assets, not these models, weights or reference photographs. CanonCGT's Apache-2.0 license does not grant rights to redistribute reference photography; keep datasets/previews/checkpoints private.

```bash
PYTHONPATH="$HAMADA_CODE" python -m unittest discover \
  -s experiments/master-recipes-v2 -p 'test_*.py' -v

CUDA_VISIBLE_DEVICES="$IDLE_GPU_UUID" TORCH_HOME="$PROJECT_TORCH_CACHE" \
PYTHONPATH="$HAMADA_CODE" python experiments/master-recipes-v2/optimize.py \
  --references "$MASTER_REFERENCES" \
  --hamada-manifest "$APPROVED_HAMADA_MANIFEST" \
  --hamada-images "$HAMADA_SRGB_PROXIES" \
  --old-luts "$ORIGINAL_MASTER_LUTS" \
  --model-repo "$CANONCGT_CODE" --checkpoint "$SSL_CHECKPOINT" \
  --output "$NEW_PRIVATE_RUN" --steps 300
```

The output directory must not already exist. `--styles <names>` runs a subset. Hamada uses only daylight entries whose existing manifest allows both training and server use. Other styles use the explicitly supplied master collections. Output reports record exclusions, ICC assumptions, split basis, model/old-LUT hashes, selected step, bounds, pseudo losses, clipping and gray-tone checks. The private four-panel preview is canonicalized input, old LUT, new LUT, reference; it is not a RAW preview.

Export is only a candidate stage. Before replacing production assets, run `generate-lut` validation on every actual written file, native parsing and RAW CPU/Metal/full-preset regression tests, and inspect old/new previews. Do not equate a lower pseudo loss with photographer-style fidelity or a Python export pass with native rendering verification.

## Editable HSL Residuals

`refine_hsl.py` is a separate calibration step. It freezes the current fourteen
CUBEs and fits only the 24 native HSL controls, rounded to the UI's integer units.
Bounds are hue +/-10, saturation +/-15 and lightness +/-2. The CUBEs are not
rewritten, and HSL is not baked into them.

Reuse the same accepted references and split mapping, including the **256px
Hamada proxies** used by the original run. The script rejects changed reference
fingerprints/splits and requires an expected SHA-256 for each CUBE in the catalog's
`lutSHA256` field. The catalog is the JSON-encoded `MasterRecipe.allRecipes`, with
those hashes added from the frozen production files.

CanonCGT generates auxiliary display-sRGB pseudo pairs again. The prediction
passes through the frozen CUBE, existing shadows/highlights/contrast/saturation,
then the same HSL formulas as the native core. Reference JPEGs do not provide
source RAW exposure or camera WB, so those upstream stages are **not reconstructed**.
This is residual color calibration, not a full-RAW grading accuracy measurement.

Select rounded controls using validation only, with RGB-change, clipping, gray
tone and skin-probe preservation gates. Test data is diagnostic, never used to
choose steps. Retain identity HSL when no candidate improves the score within the
gates; do not force every style to have nonzero values. Private pseudo pairs are
cached for later reproducibility, and all reference-derived previews stay private.

```bash
PYTHONPATH="$HAMADA_CODE" python -m unittest discover \
  -s experiments/master-recipes-v2 -p 'test_refine_hsl.py' -v

CUDA_VISIBLE_DEVICES="$IDLE_GPU_UUID" TORCH_HOME="$PROJECT_TORCH_CACHE" \
PYTHONPATH="$HAMADA_CODE" python experiments/master-recipes-v2/refine_hsl.py \
  --catalog "$FROZEN_CATALOG_JSON" --luts "$CURRENT_MASTER_LUTS" \
  --references "$MASTER_REFERENCES" \
  --hamada-manifest "$APPROVED_HAMADA_MANIFEST" \
  --hamada-images "$HAMADA_256PX_PROXIES" \
  --baseline-reports "$PREVIOUS_PRIVATE_REPORTS" \
  --model-repo "$CANONCGT_CODE" --checkpoint "$SSL_CHECKPOINT" \
  --output "$NEW_PRIVATE_HSL_RUN" --steps 300
```

The optional native probe check verifies the Python HSL calculation against 2048
actual C++ results, including out-of-range tone-stage inputs. On a configured Mac
build, generate the CSV from `native_hsl_probes.cpp` linked with
`lutools/build-macos/libsony2fuji_core.a`, and set `RAWLAB_NATIVE_HSL_PROBES` to that
CSV when running the tests. The ordinary run skips this optional check explicitly.

In the app, applying a recipe replaces the visible HSL values; edits after that
remain editable and persist. Rendering scales those current values together with
recipe strength, preserves fractional results and clamps to the native [-100,100]
range. Older saved edits and frozen jobs keep their independent-HSL behavior until
the user explicitly reapplies a recipe. HSL defaults are never reinjected during
restore or batch replay.
