"""Encode a frozen RawLab 16-bit sRGB export as an output-referred DNG.

This product includes DNG technology under license by Adobe.
"""

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

import numpy as np
import png
import tifffile


def read_reference(path):
    width, height, rows, info = png.Reader(filename=str(path)).asDirect()
    if info["bitdepth"] != 16 or info["planes"] != 3 or info.get("alpha"):
        raise ValueError("Expected a 16-bit RGB sRGB PNG without alpha")
    return np.vstack([np.asarray(row, dtype=np.uint16) for row in rows]).reshape(height, width, 3)


def xy_to_xyz(x, y):
    return np.array([x / y, 1.0, (1.0 - x - y) / y], dtype=np.float64)


def srgb_to_pcs():
    primaries = np.column_stack([
        xy_to_xyz(0.64, 0.33), xy_to_xyz(0.30, 0.60), xy_to_xyz(0.15, 0.06),
    ])
    d65 = xy_to_xyz(0.3127, 0.3290)
    d50 = xy_to_xyz(0.3457, 0.3585)
    rgb_to_xyz = primaries @ np.diag(np.linalg.solve(primaries, d65))
    bradford = np.array([
        [0.8951, 0.2664, -0.1614],
        [-0.7502, 1.7135, 0.0367],
        [0.0389, -0.0685, 1.0296],
    ])
    adaptation = np.linalg.inv(bradford) @ np.diag(
        (bradford @ d50) / (bradford @ d65)
    ) @ bradford
    return adaptation @ rgb_to_xyz


def rational_values(values, signed=False):
    scale = 100000000
    dtype = np.int64 if signed else np.uint64
    numerators = np.rint(np.asarray(values).flatten() * scale).astype(dtype)
    return tuple(component for n in numerators for component in (int(n), scale))


def neutral_xmp():
    namespaces = {
        "x": "adobe:ns:meta/",
        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
        "crs": "http://ns.adobe.com/camera-raw-settings/1.0/",
    }
    for prefix, uri in namespaces.items():
        ET.register_namespace(prefix, uri)
    root = ET.Element(f"{{{namespaces['x']}}}xmpmeta")
    rdf = ET.SubElement(root, f"{{{namespaces['rdf']}}}RDF")
    description = ET.SubElement(rdf, f"{{{namespaces['rdf']}}}Description", {
        f"{{{namespaces['rdf']}}}about": "",
    })
    settings = {
        "Version": "16.0", "ProcessVersion": "11.0",
        "HasSettings": "True", "AlreadyApplied": "False",
        "WhiteBalance": "As Shot", "AutoTone": "False",
        "Exposure2012": "0.00", "Contrast2012": "0",
        "Highlights2012": "0", "Shadows2012": "0", "Whites2012": "0",
        "Blacks2012": "0", "Clarity2012": "0", "Texture": "0",
        "Dehaze": "0", "Vibrance": "0", "Saturation": "0",
        "Sharpness": "0", "LuminanceSmoothing": "0", "ColorNoiseReduction": "0",
        "VignetteAmount": "0", "PostCropVignetteAmount": "0", "GrainAmount": "0",
        "LensProfileEnable": "0", "AutoLateralCA": "0", "ToneCurveName2012": "Linear",
    }
    for name, value in settings.items():
        description.set(f"{{{namespaces['crs']}}}{name}", value)
    for name in ("ToneCurvePV2012", "ToneCurvePV2012Red", "ToneCurvePV2012Green", "ToneCurvePV2012Blue"):
        curve = ET.SubElement(description, f"{{{namespaces['crs']}}}{name}")
        sequence = ET.SubElement(curve, f"{{{namespaces['rdf']}}}Seq")
        for point in ("0, 0", "255, 255"):
            ET.SubElement(sequence, f"{{{namespaces['rdf']}}}li").text = point
    return ET.tostring(root, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path, help="RawLab's final 16-bit RGB sRGB PNG")
    parser.add_argument("output", type=Path, help="new DNG file; parent directory must exist")
    parser.add_argument("--look", help="optional label, not an instruction to apply a LUT")
    parser.add_argument("--source-name", help="optional source label, not a source RAW to decode")
    args = parser.parse_args()
    manifest_path = args.output.with_suffix(".json")
    for destination in (args.output, manifest_path):
        if destination.exists():
            raise FileExistsError(f"Refusing to overwrite {destination.name}")
    encoded = read_reference(args.reference)
    height, width, _ = encoded.shape
    display = encoded.astype(np.float64) / 65535.0
    linear = np.where(display <= 0.04045, display / 12.92, ((display + 0.055) / 1.055) ** 2.4).astype(np.float32)
    forward = srgb_to_pcs()
    color = np.linalg.inv(forward)
    tags = [
        (254, "I", 1, 0, False),
        (271, "s", 0, "RawLab", False),
        (272, "s", 0, "Rendered sRGB", False),
        (274, "H", 1, 1, False),
        (50706, "B", 4, (1, 4, 0, 0), False),
        (50707, "B", 4, (1, 4, 0, 0), False),
        (50708, "s", 0, "RawLab Rendered sRGB", False),
        (50713, "H", 2, (1, 1), False),
        (50714, "2I", 3, (0, 1, 0, 1, 0, 1), False),
        (50717, "I", 3, (1, 1, 1), False),
        (50718, "2I", 2, (1, 1, 1, 1), False),
        (50719, "I", 2, (0, 0), False),
        (50720, "I", 2, (width, height), False),
        (50721, "2i", 9, rational_values(color, signed=True), False),
        (50727, "2I", 3, (1, 1, 1, 1, 1, 1), False),
        (50729, "2I", 2, rational_values([0.3457, 0.3585]), False),
        (50730, "2i", 1, (0, 1), False),
        (50731, "2I", 1, (1, 1), False),
        (50732, "2I", 1, (1, 1), False),
        (50778, "H", 1, 23, False),
        (50829, "I", 4, (0, 0, height, width), False),
        (50879, "H", 1, 1, False),
        (50936, "s", 0, "RawLab Baked sRGB", False),
        (50940, "f", 4, (0.0, 0.0, 1.0, 1.0), False),
        (50964, "2i", 9, rational_values(forward, signed=True), False),
        (51109, "2i", 1, (0, 1), False),
        (51110, "I", 1, 1, False),
        (700, "B", 0, neutral_xmp(), False),
    ]
    tifffile.imwrite(
        args.output, linear, photometric="rgb", planarconfig="contig",
        compression=None, rowsperstrip=64, metadata=None, byteorder="<",
        software="RawLab rendered DNG experiment", extratags=tags,
        description="Baked display-sRGB render; not sensor RAW.",
    )
    # Tifffile treats LinearRaw as one primary channel and adds unwanted ExtraSamples.
    with tifffile.TiffFile(args.output, mode="r+") as image:
        image.pages[0].tags[262].overwrite(34892)
    with tifffile.TiffFile(args.output) as image:
        page = image.pages[0]
        restored = page.asarray()
        if restored.dtype != np.float32 or restored.shape != linear.shape or not np.array_equal(restored, linear):
            raise RuntimeError("DNG float storage roundtrip failed")
        if page.tags[262].value != 34892 or page.tags[50879].value != 1 or 338 in page.tags:
            raise RuntimeError("DNG output-referred LinearRaw metadata check failed")
    roundtrip = np.where(restored <= 0.0031308, restored * 12.92, 1.055 * restored ** (1.0 / 2.4) - 0.055)
    difference = np.abs(roundtrip.astype(np.float64) - display)
    manifest = {
        "source_name": args.source_name or args.reference.name,
        "look": args.look,
        "reference": args.reference.name,
        "dng": args.output.name,
        "dimensions": [width, height],
        "pixel_format": "float32 RGB, output-referred linear sRGB, uncompressed",
        "raw_sensor_data_included": False,
        "reference_bit_depth": 16,
        "dng_storage_roundtrip_exact": True,
        "inverse_srgb_roundtrip_max_error_16bit_units": float(difference.max() * 65535),
        "inverse_srgb_roundtrip_mean_error_16bit_units": float(difference.mean() * 65535),
        "forward_matrix": forward.tolist(),
        "color_matrix": color.tolist(),
        "application_validation": "Not performed by this encoder; validate Lightroom and Camera Raw separately",
    }
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
