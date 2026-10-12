import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

import numpy as np
import png
import tifffile


HERE = Path(__file__).resolve().parent


class RenderedDNGTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.folder = Path(self.temporary.name)
        self.reference = self.folder / "reference.png"
        self.pixels = np.array([
            [[0, 0, 0], [65535, 65535, 65535], [1, 2, 3], [1024, 2048, 4096]],
            [[32768, 16384, 8192], [65535, 0, 0], [0, 65535, 0], [0, 0, 65535]],
        ], dtype=np.uint16)
        self.write_png(self.reference, self.pixels, 16)

    @staticmethod
    def write_png(path, pixels, bitdepth):
        height, width, _ = pixels.shape
        with path.open("wb") as output:
            png.Writer(width, height, greyscale=False, bitdepth=bitdepth).write(
                output, pixels.reshape(height, width * 3).tolist()
            )

    @staticmethod
    def run_script(name, *arguments):
        return subprocess.run(
            [sys.executable, str(HERE / name), *map(str, arguments)],
            capture_output=True, text=True, check=False,
        )

    def test_dng_contract_and_transfer_roundtrip(self):
        output = self.folder / "rendered.dng"
        original = self.reference.read_bytes()
        result = self.run_script("make_sample.py", self.reference, output, "--look", "test-look")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.reference.read_bytes(), original)
        with tifffile.TiffFile(output) as image:
            page = image.pages[0]
            linear = page.asarray()
            self.assertEqual(linear.dtype, np.float32)
            self.assertEqual(linear.shape, self.pixels.shape)
            self.assertEqual(page.tags[262].value, 34892)
            self.assertEqual(page.tags[277].value, 3)
            self.assertNotIn(338, page.tags)
            self.assertEqual(page.tags[50879].value, 1)
            self.assertEqual(page.tags[51109].value, (0, 1))
            self.assertEqual(page.tags[51110].dtype, tifffile.DATATYPE.LONG)
            self.assertEqual(page.tags[51110].value, 1)
            self.assertEqual(page.tags[50714].value, (0, 1, 0, 1, 0, 1))
            self.assertEqual(page.tags[50940].value, (0.0, 0.0, 1.0, 1.0))
            xmp = ET.fromstring(page.tags[700].value)
            description = xmp.find("{http://www.w3.org/1999/02/22-rdf-syntax-ns#}RDF")[0]
            self.assertEqual(description.attrib["{http://ns.adobe.com/camera-raw-settings/1.0/}Sharpness"], "0")
        display = np.where(linear <= 0.0031308, linear * 12.92, 1.055 * linear ** (1 / 2.4) - 0.055)
        self.assertLess(float(np.max(np.abs(display * 65535 - self.pixels))), 0.02)
        report = json.loads(output.with_suffix(".json").read_text())
        self.assertEqual(report["look"], "test-look")
        self.assertTrue(report["dng_storage_roundtrip_exact"])
        self.assertNotIn(str(self.folder), json.dumps(report))

    def test_rejects_eight_bit_reference(self):
        self.write_png(self.reference, (self.pixels // 257).astype(np.uint8), 8)
        output = self.folder / "rendered.dng"
        result = self.run_script("make_sample.py", self.reference, output)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("16-bit RGB", result.stderr)
        self.assertFalse(output.exists())

    def test_preserves_existing_output_and_manifest(self):
        output = self.folder / "rendered.dng"
        for existing in (output, output.with_suffix(".json")):
            with self.subTest(existing=existing.name):
                existing.write_bytes(b"keep this file")
                result = self.run_script("make_sample.py", self.reference, output)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Refusing to overwrite", result.stderr)
                self.assertEqual(existing.read_bytes(), b"keep this file")
                existing.unlink()

    def test_comparison_identity_and_failure_threshold(self):
        decoded = self.folder / "decoded.tif"
        tifffile.imwrite(decoded, self.pixels, photometric="rgb")
        output = self.folder / "comparison"
        result = self.run_script("compare_sample.py", self.reference, decoded, output)
        self.assertEqual(result.returncode, 0, result.stderr)
        report = json.loads((output / "comparison.json").read_text())
        self.assertEqual(report["max_absolute_error_16bit_units"], 0)
        self.assertTrue((output / "comparison.png").exists())
        changed = self.pixels.copy()
        changed[0, 0, 0] = 258
        tifffile.imwrite(decoded, changed, photometric="rgb")
        result = self.run_script("compare_sample.py", self.reference, decoded, output)
        self.assertEqual(result.returncode, 1, result.stderr)
        report = json.loads((output / "comparison.json").read_text())
        self.assertFalse(report["passes_one_8bit_unit_threshold"])

    def test_comparison_rejects_mismatched_or_eight_bit_tiff(self):
        decoded = self.folder / "decoded.tif"
        for pixels in (self.pixels[:, :1], (self.pixels // 257).astype(np.uint8)):
            with self.subTest(shape=pixels.shape, dtype=str(pixels.dtype)):
                tifffile.imwrite(decoded, pixels, photometric="rgb")
                result = self.run_script("compare_sample.py", self.reference, decoded, self.folder / "comparison")
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Expected a matching 16-bit RGB TIFF", result.stderr)

    def test_comparison_rejects_multiple_tiff_pages(self):
        decoded = self.folder / "decoded.tif"
        with tifffile.TiffWriter(decoded) as image:
            image.write(self.pixels, photometric="rgb")
            image.write(self.pixels, photometric="rgb")
        result = self.run_script("compare_sample.py", self.reference, decoded, self.folder / "comparison")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("single-page", result.stderr)


if __name__ == "__main__":
    unittest.main()
