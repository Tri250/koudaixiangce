import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { invoke } from '@tauri-apps/api/core';
import { open as openDialog } from '@tauri-apps/plugin-dialog';
import { toast } from 'react-toastify';
import debounce from 'lodash.debounce';
import { Palette, Upload, Loader2, RotateCcw, AlertTriangle, Camera } from 'lucide-react';
import clsx from 'clsx';
import CollapsibleSection from '../../ui/CollapsibleSection';
import Button from '../../ui/Button';
import Dropdown from '../../ui/Dropdown';
import Switch from '../../ui/Switch';
import Text from '../../ui/Text';
import { TextColors, TextVariants, TextWeights } from '../../../types/typography';
import { useEditorStore } from '../../../store/useEditorStore';
import { Adjustments } from '../../../utils/adjustments';
import { Invokes } from '../../ui/AppProperties';

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

const COLOR_SPACE_OPTIONS = [
    { label: 'sRGB', value: 'srgb' as const },
    { label: 'Display P3', value: 'p3' as const },
    { label: 'Rec. 2020', value: 'rec2020' as const },
    { label: 'ProPhoto RGB', value: 'prophoto' as const },
    { label: 'Adobe RGB', value: 'adobergb' as const },
];

interface CameraProfileInfo {
    name: string;
    make: string;
    model: string;
}

export default function ColorSpacePanel() {
    const { t } = useTranslation();
    const adjustments = useEditorStore((s) => s.adjustments);
    const selectedImage = useEditorStore((s) => s.selectedImage);
    const setEditor = useEditorStore((s) => s.setEditor);

    // State
    const [workingColorSpace, setWorkingColorSpace] = useState<string>('srgb');
    const [outputColorSpace, setOutputColorSpace] = useState<string>('srgb');
    const [softProofEnabled, setSoftProofEnabled] = useState(false);
    const [gamutWarningOverlay, setGamutWarningOverlay] = useState(false);
    const [outOfGamutCount, setOutOfGamutCount] = useState<number>(0);

    // Camera profile state
    const [detectedCamera, setDetectedCamera] = useState<{ make: string; model: string } | null>(null);
    const [cameraProfiles, setCameraProfiles] = useState<CameraProfileInfo[]>([]);
    const [selectedProfileName, setSelectedProfileName] = useState<string>('Adobe Standard');
    const [isImporting, setIsImporting] = useState(false);
    const [isLoadingProfiles, setIsLoadingProfiles] = useState(false);

    // Color profiles state (all available, not camera-specific)
    const [colorProfiles, setColorProfiles] = useState<string[]>([]);

    // Load camera profiles on mount
    useEffect(() => {
        const loadProfiles = async () => {
            setIsLoadingProfiles(true);
            try {
                const profiles = await invoke<CameraProfileInfo[]>('get_camera_profiles');
                setCameraProfiles(profiles);
            } catch (err) {
                console.error('get_camera_profiles failed:', err);
                toast.error(`${t('editor.colorSpace.cameraProfile')} failed: ${err}`);
            } finally {
                setIsLoadingProfiles(false);
            }
            try {
                const allProfiles = await invoke<string[]>(Invokes.GetColorProfiles);
                setColorProfiles(allProfiles);
            } catch (err) {
                console.error('get_color_profiles failed:', err);
            }
        };
        loadProfiles();
    }, [t]);

    // Auto-detect camera profile from EXIF
    const handleAutoDetect = useCallback(async () => {
        try {
            // Get EXIF data from selected image metadata
            const exifMake = (selectedImage as any)?.exifMake || '';
            const exifModel = (selectedImage as any)?.exifModel || '';
            const result = await invoke<{
                detectedMake: string;
                detectedModel: string;
                suggestedProfile: CameraProfileInfo;
            }>('get_camera_profile_for_image', {
                exifMake,
                exifModel,
            });
            setDetectedCamera({ make: result.detectedMake, model: result.detectedModel });
            if (result.suggestedProfile) {
                setSelectedProfileName(result.suggestedProfile.name);
            }
            toast.success(t('editor.colorSpace.autoDetectFromExif'));
        } catch (err) {
            console.error('get_camera_profile_for_image failed:', err);
            toast.error(`${t('editor.colorSpace.autoDetectFromExif')} failed: ${err}`);
        }
    }, [selectedImage, t]);

    // Set camera color profile
    const handleSetProfile = useCallback(async (profileName: string) => {
        try {
            setSelectedProfileName(profileName);
            await invoke('set_camera_color_profile', { profileName });
            toast.success(t('editor.colorSpace.cameraProfile'));
        } catch (err) {
            console.error('set_camera_color_profile failed:', err);
            toast.error(`${t('editor.colorSpace.cameraProfile')} failed: ${err}`);
        }
    }, [t]);

    // Import DCP profile
    const handleImportDCP = useCallback(async () => {
        setIsImporting(true);
        try {
            const selected = await openDialog({
                title: t('editor.colorSpace.importDCP'),
                filters: [{ name: 'DCP Profile', extensions: ['dcp', 'icc'] }],
                multiple: false,
            });
            if (!selected || typeof selected !== 'string') {
                setIsImporting(false);
                return;
            }
            await invoke('import_dcp_profile', {
                filePath: selected,
            });
            toast.success(t('editor.colorSpace.importDCP'));
            // Reload profiles after import
            const profiles = await invoke<CameraProfileInfo[]>('get_camera_profiles');
            setCameraProfiles(profiles);
        } catch (err) {
            console.error('import_dcp_profile failed:', err);
            toast.error(`${t('editor.colorSpace.importDCP')} failed: ${err}`);
        } finally {
            setIsImporting(false);
        }
    }, [t]);

    // Convert color space
    const handleConvertColorSpace = useCallback(async () => {
        try {
            const jsAdjustments = getTransformAdjustments(adjustments);
            const result = await invoke<string>('convert_color_space', {
                jsAdjustments,
                fromSpace: workingColorSpace,
                toSpace: outputColorSpace,
            });
            if (result) {
                setEditor({ retouchingResultUrl: result });
                toast.success(t('editor.colorSpace.convert'));
            }
        } catch (err) {
            console.error('convert_color_space failed:', err);
            toast.error(`${t('editor.colorSpace.convert')} failed: ${err}`);
        }
    }, [workingColorSpace, outputColorSpace, adjustments, setEditor, t]);

    // Soft proof
    const handleSoftProof = useCallback(async () => {
        try {
            const jsAdjustments = getTransformAdjustments(adjustments);
            const result = await invoke<{ proofImageBase64: string; outOfGamutPixels: number }>('soft_proof', {
                jsAdjustments,
                targetColorSpace: outputColorSpace,
            });
            setOutOfGamutCount(result.outOfGamutPixels);
            if (result.proofImageBase64) {
                setEditor({ retouchingResultUrl: result.proofImageBase64 });
            }
        } catch (err) {
            console.error('soft_proof failed:', err);
            toast.error(`${t('editor.colorSpace.softProof')} failed: ${err}`);
        }
    }, [outputColorSpace, adjustments, setEditor, t]);

    // Debounced soft proof to prevent rapid re-processing on parameter changes
    const debouncedSoftProofRef = useRef(
        debounce((proofFn: () => void) => {
            proofFn();
        }, 500)
    );

    useEffect(() => {
        if (softProofEnabled) {
            debouncedSoftProofRef.current(handleSoftProof);
        } else {
            debouncedSoftProofRef.current.cancel();
            setOutOfGamutCount(0);
            setEditor({ retouchingResultUrl: null });
        }
        return () => {
            debouncedSoftProofRef.current.cancel();
        };
    }, [softProofEnabled, handleSoftProof, setEditor]);

    const cameraProfileOptions = cameraProfiles.map((p) => ({
        label: p.name,
        value: p.name,
    }));

    return (
        <div className="flex flex-col h-full select-none overflow-hidden">
            {/* Header */}
            <div className="p-4 flex justify-between items-center shrink-0 border-b border-surface">
                <div className="flex items-center gap-2">
                    <Palette size={18} />
                    <Text variant={TextVariants.title}>{t('editor.colorSpace.title')}</Text>
                </div>
                <button
                    className="p-2 rounded-full hover:bg-surface transition-colors"
                    data-tooltip={t('editor.colorSpace.reset')}
                    onClick={() => {
                        setWorkingColorSpace('srgb');
                        setOutputColorSpace('srgb');
                        setSoftProofEnabled(false);
                        setGamutWarningOverlay(false);
                        setOutOfGamutCount(0);
                        setSelectedProfileName('Adobe Standard');
                        setEditor({ retouchingResultUrl: null });
                    }}
                >
                    <RotateCcw size={18} />
                </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 space-y-4">
                {/* Working Color Space */}
                <CollapsibleSection
                    title={t('editor.colorSpace.workingColorSpace')}
                    isOpen={true}
                    isContentVisible={true}
                    onToggle={() => {}}
                    canToggleVisibility={false}
                >
                    <Dropdown
                        options={COLOR_SPACE_OPTIONS}
                        value={workingColorSpace}
                        onChange={(v) => setWorkingColorSpace(v)}
                        placeholder={t('editor.colorSpace.selectWorkingColorSpace')}
                    />
                </CollapsibleSection>

                {/* Output Color Space */}
                <CollapsibleSection
                    title={t('editor.colorSpace.outputColorSpace')}
                    isOpen={true}
                    isContentVisible={true}
                    onToggle={() => {}}
                    canToggleVisibility={false}
                >
                    <Dropdown
                        options={COLOR_SPACE_OPTIONS}
                        value={outputColorSpace}
                        onChange={(v) => setOutputColorSpace(v)}
                        placeholder={t('editor.colorSpace.selectOutputColorSpace')}
                    />
                    <Button
                        className="w-full bg-surface mt-3"
                        onClick={handleConvertColorSpace}
                    >
                        <Palette size={16} className="mr-2" />
                        {t('editor.colorSpace.convert')}
                    </Button>
                </CollapsibleSection>

                {/* Camera Profile Section */}
                <CollapsibleSection
                    title={t('editor.colorSpace.cameraProfile')}
                    isOpen={true}
                    isContentVisible={true}
                    onToggle={() => {}}
                    canToggleVisibility={false}
                >
                    {/* Auto-detect from EXIF */}
                    <Button
                        className="w-full bg-surface mb-3"
                        onClick={handleAutoDetect}
                    >
                        <Camera size={16} className="mr-2" />
                        {t('editor.colorSpace.autoDetectFromExif')}
                    </Button>

                    {detectedCamera && (
                        <div className="mb-3 px-3 py-2 bg-card-active rounded-md">
                            <Text variant={TextVariants.small} color={TextColors.secondary}>
                                {t('editor.colorSpace.detectedCamera')}
                            </Text>
                            <Text variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.semibold}>
                                {detectedCamera.make} {detectedCamera.model}
                            </Text>
                        </div>
                    )}

                    {/* Manual profile selector */}
                    {!isLoadingProfiles && cameraProfileOptions.length > 0 && (
                        <Dropdown
                            options={cameraProfileOptions}
                            value={selectedProfileName}
                            onChange={(v) => handleSetProfile(v)}
                            placeholder={t('editor.colorSpace.selectCameraProfile')}
                            className="mb-3"
                        />
                    )}

                    {isLoadingProfiles && (
                        <div className="flex items-center gap-2 mb-3 text-text-secondary">
                            <Loader2 size={16} className="animate-spin" />
                            <Text variant={TextVariants.small}>{t('editor.colorSpace.loadingProfiles')}</Text>
                        </div>
                    )}

                    {/* Import DCP button */}
                    <Button
                        className="w-full bg-surface"
                        onClick={handleImportDCP}
                        disabled={isImporting}
                    >
                        {isImporting ? (
                            <Loader2 size={16} className="animate-spin mr-2" />
                        ) : (
                            <Upload size={16} className="mr-2" />
                        )}
                        {t('editor.colorSpace.importDCP')}
                    </Button>
                </CollapsibleSection>

                {/* Color Profiles Section */}
                {colorProfiles.length > 0 && (
                    <CollapsibleSection
                        title={t('editor.colorSpace.colorProfiles', 'Color Profiles')}
                        isOpen={true}
                        isContentVisible={true}
                        onToggle={() => {}}
                        canToggleVisibility={false}
                    >
                        <div className="space-y-1">
                            {colorProfiles.map((profile) => (
                                <div key={profile} className="px-3 py-1.5 bg-card-active rounded-md">
                                    <Text variant={TextVariants.small} color={TextColors.primary}>
                                        {profile}
                                    </Text>
                                </div>
                            ))}
                        </div>
                    </CollapsibleSection>
                )}

                {/* Soft Proof Toggle */}
                <Switch
                    checked={softProofEnabled}
                    onChange={setSoftProofEnabled}
                    label={t('editor.colorSpace.softProof')}
                />

                {/* Out-of-gamut warning when soft proof is enabled */}
                {softProofEnabled && outOfGamutCount > 0 && (
                    <div className="flex items-start gap-2 p-3 bg-yellow-500/10 border border-yellow-500/30 rounded-lg">
                        <AlertTriangle size={16} className="text-yellow-500 shrink-0 mt-0.5" />
                        <div>
                            <Text variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.semibold}>
                                {t('editor.colorSpace.outOfGamutWarning')}
                            </Text>
                            <Text variant={TextVariants.small} color={TextColors.secondary}>
                                {t('editor.colorSpace.outOfGamutCount', { count: outOfGamutCount })}
                            </Text>
                        </div>
                    </div>
                )}

                {/* Gamut Warning Overlay */}
                <Switch
                    checked={gamutWarningOverlay}
                    onChange={setGamutWarningOverlay}
                    label={t('editor.colorSpace.gamutWarningOverlay')}
                />
            </div>
        </div>
    );
}
