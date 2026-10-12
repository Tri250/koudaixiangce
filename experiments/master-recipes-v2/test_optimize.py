import tempfile
import unittest
from pathlib import Path

import torch
from PIL import Image, ImageCms

from hamada_neural.cube import read_cube, sample_lut
from optimize import correction_table, export_checked, fit_style, identity_grid, load_reference, repair_table, smooth_residual, split_groups


class OptimizationTests(unittest.TestCase):
    def test_bounded_correction_is_identity_at_zero(self):
        old = identity_grid(17)
        params = torch.zeros(9, 9, 9, 3, requires_grad=True)
        corrected = correction_table(old, params)
        self.assertTrue(torch.allclose(old, corrected, atol=1e-7))
        corrected.square().mean().backward()
        self.assertGreater(float(params.grad.abs().sum()), 0)
        large = correction_table(old, torch.full_like(params, 100))
        self.assertLessEqual(float((large - old).abs().max()), 0.040001)

    def test_repair_keeps_tinted_gray_and_restores_gray_tone(self):
        old = identity_grid(17)
        old[..., 0] += 0.09
        old[8, 8, 8] -= 0.09
        repaired = repair_table(old)
        self.assertTrue(torch.isfinite(repaired).all())
        self.assertGreater(float(repaired.min()), 0)
        self.assertLess(float(repaired.max()), 1)
        weights = torch.tensor([0.2126, 0.7152, 0.0722])
        i = torch.arange(17)
        y = (repaired[i, i, i] * weights).sum(-1)
        self.assertGreaterEqual(float(torch.diff(y).min()), -1e-6)
        self.assertGreater(float(repaired[8, 8, 8, 0] - repaired[8, 8, 8, 2]), 0.035)

    def test_export_preserves_axis_order_and_rejects_nonfinite(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "test.cube"
            old = identity_grid(17)
            table = repair_table(old[..., [2, 1, 0]])
            export_checked(table, path, "test")
            _, actual = read_cube(path)
            self.assertEqual(tuple(actual.shape), (65, 65, 65, 3))
            probes = torch.tensor([[0.1, 0.4, 0.8], [0.7, 0.2, 0.3]])
            self.assertLess(float((sample_lut(table, probes) - sample_lut(actual, probes)).abs().max()), 1e-5)
            bad_path = Path(folder) / "bad.cube"
            table[0, 0, 0, 0] = float("nan")
            with self.assertRaises(ValueError):
                export_checked(table, bad_path, "bad")
            self.assertFalse(bad_path.exists())

    def test_icc_and_untagged_color_inputs(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "test.png"
            image = Image.new("RGB", (20, 12), (170, 80, 20))
            profile = ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB"))
            image.save(path, icc_profile=profile.tobytes())
            rgb, metadata = load_reference(path)
            self.assertEqual(metadata["color_handling"], "icc_to_srgb")
            self.assertEqual(rgb.shape[-1], 3)
            self.assertLess(float(abs(rgb.mean((0, 1))[0] - 170 / 255)), 0.01)
            image.save(path)
            _, metadata = load_reference(path)
            self.assertEqual(metadata["color_handling"], "untagged_assumed_srgb")
            image.save(path, icc_profile=b"invalid-profile")
            with self.assertRaises(ValueError):
                load_reference(path)

    def test_duplicate_groups_do_not_cross_splits(self):
        entries = [{"source_group": f"group_{i // 2}", "asset_id": str(i)} for i in range(30)]
        first = split_groups(entries)
        second = split_groups(list(reversed(entries)))
        mapping = {item["asset_id"]: item["split"] for item in first}
        self.assertEqual(mapping, {item["asset_id"]: item["split"] for item in second})
        for group in {item["source_group"] for item in first}:
            self.assertEqual(len({item["split"] for item in first if item["source_group"] == group}), 1)
        self.assertEqual({item["split"] for item in first}, {"train", "val", "test"})

    def test_no_acceptable_candidate_fails_instead_of_returning_repair(self):
        old = identity_grid(5) + 4
        image = identity_grid(3).reshape(9, 3, 3)
        entries = [{"split": split} for split in ("train", "val", "test")]
        pairs = [(image, image, 0)] * 3
        with self.assertRaisesRegex(ValueError, "No candidate passed"):
            fit_style(old, entries, pairs, torch.device("cpu"), steps=0)

    def test_export_rejects_gray_tone_reversal(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "bad-tone.cube"
            table = identity_grid(17)
            table[8, 8, 8] -= 0.2
            with self.assertRaisesRegex(ValueError, "Gray tone"):
                export_checked(table, path, "bad-tone")
            self.assertFalse(path.exists())

    def test_smoothing_keeps_identity_and_reduces_isolated_color_jump(self):
        identity = identity_grid(17)
        self.assertTrue(torch.allclose(smooth_residual(identity), identity, atol=1e-7))
        old = identity.clone()
        old[5, 9, 12] += 0.12
        smoothed = smooth_residual(old)
        self.assertLess(float((smoothed[5, 9, 12] - identity[5, 9, 12]).max()), 0.06)
        self.assertLess(float((smoothed - identity).abs().mean()), 0.001)


if __name__ == "__main__":
    unittest.main()
