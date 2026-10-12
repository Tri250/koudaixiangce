"""Fit bounded, editable HSL residuals without changing or rebaking the CUBEs.

Canonicalized display pseudo pairs are auxiliary color evidence, not RAW pairs.
Exposure and camera WB cannot be reconstructed from these reference JPEGs.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path

import numpy as np
import torch
from PIL import Image

from hamada_neural.cube import read_cube, sample_lut
from optimize import (STYLES, collect_references, color_loss, identity_grid,
                      load_model, luminance, pseudo_pairs, sampled_pairs)


LIMITS = (10.0, 15.0, 2.0)


def read_frozen_lut(path, expected_sha256):
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected_sha256:
        raise ValueError("Frozen LUT hash mismatch")
    contract, lut = read_cube(path)
    if (contract["gamma"] != "sRGB to sRGB" or contract["gamut"] != "ITU-R BT.709 to ITU-R BT.709"
            or contract["asset_kind"] != "referenceStyle" or contract["domain_min"] != [0, 0, 0]
            or contract["domain_max"] != [1, 1, 1] or tuple(lut.shape) != (65, 65, 65, 3)
            or not torch.isfinite(lut).all() or lut.min() < 0 or lut.max() > 1):
        raise ValueError("Expected a frozen display-sRGB/BT.709 65-grid creative LUT")
    return lut


def apply_hsl(rgb, controls, exact_bypass=True):
    maximum, index = rgb.max(-1)
    minimum = rgb.min(-1).values
    delta = maximum - minimum
    denominator = delta.clamp_min(1e-6)
    r, g, b = rgb.unbind(-1)
    hue = torch.where(index == 0, 60 * torch.remainder((g - b) / denominator, 6),
                      torch.where(index == 1, 60 * ((b - r) / denominator + 2),
                                  60 * ((r - g) / denominator + 4)))
    centers = rgb.new_tensor([0, 30, 60, 120, 180, 240, 270, 300, 360])
    lower = torch.bucketize(hue.contiguous(), centers[1:8], right=True)
    upper = (lower + 1) % 8
    blend = (hue - centers[lower]) / (centers[lower + 1] - centers[lower])
    blend = blend.square() * (3 - 2 * blend)
    values = controls[:, lower] * (1 - blend) + controls[:, upper] * blend
    dh, ds, dl = values.unbind(0)
    lightness = ((maximum + minimum) * 0.5).clamp(0, 1)
    saturation = delta / (1 - (2 * lightness - 1).abs()).clamp_min(1e-6)
    hue = torch.remainder(hue + dh * 0.3, 360)
    saturation = (saturation * (1 + ds * 0.01)).clamp(0, 1)
    lightness = (lightness + dl * 0.005).clamp(0, 1)
    chroma = (1 - (2 * lightness - 1).abs()) * saturation
    x = chroma * (1 - (torch.remainder(hue / 60, 2) - 1).abs())
    zero = torch.zeros_like(chroma)
    choices = torch.stack([torch.stack(parts, -1) for parts in
                           ((chroma, x, zero), (x, chroma, zero), (zero, chroma, x),
                            (zero, x, chroma), (x, zero, chroma), (chroma, zero, x))])
    sector = (hue / 60).floor().long().clamp(0, 5)
    result = choices.gather(0, sector[None, ..., None].expand(1, *sector.shape, 3))[0]
    result = result + (lightness - chroma * 0.5)[..., None]
    bypass = delta <= 1e-6
    if exact_bypass:
        bypass = bypass | ((dh == 0) & (ds == 0) & (dl == 0))
    return torch.where(bypass[..., None], rgb, result)


def apply_tone(rgb, recipe):
    shadows = recipe["shadowsDelta"] / 100
    highlights = -recipe["highlightsDelta"] / 100
    result = rgb
    if shadows != 0 or highlights != 0:
        y = luminance(result)
        adjusted = y
        if shadows != 0:
            adjusted = adjusted.clamp(0, 1).pow(1 - shadows * 0.5)
        if highlights != 0:
            adjusted = 1 - (1 - adjusted.clamp(0, 1)).pow(1 - highlights * 0.5)
        result = torch.where(y > 0, result * adjusted / y.clamp_min(1e-8), adjusted)
    contrast = 1 + recipe["contrastDelta"] / 100
    saturation = 1 + recipe["saturationDelta"] / 100
    if contrast != 1 or saturation != 1:
        result = (result - 0.5) * contrast + 0.5
        y = luminance(result)
        result = y + (result - y) * saturation
    return result


def clipped_fraction(rgb):
    return float(((rgb <= 1e-6) | (rgb >= 1 - 1e-6)).any(-1).float().mean())


def candidate_quality(anchors, controls, references, gray=None, skin=None):
    baseline = anchors.clamp(0, 1)
    changed = apply_hsl(anchors, controls).clamp(0, 1)
    delta = (changed - baseline).abs()
    reference_before = references.clamp(0, 1)
    reference_after = apply_hsl(references, controls).clamp(0, 1)
    clipping_change = clipped_fraction(reference_after) - clipped_fraction(reference_before)
    quality = {"mean_rgb_change": float(delta.mean()), "max_rgb_change": float(delta.max()),
               "clipped_fraction_increase": clipping_change, "gray_max_change": 0.0,
               "gray_reversals": 0, "skin_max_change": 0.0}
    if gray is not None:
        changed_gray = apply_hsl(gray, controls).clamp(0, 1)
        quality["gray_max_change"] = float((changed_gray - gray.clamp(0, 1)).abs().max())
        quality["gray_reversals"] = int((torch.diff(luminance(changed_gray)[:, 0]) < -1e-6).sum())
    if skin is not None:
        quality["skin_max_change"] = float((apply_hsl(skin, controls).clamp(0, 1) - skin.clamp(0, 1)).abs().max())
    bounded = controls.shape == (3, 8) and bool(torch.isfinite(controls).all())
    bounded = bounded and all(bool((controls[i].abs() <= limit).all()) for i, limit in enumerate(LIMITS))
    quality["accepted"] = (bounded and quality["mean_rgb_change"] <= 0.015
                           and quality["max_rgb_change"] <= 0.065 and clipping_change <= 0.005
                           and quality["gray_max_change"] <= 0.015 and quality["gray_reversals"] == 0
                           and quality["skin_max_change"] <= 0.025)
    return quality


def fit_hsl(train_x, train_y, val_x, val_y, test_x, test_y, anchors, steps=300, gray=None, skin=None):
    bounds = anchors.new_tensor(LIMITS)[:, None]
    params = torch.zeros(3, 8, device=anchors.device, requires_grad=True)
    optimizer = torch.optim.Adam([params], lr=0.04)
    baseline = anchors.clamp(0, 1)
    val_before = float(color_loss(val_x.clamp(0, 1), val_y))
    best = torch.zeros_like(params)
    best_score, best_step = val_before, 0
    history = []
    for step in range(steps + 1):
        if step % 25 == 0 or step == steps:
            with torch.no_grad():
                controls = (bounds * params.tanh()).round()
                quality = candidate_quality(anchors, controls, val_x, gray, skin)
                loss = float(color_loss(apply_hsl(val_x, controls).clamp(0, 1), val_y))
                preservation = (apply_hsl(anchors, controls).clamp(0, 1) - baseline).square().mean()
                score = loss + 2 * float(preservation)
                history.append(dict(step=step, validation_loss=loss, score=score, **quality))
                if quality["accepted"] and loss <= val_before * 0.999 and score < best_score:
                    best, best_score, best_step = controls.clone(), score, step
        if step == steps:
            break
        controls = bounds * params.tanh()
        index = step % train_x.shape[0]
        predicted = apply_hsl(train_x[index], controls, exact_bypass=False).clamp(0, 1)
        anchor_after = apply_hsl(anchors, controls, exact_bypass=False).clamp(0, 1)
        normalized = controls / bounds
        smoothness = (normalized - normalized.roll(1, -1)).square().mean()
        loss = color_loss(predicted, train_y[index]) + 4 * (anchor_after - baseline).square().mean()
        loss = loss + 0.0002 * normalized.square().mean() + 0.0001 * smoothness
        if skin is not None:
            loss = loss + 10 * (apply_hsl(skin, controls, exact_bypass=False).clamp(0, 1) - skin.clamp(0, 1)).square().mean()
        optimizer.zero_grad()
        loss.backward()
        if not torch.isfinite(params.grad).all():
            raise ValueError("Nonfinite HSL fitting gradient")
        optimizer.step()
    with torch.no_grad():
        quality = candidate_quality(anchors, best, val_x, gray, skin)
        if not quality["accepted"]:
            raise ValueError("No HSL candidate, including identity, passed preservation gates")
        report = {"selected_step": best_step, "validation_loss_before": val_before,
                  "validation_loss_after": float(color_loss(apply_hsl(val_x, best).clamp(0, 1), val_y)),
                  "test_loss_before": float(color_loss(test_x.clamp(0, 1), test_y)),
                  "test_loss_after": float(color_loss(apply_hsl(test_x, best).clamp(0, 1), test_y)),
                  "quality": quality, "history": history}
    return best, report


def preview(source, before, controls, target, path):
    panels = [source, before, apply_hsl(before, controls), target]
    arrays = [(panel.clamp(0, 1).cpu().numpy() * 255).round().astype(np.uint8) for panel in panels]
    Image.fromarray(np.concatenate(arrays, axis=1)).save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--luts", type=Path, required=True)
    parser.add_argument("--references", type=Path, required=True)
    parser.add_argument("--hamada-manifest", type=Path, required=True)
    parser.add_argument("--hamada-images", type=Path, required=True)
    parser.add_argument("--baseline-reports", type=Path, required=True)
    parser.add_argument("--model-repo", type=Path, required=True)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--steps", type=int, default=300)
    args = parser.parse_args()
    catalog = json.loads(args.catalog.read_text())
    if len(catalog) != 14 or len({recipe["id"] for recipe in catalog}) != 14:
        raise ValueError("Expected the complete fourteen-recipe catalog")
    args.output.mkdir(parents=True, exist_ok=False)
    torch.manual_seed(20261011)
    torch.set_num_threads(4)
    device = torch.device("cuda:0")
    model = load_model(args.model_repo, args.checkpoint, device)
    environment = {"torch": torch.__version__, "gpu": torch.cuda.get_device_name(),
                   "visible_gpu_uuid": os.environ.get("CUDA_VISIBLE_DEVICES"),
                   "checkpoint_sha256": hashlib.sha256(args.checkpoint.read_bytes()).hexdigest(),
                   "bounds": dict(zip(("hue", "saturation", "lightness"), LIMITS)),
                   "lut_changed": False, "hsl_baked_into_lut": False,
                   "supervision": "Canonicalized display pseudo pairs; not full RAW or real before/after pairs",
                   "upstream_exposure_and_camera_wb_reconstructed": False,
                   "post_lut_tone_controls_applied": True}
    (args.output / "environment.json").write_text(json.dumps(environment, indent=2))
    summaries = {}
    for recipe in catalog:
        style = Path(recipe["lutFileName"]).stem
        folder = args.references / STYLES[style] if STYLES[style] else None
        entries, images, rejected = collect_references(style, folder, args.hamada_manifest, args.hamada_images)
        previous = json.loads((args.baseline_reports / f"{style}.report.json").read_text())
        previous_split = {entry["fingerprint"]: entry["split"] for entry in previous["references"]}
        if {entry["fingerprint"]: entry["split"] for entry in entries} != previous_split:
            raise ValueError(f"References/splits changed for {style}")
        pairs = pseudo_pairs(model, images, device)
        torch.save(pairs, args.output / f"{style}.private-pairs.pt")
        lut = read_frozen_lut(args.luts / recipe["lutFileName"], recipe["lutSHA256"])
        lut = lut.to(device)
        mapped = [(apply_tone(sample_lut(lut, source.to(device)), recipe).cpu(), target, oob)
                  for source, target, oob in pairs]
        train = sampled_pairs(entries, mapped, "train", device, 20261014)
        val = sampled_pairs(entries, mapped, "val", device, 20261015)
        test = sampled_pairs(entries, mapped, "test", device, 20261016)
        anchors = apply_tone(sample_lut(lut, identity_grid(17, device).reshape(-1, 3)), recipe)
        gray_input = torch.linspace(0.02, 0.98, 129, device=device)[:, None].repeat(1, 3)
        skin_input = lut.new_tensor([[0.75, 0.51, 0.36], [0.52, 0.33, 0.24], [0.9, 0.72, 0.61]])
        gray = apply_tone(sample_lut(lut, gray_input), recipe)
        skin = apply_tone(sample_lut(lut, skin_input), recipe)
        controls, metrics = fit_hsl(*train, *val, *test, anchors, args.steps, gray, skin)
        counts = {split: sum(entry["split"] == split for entry in entries) for split in ("train", "val", "test")}
        summary = {"id": recipe["id"], "hsl": dict(zip(("hue", "saturation", "lightness"), controls.int().cpu().tolist())),
                   "split_counts": counts, "retained_references": len(entries), "rejected_references": len(rejected),
                   "lut_sha256": hashlib.sha256((args.luts / recipe["lutFileName"]).read_bytes()).hexdigest(),
                   **{key: value for key, value in metrics.items() if key != "history"}}
        private = {**summary, "references": entries, "rejected": rejected, "history": metrics["history"],
                   "teacher_oob_mean": float(np.mean([pair[2] for pair in pairs]))}
        (args.output / f"{style}.private-report.json").write_text(json.dumps(private, indent=2))
        heldout = next(i for i, entry in enumerate(entries) if entry["split"] == "test")
        preview(pairs[heldout][0].to(device), mapped[heldout][0].to(device), controls,
                pairs[heldout][1].to(device), args.output / f"{style}.private-preview.png")
        summaries[recipe["id"]] = summary
        (args.output / "summary.json").write_text(json.dumps(summaries, indent=2))
        print(f"[{style}] {counts}, step={metrics['selected_step']}, HSL={summary['hsl']}, quality={metrics['quality']}", flush=True)


if __name__ == "__main__":
    main()
