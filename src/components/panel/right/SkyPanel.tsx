import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { invoke } from '@tauri-apps/api/core';
import { convertFileSrc } from '@tauri-apps/api/core';
import { open as openDialog } from '@tauri-apps/plugin-dialog';
import { Cloud, Sun, Loader2, RotateCcw, ImagePlus, AlertCircle, CheckCircle } from 'lucide-react';
import clsx from 'clsx';
import Slider from '../../ui/Slider';
import Button from '../../ui/Button';
import Text from '../../ui/Text';
import { TextColors, TextVariants } from '../../../types/typography';
import { useEditorStore } from '../../../store/useEditorStore';
import { Invokes } from '../../ui/AppProperties';
import { Adjustments } from '../../../utils/adjustments';

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

/// Generate a synthetic vertical-gradient sky image (1024×1024 PNG) from the
/// given preset colours. Used when the user picks a sky preset instead of
/// supplying their own sky photo, so the "Apply" action always has a sky to
/// composite.
function generateGradientSkyBase64(from: string, to: string): string {
  const size = 1024;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  if (!ctx) return '';
  const gradient = ctx.createLinearGradient(0, 0, 0, size);
  gradient.addColorStop(0, from);
  gradient.addColorStop(1, to);
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, size, size);
  return canvas.toDataURL('image/png');
}

interface SkyPreset {
  id: string;
  nameKey: string;
  color: string;
  gradientFrom: string;
  gradientTo: string;
}

const SKY_PRESETS: SkyPreset[] = [
  { id: 'blue_sky', nameKey: 'editor.sky.presets.blueSky', color: '#4A90D9', gradientFrom: '#87CEEB', gradientTo: '#4A90D9' },
  { id: 'sunset', nameKey: 'editor.sky.presets.sunset', color: '#FF6B35', gradientFrom: '#FF8C42', gradientTo: '#FF6B35' },
  { id: 'dramatic_clouds', nameKey: 'editor.sky.presets.dramaticClouds', color: '#5C6B73', gradientFrom: '#7A8B8C', gradientTo: '#3D4F4F' },
  { id: 'night', nameKey: 'editor.sky.presets.night', color: '#1A1A2E', gradientFrom: '#16213E', gradientTo: '#0F3460' },
  { id: 'golden_hour', nameKey: 'editor.sky.presets.goldenHour', color: '#F0A500', gradientFrom: '#FFD700', gradientTo: '#F0A500' },
  { id: 'overcast', nameKey: 'editor.sky.presets.overcast', color: '#B0B0B0', gradientFrom: '#D0D0D0', gradientTo: '#A0A0A0' },
  { id: 'stormy', nameKey: 'editor.sky.presets.stormy', color: '#4A4A4A', gradientFrom: '#5C5C5C', gradientTo: '#3A3A3A' },
  { id: 'aurora', nameKey: 'editor.sky.presets.aurora', color: '#00BFA5', gradientFrom: '#00E676', gradientTo: '#1A237E' },
];

export default function SkyPanel() {
  const { t } = useTranslation();
  const selectedImage = useEditorStore((s) => s.selectedImage);
  const adjustments = useEditorStore((s) => s.adjustments);
  const setEditor = useEditorStore((s) => s.setEditor);
  const [isDetectingSky, setIsDetectingSky] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [selectedPreset, setSelectedPreset] = useState<string | null>(null);
  const [skyImagePath, setSkyImagePath] = useState<string | null>(null);
  const [feather, setFeather] = useState(10);
  const [colorMatchStrength, setColorMatchStrength] = useState(50);
  const [horizonAdjust, setHorizonAdjust] = useState(0);
  const [skyMaskGenerated, setSkyMaskGenerated] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [statusType, setStatusType] = useState<'success' | 'error'>('success');
  const statusTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showStatus = useCallback((message: string, type: 'success' | 'error') => {
    if (statusTimeoutRef.current) clearTimeout(statusTimeoutRef.current);
    setStatusMessage(message);
    setStatusType(type);
    statusTimeoutRef.current = setTimeout(() => setStatusMessage(null), 4000);
  }, []);

  // Clear any pending status timeout when the panel unmounts to avoid
  // scheduling a state update on an unmounted component.
  useEffect(() => {
    return () => {
      if (statusTimeoutRef.current) clearTimeout(statusTimeoutRef.current);
    };
  }, []);

  const handleDetectSky = useCallback(async () => {
    if (!selectedImage?.path) return;
    setIsDetectingSky(true);
    setStatusMessage(null);
    try {
      const transformAdjustments = getTransformAdjustments(adjustments);
      await invoke(Invokes.GenerateAiSkyMask, {
        js_adjustments: transformAdjustments,
        flip_horizontal: adjustments.flipHorizontal,
        flip_vertical: adjustments.flipVertical,
        orientation_steps: adjustments.orientationSteps,
        rotation: adjustments.rotation,
      });
      setSkyMaskGenerated(true);
      showStatus(t('editor.sky.detectSuccess'), 'success');
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      console.error('generate_ai_sky_mask failed:', err);
      showStatus(t('editor.sky.detectFailed') + (msg ? `: ${msg}` : ''), 'error');
    } finally {
      setIsDetectingSky(false);
    }
  }, [selectedImage, adjustments, showStatus, t]);

  const handleSelectSkyImage = useCallback(async () => {
    try {
      const selected = await openDialog({
        multiple: false,
        filters: [
          {
            name: t('editor.sky.imageFilterLabel'),
            extensions: ['png', 'jpg', 'jpeg', 'webp', 'bmp', 'tiff'],
          },
        ],
      });
      if (typeof selected === 'string') {
        setSkyImagePath(selected);
        setSelectedPreset(null);
      }
    } catch (err) {
      console.error('Sky image selection failed:', err);
    }
  }, [t]);

  const handleApply = useCallback(async () => {
    if (!skyMaskGenerated) {
      showStatus(t('editor.sky.detectFirst'), 'error');
      return;
    }
    if (!selectedImage?.path) return;
    if (!skyImagePath && !selectedPreset) {
      showStatus(t('editor.sky.selectSkyFirst'), 'error');
      return;
    }
    setIsProcessing(true);
    setStatusMessage(null);
    try {
      const transformAdjustments = getTransformAdjustments(adjustments);
      let skyImageBase64: string;
      if (skyImagePath) {
        skyImageBase64 = await readFileAsBase64DataUrl(skyImagePath);
      } else {
        // Generate a synthetic gradient sky from the selected preset.
        const preset = SKY_PRESETS.find((p) => p.id === selectedPreset);
        if (!preset) throw new Error('No sky image selected');
        skyImageBase64 = generateGradientSkyBase64(preset.gradientFrom, preset.gradientTo);
        if (!skyImageBase64) throw new Error('Failed to generate sky gradient');
      }
      const result = await invoke<{ image: string }>('replace_sky', {
        js_adjustments: transformAdjustments,
        sky_image_base64: skyImageBase64,
        feather,
        color_match_strength: colorMatchStrength,
        horizon_adjust: horizonAdjust,
      });
      if (result?.image) {
        setEditor({ retouchingResultUrl: result.image });
        showStatus(t('editor.sky.applySuccess'), 'success');
      } else {
        showStatus(t('editor.sky.applyFailed'), 'error');
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      console.error('replace_sky failed:', err);
      showStatus(t('editor.sky.applyFailed') + (msg ? `: ${msg}` : ''), 'error');
    } finally {
      setIsProcessing(false);
    }
  }, [skyMaskGenerated, skyImagePath, selectedPreset, feather, colorMatchStrength, horizonAdjust, selectedImage, adjustments, setEditor, showStatus, t]);

  return (
    <div className="flex flex-col h-full select-none overflow-hidden">
      <div className="p-4 flex justify-between items-center shrink-0 border-b border-surface">
        <Text variant={TextVariants.title}>{t('editor.sky.title')}</Text>
        <button
          className="p-2 rounded-full hover:bg-surface transition-colors"
          onClick={() => {
            setSkyMaskGenerated(false);
            setSkyImagePath(null);
            setSelectedPreset(null);
            setFeather(10);
            setColorMatchStrength(50);
            setHorizonAdjust(0);
            setStatusMessage(null);
            setEditor({ retouchingResultUrl: null });
          }}
          data-tooltip={t('editor.sky.resetTooltip')}
        >
          <RotateCcw size={18} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 space-y-4">
        {/* Detect Sky */}
        <Button className="w-full" onClick={handleDetectSky} disabled={isDetectingSky}>
          {isDetectingSky ? <Loader2 size={16} className="animate-spin" /> : <Cloud size={16} />}
          <span className="ml-2">{isDetectingSky ? t('editor.sky.detecting') : t('editor.sky.detectSky')}</span>
        </Button>

        {skyMaskGenerated && (
          <Text variant={TextVariants.small} color={TextColors.success} className="text-center">
            {t('editor.sky.maskDetected')}
          </Text>
        )}

        {/* Select Sky Image */}
        <Button className="w-full bg-surface" onClick={handleSelectSkyImage}>
          <ImagePlus size={16} />
          <span className="ml-2">{t('editor.sky.selectSkyImage')}</span>
        </Button>

        {skyImagePath && (
          <Text variant={TextVariants.small} color={TextColors.primary} className="text-center truncate">
            {skyImagePath}
          </Text>
        )}

        {/* Sky Presets Grid */}
        <div>
          <Text variant={TextVariants.label} className="mb-2 block">
            {t('editor.sky.presetsTitle')}
          </Text>
          <div className="grid grid-cols-3 sm:grid-cols-4 gap-2">
            {SKY_PRESETS.map((preset) => (
              <button
                key={preset.id}
                className={clsx(
                  'aspect-square rounded-lg overflow-hidden border-2 transition-all',
                  selectedPreset === preset.id
                    ? 'border-accent ring-1 ring-accent'
                    : 'border-transparent hover:border-border-color',
                )}
                style={{
                  background: `linear-gradient(135deg, ${preset.gradientFrom}, ${preset.gradientTo})`,
                }}
                onClick={() => {
                  setSelectedPreset(preset.id);
                  setSkyImagePath(null);
                }}
                data-tooltip={t(preset.nameKey as any)}
              >
                <div className="w-full h-full flex items-end justify-center pb-1">
                  <span className="text-xs text-white/80 font-medium drop-shadow-md">
                    {t(preset.nameKey as any)}
                  </span>
                </div>
              </button>
            ))}
          </div>
        </div>

        {/* Parameters */}
        <Slider
          label={t('editor.sky.feather')}
          min={0}
          max={100}
          step={1}
          value={feather}
          onChange={(e) => setFeather(Number(e.target.value))}
          fillOrigin="min"
        />
        <Slider
          label={t('editor.sky.colorMatchStrength')}
          min={0}
          max={100}
          step={1}
          value={colorMatchStrength}
          onChange={(e) => setColorMatchStrength(Number(e.target.value))}
          fillOrigin="min"
        />
        <Slider
          label={t('editor.sky.horizonAdjust')}
          min={-50}
          max={50}
          step={1}
          value={horizonAdjust}
          onChange={(e) => setHorizonAdjust(Number(e.target.value))}
        />

        {/* Apply */}
        <Button
          className="w-full"
          onClick={handleApply}
          disabled={isProcessing || !skyMaskGenerated || (!skyImagePath && !selectedPreset)}
        >
          {isProcessing ? <Loader2 size={16} className="animate-spin" /> : <Sun size={16} />}
          <span className="ml-2">{isProcessing ? t('editor.sky.processing') : t('editor.sky.apply')}</span>
        </Button>
      </div>

      {/* Status message */}
      {statusMessage && (
        <div
          className={clsx(
            'shrink-0 flex items-center gap-2 px-4 py-2 text-sm border-t',
            statusType === 'error'
              ? 'bg-red-500/10 text-red-400 border-red-500/20'
              : 'bg-green-500/10 text-green-400 border-green-500/20',
          )}
        >
          {statusType === 'error' ? <AlertCircle size={14} className="shrink-0" /> : <CheckCircle size={14} className="shrink-0" />}
          <span className="truncate">{statusMessage}</span>
        </div>
      )}
    </div>
  );
}
