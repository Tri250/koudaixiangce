import React, { useState, useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { invoke } from '@tauri-apps/api/core';
import { Circle, Loader2, RotateCcw, Check } from 'lucide-react';
import clsx from 'clsx';
import CollapsibleSection from '../../ui/CollapsibleSection';
import Slider from '../../ui/Slider';
import Button from '../../ui/Button';
import Dropdown from '../../ui/Dropdown';
import Text from '../../ui/Text';
import { TextColors, TextVariants, TextWeights } from '../../../types/typography';
import { useEditorStore } from '../../../store/useEditorStore';
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

interface MonoPresetOption {
    id: string;
    nameKey: string;
    color: string;
}

const MONO_PRESETS: MonoPresetOption[] = [
    { id: 'neutral', nameKey: 'editor.monochrome.presets.neutral', color: '#808080' },
    { id: 'red', nameKey: 'editor.monochrome.presets.red', color: '#C04040' },
    { id: 'orange', nameKey: 'editor.monochrome.presets.orange', color: '#C08040' },
    { id: 'yellow', nameKey: 'editor.monochrome.presets.yellow', color: '#C0C040' },
    { id: 'green', nameKey: 'editor.monochrome.presets.green', color: '#40C040' },
    { id: 'blue', nameKey: 'editor.monochrome.presets.blue', color: '#4040C0' },
    { id: 'infrared', nameKey: 'editor.monochrome.presets.infrared', color: '#E0E0FF' },
];

const TONING_TYPE_OPTIONS = [
    { label: 'None (无)', value: 'none' as const },
    { label: 'Sepia (棕褐)', value: 'sepia' as const },
    { label: 'Selenium (硒)', value: 'selenium' as const },
    { label: 'Copper (铜)', value: 'copper' as const },
    { label: 'Cyanotype (蓝晒)', value: 'cyanotype' as const },
    { label: 'Gold (金)', value: 'gold' as const },
    { label: 'Split (分离)', value: 'split' as const },
];

export default function MonochromePanel() {
    const { t } = useTranslation();
    const adjustments = useEditorStore((s) => s.adjustments);
    const setEditor = useEditorStore((s) => s.setEditor);

    // State
    const [selectedPreset, setSelectedPreset] = useState<string>('neutral');
    const [redWeight, setRedWeight] = useState(33);
    const [greenWeight, setGreenWeight] = useState(33);
    const [blueWeight, setBlueWeight] = useState(34);
    const [contrast, setContrast] = useState(100);
    const [toningType, setToningType] = useState<string>('none');
    const [toningStrength, setToningStrength] = useState(0);
    const [isProcessing, setIsProcessing] = useState(false);

    // Split toning state
    const [shadowColor, setShadowColor] = useState('#8B4513');
    const [highlightColor, setHighlightColor] = useState('#D4AF37');
    const [splitBalance, setSplitBalance] = useState(50);

    const totalWeight = useMemo(() => redWeight + greenWeight + blueWeight, [redWeight, greenWeight, blueWeight]);

    const handleApply = useCallback(async () => {
        setIsProcessing(true);
        try {
            const jsAdjustments = getTransformAdjustments(adjustments);
            const result = await invoke<string>('convert_to_monochrome', {
                jsAdjustments,
                redWeight: redWeight / 100,
                greenWeight: greenWeight / 100,
                blueWeight: blueWeight / 100,
                contrast,
                preset: selectedPreset,
                toningType,
                toningStrength,
                shadowColor,
                highlightColor,
                splitBalance,
            });
            if (result) {
                setEditor({ retouchingResultUrl: result });
            }
        } catch (err) {
            console.error('convert_to_monochrome failed:', err);
        } finally {
            setIsProcessing(false);
        }
    }, [redWeight, greenWeight, blueWeight, contrast, selectedPreset, toningType, toningStrength, shadowColor, highlightColor, splitBalance, adjustments, setEditor]);

    const handlePresetSelect = useCallback((presetId: string) => {
        setSelectedPreset(presetId);
        // Set default weights for presets
        switch (presetId) {
            case 'neutral':
                setRedWeight(33); setGreenWeight(33); setBlueWeight(34);
                break;
            case 'red':
                setRedWeight(100); setGreenWeight(0); setBlueWeight(0);
                break;
            case 'orange':
                setRedWeight(70); setGreenWeight(30); setBlueWeight(0);
                break;
            case 'yellow':
                setRedWeight(50); setGreenWeight(50); setBlueWeight(0);
                break;
            case 'green':
                setRedWeight(0); setGreenWeight(100); setBlueWeight(0);
                break;
            case 'blue':
                setRedWeight(0); setGreenWeight(0); setBlueWeight(100);
                break;
            case 'infrared':
                setRedWeight(100); setGreenWeight(0); setBlueWeight(-50);
                break;
        }
    }, []);

    return (
        <div className="flex flex-col h-full select-none overflow-hidden">
            {/* Header */}
            <div className="p-4 flex justify-between items-center shrink-0 border-b border-surface">
                <div className="flex items-center gap-2">
                    <Circle size={18} />
                    <Text variant={TextVariants.title}>{t('editor.monochrome.title')}</Text>
                </div>
                <button
                    className="p-2 rounded-full hover:bg-surface transition-colors"
                    data-tooltip={t('editor.monochrome.reset')}
                    onClick={() => {
                        handlePresetSelect('neutral');
                        setContrast(100);
                        setToningType('none');
                        setToningStrength(0);
                        setShadowColor('#8B4513');
                        setHighlightColor('#D4AF37');
                        setSplitBalance(50);
                        setEditor({ retouchingResultUrl: null });
                    }}
                >
                    <RotateCcw size={18} />
                </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 space-y-4">
                {/* Preset Selector Grid */}
                <CollapsibleSection
                    title={t('editor.monochrome.preset')}
                    isOpen={true}
                    isContentVisible={true}
                    onToggle={() => {}}
                    canToggleVisibility={false}
                >
                    <div className="grid grid-cols-4 gap-2">
                        {MONO_PRESETS.map((preset) => (
                            <button
                                key={preset.id}
                                className={clsx(
                                    'relative flex flex-col items-center gap-1 p-2 rounded-lg transition-colors',
                                    selectedPreset === preset.id
                                        ? 'bg-accent/20 ring-1 ring-accent'
                                        : 'bg-card-active hover:bg-bg-primary'
                                )}
                                onClick={() => handlePresetSelect(preset.id)}
                            >
                                <div
                                    className="w-8 h-8 rounded-full border border-border-color"
                                    style={{ backgroundColor: preset.color }}
                                />
                                <Text variant={TextVariants.small} color={TextColors.secondary} className="truncate w-full text-center">
                                    {t(preset.nameKey)}
                                </Text>
                                {selectedPreset === preset.id && (
                                    <Check size={14} className="absolute top-1 right-1 text-accent" />
                                )}
                            </button>
                        ))}
                    </div>
                </CollapsibleSection>

                {/* Channel Mixer */}
                <CollapsibleSection
                    title={t('editor.monochrome.channelMixer')}
                    isOpen={true}
                    isContentVisible={true}
                    onToggle={() => {}}
                    canToggleVisibility={false}
                >
                    <Slider
                        label={t('editor.monochrome.redWeight')}
                        min={-200}
                        max={200}
                        step={1}
                        value={redWeight}
                        onChange={(e) => setRedWeight(Number(e.target.value))}
                        suffix="%"
                    />
                    <Slider
                        label={t('editor.monochrome.greenWeight')}
                        min={-200}
                        max={200}
                        step={1}
                        value={greenWeight}
                        onChange={(e) => setGreenWeight(Number(e.target.value))}
                        suffix="%"
                    />
                    <Slider
                        label={t('editor.monochrome.blueWeight')}
                        min={-200}
                        max={200}
                        step={1}
                        value={blueWeight}
                        onChange={(e) => setBlueWeight(Number(e.target.value))}
                        suffix="%"
                    />

                    {/* Total Indicator */}
                    <div className={clsx(
                        'flex items-center justify-between mt-2 px-3 py-2 rounded-md',
                        totalWeight === 100 ? 'bg-green-500/10' : 'bg-yellow-500/10'
                    )}>
                        <Text variant={TextVariants.small} color={TextColors.secondary}>
                            {t('editor.monochrome.totalWeight')}
                        </Text>
                        <Text
                            variant={TextVariants.small}
                            weight={TextWeights.semibold}
                            color={totalWeight === 100 ? TextColors.primary : 'text-yellow-500'}
                        >
                            {totalWeight}%
                        </Text>
                    </div>
                </CollapsibleSection>

                {/* Contrast */}
                <CollapsibleSection
                    title={t('editor.monochrome.contrast')}
                    isOpen={true}
                    isContentVisible={true}
                    onToggle={() => {}}
                    canToggleVisibility={false}
                >
                    <Slider
                        label={t('editor.monochrome.contrast')}
                        min={0}
                        max={200}
                        step={1}
                        value={contrast}
                        onChange={(e) => setContrast(Number(e.target.value))}
                    />
                </CollapsibleSection>

                {/* Toning */}
                <CollapsibleSection
                    title={t('editor.monochrome.toning')}
                    isOpen={true}
                    isContentVisible={true}
                    onToggle={() => {}}
                    canToggleVisibility={false}
                >
                    <Dropdown
                        options={TONING_TYPE_OPTIONS}
                        value={toningType}
                        onChange={(v) => setToningType(v)}
                        placeholder={t('editor.monochrome.selectToning')}
                        className="mb-3"
                    />

                    {toningType !== 'none' && (
                        <Slider
                            label={t('editor.monochrome.toningStrength')}
                            min={0}
                            max={100}
                            step={1}
                            value={toningStrength}
                            onChange={(e) => setToningStrength(Number(e.target.value))}
                        />
                    )}

                    {/* Split Toning Controls */}
                    {toningType === 'split' && (
                        <div className="space-y-3 mt-3">
                            <div className="flex items-center gap-3">
                                <Text variant={TextVariants.small} color={TextColors.secondary}>
                                    {t('editor.monochrome.shadowColor')}
                                </Text>
                                <input
                                    type="color"
                                    value={shadowColor}
                                    onChange={(e) => setShadowColor(e.target.value)}
                                    className="w-8 h-8 rounded cursor-pointer"
                                />
                            </div>
                            <div className="flex items-center gap-3">
                                <Text variant={TextVariants.small} color={TextColors.secondary}>
                                    {t('editor.monochrome.highlightColor')}
                                </Text>
                                <input
                                    type="color"
                                    value={highlightColor}
                                    onChange={(e) => setHighlightColor(e.target.value)}
                                    className="w-8 h-8 rounded cursor-pointer"
                                />
                            </div>
                            <Slider
                                label={t('editor.monochrome.splitBalance')}
                                min={0}
                                max={100}
                                step={1}
                                value={splitBalance}
                                onChange={(e) => setSplitBalance(Number(e.target.value))}
                            />
                        </div>
                    )}
                </CollapsibleSection>

                {/* Apply Button */}
                <Button
                    className="w-full bg-accent text-white"
                    onClick={handleApply}
                    disabled={isProcessing}
                >
                    {isProcessing ? (
                        <Loader2 size={16} className="animate-spin mr-2" />
                    ) : null}
                    {t('editor.monochrome.apply')}
                </Button>
            </div>
        </div>
    );
}
