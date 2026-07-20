import React, { useState, useCallback, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { invoke } from '@tauri-apps/api/core';
import { convertFileSrc } from '@tauri-apps/api/core';
import { open as openDialog } from '@tauri-apps/plugin-dialog';
import { toast } from 'react-toastify';
import {
  Palette,
  Sun,
  ZoomIn,
  CreditCard,
  Shirt,
  CircleDot,
  Recycle,
  Flower2,
  Loader2,
  RotateCcw,
  Sparkles,
  UserMinus,
  Search,
} from 'lucide-react';
import clsx from 'clsx';
import Slider from '../../ui/Slider';
import CollapsibleSection from '../../ui/CollapsibleSection';
import Switch from '../../ui/Switch';
import Button from '../../ui/Button';
import Dropdown from '../../ui/Dropdown';
import Text from '../../ui/Text';
import { TextColors, TextVariants, TextWeights } from '../../../types/typography';
import { useEditorStore } from '../../../store/useEditorStore';
import { Adjustments } from '../../../utils/adjustments';
import { useRetouching } from '../../../hooks/useRetouching';

// ── Helper ────────────────────────────────────────────────────────────

const getTransformAdjustments = (adj: Adjustments) => ({
  transformDistortion: adj.transformDistortion,
  transformVertical: adj.transformVertical,
  transformHorizontal: adj.transformHorizontal,
  transformRotate: adj.transformRotate,
  transformAspect: adj.transformAspect,
  transformScale: adj.transformScale,
  transformXOffset: adj.transformXOffset,
  transformYOffset: adj.transformYOffset,
  lensDistortionAmount: adj.lensDistortionAmount,
  lensVignetteAmount: adj.lensVignetteAmount,
  lensTcaAmount: adj.lensTcaAmount,
  lensDistortionParams: adj.lensDistortionParams,
  lensMaker: adj.lensMaker,
  lensModel: adj.lensModel,
  lensDistortionEnabled: adj.lensDistortionEnabled,
  lensTcaEnabled: adj.lensTcaEnabled,
  lensVignetteEnabled: adj.lensVignetteEnabled,
});

async function readFileAsBase64DataUrl(filePath: string): Promise<string> {
  const assetUrl = convertFileSrc(filePath);
  const response = await fetch(assetUrl);
  const blob = await response.blob();
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve(reader.result as string);
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

function hexToRgbTuple(hex: string): [number, number, number] {
  // Bug fix H1: Robust hex parsing with validation.
  // Invalid hex (short form, non-hex chars, empty string) would produce NaN,
  // which JSON.stringify converts to null, breaking serde u8 deserialization.
  const sanitized = (hex || '').replace('#', '').trim();
  // Support both #RRGGBB (6 chars) and #RGB (3 chars, expanded to RRGGBB)
  const full = sanitized.length === 3
    ? sanitized.split('').map((c) => c + c).join('')
    : sanitized;
  if (!/^[0-9a-fA-F]{6}$/.test(full)) {
    // Fallback to white instead of NaN-causing garbage
    return [255, 255, 255];
  }
  return [
    parseInt(full.substring(0, 2), 16),
    parseInt(full.substring(2, 4), 16),
    parseInt(full.substring(4, 6), 16),
  ];
}

// ── Color Match Section ──────────────────────────────────────────────

function ColorMatchSection() {
  const { t } = useTranslation();
  const adjustments = useEditorStore((s) => s.adjustments);
  const setEditor = useEditorStore((s) => s.setEditor);
  const [matchMethod, setMatchMethod] = useState<'histogram' | 'linear'>('histogram');
  const [strength, setStrength] = useState(50);
  const [referencePath, setReferencePath] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);

  const handleSelectReference = useCallback(async () => {
    try {
      const selected = await openDialog({
        filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp', 'tiff', 'bmp'] }],
        multiple: false,
      });
      if (selected && typeof selected === 'string') setReferencePath(selected);
    } catch (err) {
      console.error('select_reference_image failed:', err);
    }
  }, []);

  const handleApply = useCallback(async () => {
    if (!referencePath) return;
    setIsProcessing(true);
    try {
      const sourceAdjustments = getTransformAdjustments(adjustments);
      const referenceImageBase64 = await readFileAsBase64DataUrl(referencePath);
      // Bug fix M1: Backend ai_match_colors has a strength=1 edge case bug:
      //   if strength > 1.0 → strength / 100 (correct)
      //   else → strength (so 1 becomes 1.0 = 100% instead of 0.01)
      // Pre-normalizing to 0.0-1.0 on the frontend bypasses the buggy branch.
      const normalizedStrength = Math.max(0, Math.min(1, strength / 100));
      const result = await invoke<{
        temperature?: number;
        tint?: number;
        vibrance?: number;
        saturation?: number;
      }>('ai_match_colors', { source_adjustments: sourceAdjustments, reference_image_base64: referenceImageBase64, match_method: matchMethod, strength: normalizedStrength });
      // ai_match_colors returns color adjustment parameters; apply them to
      // the editor adjustments so the GPU preview reflects the match.
      if (result) {
        setEditor({
          adjustments: {
            ...adjustments,
            temperature: (adjustments.temperature ?? 0) + (result.temperature ?? 0) * 100,
            tint: (adjustments.tint ?? 0) + (result.tint ?? 0) * 100,
            vibrance: (adjustments.vibrance ?? 0) + (result.vibrance ?? 0) * 100,
            saturation: (adjustments.saturation ?? 0) + (result.saturation ?? 0) * 100,
          },
        });
        toast.success(t('editor.creative.colorMatch.apply'));
      }
    } catch (err) {
      console.error('apply_color_match failed:', err);
      toast.error(`${t('editor.creative.colorMatch.apply')} failed: ${err}`);
    } finally {
      setIsProcessing(false);
    }
  }, [referencePath, matchMethod, strength, adjustments, setEditor, t]);

  const methodOptions = [
    { label: t('editor.creative.colorMatch.methodHistogram'), value: 'histogram' as const },
    { label: t('editor.creative.colorMatch.methodLinear'), value: 'linear' as const },
  ];

  return (
    <div className="space-y-3 pt-2">
      <Button className="w-full bg-surface" onClick={handleSelectReference}>
        <Palette size={16} />
        <span className="ml-2">{t('editor.creative.colorMatch.selectReference')}</span>
      </Button>
      {referencePath && (
        <Text variant={TextVariants.small} color={TextColors.primary} className="truncate">
          {referencePath}
        </Text>
      )}
      <Dropdown
        options={methodOptions}
        value={matchMethod}
        onChange={setMatchMethod}
      />
      <Slider
        label={t('editor.creative.colorMatch.strength')}
        min={0}
        max={100}
        step={1}
        value={strength}
        onChange={(e) => setStrength(Number(e.target.value))}
        fillOrigin="min"
      />
      <Button className="w-full" onClick={handleApply} disabled={isProcessing || !referencePath}>
        {isProcessing ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
        <span className="ml-2">{t('editor.creative.colorMatch.apply')}</span>
      </Button>
    </div>
  );
}

// ── Fill Light Section ───────────────────────────────────────────────

function FillLightSection() {
  const { t } = useTranslation();
  const adjustments = useEditorStore((s) => s.adjustments);
  const setEditor = useEditorStore((s) => s.setEditor);
  const [direction, setDirection] = useState(0);
  const [intensity, setIntensity] = useState(50);
  const [softness, setSoftness] = useState(30);
  const [colorTemp, setColorTemp] = useState(0);
  const [isProcessing, setIsProcessing] = useState(false);

  const handleApply = useCallback(async () => {
    setIsProcessing(true);
    try {
      const jsAdjustments = getTransformAdjustments(adjustments);
      // Bug fix H3: Guard against NaN/Infinity sliders. Backend doesn't
      // validate is_finite(), and NaN would propagate through every pixel
      // computation, producing a fully black/garbage image.
      const safeDirection = Number.isFinite(direction) ? direction : 0;
      const safeIntensity = Number.isFinite(intensity) ? Math.max(0, Math.min(100, intensity)) : 0;
      const safeSoftness = Number.isFinite(softness) ? Math.max(0, Math.min(100, softness)) : 0;
      const safeColorTemp = Number.isFinite(colorTemp) ? Math.max(-100, Math.min(100, colorTemp)) : 0;
      const result = await invoke<string>('apply_fill_light', {
        js_adjustments: jsAdjustments,
        direction: safeDirection,
        // Bug fix #4: Normalize 0-100 values to 0.0-1.0 for the backend
        intensity: safeIntensity / 100,
        softness: safeSoftness / 100,
        // color_temp slider is -100..100; backend clamps to 0.0-1.0
        // (0 = cool blue, 1 = warm orange), so map -100..100 -> 0..1
        color_temp: (safeColorTemp + 100) / 200,
      });
      if (result) {
        setEditor({ retouchingResultUrl: result });
        toast.success(t('editor.creative.fillLight.apply'));
      }
    } catch (err) {
      console.error('apply_fill_light failed:', err);
      toast.error(`${t('editor.creative.fillLight.apply')} failed: ${err}`);
    } finally {
      setIsProcessing(false);
    }
  }, [adjustments, direction, intensity, softness, colorTemp, setEditor, t]);

  return (
    <div className="space-y-2 pt-2">
      <Slider
        label={t('editor.creative.fillLight.direction')}
        min={0}
        max={360}
        step={1}
        value={direction}
        onChange={(e) => setDirection(Number(e.target.value))}
        fillOrigin="min"
        suffix="°"
      />
      <Slider
        label={t('editor.creative.fillLight.intensity')}
        min={0}
        max={100}
        step={1}
        value={intensity}
        onChange={(e) => setIntensity(Number(e.target.value))}
        fillOrigin="min"
      />
      <Slider
        label={t('editor.creative.fillLight.softness')}
        min={0}
        max={100}
        step={1}
        value={softness}
        onChange={(e) => setSoftness(Number(e.target.value))}
        fillOrigin="min"
      />
      <Slider
        label={t('editor.creative.fillLight.colorTemp')}
        min={-100}
        max={100}
        step={1}
        value={colorTemp}
        onChange={(e) => setColorTemp(Number(e.target.value))}
      />
      <Button className="w-full" onClick={handleApply} disabled={isProcessing}>
        {isProcessing ? <Loader2 size={16} className="animate-spin" /> : <Sun size={16} />}
        <span className="ml-2">{t('editor.creative.fillLight.apply')}</span>
      </Button>
    </div>
  );
}

// ── Super Resolution Section ─────────────────────────────────────────

function SuperResolutionSection() {
  const { t } = useTranslation();
  const adjustments = useEditorStore((s) => s.adjustments);
  const setEditor = useEditorStore((s) => s.setEditor);
  const [modelType, setModelType] = useState<'esrgan' | 'real_esrgan'>('real_esrgan');
  const [isProcessing, setIsProcessing] = useState(false);

  const handleApply = useCallback(async () => {
    setIsProcessing(true);
    try {
      const jsAdjustments = getTransformAdjustments(adjustments);
      // Bug fix C1: Backend ESRGAN model is `realesrgan_x2plus.onnx` (2x only).
      // When the model is loaded, scale_factor=4 causes an index-out-of-bounds
      // panic in process_tile/process_full_image because the model output tensor
      // is sized for 2x but we read with 4x indices. Restrict to 2x until the
      // backend implements true 4x (e.g. two-pass 2x) or a 4x model is added.
      const scaleFactor = 2;
      const result = await invoke<string>('apply_super_resolution', { js_adjustments: jsAdjustments, scale_factor: scaleFactor, model_type: modelType });
      if (result) {
        setEditor({ retouchingResultUrl: result });
        toast.success(t('editor.creative.superResolution.apply'));
      }
    } catch (err) {
      console.error('apply_super_resolution failed:', err);
      toast.error(`${t('editor.creative.superResolution.apply')} failed: ${err}`);
    } finally {
      setIsProcessing(false);
    }
  }, [modelType, adjustments, setEditor, t]);

  return (
    <div className="space-y-3 pt-2">
      {/* Scale selector: only 2x available (see C1 comment in handleApply) */}
      <div className="grid grid-cols-1 gap-2">
        <button
          className="p-2 rounded-md text-sm font-medium transition-colors flex items-center justify-center gap-2 text-primary bg-surface ring-1 ring-accent"
        >
          2x
        </button>
      </div>
      <Dropdown
        options={[
          { label: 'Real-ESRGAN', value: 'real_esrgan' as const },
          { label: 'ESRGAN', value: 'esrgan' as const },
        ]}
        value={modelType}
        onChange={setModelType}
      />
      <Button className="w-full" onClick={handleApply} disabled={isProcessing}>
        {isProcessing ? <Loader2 size={16} className="animate-spin" /> : <ZoomIn size={16} />}
        <span className="ml-2">{t('editor.creative.superResolution.apply')}</span>
      </Button>
    </div>
  );
}

// ── ID Photo Section ─────────────────────────────────────────────────

function IdPhotoSection() {
  const { t } = useTranslation();
  const adjustments = useEditorStore((s) => s.adjustments);
  const setEditor = useEditorStore((s) => s.setEditor);
  const [sizePreset, setSizePreset] = useState<'one_inch' | 'two_inch' | 'passport'>('one_inch');
  const [bgColor, setBgColor] = useState('#FFFFFF');
  const [isProcessing, setIsProcessing] = useState(false);

  const handleProcess = useCallback(async () => {
    setIsProcessing(true);
    try {
      const jsAdjustments = getTransformAdjustments(adjustments);
      const backgroundColor = hexToRgbTuple(bgColor);
      // Bug fix #1: Backend expects '1inch'/'2inch'/'passport', not 'one_inch'/'two_inch'
      const sizeMap: Record<typeof sizePreset, string> = {
        one_inch: '1inch',
        two_inch: '2inch',
        passport: 'passport',
      };
      const result = await invoke<string>('process_id_photo', { js_adjustments: jsAdjustments, size: sizeMap[sizePreset], background_color: backgroundColor });
      if (result) {
        setEditor({ retouchingResultUrl: result });
        toast.success(t('editor.creative.idPhoto.process'));
      }
    } catch (err) {
      console.error('process_id_photo failed:', err);
      toast.error(`${t('editor.creative.idPhoto.process')} failed: ${err}`);
    } finally {
      setIsProcessing(false);
    }
  }, [sizePreset, bgColor, adjustments, setEditor, t]);

  const presetOptions = [
    { label: t('editor.creative.idPhoto.oneInch'), value: 'one_inch' as const },
    { label: t('editor.creative.idPhoto.twoInch'), value: 'two_inch' as const },
    { label: t('editor.creative.idPhoto.passport'), value: 'passport' as const },
  ];

  return (
    <div className="space-y-3 pt-2">
      <Dropdown
        options={presetOptions}
        value={sizePreset}
        onChange={setSizePreset}
      />
      <div className="flex items-center gap-3">
        <Text variant={TextVariants.label}>{t('editor.creative.idPhoto.bgColor')}</Text>
        <input
          type="color"
          value={bgColor}
          onChange={(e) => setBgColor(e.target.value)}
          className="w-8 h-8 rounded cursor-pointer border border-border-color"
        />
      </div>
      <Button className="w-full" onClick={handleProcess} disabled={isProcessing}>
        {isProcessing ? <Loader2 size={16} className="animate-spin" /> : <CreditCard size={16} />}
        <span className="ml-2">{t('editor.creative.idPhoto.process')}</span>
      </Button>
    </div>
  );
}

// ── Clothing Section ─────────────────────────────────────────────────

function ClothingSection() {
  const { t } = useTranslation();
  const adjustments = useEditorStore((s) => s.adjustments);
  const setEditor = useEditorStore((s) => s.setEditor);
  const { detectBody, bodyDetections } = useRetouching();
  const [wrinkleStrength, setWrinkleStrength] = useState(50);
  const [blemishToggle, setBlemishToggle] = useState(true);
  const [isProcessing, setIsProcessing] = useState(false);
  const [isDetecting, setIsDetecting] = useState(false);
  const [hasDetectedBody, setHasDetectedBody] = useState(false);

  const handleDetect = useCallback(async () => {
    setIsDetecting(true);
    try {
      const poses = await detectBody();
      setHasDetectedBody(true);
      if (poses.length === 0) {
        toast.info(t('editor.creative.clothing.noBodyDetected'));
      } else {
        toast.success(t('editor.creative.clothing.detectedCount', { count: poses.length }));
      }
    } catch (err) {
      console.error('detect body failed:', err);
      toast.error(`${t('editor.creative.clothing.detect')} failed: ${err}`);
    } finally {
      setIsDetecting(false);
    }
  }, [detectBody, t]);

  const handleApply = useCallback(async () => {
    setIsProcessing(true);
    try {
      const jsAdjustments = getTransformAdjustments(adjustments);
      // Bug fix M2: Pass detected body keypoints instead of empty array.
      // Empty array makes the backend fall back to "lower 60% is clothing"
      // heuristic, which incorrectly processes backgrounds and faces.
      // Flatten all detected poses' keypoints into a single array (backend
      // expects an array of {x, y, confidence, name} objects).
      const allKeypoints = (bodyDetections ?? []).flatMap((pose) => pose.keypoints ?? []);
      const result = await invoke<string>('retouch_clothing', {
        js_adjustments: jsAdjustments,
        body_keypoints: allKeypoints,
        // Bug fix M4: Clamp wrinkleStrength to [0, 1] and guard against NaN.
        remove_wrinkles: Number.isFinite(wrinkleStrength) ? Math.max(0, Math.min(1, wrinkleStrength / 100)) : 0,
        remove_stains: blemishToggle,
      });
      if (result) {
        setEditor({ retouchingResultUrl: result });
        toast.success(t('editor.creative.clothing.apply'));
      }
    } catch (err) {
      console.error('retouch_clothing failed:', err);
      toast.error(`${t('editor.creative.clothing.apply')} failed: ${err}`);
    } finally {
      setIsProcessing(false);
    }
  }, [wrinkleStrength, blemishToggle, adjustments, setEditor, bodyDetections, t]);

  return (
    <div className="space-y-2 pt-2">
      <Button className="w-full bg-surface" onClick={handleDetect} disabled={isDetecting || isProcessing}>
        {isDetecting ? <Loader2 size={16} className="animate-spin" /> : <Search size={16} />}
        <span className="ml-2">{t('editor.creative.clothing.detect')}</span>
      </Button>
      {hasDetectedBody && (bodyDetections?.length ?? 0) > 0 && (
        <Text variant={TextVariants.small} color={TextColors.primary}>
          {t('editor.creative.clothing.detectedCount', { count: bodyDetections.length })}
        </Text>
      )}
      <Slider
        label={t('editor.creative.clothing.wrinkleStrength')}
        min={0}
        max={100}
        step={1}
        value={wrinkleStrength}
        onChange={(e) => setWrinkleStrength(Number(e.target.value))}
        fillOrigin="min"
      />
      <Switch
        checked={blemishToggle}
        label={t('editor.creative.clothing.blemishToggle')}
        onChange={setBlemishToggle}
      />
      <Button className="w-full" onClick={handleApply} disabled={isProcessing}>
        {isProcessing ? <Loader2 size={16} className="animate-spin" /> : <Shirt size={16} />}
        <span className="ml-2">{t('editor.creative.clothing.apply')}</span>
      </Button>
    </div>
  );
}

// ── Lens Blur Section ────────────────────────────────────────────────

function LensBlurSection() {
  const { t } = useTranslation();
  const adjustments = useEditorStore((s) => s.adjustments);
  const setEditor = useEditorStore((s) => s.setEditor);
  const [blurType, setBlurType] = useState<'gaussian' | 'bokeh' | 'tilt_shift'>('gaussian');
  const [blurAmount, setBlurAmount] = useState(30);
  const [isProcessing, setIsProcessing] = useState(false);

  const blurTypeOptions = [
    { label: t('editor.creative.lensBlur.gaussian'), value: 'gaussian' as const },
    { label: t('editor.creative.lensBlur.bokeh'), value: 'bokeh' as const },
    { label: t('editor.creative.lensBlur.tiltShift'), value: 'tilt_shift' as const },
  ];

  const handleApply = useCallback(async () => {
    setIsProcessing(true);
    try {
      const jsAdjustments = getTransformAdjustments(adjustments);
      // Bug fix H2: Guard against NaN/Infinity. Backend doesn't validate
      // focal_point or blur_amount, NaN would produce garbage pixels.
      const safeBlurAmount = Number.isFinite(blurAmount) ? Math.max(0, Math.min(100, blurAmount)) : 0;
      const result = await invoke<string>('apply_lens_blur', {
        js_adjustments: jsAdjustments,
        blur_type: blurType,
        // Focal point is always at image center (0.5, 0.5), normalized.
        focal_point: [0.5, 0.5] as [number, number],
        // Bug fix #4: Normalize 0-100 to 0.0-1.0 for the backend
        // (backend uses blur_amount * 20.0 as max pixel radius)
        blur_amount: safeBlurAmount / 100,
        depth_mask_base64: null,
      });
      if (result) {
        setEditor({ retouchingResultUrl: result });
        toast.success(t('editor.creative.lensBlur.apply'));
      }
    } catch (err) {
      console.error('apply_lens_blur failed:', err);
      toast.error(`${t('editor.creative.lensBlur.apply')} failed: ${err}`);
    } finally {
      setIsProcessing(false);
    }
  }, [blurType, blurAmount, adjustments, setEditor, t]);

  return (
    <div className="space-y-3 pt-2">
      <Dropdown
        options={blurTypeOptions}
        value={blurType}
        onChange={setBlurType}
      />
      <Slider
        label={t('editor.creative.lensBlur.blurAmount')}
        min={0}
        max={100}
        step={1}
        value={blurAmount}
        onChange={(e) => setBlurAmount(Number(e.target.value))}
        fillOrigin="min"
      />
      <Button className="w-full" onClick={handleApply} disabled={isProcessing}>
        {isProcessing ? <Loader2 size={16} className="animate-spin" /> : <CircleDot size={16} />}
        <span className="ml-2">{t('editor.creative.lensBlur.apply')}</span>
      </Button>
    </div>
  );
}

// ── Old Photo Restore Section ────────────────────────────────────────

function OldPhotoRestoreSection() {
  const { t } = useTranslation();
  const adjustments = useEditorStore((s) => s.adjustments);
  const setEditor = useEditorStore((s) => s.setEditor);
  const [denoiseStrength, setDenoiseStrength] = useState(50);
  const [scratchRemoval, setScratchRemoval] = useState(true);
  const [colorize, setColorize] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);

  const handleRestore = useCallback(async () => {
    setIsProcessing(true);
    try {
      const jsAdjustments = getTransformAdjustments(adjustments);
      // Bug fix M5: Guard against NaN. Backend restore_old_photo doesn't
      // validate denoise_strength; NaN would corrupt the gaussian blur sigma
      // and produce a black image (NaN clamp returns NaN per Rust docs).
      const safeDenoise = Number.isFinite(denoiseStrength) ? Math.max(0, Math.min(100, denoiseStrength)) : 0;
      // Bug fix #4: Normalize 0-100 to 0.0-1.0 for the backend
      // (backend uses denoise_strength * 3.0 as gaussian sigma)
      const result = await invoke<string>('restore_old_photo', { js_adjustments: jsAdjustments, denoise_strength: safeDenoise / 100, scratch_removal: scratchRemoval, colorize });
      if (result) {
        setEditor({ retouchingResultUrl: result });
        toast.success(t('editor.creative.oldPhoto.restore'));
      }
    } catch (err) {
      console.error('restore_old_photo failed:', err);
      toast.error(`${t('editor.creative.oldPhoto.restore')} failed: ${err}`);
    } finally {
      setIsProcessing(false);
    }
  }, [denoiseStrength, scratchRemoval, colorize, adjustments, setEditor, t]);

  return (
    <div className="space-y-3 pt-2">
      <Slider
        label={t('editor.creative.oldPhoto.denoiseStrength')}
        min={0}
        max={100}
        step={1}
        value={denoiseStrength}
        onChange={(e) => setDenoiseStrength(Number(e.target.value))}
        fillOrigin="min"
      />
      <Switch
        checked={scratchRemoval}
        label={t('editor.creative.oldPhoto.scratchRemoval')}
        onChange={setScratchRemoval}
      />
      <Switch
        checked={colorize}
        label={t('editor.creative.oldPhoto.colorize')}
        onChange={setColorize}
      />
      <Button className="w-full" onClick={handleRestore} disabled={isProcessing}>
        {isProcessing ? <Loader2 size={16} className="animate-spin" /> : <Recycle size={16} />}
        <span className="ml-2">{t('editor.creative.oldPhoto.restore')}</span>
      </Button>
    </div>
  );
}

// ── Seasonal Effects Section ─────────────────────────────────────────

function SeasonalEffectsSection() {
  const { t } = useTranslation();
  const adjustments = useEditorStore((s) => s.adjustments);
  const setEditor = useEditorStore((s) => s.setEditor);
  const [effectType, setEffectType] = useState<'sakura' | 'summer' | 'autumn' | 'winter'>('sakura');
  const [intensity, setIntensity] = useState(50);
  const [isProcessing, setIsProcessing] = useState(false);

  const handleApply = useCallback(async () => {
    setIsProcessing(true);
    try {
      const jsAdjustments = getTransformAdjustments(adjustments);
      // Bug fix #2: Backend expects 'summer_sun'/'autumn_leaves'/'winter_snow',
      // not 'summer'/'autumn'/'winter'
      const effectTypeMap: Record<typeof effectType, string> = {
        sakura: 'sakura',
        summer: 'summer_sun',
        autumn: 'autumn_leaves',
        winter: 'winter_snow',
      };
      // Bug fix M7: Guard against NaN. Backend applies clamp(0.0, 1.0) but
      // f32::clamp(NaN, ...) returns NaN per Rust docs, which then propagates
      // through every pixel computation and turns the image black.
      const safeIntensity = Number.isFinite(intensity) ? Math.max(0, Math.min(100, intensity)) : 0;
      // Normalize intensity from 0-100 to 0.0-1.0 for the backend
      const normalizedIntensity = safeIntensity / 100;
      const result = await invoke<string>('apply_seasonal_effect', { js_adjustments: jsAdjustments, effect_type: effectTypeMap[effectType], intensity: normalizedIntensity });
      if (result) {
        setEditor({ retouchingResultUrl: result });
        toast.success(t('editor.creative.seasonal.apply'));
      }
    } catch (err) {
      console.error('apply_seasonal_effect failed:', err);
      toast.error(`${t('editor.creative.seasonal.apply')} failed: ${err}`);
    } finally {
      setIsProcessing(false);
    }
  }, [effectType, intensity, adjustments, setEditor, t]);

  const effectOptions = [
    { id: 'sakura' as const, labelKey: 'editor.creative.seasonal.sakura', color: '#FFB7C5' },
    { id: 'summer' as const, labelKey: 'editor.creative.seasonal.summer', color: '#00BFFF' },
    { id: 'autumn' as const, labelKey: 'editor.creative.seasonal.autumn', color: '#FF8C00' },
    { id: 'winter' as const, labelKey: 'editor.creative.seasonal.winter', color: '#B0E0E6' },
  ];

  return (
    <div className="space-y-3 pt-2">
      <div className="grid grid-cols-2 gap-2">
        {effectOptions.map(({ id, labelKey, color }) => (
          <button
            key={id}
            className={clsx(
              'p-2.5 rounded-md text-sm font-medium transition-colors flex items-center justify-center gap-2 border-2',
              effectType === id
                ? 'border-accent bg-surface text-text-primary'
                : 'border-transparent bg-surface text-text-secondary hover:bg-card-active hover:text-text-primary',
            )}
            onClick={() => setEffectType(id)}
          >
            <div className="w-3 h-3 rounded-full" style={{ backgroundColor: color }} />
            <span>{t(labelKey as any)}</span>
          </button>
        ))}
      </div>
      <Slider
        label={t('editor.creative.seasonal.intensity')}
        min={0}
        max={100}
        step={1}
        value={intensity}
        onChange={(e) => setIntensity(Number(e.target.value))}
        fillOrigin="min"
      />
      <Button className="w-full" onClick={handleApply} disabled={isProcessing}>
        {isProcessing ? <Loader2 size={16} className="animate-spin" /> : <Flower2 size={16} />}
        <span className="ml-2">{t('editor.creative.seasonal.apply')}</span>
      </Button>
    </div>
  );
}

// ── People Removal Section ───────────────────────────────────────────

function PeopleRemovalSection() {
  const { t } = useTranslation();
  const adjustments = useEditorStore((s) => s.adjustments);
  const setEditor = useEditorStore((s) => s.setEditor);
  const { detectBody } = useRetouching();
  const [isProcessing, setIsProcessing] = useState(false);
  const [isDetecting, setIsDetecting] = useState(false);
  const [detectedRegions, setDetectedRegions] = useState<[number, number, number, number][]>([]);

  const handleDetect = useCallback(async () => {
    setIsDetecting(true);
    try {
      const poses = await detectBody();
      // Bug fix #5: Compute bounding boxes from detected body keypoints,
      // with 10% padding so the inpaint region fully covers each person.
      // Bug fix M6: Filter out keypoints with NaN/Infinity x/y (model
      // sometimes emits NaN for undetected joints); NaN would propagate
      // through Math.min/Math.max and produce NaN regions that the backend
      // silently skips (rx.max(0.0) returns NaN when rx is NaN).
      const regions: [number, number, number, number][] = poses
        .map((pose) => {
          const kps = pose.keypoints;
          if (!kps || kps.length === 0) return null;
          // Filter low-confidence keypoints so noise doesn't expand the bbox
          const confident = kps.filter(
            (k) => k.confidence > 0.3 && Number.isFinite(k.x) && Number.isFinite(k.y),
          );
          if (confident.length === 0) return null;
          const xs = confident.map((k) => k.x);
          const ys = confident.map((k) => k.y);
          const minX = Math.min(...xs);
          const minY = Math.min(...ys);
          const maxX = Math.max(...xs);
          const maxY = Math.max(...ys);
          const w = maxX - minX;
          const h = maxY - minY;
          // Skip degenerate (zero-area) boxes
          if (w <= 0 || h <= 0) return null;
          const padX = w * 0.1;
          const padY = h * 0.1;
          return [minX - padX, minY - padY, w + padX * 2, h + padY * 2] as [number, number, number, number];
        })
        .filter((r): r is [number, number, number, number] => r !== null);
      setDetectedRegions(regions);
      if (regions.length === 0) {
        toast.info(t('editor.creative.peopleRemoval.noPeopleDetected'));
      } else {
        toast.success(t('editor.creative.peopleRemoval.detectedCount', { count: regions.length }));
      }
    } catch (err) {
      console.error('detect people failed:', err);
      toast.error(`${t('editor.creative.peopleRemoval.detect')} failed: ${err}`);
    } finally {
      setIsDetecting(false);
    }
  }, [detectBody, t]);

  const handleApply = useCallback(async () => {
    // Bug fix #5: Avoid invoking ai_remove_people with an empty array,
    // which would silently no-op and mislead the user with a "success" toast.
    if (detectedRegions.length === 0) {
      toast.warn(t('editor.creative.peopleRemoval.detectFirst'));
      return;
    }
    setIsProcessing(true);
    try {
      const jsAdjustments = getTransformAdjustments(adjustments);
      const result = await invoke<string>('ai_remove_people', {
        js_adjustments: jsAdjustments,
        person_regions: detectedRegions,
      });
      if (result) {
        setEditor({ retouchingResultUrl: result });
        toast.success(t('editor.creative.peopleRemoval.apply'));
      }
    } catch (err) {
      console.error('ai_remove_people failed:', err);
      toast.error(`${t('editor.creative.peopleRemoval.apply')} failed: ${err}`);
    } finally {
      setIsProcessing(false);
    }
  }, [detectedRegions, adjustments, setEditor, t]);

  return (
    <div className="space-y-3 pt-2">
      <Text variant={TextVariants.small} color={TextColors.secondary}>
        {t('editor.creative.peopleRemoval.description')}
      </Text>
      <Button className="w-full bg-surface" onClick={handleDetect} disabled={isDetecting || isProcessing}>
        {isDetecting ? <Loader2 size={16} className="animate-spin" /> : <Search size={16} />}
        <span className="ml-2">{t('editor.creative.peopleRemoval.detect')}</span>
      </Button>
      {detectedRegions.length > 0 && (
        <Text variant={TextVariants.small} color={TextColors.primary}>
          {t('editor.creative.peopleRemoval.detectedCount', { count: detectedRegions.length })}
        </Text>
      )}
      <Button className="w-full" onClick={handleApply} disabled={isProcessing || isDetecting || detectedRegions.length === 0}>
        {isProcessing ? <Loader2 size={16} className="animate-spin" /> : <UserMinus size={16} />}
        <span className="ml-2">{t('editor.creative.peopleRemoval.apply')}</span>
      </Button>
    </div>
  );
}

// ── Collapsible section state ────────────────────────────────────────

const SECTION_KEYS = [
  'colorMatch',
  'fillLight',
  'superResolution',
  'idPhoto',
  'clothing',
  'lensBlur',
  'oldPhoto',
  'seasonal',
  'peopleRemoval',
] as const;

type SectionKey = typeof SECTION_KEYS[number];

const SECTION_CONFIG: { key: SectionKey; icon: typeof Palette; titleKey: string }[] = [
  { key: 'colorMatch', icon: Palette, titleKey: 'editor.creative.colorMatch.title' },
  { key: 'fillLight', icon: Sun, titleKey: 'editor.creative.fillLight.title' },
  { key: 'superResolution', icon: ZoomIn, titleKey: 'editor.creative.superResolution.title' },
  { key: 'idPhoto', icon: CreditCard, titleKey: 'editor.creative.idPhoto.title' },
  { key: 'clothing', icon: Shirt, titleKey: 'editor.creative.clothing.title' },
  { key: 'lensBlur', icon: CircleDot, titleKey: 'editor.creative.lensBlur.title' },
  { key: 'oldPhoto', icon: Recycle, titleKey: 'editor.creative.oldPhoto.title' },
  { key: 'seasonal', icon: Flower2, titleKey: 'editor.creative.seasonal.title' },
  { key: 'peopleRemoval', icon: UserMinus, titleKey: 'editor.creative.peopleRemoval.title' },
];

const SECTION_COMPONENTS: Record<SectionKey, React.FC> = {
  colorMatch: ColorMatchSection,
  fillLight: FillLightSection,
  superResolution: SuperResolutionSection,
  idPhoto: IdPhotoSection,
  clothing: ClothingSection,
  lensBlur: LensBlurSection,
  oldPhoto: OldPhotoRestoreSection,
  seasonal: SeasonalEffectsSection,
  peopleRemoval: PeopleRemovalSection,
};

// ── Main Panel ───────────────────────────────────────────────────────

export default function CreativePanel() {
  const { t } = useTranslation();
  const setEditor = useEditorStore((s) => s.setEditor);
  const [collapsibleState, setCollapsibleState] = useState<Record<SectionKey, boolean>>(
    () => Object.fromEntries(SECTION_KEYS.map((k) => [k, false])) as Record<SectionKey, boolean>,
  );

  // Bug fix #3: Clear retouchingResultUrl when leaving the creative panel
  useEffect(() => {
    return () => {
      setEditor({ retouchingResultUrl: null });
    };
  }, [setEditor]);

  const handleToggleSection = useCallback((section: SectionKey) => {
    setCollapsibleState((prev) => ({ ...prev, [section]: !prev[section] }));
  }, []);

  return (
    <div className="flex flex-col h-full select-none overflow-hidden">
      <div className="p-4 flex justify-between items-center shrink-0 border-b border-surface">
        <Text variant={TextVariants.title}>{t('editor.creative.title')}</Text>
        <button
          className="p-2 rounded-full hover:bg-surface transition-colors"
          data-tooltip={t('editor.creative.resetTooltip')}
          onClick={() => setEditor({ retouchingResultUrl: null })}
        >
          <RotateCcw size={18} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 flex flex-col gap-2">
        {SECTION_CONFIG.map(({ key, titleKey }) => {
          const SectionComponent = SECTION_COMPONENTS[key];
          return (
            <div className="shrink-0" key={key}>
              <CollapsibleSection
                title={t(titleKey as any)}
                isOpen={collapsibleState[key]}
                onToggle={() => handleToggleSection(key)}
                canToggleVisibility={false}
                isContentVisible={true}
              >
                <SectionComponent />
              </CollapsibleSection>
            </div>
          );
        })}
      </div>
    </div>
  );
}
