# Rendered DNG Experiment

These standalone tools encode RawLab's final 16-bit RGB sRGB PNG export as an
output-referred, uncompressed float32 DNG. All current edits are already baked
into the reference pixels. This is not a production export feature, RAW-to-DNG
conversion, or a way to recover individual RawLab adjustments in another app.

## Setup

Python 3.12 was used for verification. From the repository root:

```bash
python3 -m venv experiments/rendered-dng/.venv
experiments/rendered-dng/.venv/bin/python -m pip install -r experiments/rendered-dng/requirements.txt
experiments/rendered-dng/.venv/bin/python -m unittest discover -s experiments/rendered-dng -p 'test_*.py'
mkdir -p output/rendered-dng
```

Dependencies are isolated to this experiment. Photos, DNGs, TIFFs, comparison
images, manifests, logs and SDK binaries belong in the ignored `output/`
directory, not alongside the tracked source.

## Generate a Sample

Export the desired photo from RawLab as a 16-bit PNG with the current adjustments.
The input must be display-encoded sRGB, RGB without alpha. The encoder checks the
sample layout and bit depth, but does not infer or convert arbitrary ICC profiles;
do not supply Adobe RGB, Display P3, linear PNG or other transfer functions.

```bash
experiments/rendered-dng/.venv/bin/python experiments/rendered-dng/make_sample.py \
    output/rendered-dng/reference.png output/rendered-dng/rendered.dng
```

The output parent directory must exist. An existing DNG or matching JSON manifest
is not overwritten. Optional `--look` and `--source-name` add labels to the
manifest only; they do not load a LUT, open a RAW or reapply any adjustment.
Reports use filenames instead of recording machine-specific absolute paths.

The encoder inverse-decodes sRGB before storing float32 samples, uses a virtual
linear-sRGB camera with a Bradford D65-to-D50 transform, and writes
ColorimetricReference=1. Zero exposure offsets, an identity ProfileToneCurve and
DefaultBlackRender=None prevent the SDK's scene-rendering defaults from being
applied again. It verifies that the float samples read back exactly and that the
main image has LinearRaw interpretation, three RGB channels and no ExtraSamples.

Float32 storage preserves the rendered reference's precision through the linear
encoding. It does not create new sensor detail, wider gamut or dynamic range.
The probe includes no original RAW, JPEG preview or camera-specific EXIF copy.

## Validate and Compare

Obtain Adobe DNG SDK 1.4 source separately under its applicable license. The
builder does not download or vendor the SDK. This build helper supports macOS
only and expects a root directory with `source/dng_validate.cpp`, clang++, and
zlib development files. It rejects other operating systems rather than implying
that their SDK toolchains have been validated.

```bash
bash experiments/rendered-dng/build_validator.sh /path/to/dng-sdk-1.4 output/rendered-dng/sdk
output/rendered-dng/sdk/dng_validate -v -cs1 -16 \
    -tif output/rendered-dng/decoded output/rendered-dng/rendered.dng
experiments/rendered-dng/.venv/bin/python experiments/rendered-dng/compare_sample.py \
    output/rendered-dng/reference.png output/rendered-dng/decoded.tif \
    output/rendered-dng/comparison
```

The SDK `-tif` argument is a prefix: it appends `.tif` itself. Without an explicit
output directory, the builder uses its own ignored `output/sdk-validation/`.
XMP and libJPEG support are disabled; the sample uses uncompressed image data.

The comparison requires a single-page, unresized 16-bit RGB sRGB TIFF without alpha. It writes
`comparison.json` and a labeled `comparison.png`, replacing earlier comparison
outputs in that directory. It exits 0 when the maximum RGB channel difference is
at most one 8-bit step (257 16-bit units), or 1 when that experimental threshold is
exceeded. Invalid inputs exit nonzero. This is a numerical threshold, not a
perceptual color-accuracy certification.

## Verification Boundary

The original full-resolution local probe passed Adobe SDK 1.4 format validation
without warnings or errors. Its SDK-rendered sRGB TIFF differed from the RawLab
reference by at most 6/65535 per channel, with mean absolute error 0.72860/65535.
Synthetic tests cover the DNG tags, float transfer roundtrip, input rejection,
overwrite prevention, identity comparison and a failing comparison threshold.

Neutral Adobe Camera Raw XMP settings request no additional tone, sharpening,
denoising or effects. The no-XMP SDK build does not validate their interpretation.
Lightroom Develop and Photoshop Camera Raw have not been validated. Their actual
default rendering and 16-bit sRGB export need separate checks; viewing an
embedded preview or passing this SDK test is not equivalent to those checks.

References:

- [Adobe DNG specification](https://helpx.adobe.com/content/dam/help/en/camera-raw/digital-negative/jcr_content/root/content/flex/items/position/position-par/download_section_733958301/download-1/DNG_Spec_1_7_1_0.pdf)
- [Adobe DNG SDK resources](https://helpx.adobe.com/camera-raw/desktop/dng-and-file-formats/digital-negative.html)

## Licensing

This product includes DNG technology under license by Adobe.

The DNG specification's patent license is separate from the Adobe SDK source
license. No SDK source, binaries or third-party package code are included here;
obtain and use those dependencies under their respective licenses.
