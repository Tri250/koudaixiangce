"""Bounded reference-assisted refinement of authored display-sRGB looks.

CanonCGT pseudo pairs are auxiliary evidence, not photographer ground truth.
The exported transform is fixed by references and the authored prior only.
"""

import argparse
import hashlib
import io
import json
import sys
from pathlib import Path
from types import SimpleNamespace

import numpy as np
import torch
import torch.nn.functional as F
import yaml
from PIL import Image, ImageCms, ImageOps

from hamada_neural.cube import LUTTransform, export_cube, read_cube, sample_lut
from hamada_neural.model import build_identity_grid as identity_grid


STYLES = {
    "hamada_daylight_pro_v2": None,
    "takashi_yasui_zen": "01-takashi-yasui",
    "takahiro_sakai_blue": "02-takahiro-sakai",
    "withluke_epic": "03-withluke",
    "daniel_casson_autumn": "04-daniel-casson",
    "billy_dinh_cinema": "05-billy-dinh",
    "rkrkrk_cyberpunk": "06-rkrkrk",
    "samalive_vintage": "07-samalive",
    "iwona_fairy_tale": "08-iwona-podlasinska",
    "cinestill_800t": "09-cinestill-800t",
    "wilson_lee_golden": "10-wilson-lee",
    "luizclas_portrait": "11-luizclas",
    "ivan_ivanchenko_twilight": "12-ivan-ivanchenko",
    "jungraphy_neon": "13-jungraphy",
}
LUMA = (0.2126, 0.7152, 0.0722)


def luminance(rgb):
    return (rgb * rgb.new_tensor(LUMA)).sum(-1, keepdim=True)


def smooth_residual(table):
    identity = identity_grid(table.shape[0], table.device)
    residual = (table - identity).permute(3, 0, 1, 2)[None]
    for axis in range(3):
        shape = [1, 1, 1]
        shape[axis] = 3
        kernel = table.new_tensor([0.25, 0.5, 0.25]).reshape(1, 1, *shape).repeat(3, 1, 1, 1, 1)
        padding = [0] * 6
        padding[2 * (2 - axis)] = padding[2 * (2 - axis) + 1] = 1
        residual = F.conv3d(F.pad(residual, padding, mode="replicate"), kernel, groups=3)
    return identity + residual[0].permute(1, 2, 3, 0)


def correction_table(old, params):
    delta = 0.04 * torch.tanh(params)
    expanded = F.interpolate(delta.permute(3, 0, 1, 2)[None], size=old.shape[:3],
                             mode="trilinear", align_corners=True)[0].permute(1, 2, 3, 0)
    return old + expanded


def repair_table(table):
    if not torch.isfinite(table).all():
        raise ValueError("Nonfinite LUT")
    identity = identity_grid(table.shape[0], table.device)
    y = luminance(table).clamp(0, 1)
    index = torch.arange(table.shape[0], device=table.device)
    gray_y = y[index, index, index, 0]
    gray_delta = torch.cummax(gray_y, dim=0).values - gray_y
    # 单独增加一种原色也会改变色相，不能把其亮度下降一概当成影调反转。
    # 只修复灰轴影调，并将同一微量修正平滑应用到相邻颜色。
    position = luminance(identity) * (table.shape[0] - 1)
    lower = position.floor().long().clamp(0, table.shape[0] - 2)
    correction = gray_delta[lower] * (1 - position + lower) + gray_delta[lower + 1] * (position - lower)
    y = 0.001 + 0.998 * (0.99 * (y + correction).clamp(0, 1) + 0.01 * luminance(identity))
    chroma = table - luminance(table)
    positive = torch.where(chroma > 0, (0.999 - y) / chroma.clamp_min(1e-8), torch.inf)
    negative = torch.where(chroma < 0, (y - 0.001) / (-chroma).clamp_min(1e-8), torch.inf)
    scale = torch.minimum(positive.amin(-1, keepdim=True), negative.amin(-1, keepdim=True)).clamp(0, 1)
    return y + chroma * scale


def export_checked(table, path, name):
    if not torch.isfinite(table).all() or table.min() < 0 or table.max() > 1:
        raise ValueError("Invalid export table")
    gray = torch.linspace(0, 1, 257, device=table.device)[:, None].repeat(1, 3)
    if (torch.diff(luminance(sample_lut(table, gray))[:, 0]) < -1e-6).any():
        raise ValueError("Gray tone must be nondecreasing")
    report = export_cube(LUTTransform(table.cpu(), name=name, asset_kind="referenceStyle"), path, grid_size=65)
    if not report.qa.passed or report.grid_size != 65:
        Path(path).unlink(missing_ok=True)
        Path(path).with_suffix(".metadata.json").unlink(missing_ok=True)
        raise ValueError("CUBE must pass representation error <= 0.02 at 65-grid")
    path = Path(path)
    text = path.read_text().replace("# Created by RawLab Hamada Neural CUBE Pipeline",
                                    "# RawLab reference-assisted refinement v2; authored prior + CanonCGT pseudo supervision")
    path.write_text(text)
    return report


def load_reference(path):
    with Image.open(path) as opened:
        profile = opened.info.get("icc_profile")
        original_mode = opened.mode
        image = ImageOps.exif_transpose(opened)
        image.thumbnail((256, 256), Image.Resampling.LANCZOS)
        if profile:
            try:
                src = ImageCms.ImageCmsProfile(io.BytesIO(profile))
                dst = ImageCms.createProfile("sRGB")
                image = ImageCms.profileToProfile(image, src, dst, outputMode="RGB")
            except (ImageCms.PyCMSError, OSError, ValueError) as exc:
                raise ValueError(f"Invalid or unsupported ICC: {path.name}") from exc
            handling = "icc_to_srgb"
        else:
            image = image.convert("RGB")
            handling = "untagged_assumed_srgb"
        rgb = np.asarray(image.convert("RGB"), dtype=np.float32) / 255
        return rgb, {"color_handling": handling, "original_mode": original_mode,
                     "monochrome": bool(np.abs(rgb - rgb[..., :1]).max() < 0.008)}


def split_groups(entries):
    groups = sorted({item["source_group"] for item in entries},
                    key=lambda group: hashlib.sha256(group.encode()).hexdigest())
    if len(groups) < 5:
        raise ValueError("Need at least five independent reference groups")
    count = max(1, len(groups) // 5)
    mapping = {group: "test" if i < count else "val" if i < count * 2 else "train"
               for i, group in enumerate(groups)}
    return [dict(item, split=mapping[item["source_group"]]) for item in entries]


def load_model(repo, checkpoint, device):
    sys.path.insert(0, str(repo))
    from models.networks.SSL_training import CanonCGT_SSL
    config = yaml.safe_load((repo / "configs/Stage3_SSL_training_Flickr2K_PPR10K_LSDIR.yaml").read_text())
    model = CanonCGT_SSL(SimpleNamespace(**config))
    loaded = torch.load(checkpoint, map_location="cpu", weights_only=False)
    state = loaded.get("model_state_dict", loaded)
    model.load_state_dict({key.removeprefix("module."): value for key, value in state.items()}, strict=True)
    return model.to(device).eval()


def collect_references(style, folder, hamada_manifest, hamada_images):
    if folder:
        records = [{"path": path, "source_group": None,
                    "authorization": {"use_basis": "operator_supplied_reference_directory",
                                      "copyright_status": "not_verified"}} for path in sorted(folder.iterdir())
                   if path.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp")]
    else:
        rows = [json.loads(line) for line in hamada_manifest.read_text().splitlines() if line.strip()]
        records = [{"path": hamada_images / row["path"], "source_group": row["source_id"],
                    "authorization": {"use_basis": "approved_manifest", "rights_status": row.get("rights_status"),
                                      "allow_train": row["allow_train"], "allow_transfer": row["allow_transfer"]}}
                   for row in rows if row.get("allow_train") and row.get("allow_transfer")
                   and row["style_id"] == "hamada_daylight"]
    entries, images, rejected, representatives = [], [], [], []
    for record in records:
        path = record["path"]
        try:
            rgb, info = load_reference(path)
        except (OSError, ValueError) as exc:
            rejected.append({"file": path.name, "reason": str(exc)})
            continue
        if info["monochrome"]:
            rejected.append({"file": path.name, "reason": "monochrome"})
            continue
        proxy = np.asarray(Image.fromarray((rgb * 255).astype(np.uint8)).resize((64, 64)), dtype=np.float32) / 255
        fingerprint = hashlib.sha256((proxy * 255).astype(np.uint8).tobytes()).hexdigest()
        duplicate = next((item for item in representatives if np.abs(proxy - item["proxy"]).mean() < 0.006), None)
        if duplicate:
            rejected.append({"file": path.name, "reason": "duplicate_proxy", "duplicate_of": duplicate["file"]})
            continue
        representatives.append({"file": path.name, "proxy": proxy})
        index = len(entries)
        with Image.open(path) as source:
            capture_day = str(source.getexif().get(36867, ""))[:10]
        group = record["source_group"] or (f"{style}_{capture_day}" if capture_day else f"{style}_{fingerprint}")
        entries.append({"asset_id": f"{style}_{index:03d}", "file": path.name, "fingerprint": fingerprint,
                        "source_group": group, "split_basis": "provided_series" if record["source_group"] else
                        "capture_day" if capture_day else "proxy_dedup_only",
                        "label_origin": "model_teacher", **record["authorization"], **info})
        images.append(torch.from_numpy(rgb).permute(2, 0, 1))
    return split_groups(entries), images, rejected


def pseudo_pairs(model, images, device):
    pairs = []
    with torch.inference_mode():
        for image in images:
            target = F.interpolate(image[None].to(device), size=(224, 224), mode="bilinear", align_corners=False)
            token = model.Embedding_Net(target)
            raw = model.Canonicalizer(target, condition=token)["result"]
            source = raw.clamp(0, 1)
            pairs.append((source[0].permute(1, 2, 0).cpu(), target[0].permute(1, 2, 0).cpu(),
                          float(((raw < 0) | (raw > 1)).float().mean())))
    return pairs


def color_loss(actual, target):
    y_actual, y_target = luminance(actual), luminance(target)
    return (actual - y_actual - target + y_target).square().mean() + 0.05 * (y_actual - y_target).square().mean()


def sampled_pairs(entries, pairs, split, device, seed):
    generator = torch.Generator().manual_seed(seed)
    selected = []
    for entry, (source, target, _) in zip(entries, pairs):
        if entry["split"] != split:
            continue
        indices = torch.randperm(source.numel() // 3, generator=generator)[:2048]
        selected.append((source.reshape(-1, 3)[indices], target.reshape(-1, 3)[indices]))
    if not selected:
        raise ValueError(f"Empty {split} split")
    return torch.stack([item[0] for item in selected]).to(device), torch.stack([item[1] for item in selected]).to(device)


def table_quality(table, old):
    y = luminance(table).squeeze(-1)
    interior = table[1:-1, 1:-1, 1:-1]
    gray = torch.linspace(0, 1, 257, device=table.device)[:, None].repeat(1, 3)
    gray_y = luminance(sample_lut(table, gray))[:, 0]
    return {"interior_clipped_percent": float(((interior <= 1e-6) | (interior >= 1 - 1e-6)).any(-1).float().mean() * 100),
            "luma_reversals": sum(int((torch.diff(y, dim=axis) < -1e-6).sum()) for axis in range(3)),
            "severe_luma_reversals": sum(int((torch.diff(y, dim=axis) < -0.030).sum()) for axis in range(3)),
            "gray_reversals": int((torch.diff(gray_y) < -1e-6).sum()),
            "mean_change": float((table - old).abs().mean()), "max_change": float((table - old).abs().max()),
            "gray_max_change": float((sample_lut(table, gray) - sample_lut(old, gray)).abs().max())}


def fit_style(old, entries, pairs, device, steps):
    old = old.to(device)
    quality = table_quality(old, old)
    edges = 3 * old.shape[0] ** 2 * (old.shape[0] - 1)
    smoothed = quality["severe_luma_reversals"] / edges > 0.005
    base = smooth_residual(old) if smoothed else old
    train_x, train_y = sampled_pairs(entries, pairs, "train", device, 20261011)
    val_x, val_y = sampled_pairs(entries, pairs, "val", device, 20261012)
    test_x, test_y = sampled_pairs(entries, pairs, "test", device, 20261013)
    grid = identity_grid(17, device)
    anchor_old = sample_lut(old, grid)
    gray = torch.linspace(0.02, 0.98, 129, device=device)[:, None].repeat(1, 3)
    skin = old.new_tensor([[0.75, 0.51, 0.36], [0.52, 0.33, 0.24], [0.9, 0.72, 0.61]])
    probes = torch.cat([gray, skin])
    probe_old = sample_lut(old, probes)
    params = torch.zeros(9, 9, 9, 3, device=device, requires_grad=True)
    optimizer = torch.optim.Adam([params], lr=0.03)
    best, best_score, best_step, history = None, float("inf"), None, []
    for step in range(steps + 1):
        delta = 0.04 * torch.tanh(params)
        if step % 50 == 0 or step == steps:
            with torch.no_grad():
                candidate = repair_table(correction_table(base, params))
                val_loss = color_loss(sample_lut(candidate, val_x), val_y)
                preserve = (sample_lut(candidate, grid) - anchor_old).square().mean()
                anchor = (sample_lut(candidate, probes) - probe_old).square().mean()
                score = float(val_loss + 0.5 * preserve + 2 * anchor)
                quality = table_quality(candidate, old)
                history.append({"step": step, "validation_pseudo_loss": float(val_loss), "selection_score": score, **quality})
                if (score < best_score and quality["gray_max_change"] <= 0.035
                        and quality["max_change"] <= 0.08 and quality["mean_change"] <= 0.015
                        and quality["gray_reversals"] == 0):
                    best, best_score, best_step = candidate.detach().clone(), score, step
        if step == steps:
            break
        index = step % train_x.shape[0]
        candidate = repair_table(correction_table(base, params))
        prediction = sample_lut(candidate, train_x[index])
        anchor_prediction = sample_lut(candidate, grid)
        probe_prediction = sample_lut(candidate, probes)
        smooth = sum(torch.diff(delta, n=2, dim=axis).square().mean() for axis in range(3))
        y = luminance(anchor_prediction).squeeze(-1)
        monotone = sum(F.relu(-torch.diff(y, dim=axis)).square().mean() for axis in range(3))
        loss = (color_loss(prediction, train_y[index]) + 6 * (anchor_prediction - anchor_old).square().mean()
                + 10 * (probe_prediction - probe_old).square().mean() + 8 * smooth + 10 * monotone
                + 20 * (F.relu(-anchor_prediction).square().mean() + F.relu(anchor_prediction - 1).square().mean()))
        optimizer.zero_grad()
        loss.backward()
        optimizer.step()
    if best is None:
        raise ValueError("No candidate passed gray-tone and authored-look preservation gates")
    with torch.no_grad():
        metrics = {"selected_step": best_step, "prior_smoothed": smoothed,
                   "old_validation_pseudo_loss": float(color_loss(sample_lut(old, val_x), val_y)),
                   "new_validation_pseudo_loss": float(color_loss(sample_lut(best, val_x), val_y)),
                   "old_test_pseudo_loss": float(color_loss(sample_lut(old, test_x), test_y)),
                   "new_test_pseudo_loss": float(color_loss(sample_lut(best, test_x), test_y)),
                   "old_quality": table_quality(old, old), "new_quality": table_quality(best, old), "history": history}
    return best.cpu(), metrics


def save_preview(old, new, source, target, path):
    panels = [source, sample_lut(old, source), sample_lut(new, source), target]
    arrays = [(panel.clamp(0, 1).numpy() * 255).round().astype(np.uint8) for panel in panels]
    Image.fromarray(np.concatenate(arrays, axis=1)).save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--references", type=Path, required=True)
    parser.add_argument("--hamada-manifest", type=Path, required=True)
    parser.add_argument("--hamada-images", type=Path, required=True)
    parser.add_argument("--old-luts", type=Path, required=True)
    parser.add_argument("--model-repo", type=Path, required=True)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--styles", nargs="+", choices=STYLES, default=list(STYLES))
    parser.add_argument("--steps", type=int, default=300)
    args = parser.parse_args()
    torch.manual_seed(20261011)
    torch.set_num_threads(4)
    device = torch.device("cuda:0")
    args.output.mkdir(parents=True, exist_ok=False)
    model = load_model(args.model_repo, args.checkpoint, device)
    environment = {"torch": torch.__version__, "gpu": torch.cuda.get_device_name(),
                   "checkpoint_sha256": hashlib.sha256(args.checkpoint.read_bytes()).hexdigest(),
                   "steps": args.steps, "supervision": "CanonCGT canonicalization pseudo pairs, not real pairs",
                   "six_ui_controls_baked": False, "space": "display-sRGB, BT.709, [0,1]",
                   "correction_budget": 0.04, "max_mean_change": 0.015, "max_gray_change": 0.035}
    (args.output / "environment.json").write_text(json.dumps(environment, indent=2))
    reports = {}
    for style in args.styles:
        folder = args.references / STYLES[style] if STYLES[style] else None
        entries, images, rejected = collect_references(style, folder, args.hamada_manifest, args.hamada_images)
        counts = {split: sum(entry["split"] == split for entry in entries) for split in ("train", "val", "test")}
        print(f"[{style}] {counts}; excluded={len(rejected)}", flush=True)
        pairs = pseudo_pairs(model, images, device)
        contract, old = read_cube(args.old_luts / f"{style}.cube")
        if (contract["gamma"] != "sRGB to sRGB" or contract["gamut"] != "ITU-R BT.709 to ITU-R BT.709"
                or contract["asset_kind"] != "referenceStyle" or contract["domain_min"] != [0, 0, 0]
                or contract["domain_max"] != [1, 1, 1] or old.shape[0] != 65):
            raise ValueError("Authored prior must be a 65-grid display-sRGB referenceStyle LUT")
        new, metrics = fit_style(old, entries, pairs, device, args.steps)
        report = export_checked(new, args.output / f"{style}.cube", style)
        private = {"references": entries, "rejected": rejected, "split_counts": counts,
                   "teacher_oob_mean": float(np.mean([pair[2] for pair in pairs])),
                   "representation_max_error": report.qa.max_channel_error,
                   "prior_sha256": hashlib.sha256((args.old_luts / f"{style}.cube").read_bytes()).hexdigest(), **metrics}
        (args.output / f"{style}.report.json").write_text(json.dumps(private, indent=2))
        heldout = next(i for i, entry in enumerate(entries) if entry["split"] == "test")
        save_preview(old, new, pairs[heldout][0], pairs[heldout][1], args.output / f"{style}.private-preview.png")
        reports[style] = {key: private[key] for key in ("split_counts", "selected_step", "representation_max_error", "old_quality", "new_quality",
                                                      "old_validation_pseudo_loss", "new_validation_pseudo_loss",
                                                      "old_test_pseudo_loss", "new_test_pseudo_loss")}
        (args.output / "summary.json").write_text(json.dumps(reports, indent=2))
        print(f"[{style}] exported: {metrics['new_quality']}", flush=True)


if __name__ == "__main__":
    main()
