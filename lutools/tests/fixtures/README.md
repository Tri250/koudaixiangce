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

Regenerate with `python3 lutools/tests/make_jxl_fixture.py lutools/tests/fixtures`
using `cjxl` 0.11.1. No encoder is needed to run the native regression tests.
