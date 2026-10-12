"""Compare a re-decoded 16-bit sRGB TIFF against a frozen RawLab PNG."""

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
import tifffile

from make_sample import read_reference


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("decoded", type=Path, help="unresized 16-bit RGB sRGB TIFF")
    parser.add_argument("output", type=Path, help="directory for comparison.json and comparison.png")
    args = parser.parse_args()
    reference = read_reference(args.reference)
    with tifffile.TiffFile(args.decoded) as image:
        if len(image.pages) != 1:
            raise ValueError("Expected a single-page 16-bit RGB TIFF")
        page = image.pages[0]
        decoded = page.asarray()
        if page.photometric != tifffile.PHOTOMETRIC.RGB or page.extrasamples:
            raise ValueError("Expected a matching 16-bit RGB TIFF without alpha")
    if decoded.shape != reference.shape or decoded.dtype != np.uint16:
        raise ValueError("Expected a matching 16-bit RGB TIFF without resizing")
    height, width, _ = reference.shape
    delta = decoded.astype(np.int32) - reference.astype(np.int32)
    absolute = np.abs(delta)
    metrics = {
        "reference": args.reference.name,
        "decoded": args.decoded.name,
        "dimensions": [width, height],
        "max_absolute_error_16bit_units": int(absolute.max()),
        "mean_absolute_error_16bit_units": float(absolute.mean()),
        "p99_absolute_error_16bit_units": float(np.percentile(absolute, 99)),
        "mean_signed_channel_error_16bit_units": delta.mean(axis=(0, 1)).tolist(),
        "max_absolute_error_8bit_units": float(absolute.max() / 257.0),
        "rmse_16bit_units": float(np.sqrt(np.mean(delta.astype(np.float64) ** 2))),
        "pass_threshold_8bit_units": 1.0,
        "passes_one_8bit_unit_threshold": bool(absolute.max() <= 257),
    }
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "comparison.json").write_text(json.dumps(metrics, indent=2) + "\n", encoding="utf-8")
    panel_width = 960
    panel_height = round(panel_width * height / width)
    board = Image.new("RGB", (panel_width * 2 + 48, panel_height + 130), "#f4f4f4")
    draw = ImageDraw.Draw(board)
    font = ImageFont.load_default(size=24)
    small = ImageFont.load_default(size=18)
    for x, data, label in (
        (16, reference, "RawLab final export (16-bit PNG)"),
        (panel_width + 32, decoded, "DNG re-decoded (16-bit sRGB TIFF)"),
    ):
        thumbnail = Image.fromarray(np.rint(data.astype(np.float32) / 257).astype(np.uint8))
        thumbnail = thumbnail.resize((panel_width, panel_height), Image.Resampling.LANCZOS)
        board.paste(thumbnail, (x, 54))
        draw.text((x, 17), label, font=font, fill="#222222")
    draw.text((16, panel_height + 76),
              f"Max RGB difference: {metrics['max_absolute_error_8bit_units']:.4f} / 255. This comparison does not identify or validate the decoding application.",
              font=small, fill="#444444")
    board.save(args.output / "comparison.png")
    print(json.dumps(metrics, indent=2))
    return 0 if metrics["passes_one_8bit_unit_threshold"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
