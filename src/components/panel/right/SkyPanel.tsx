import React, { useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { invoke } from '@tauri-apps/api/core';
import { open } from '@tauri-apps/plugin-dialog';
import { Cloud, Sun, Loader2, RotateCcw, ImagePlus } from 'lucide-react';
import clsx from 'clsx';
import Slider from '../../ui/Slider';
import Button from '../../ui/Button';
import Text from '../../ui/Text';
import { TextColors, TextVariants } from '../../../types/typography';
import { useEditorStore } from '../../../store/useEditorStore';

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

function getImageDataBase64(): string {
  return useEditorStore.getState().selectedImage?.original_base64 || '';
}

function updateSelectedImageBase64(result: string) {
  const state = useEditorStore.getState();
  if (state.selectedImage) {
    state.setEditor({
      selectedImage: { ...state.selectedImage, original_base64: result },
    });
  }
}

function generatePresetBase64(preset: SkyPreset, width = 512, height = 512): string {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d')!;
  const gradient = ctx.createLinearGradient(0, 0, 0, height);
  gradient.addColorStop(0, preset.gradientFrom);
  gradient.addColorStop(1, preset.gradientTo);
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, width, height);
  return canvas.toDataURL('image/png');
}

export default function SkyPanel() {
  const { t } = useTranslation();
  const selectedImage = useEditorStore((s) => s.selectedImage);
  const [isDetectingSky, setIsDetectingSky] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [selectedPreset, setSelectedPreset] = useState<string | null>(null);
  const [skyImagePath, setSkyImagePath] = useState<string | null>(null);
  const [feather, setFeather] = useState(10);
  const [colorMatchStrength, setColorMatchStrength] = useState(50);
  const [horizonAdjust, setHorizonAdjust] = useState(0);
  const [maskBase64, setMaskBase64] = useState<string | null>(null);

  const handleDetectSky = useCallback(async () => {
    const imageDataBase64 = getImageDataBase64();
    if (!imageDataBase64) return;
    setIsDetectingSky(true);
    try {
      const result = await invoke<string>('generate_ai_sky_mask_base64', { imageDataBase64 });
      setMaskBase64(result);
    } catch (err) {
      console.error('generate_ai_sky_mask_base64 failed:', err);
    } finally {
      setIsDetectingSky(false);
    }
  }, []);

  const handleSelectSkyImage = useCallback(async () => {
    try {
      const selected = await open({
        multiple: false,
        filters: [
          { name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] },
        ],
      });
      if (typeof selected === 'string') {
        setSkyImagePath(selected);
        setSelectedPreset(null);
      }
    } catch (err) {
      console.error('Failed to open sky image dialog:', err);
    }
  }, []);

  const handleApply = useCallback(async () => {
    const imageDataBase64 = getImageDataBase64();
    if (!imageDataBase64 || !maskBase64) return;
    if (!skyImagePath && !selectedPreset) return;

    setIsProcessing(true);
    try {
      let sky_image_base64: string | undefined;
      let sky_image_path: string | undefined;

      if (selectedPreset) {
        const preset = SKY_PRESETS.find((p) => p.id === selectedPreset);
        if (preset) {
          sky_image_base64 = generatePresetBase64(preset);
        }
      } else if (skyImagePath) {
        sky_image_path = skyImagePath;
      }

      const result = await invoke<string>('replace_sky_base64', {
        imageDataBase64,
        skyImageBase64: sky_image_base64 || null,
        skyImagePath: sky_image_path || null,
        maskBase64: maskBase64,
        blendMode: 'normal',
        feather: feather,
        colorMatchStrength: colorMatchStrength / 100.0,
        horizonAdjust: horizonAdjust / 100.0,
      });

      updateSelectedImageBase64(result);
    } catch (err) {
      console.error('replace_sky_base64 failed:', err);
    } finally {
      setIsProcessing(false);
    }
  }, [maskBase64, skyImagePath, selectedPreset, feather, colorMatchStrength, horizonAdjust]);

  return (
    <div className="flex flex-col h-full select-none overflow-hidden">
      <div className="p-4 flex justify-between items-center shrink-0 border-b border-surface">
        <Text variant={TextVariants.title}>{t('editor.sky.title')}</Text>
        <button
          className="p-2 rounded-full hover:bg-surface transition-colors"
          onClick={() => {
            setMaskBase64(null);
            setSkyImagePath(null);
            setSelectedPreset(null);
            setFeather(10);
            setColorMatchStrength(50);
            setHorizonAdjust(0);
          }}
          data-tooltip={t('editor.sky.resetTooltip')}
        >
          <RotateCcw size={18} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 space-y-4">
        {/* Detect Sky */}
        <Button className="w-full" onClick={handleDetectSky} disabled={isDetectingSky || !selectedImage}>
          {isDetectingSky ? <Loader2 size={16} className="animate-spin" /> : <Cloud size={16} />}
          <span className="ml-2">{t('editor.sky.detectSky')}</span>
        </Button>

        {maskBase64 && (
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
            {skyImagePath.split(/[\\/]/).pop()}
          </Text>
        )}

        {/* Sky Presets Grid */}
        <div>
          <Text variant={TextVariants.label} className="mb-2 block">
            {t('editor.sky.presetsTitle')}
          </Text>
          <div className="grid grid-cols-4 gap-2">
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
                data-tooltip={t(preset.nameKey)}
              >
                <div className="w-full h-full flex items-end justify-center pb-1">
                  <span className="text-xs text-white/80 font-medium drop-shadow-md">
                    {t(preset.nameKey)}
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
          disabled={isProcessing || !maskBase64 || (!skyImagePath && !selectedPreset)}
        >
          {isProcessing ? <Loader2 size={16} className="animate-spin" /> : <Sun size={16} />}
          <span className="ml-2">{t('editor.sky.apply')}</span>
        </Button>
      </div>
    </div>
  );
}
