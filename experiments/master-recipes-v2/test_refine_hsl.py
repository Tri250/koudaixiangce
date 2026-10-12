import colorsys
import csv
import os
import tempfile
from pathlib import Path
import unittest

import torch

from refine_hsl import apply_hsl, apply_tone, candidate_quality, fit_hsl, read_frozen_lut


class HSLRefinementTests(unittest.TestCase):
    def test_wrong_frozen_asset_is_rejected_before_parsing(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "prior.cube"
            path.write_text("different asset")
            with self.assertRaisesRegex(ValueError, "Frozen LUT hash mismatch"):
                read_frozen_lut(path, "0" * 64)

    @unittest.skipUnless(os.environ.get("RAWLAB_NATIVE_HSL_PROBES"), "Native C++ probes not supplied")
    def test_native_cpp_probe_parity_including_overrange_tone_values(self):
        with open(os.environ["RAWLAB_NATIVE_HSL_PROBES"], newline="") as source:
            rows = torch.tensor([[float(value) for value in row] for row in csv.reader(source)])
        controls = torch.tensor([[10, -5, 0, 8, -10, 5, 2, -7],
                                 [-15, 10, 5, -8, 7, -9, 4, 12],
                                 [1, -2, 0, 1, 2, -1, 0, 1]], dtype=torch.float32)
        self.assertEqual(rows.shape[0], 2048)
        self.assertLess(float((apply_hsl(rows[:, :3], controls) - rows[:, 3:]).abs().max()), 3e-6)

    def test_identity_and_achromatic_bypass(self):
        rgb = torch.rand(100, 3)
        self.assertTrue(torch.equal(apply_hsl(rgb, torch.zeros(3, 8)), rgb))
        gray = torch.linspace(0, 1, 31)[:, None].repeat(1, 3)
        self.assertTrue(torch.equal(apply_hsl(gray, torch.full((3, 8), 8.0)), gray))

    def test_matches_scalar_native_contract_including_hue_wrap(self):
        torch.manual_seed(42)
        rgb = torch.cat([torch.rand(200, 3), torch.eye(3)])
        controls = torch.tensor([[10, -5, 0, 8, -10, 5, 2, -7],
                                 [-15, 10, 5, -8, 7, -9, 4, 12],
                                 [1, -2, 0, 1, 2, -1, 0, 1]], dtype=torch.float32)
        expected = []
        centers = [0, 30, 60, 120, 180, 240, 270, 300, 360]
        for pixel in rgb.tolist():
            hue, lightness, saturation = colorsys.rgb_to_hls(*pixel)
            degrees = hue * 360
            lower = next((i for i in range(8) if degrees < centers[i + 1]), 7)
            blend = (degrees - centers[lower]) / (centers[lower + 1] - centers[lower])
            blend = blend * blend * (3 - 2 * blend)
            dh, ds, dl = [float(row[lower] * (1 - blend) + row[(lower + 1) % 8] * blend)
                          for row in controls]
            expected.append(colorsys.hls_to_rgb((hue + dh / 1200) % 1,
                                                min(1, max(0, lightness + dl / 200)),
                                                min(1, max(0, saturation * (1 + ds / 100)))))
        self.assertLess(float((apply_hsl(rgb, controls) - torch.tensor(expected)).abs().max()), 3e-6)

    def test_zero_initialization_has_finite_training_gradient(self):
        rgb = torch.tensor([[0.8, 0.3, 0.2], [0.1, 0.4, 0.9], [0.3, 0.7, 0.2]])
        controls = torch.zeros(3, 8, requires_grad=True)
        apply_hsl(rgb, controls, exact_bypass=False).square().mean().backward()
        self.assertTrue(torch.isfinite(controls.grad).all())
        self.assertGreater(float(controls.grad.abs().sum()), 0)

    def test_tone_identity_and_known_contrast_saturation(self):
        rgb = torch.tensor([[0.2, 0.5, 0.8], [0.7, 0.3, 0.2]])
        settings = dict(shadowsDelta=0, highlightsDelta=0, contrastDelta=0, saturationDelta=0)
        self.assertTrue(torch.equal(apply_tone(rgb, settings), rgb))
        settings.update(contrastDelta=20, saturationDelta=-30)
        contrasted = (rgb - 0.5) * 1.2 + 0.5
        y = (contrasted * torch.tensor([0.2126, 0.7152, 0.0722])).sum(-1, keepdim=True)
        self.assertTrue(torch.allclose(apply_tone(rgb, settings), y + (contrasted - y) * 0.7))

    def test_quality_rejects_large_or_clipping_correction(self):
        source = torch.tensor([[0.99, 0.95, 0.9], [0.7, 0.3, 0.2], [0.1, 0.4, 0.9]])
        quality = candidate_quality(source, torch.full((3, 8), 90.0), source)
        self.assertFalse(quality["accepted"])
        self.assertTrue(candidate_quality(source, torch.zeros(3, 8), source)["accepted"])

    def test_fit_keeps_integer_bounded_controls_and_heldout_diagnostics(self):
        torch.manual_seed(7)
        source = torch.rand(3, 256, 3) * 0.8 + 0.1
        target_controls = torch.zeros(3, 8)
        target_controls[1, 5] = -6
        target = apply_hsl(source, target_controls)
        controls, report = fit_hsl(source[:1], target[:1], source[1:2], target[1:2],
                                   source[2:], target[2:], source.reshape(-1, 3), steps=100)
        self.assertTrue(torch.equal(controls, controls.round()))
        self.assertTrue((controls[0].abs() <= 10).all())
        self.assertTrue((controls[1].abs() <= 15).all())
        self.assertTrue((controls[2].abs() <= 2).all())
        self.assertLessEqual(report["validation_loss_after"], report["validation_loss_before"])
        self.assertIn("test_loss_after", report)
        self.assertTrue(report["quality"]["accepted"])


if __name__ == "__main__":
    unittest.main()
