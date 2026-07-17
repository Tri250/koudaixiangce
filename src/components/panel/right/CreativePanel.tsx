import React, { useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { invoke } from '@tauri-apps/api/core';
import { open as openDialog } from '@tauri-apps/plugin-dialog';
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

// ── Color Match Section ──────────────────────────────────────────────

function ColorMatchSection() {
  const { t } = useTranslation();
  const selectedImage = useEditorStore((s) => s.selectedImage);
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
      const imagePath = selectedImage?.path ?? '';
      await invoke('ai_match_colors', { imagePath, referencePath, method: matchMethod, strength });
    } catch (err) {
      console.error('apply_color_match failed:', err);
    } finally {
      setIsProcessing(false);
    }
  }, [referencePath, matchMethod, strength, selectedImage]);

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
  const selectedImage = useEditorStore((s) => s.selectedImage);
  const [direction, setDirection] = useState(0);
  const [intensity, setIntensity] = useState(50);
  const [softness, setSoftness] = useState(30);
  const [colorTemp, setColorTemp] = useState(0);

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
    </div>
  );
}

// ── Super Resolution Section ─────────────────────────────────────────

function SuperResolutionSection() {
  const { t } = useTranslation();
  const selectedImage = useEditorStore((s) => s.selectedImage);
  const [scale, setScale] = useState<'2x' | '4x'>('2x');
  const [isProcessing, setIsProcessing] = useState(false);

  const handleApply = useCallback(async () => {
    setIsProcessing(true);
    try {
      const imagePath = selectedImage?.path ?? '';
      await invoke('apply_super_resolution', { imagePath, scale: scale === '2x' ? 2 : 4 });
    } catch (err) {
      console.error('apply_super_resolution failed:', err);
    } finally {
      setIsProcessing(false);
    }
  }, [scale, selectedImage]);

  return (
    <div className="space-y-3 pt-2">
      <div className="grid grid-cols-2 gap-2">
        {(['2x', '4x'] as const).map((s) => (
          <button
            key={s}
            className={clsx(
              'p-2 rounded-md text-sm font-medium transition-colors flex items-center justify-center gap-2',
              scale === s
                ? 'text-primary bg-surface ring-1 ring-accent'
                : 'bg-surface text-text-secondary hover:bg-card-active',
            )}
            onClick={() => setScale(s)}
          >
            {s}
          </button>
        ))}
      </div>
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
  const selectedImage = useEditorStore((s) => s.selectedImage);
  const [sizePreset, setSizePreset] = useState<'one_inch' | 'two_inch' | 'passport'>('one_inch');
  const [bgColor, setBgColor] = useState('#FFFFFF');
  const [isProcessing, setIsProcessing] = useState(false);

  const handleProcess = useCallback(async () => {
    setIsProcessing(true);
    try {
      const imagePath = selectedImage?.path ?? '';
      await invoke('process_id_photo', { imagePath, sizePreset, bgColor });
    } catch (err) {
      console.error('process_id_photo failed:', err);
    } finally {
      setIsProcessing(false);
    }
  }, [sizePreset, bgColor, selectedImage]);

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
  const [wrinkleStrength, setWrinkleStrength] = useState(50);
  const [blemishToggle, setBlemishToggle] = useState(true);

  return (
    <div className="space-y-2 pt-2">
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
    </div>
  );
}

// ── Lens Blur Section ────────────────────────────────────────────────

function LensBlurSection() {
  const { t } = useTranslation();
  const [blurType, setBlurType] = useState<'gaussian' | 'bokeh' | 'tilt_shift'>('gaussian');
  const [blurAmount, setBlurAmount] = useState(30);

  const blurTypeOptions = [
    { label: t('editor.creative.lensBlur.gaussian'), value: 'gaussian' as const },
    { label: t('editor.creative.lensBlur.bokeh'), value: 'bokeh' as const },
    { label: t('editor.creative.lensBlur.tiltShift'), value: 'tilt_shift' as const },
  ];

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
    </div>
  );
}

// ── Old Photo Restore Section ────────────────────────────────────────

function OldPhotoRestoreSection() {
  const { t } = useTranslation();
  const selectedImage = useEditorStore((s) => s.selectedImage);
  const [denoiseStrength, setDenoiseStrength] = useState(50);
  const [scratchRemoval, setScratchRemoval] = useState(true);
  const [colorize, setColorize] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);

  const handleRestore = useCallback(async () => {
    setIsProcessing(true);
    try {
      const imagePath = selectedImage?.path ?? '';
      await invoke('restore_old_photo', { imagePath, denoiseStrength, scratchRemoval, colorize });
    } catch (err) {
      console.error('restore_old_photo failed:', err);
    } finally {
      setIsProcessing(false);
    }
  }, [denoiseStrength, scratchRemoval, colorize, selectedImage]);

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
  const selectedImage = useEditorStore((s) => s.selectedImage);
  const [effectType, setEffectType] = useState<'sakura' | 'summer' | 'autumn' | 'winter'>('sakura');
  const [intensity, setIntensity] = useState(50);
  const [isProcessing, setIsProcessing] = useState(false);

  const handleApply = useCallback(async () => {
    setIsProcessing(true);
    try {
      const imagePath = selectedImage?.path ?? '';
      await invoke('apply_seasonal_effect', { imagePath, effectType, intensity });
    } catch (err) {
      console.error('apply_seasonal_effect failed:', err);
    } finally {
      setIsProcessing(false);
    }
  }, [effectType, intensity, selectedImage]);

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
            <span>{t(labelKey)}</span>
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
};

// ── Main Panel ───────────────────────────────────────────────────────

export default function CreativePanel() {
  const { t } = useTranslation();
  const [collapsibleState, setCollapsibleState] = useState<Record<SectionKey, boolean>>(
    () => Object.fromEntries(SECTION_KEYS.map((k) => [k, false])) as Record<SectionKey, boolean>,
  );

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
                title={t(titleKey)}
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
