#!/usr/bin/env python3
"""Generate a minimal synthetic DNG that LibRaw can decode.

Produces two fixtures under lutools/examples/:
  - test_minimal.dng        : 32×32 RGGB Bayer, uniform mid-grey (WB-neutral patch)
  - test_highlight.dng      : 32×32, center 16×16 saturated white car-body ROI
                              (for highlight_regression / TC-HL-001 semantics)

Both declare the DNG 1.4 schema with CanonicalColorMatrix + CFAPattern +
AnalogBalance + Calibration + two illuminants (D50 / D65), matching the
dual-illuminant DNG fixture synthetic.white_balance_tests.cpp already relies on.
"""
from __future__ import annotations

import struct
import sys
from pathlib import Path

import numpy as np
import tifffile

OUT_DIR = Path(__file__).resolve().parent.parent / "examples"
OUT_DIR.mkdir(exist_ok=True)

W, H = 32, 32
# Uniform mid-grey Bayer RGGB patch (≈10-bit sensor half-range, 16-bit storage)
MID = 16383  # 50% of 16-bit
WHITE = 65535  # full-well saturated

# Canonical RGGB Bayer mosaic: rows = [R,G], columns = [G,B]
# We just write one sample per pixel (SamplesPerPixel=1, CFA interpretation)
def build_minimal_bayer(width, height, pattern):
    """pattern: callable(y, x) -> uint16"""
    data = np.zeros((height, width), dtype=np.uint16)
    for y in range(height):
        for x in range(width):
            data[y, x] = np.uint16(pattern(y, x))
    return data


def write_dng(path: Path, pixels_u16: np.ndarray, *, title: str):
    """Wrap a 2D uint16 Bayer grid in a DNG 1.4 TIFF container.

    The tifffile API does not expose all DNG private IFDs directly, so we
    build them into IFD0 via the custom tags dict. LibRaw does not need
    IFD1 thumbnail to open the file.
    """
    assert pixels_u16.dtype == np.uint16
    h, w = pixels_u16.shape

    # ---------- DNG private tag IDs (Adobe DNG Spec v1.6) ----------
    DNGVersion = 50706          # BYTE[4]  e.g. [1,4,0,0]
    UniqueCameraModel = 50707   # ASCII
    CFAPattern = 50708          # BYTE[4]  [0,1,1,2] == RGGB
    CFALayout = 50710           # SHORT   1 == standard Bayer
    LinearizationTable = 50712  # optional; skip (identity)
    BlackLevel = 50714          # RATIONAL[4]
    WhiteLevel = 50715          # SHORT[4]
    AnalogBalance = 50727       # RATIONAL[3]  per-channel gain
    Illuminant1 = 50778         # SHORT 17 == D50
    Illuminant2 = 50779         # SHORT 21 == D65
    CalibrationMatrix1 = 50721  # RATIONAL[9]
    CalibrationMatrix2 = 50722  # RATIONAL[9]
    ColorMatrix1 = 50725        # RATIONAL[9]
    ColorMatrix2 = 50726        # RATIONAL[9]
    DNGBaselineExposure = 50731 # DOUBLE

    # ---------- helper: float matrix -> rational pairs ----------
    def rmat(m):
        out = []
        for v in np.asarray(m).ravel():
            # 1/1000 precision is enough for synthetic calibration
            num = int(round(v * 1000))
            out.append((num, 1000))
        return out

    # CalibrationMatrix: slight per-channel offset so the DNG is not trivially identity
    calibration = [
        [1.000, 0.010, 0.000],
        [0.000, 1.020, 0.010],
        [0.010, 0.000, 1.010],
    ]
    # ColorMatrix: reasonable camera → XYZ
    colormatrix = [
        [1.000, 0.400, 0.000],
        [0.000, 1.000, 0.000],
        [0.000, 0.200, 1.000],
    ]

    analog_balance = [(2000, 1000), (1500, 1000), (2500, 1000)]  # R=2.0 G=1.5 B=2.5

    dng_tags = {
        # --- core TIFF IFD0 tags ---
        "ImageDescription": title,
        "Make": "Synthetic",
        "Model": "MinimalDNGv1",
        "DateTime": "2026:10:15 00:00:00",
        "Orientation": 1,  # top-left
        "XResolution": (72, 1),
        "YResolution": (72, 1),
        "ResolutionUnit": 2,  # inch
        # --- DNG private tags (tifffile uses uint16 IDs, prefixed "XMP:" or raw) ---
        # tifffile handles DNGVersion natively as "DNGVersion"; we pass extras via tifffile.TiffWriter
    }

    # tifffile TiffWriter: we add DNG private tags manually via the `extratags` dict.
    # Let's build them as raw TIFF entries and append after writing.
    with tifffile.TiffWriter(str(path), bigsize=False) as tif:
        # First write the Bayer image as IFD0 (SamplesPerPixel=1, PhotometricInterpretation=32803)
        tif.write(
            pixels_u16,
            photometric="CFA",            # PhotometricInterpretation=32803
            compression=None,              # 1 = none
            rowsperstrip=h,               # single strip
            metadata=dng_tags,
        )

        # Append DNG private tags to IFD0 by poking the already-written directory.
        # tifffile doesn't expose this cleanly from the writer, so we post-process via
        # low-level TiffFile / TiffPage API — but that's awkward.
        # Simpler: add them right after closing via a second in-place rewrite? No.
        #
        # Actually tifffile's `extratags` kwarg accepts a dict of {name: (id, dtype, count, value)}.
        # Let's use that in the .write() call above.
        #
        # Re-do the write with extratags included.
        pass

    # ---- Fallback: rewrite using the lower-level IFD construction ----
    # We'll use tifffile.imwrite with a manually built extra IFD dict.
    # Re-create:
    path.unlink(missing_ok=True)
    with tifffile.TiffWriter(str(path)) as tif:
        extratags = {
            50706: ("DNGVersion", None, 4, b"\x01\x04\x00\x00"),
            50707: ("UniqueCameraModel", None, None, b"Synthetic-Minimal-DNG"),
            50708: ("CFAPattern", None, 4, np.array([0, 1, 1, 2], dtype=np.uint8).tobytes()),
            50710: ("CFALayout", None, None, np.array([1], dtype=np.uint16).tobytes()),
            50714: ("DNGBlackLevel", None, 4, np.array([0, 0, 0, 0], dtype=np.uint32).tobytes()),
            50715: ("DNGWhiteLevel", None, 4, np.array([WHITE, WHITE, WHITE, WHITE], dtype=np.uint32).tobytes()),
            50721: ("CalibrationMatrix1", None, 9, _pack_rational([1.000, 0.010, 0.000, 0.000, 1.020, 0.010, 0.010, 0.000, 1.010])),
            50722: ("CalibrationMatrix2", None, 9, _pack_rational([1.005, 0.012, 0.001, 0.001, 1.025, 0.011, 0.011, 0.001, 1.015])),
            50725: ("ColorMatrix1", None, 9, _pack_rational(colormatrix_flat())),
            50726: ("ColorMatrix2", None, 9, _pack_rational(colormatrix_flat(offset=0.005))),
            50727: ("AnalogBalance", None, 3, _pack_rational([2.0, 1.5, 2.5])),
            50731: ("DNGBaselineExposure", None, None, struct.pack("<d", 0.0)),
            50778: ("Illuminant1", None, None, np.array([17], dtype=np.uint16).tobytes()),
            50779: ("Illuminant2", None, None, np.array([21], dtype=np.uint16).tobytes()),
        }
        tif.write(
            pixels_u16,
            photometric="CFA",
            compression=None,
            subifds=None,
            extratags=extratags,
            metadata=dng_tags,
        )
    print(f"wrote {path}  {path.stat().st_size} bytes  shape={pixels_u16.shape}  dtype={pixels_u16.dtype}")


def _pack_rational(values):
    """Pack floats as TIFF RATIONAL (num uint32 / den uint32 pairs)."""
    out = bytearray()
    for v in values:
        den = 10000
        num = int(round(v * den))
        out += struct.pack("<II", num, den)
    return bytes(out)


def colormatrix_flat(offset=0.0):
    m = np.array([
        [1.000, 0.400 + offset, 0.000 + offset],
        [0.000 + offset, 1.000, 0.000],
        [0.000, 0.200 + offset, 1.000 + offset],
    ])
    return m.ravel().tolist()


def make_minimal():
    """Uniform mid-grey Bayer — WB-neutral target."""
    def pattern(y, x):
        return MID  # all same level → R/G/B means roughly equal in neutral WB
    pix = build_minimal_bayer(W, H, pattern)
    return OUT_DIR / "test_minimal.dng", pix, "Minimal-DNG 32x32 uniform mid-grey RGGB"


def make_highlight():
    """Center 16×16 saturated white on grey background — car-body ROI fixture."""
    half = H // 4  # 8
    def pattern(y, x):
        if half <= y < H - half and half <= x < W - half:
            return WHITE
        return MID
    pix = build_minimal_bayer(W, H, pattern)
    return OUT_DIR / "test_highlight.dng", pix, "Highlight-DNG 32x32, center 16x16 saturated ROI"


def main():
    out1, pix1, title1 = make_minimal()
    write_dng(out1, pix1, title=title1)
    out2, pix2, title2 = make_highlight()
    write_dng(out2, pix2, title=title2)
    print(f"\nOK: {out1.name} + {out2.name} in {OUT_DIR}")


if __name__ == "__main__":
    main()
