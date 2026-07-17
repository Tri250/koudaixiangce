import React, { useState, useCallback } from 'react';
import { useTranslation } from '../../../lib/i18n-helpers';
import { invoke } from '@tauri-apps/api/core';
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
  const [skyMaskGenerated, setSkyMaskGenerated] = useState(false);

  const handleDetectSky = useCallback(async () => {
    const imagePath = selectedImage?.path ?? '';
    if (!imagePath) return;
    setIsDetectingSky(true);
    try {
      await invoke('detect_sky_mask', { imagePath });
      setSkyMaskGenerated(true);
    } catch (err) {
      console.error('detect_sky_mask failed:', err);
    } finally {
      setIsDetectingSky(false);
    }
  }, [selectedImage]);

  const handleSelectSkyImage = useCallback(async () => {
    try {
      const selected = await invoke<string>('select_sky_image');
      if (selected) {
        setSkyImagePath(selected);
        setSelectedPreset(null);
      }
    } catch (err) {
      console.error('select_sky_image failed:', err);
    }
  }, []);

  const handleApply = useCallback(async () => {
    if (!skyMaskGenerated) return;
    const imagePath = selectedImage?.path ?? '';
    if (!imagePath) return;
    setIsProcessing(true);
    try {
      await invoke('replace_sky', {
        imagePath,
        skyImagePath: skyImagePath || selectedPreset || '',
        feather,
        colorMatchStrength,
        horizonAdjust,
      });
    } catch (err) {
      console.error('replace_sky failed:', err);
    } finally {
      setIsProcessing(false);
    }
  }, [skyMaskGenerated, skyImagePath, selectedPreset, feather, colorMatchStrength, horizonAdjust, selectedImage]);

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
          <span className="ml-2">{t('editor.sky.detectSky')}</span>
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
          disabled={isProcessing || !skyMaskGenerated || (!skyImagePath && !selectedPreset)}
        >
          {isProcessing ? <Loader2 size={16} className="animate-spin" /> : <Sun size={16} />}
          <span className="ml-2">{t('editor.sky.apply')}</span>
        </Button>
      </div>
    </div>
  );
}
