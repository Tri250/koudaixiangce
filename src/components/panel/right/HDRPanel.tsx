import React, { useState, useCallback, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { invoke } from '@tauri-apps/api/core';
import { Sun, Download, Image, Loader2, AlertTriangle, RotateCcw } from 'lucide-react';
import clsx from 'clsx';
import CollapsibleSection from '../../ui/CollapsibleSection';
import Slider from '../../ui/Slider';
import Button from '../../ui/Button';
import Dropdown from '../../ui/Dropdown';
import Switch from '../../ui/Switch';
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

const PEAK_BRIGHTNESS_OPTIONS = [
    { label: '1000 nits', value: 1000 },
    { label: '2000 nits', value: 2000 },
    { label: '4000 nits', value: 4000 },
    { label: '10000 nits', value: 10000 },
];

const HIGHLIGHT_MODE_OPTIONS = [
    { label: 'Recover (恢复)', value: 'recover' as const },
    { label: 'Clip (裁剪)', value: 'clip' as const },
    { label: 'Roll-off (滚降)', value: 'rolloff' as const },
    { label: 'Smart Blend (智能混合)', value: 'smart_blend' as const },
];

const COLOR_SPACE_OPTIONS = [
    { label: 'sRGB', value: 'srgb' as const },
    { label: 'Display P3', value: 'p3' as const },
    { label: 'Rec. 2020', value: 'rec2020' as const },
];

export default function HDRPanel() {
    const { t } = useTranslation();
    const adjustments = useEditorStore((s) => s.adjustments);
    const setEditor = useEditorStore((s) => s.setEditor);

    // State
    const [hdrPreviewEnabled, setHdrPreviewEnabled] = useState(false);
    const [highlightMode, setHighlightMode] = useState<string>('recover');
    const [recoveryAmount, setRecoveryAmount] = useState(50);
    const [peakBrightness, setPeakBrightness] = useState(1000);
    const [outputColorSpace, setOutputColorSpace] = useState('srgb');
    const [isProcessing, setIsProcessing] = useState(false);
    const [outOfGamutWarning, setOutOfGamutWarning] = useState<number | null>(null);
    const [isExportingJpeg, setIsExportingJpeg] = useState(false);
    const [isExportingTiff, setIsExportingTiff] = useState(false);
    const [gamutWarningOverlay, setGamutWarningOverlay] = useState(false);
    const [tiffBitDepth, setTiffBitDepth] = useState<16 | 32>(16);

    const handleApplyHighlightRecovery = useCallback(async () => {
        if (!hdrPreviewEnabled) {
            setEditor({ retouchingResultUrl: null });
            return;
        }
        setIsProcessing(true);
        try {
            const jsAdjustments = getTransformAdjustments(adjustments);
            const result = await invoke<string>('apply_hdr_highlight_recovery', {
                jsAdjustments,
                mode: highlightMode,
                recoveryAmount,
                peakBrightnessNits: peakBrightness,
            });
            if (result) {
                setEditor({ retouchingResultUrl: result });
            }
        } catch (err) {
            console.error('apply_hdr_highlight_recovery failed:', err);
        } finally {
            setIsProcessing(false);
        }
    }, [hdrPreviewEnabled, highlightMode, recoveryAmount, peakBrightness, adjustments, setEditor]);

    useEffect(() => {
        if (hdrPreviewEnabled) {
            handleApplyHighlightRecovery();
        }
    }, [hdrPreviewEnabled, highlightMode, recoveryAmount, peakBrightness, handleApplyHighlightRecovery]);

    const handleCheckOutOfGamut = useCallback(async () => {
        if (!hdrPreviewEnabled) {
            setOutOfGamutWarning(null);
            return;
        }
        try {
            const jsAdjustments = getTransformAdjustments(adjustments);
            const count = await invoke<number>('check_out_of_gamut', {
                jsAdjustments,
                targetColorSpace: outputColorSpace,
            });
            setOutOfGamutWarning(count);
        } catch (err) {
            console.error('check_out_of_gamut failed:', err);
            setOutOfGamutWarning(null);
        }
    }, [hdrPreviewEnabled, outputColorSpace, adjustments]);

    useEffect(() => {
        handleCheckOutOfGamut();
    }, [hdrPreviewEnabled, outputColorSpace, handleCheckOutOfGamut]);

    const handleExportUltraHDR = useCallback(async () => {
        setIsExportingJpeg(true);
        try {
            const jsAdjustments = getTransformAdjustments(adjustments);
            await invoke('export_ultra_hdr_jpeg', {
                jsAdjustments,
                sdrImageBase64: '',
                peakBrightnessNits: peakBrightness,
                quality: 90,
            });
        } catch (err) {
            console.error('export_ultra_hdr_jpeg failed:', err);
        } finally {
            setIsExportingJpeg(false);
        }
    }, [peakBrightness, adjustments]);

    const handleExportHDRTIFF = useCallback(async () => {
        setIsExportingTiff(true);
        try {
            const jsAdjustments = getTransformAdjustments(adjustments);
            await invoke('export_hdr_tiff', {
                jsAdjustments,
                peakBrightnessNits: peakBrightness,
                bitDepth: tiffBitDepth,
            });
        } catch (err) {
            console.error('export_hdr_tiff failed:', err);
        } finally {
            setIsExportingTiff(false);
        }
    }, [peakBrightness, tiffBitDepth, adjustments]);

    return (
        <div className="flex flex-col h-full select-none overflow-hidden">
            {/* Header */}
            <div className="p-4 flex justify-between items-center shrink-0 border-b border-surface">
                <div className="flex items-center gap-2">
                    <Sun size={18} />
                    <Text variant={TextVariants.title}>{t('editor.hdr.title')}</Text>
                </div>
                <button
                    className="p-2 rounded-full hover:bg-surface transition-colors"
                    data-tooltip={t('editor.hdr.reset')}
                    onClick={() => {
                        setHdrPreviewEnabled(false);
                        setHighlightMode('recover');
                        setRecoveryAmount(50);
                        setPeakBrightness(1000);
                        setOutputColorSpace('srgb');
                        setOutOfGamutWarning(null);
                        setTiffBitDepth(16);
                        setEditor({ retouchingResultUrl: null });
                    }}
                >
                    <RotateCcw size={18} />
                </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 space-y-4">
                {/* HDR Preview Toggle */}
                <Switch
                    checked={hdrPreviewEnabled}
                    onChange={setHdrPreviewEnabled}
                    label={t('editor.hdr.previewToggle')}
                />

                {/* Highlight Recovery Section */}
                <CollapsibleSection
                    title={t('editor.hdr.highlightRecovery')}
                    isOpen={true}
                    isContentVisible={true}
                    onToggle={() => {}}
                    canToggleVisibility={false}
                >
                    <Dropdown
                        options={HIGHLIGHT_MODE_OPTIONS}
                        value={highlightMode}
                        onChange={(v) => setHighlightMode(v)}
                        placeholder={t('editor.hdr.selectMode')}
                        className="mb-3"
                    />

                    <Slider
                        label={t('editor.hdr.recoveryAmount')}
                        min={0}
                        max={100}
                        step={1}
                        value={recoveryAmount}
                        onChange={(e) => setRecoveryAmount(Number(e.target.value))}
                    />

                    {isProcessing && (
                        <div className="flex items-center gap-2 mt-2 text-text-secondary">
                            <Loader2 size={16} className="animate-spin" />
                            <Text variant={TextVariants.small}>{t('editor.hdr.processing')}</Text>
                        </div>
                    )}
                </CollapsibleSection>

                {/* Peak Brightness Section */}
                <CollapsibleSection
                    title={t('editor.hdr.peakBrightness')}
                    isOpen={true}
                    isContentVisible={true}
                    onToggle={() => {}}
                    canToggleVisibility={false}
                >
                    <div className="grid grid-cols-2 gap-2">
                        {PEAK_BRIGHTNESS_OPTIONS.map((option) => (
                            <button
                                key={option.value}
                                className={clsx(
                                    'px-3 py-2 rounded-md text-sm font-medium transition-colors',
                                    peakBrightness === option.value
                                        ? 'bg-accent text-white'
                                        : 'bg-card-active text-text-primary hover:bg-bg-primary'
                                )}
                                onClick={() => setPeakBrightness(option.value)}
                            >
                                {option.label}
                            </button>
                        ))}
                    </div>
                </CollapsibleSection>

                {/* Output Color Space */}
                <CollapsibleSection
                    title={t('editor.hdr.outputColorSpace')}
                    isOpen={true}
                    isContentVisible={true}
                    onToggle={() => {}}
                    canToggleVisibility={false}
                >
                    <Dropdown
                        options={COLOR_SPACE_OPTIONS}
                        value={outputColorSpace}
                        onChange={(v) => setOutputColorSpace(v)}
                        placeholder={t('editor.hdr.selectOutputColorSpace')}
                    />
                </CollapsibleSection>

                {/* Out-of-Gamut Warning */}
                {outOfGamutWarning !== null && outOfGamutWarning > 0 && (
                    <div className="flex items-start gap-2 p-3 bg-yellow-500/10 border border-yellow-500/30 rounded-lg">
                        <AlertTriangle size={16} className="text-yellow-500 shrink-0 mt-0.5" />
                        <div>
                            <Text variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.semibold}>
                                {t('editor.hdr.outOfGamutWarning')}
                            </Text>
                            <Text variant={TextVariants.small} color={TextColors.secondary}>
                                {t('editor.hdr.outOfGamutCount', { count: outOfGamutWarning })}
                            </Text>
                        </div>
                    </div>
                )}

                {/* Export Buttons */}
                <CollapsibleSection
                    title={t('editor.hdr.export')}
                    isOpen={true}
                    isContentVisible={true}
                    onToggle={() => {}}
                    canToggleVisibility={false}
                >
                    <Button
                        className="w-full bg-surface mb-2"
                        onClick={handleExportUltraHDR}
                        disabled={isExportingJpeg}
                    >
                        {isExportingJpeg ? (
                            <Loader2 size={16} className="animate-spin mr-2" />
                        ) : (
                            <Download size={16} className="mr-2" />
                        )}
                        {t('editor.hdr.exportUltraHDR')}
                    </Button>

                    <Button
                        className="w-full bg-surface mb-2"
                        onClick={handleExportHDRTIFF}
                        disabled={isExportingTiff}
                    >
                        {isExportingTiff ? (
                            <Loader2 size={16} className="animate-spin mr-2" />
                        ) : (
                            <Image size={16} className="mr-2" />
                        )}
                        {t('editor.hdr.exportHDRTIFF')}
                    </Button>

                    {/* TIFF Bit Depth Selector */}
                    <div className="flex gap-2 mt-2">
                        <button
                            className={clsx(
                                'flex-1 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                                tiffBitDepth === 16 ? 'bg-accent text-white' : 'bg-card-active text-text-primary'
                            )}
                            onClick={() => setTiffBitDepth(16)}
                        >
                            16-bit
                        </button>
                        <button
                            className={clsx(
                                'flex-1 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                                tiffBitDepth === 32 ? 'bg-accent text-white' : 'bg-card-active text-text-primary'
                            )}
                            onClick={() => setTiffBitDepth(32)}
                        >
                            32-bit Float
                        </button>
                    </div>
                </CollapsibleSection>

                {/* Gamut Warning Overlay Toggle */}
                <Switch
                    checked={gamutWarningOverlay}
                    onChange={setGamutWarningOverlay}
                    label={t('editor.hdr.gamutWarningOverlay')}
                />
            </div>
        </div>
    );
}
