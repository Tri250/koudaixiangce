# Synthetic DNG Fixtures

`linear-jxl.dng` and `linear-uncompressed.dng` contain the same generated
32 x 24 RGB ramp, stored as three-channel 16-bit LinearRaw with orientation 6.
They are not photographs and contain no private metadata. The JPEG XL stream
uses lossless compression; tests compare its decoded pixels with the
uncompressed file, independently of the SDK's decoder.

`linear-jxl-gain.dng` stores the original ramp with a required Stage2 GainMap
opcode multiplying all three planes by 2. `linear-doubled.dng` stores the
independently doubled integer samples without any opcode. This pair verifies
that the SDK applies required opcode lists rather than only decompressing JXL.

`linear-jxl-stage1.dng` contains only a required Stage1 MapTable, mapping each
16-bit sample to its integer half. `linear-halved.dng` contains independently
halved samples without an opcode. `linear-jxl-required-unknown.dng` contains an
unsupported required Stage1 opcode and must fail instead of returning pixels.

`linear-jxl-crop-gain.dng` combines the required Stage2 GainMap with an internal
ActiveArea of (top=1, left=1, bottom=23, right=31), inside the stored 32 x 24
image. `linear-crop-doubled.dng` stores independently doubled samples with the
same ActiveArea and no opcode. Both must produce oriented 22 x 30 output, with
no second crop or shifted pixels.

`linear-jxl-default-crop-gain.dng` and `linear-default-crop-doubled.dng` add
DefaultCropOrigin (1,1) and DefaultCropSize (28,20) within that ActiveArea.
They verify the conversion from storage to Stage2 coordinates and must produce
oriented 20 x 28 output with identical pixels.

The native suite also uses the SDK's `DNG_SetSimulateMemoryFull` diagnostic hook
to assert OutOfMemory on initial load, white-balance reload and the C ABI.

Regenerate with `python3 lutools/tests/make_jxl_fixture.py lutools/tests/fixtures`
using `cjxl` 0.11.1. No encoder is needed to run the native regression tests.
