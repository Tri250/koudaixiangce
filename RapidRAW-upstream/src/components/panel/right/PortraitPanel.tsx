import { useState, useCallback } from 'react';
import { User, Sparkles, Brush, Palette, RefreshCw, Check } from 'lucide-react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { invoke } from '@tauri-apps/api/core';
import { toast } from 'react-toastify';
import Slider from '../../ui/Slider';
import Text from '../../ui/Text';
import { TextVariants, TextColors } from '../../../types/typography';
import { useEditorStore } from '../../../store/useEditorStore';
import { useEditorActions } from '../../../hooks/useEditorActions';
import { Invokes } from '../../ui/AppProperties';
import { Adjustments, MaskContainer } from '../../../utils/adjustments';
import { Mask, SubMask } from './Masks';

export default function PortraitPanel() {
  const { t } = useTranslation();
  const { setAdjustments } = useEditorActions();
  const { adjustments, selectedImage, isGeneratingAiMask, setEditor } = useEditorStore((state) => ({
    adjustments: state.adjustments,
    selectedImage: state.selectedImage,
    isGeneratingAiMask: state.isGeneratingAiMask,
    setEditor: state.setEditor,
  }));

  const [skinSmoothness, setSkinSmoothness] = useState(0);
  const [skinBrightness, setSkinBrightness] = useState(0);
  const [skinContrast, setSkinContrast] = useState(0);
  const [skinSaturation, setSkinSaturation] = useState(0);
  const [isGeneratingSkinMask, setIsGeneratingSkinMask] = useState(false);
  const [skinMaskId, setSkinMaskId] = useState<string | null>(null);

  const handleGenerateSkinMask = useCallback(async () => {
    if (!selectedImage?.path || isGeneratingSkinMask) return;
    setIsGeneratingSkinMask(true);
    setEditor({ isGeneratingAiMask: true });

    try {
      const existingSkinMask = adjustments.masks?.find((m: MaskContainer) => m.name === '皮肤蒙版');
      if (existingSkinMask) {
        setSkinMaskId(existingSkinMask.id);
        setIsGeneratingSkinMask(false);
        setEditor({ isGeneratingAiMask: false });
        toast.success(t('editor.portrait.skinMaskExisting'));
        return;
      }

      const newParameters = await invoke(Invokes.GenerateSkinMask, {
        path: selectedImage.path,
      });

      const newMaskId = `mask-${Date.now()}`;
      const newSubMaskId = `submask-${Date.now()}`;

      const newMask: MaskContainer = {
        id: newMaskId,
        name: '皮肤蒙版',
        invert: false,
        opacity: 100,
        visible: true,
        subMasks: [
          {
            id: newSubMaskId,
            type: Mask.Brush,
            mode: 'additive',
            visible: true,
            invert: false,
            opacity: 100,
            parameters: {
              ...newParameters,
            },
          },
        ],
        adjustments: {
          brightness: 0,
          contrast: 0,
          clarity: 0,
          saturation: 0,
          vibrance: 0,
          colorGrading: {
            balance: 0,
            blending: 50,
            global: { hue: 0, saturation: 0, luminance: 0 },
            highlights: { hue: 0, saturation: 0, luminance: 0 },
            midtones: { hue: 0, saturation: 0, luminance: 0 },
            shadows: { hue: 0, saturation: 0, luminance: 0 },
          },
          hsl: {
            aquas: { hue: 0, saturation: 0, luminance: 0 },
            blues: { hue: 0, saturation: 0, luminance: 0 },
            greens: { hue: 0, saturation: 0, luminance: 0 },
            magentas: { hue: 0, saturation: 0, luminance: 0 },
            oranges: { hue: 0, saturation: 0, luminance: 0 },
            purples: { hue: 0, saturation: 0, luminance: 0 },
            reds: { hue: 0, saturation: 0, luminance: 0 },
            yellows: { hue: 0, saturation: 0, luminance: 0 },
          },
          hue: 0,
          temperature: 0,
          tint: 0,
          blacks: 0,
          whites: 0,
          highlights: 0,
          shadows: 0,
          exposure: 0,
          lumaNoiseReduction: 0,
          colorNoiseReduction: 0,
          sharpness: 0,
          sharpnessThreshold: 15,
          structure: 0,
          dehaze: 0,
          centré: 0,
          curves: {
            blue: [{ x: 0, y: 0 }, { x: 255, y: 255 }],
            green: [{ x: 0, y: 0 }, { x: 255, y: 255 }],
            luma: [{ x: 0, y: 0 }, { x: 255, y: 255 }],
            red: [{ x: 0, y: 0 }, { x: 255, y: 255 }],
          },
          pointCurves: {
            blue: [{ x: 0, y: 0 }, { x: 255, y: 255 }],
            green: [{ x: 0, y: 0 }, { x: 255, y: 255 }],
            luma: [{ x: 0, y: 0 }, { x: 255, y: 255 }],
            red: [{ x: 0, y: 0 }, { x: 255, y: 255 }],
          },
          parametricCurve: {
            luma: {
              darks: 0,
              shadows: 0,
              highlights: 0,
              lights: 0,
              whiteLevel: 0,
              blackLevel: 0,
              split1: 25,
              split2: 50,
              split3: 75,
            },
            red: {
              darks: 0,
              shadows: 0,
              highlights: 0,
              lights: 0,
              whiteLevel: 0,
              blackLevel: 0,
              split1: 25,
              split2: 50,
              split3: 75,
            },
            green: {
              darks: 0,
              shadows: 0,
              highlights: 0,
              lights: 0,
              whiteLevel: 0,
              blackLevel: 0,
              split1: 25,
              split2: 50,
              split3: 75,
            },
            blue: {
              darks: 0,
              shadows: 0,
              highlights: 0,
              lights: 0,
              whiteLevel: 0,
              blackLevel: 0,
              split1: 25,
              split2: 50,
              split3: 75,
            },
          },
          curveMode: 'point',
          sectionVisibility: {
            basic: true,
            curves: true,
            color: true,
            details: true,
            effects: true,
          },
        },
      };

      setAdjustments((prev: Adjustments) => ({
        ...prev,
        masks: [...(prev.masks || []), newMask],
      }));

      setSkinMaskId(newMaskId);
      toast.success(t('editor.portrait.skinMaskGenerated'));
    } catch (error) {
      toast.error(t('editor.portrait.skinMaskFailed', { message: String(error) }));
    } finally {
      setIsGeneratingSkinMask(false);
      setEditor({ isGeneratingAiMask: false });
    }
  }, [selectedImage?.path, isGeneratingSkinMask, adjustments.masks, setAdjustments, setEditor, t]);

  const handleSkinAdjustmentChange = useCallback(
    (key: keyof Mask['adjustments'], value: number) => {
      if (!skinMaskId) {
        toast.warning(t('editor.portrait.skinMaskRequired'));
        return;
      }

      setAdjustments((prev: Adjustments) => ({
        ...prev,
        masks: prev.masks.map((m: MaskContainer) => {
          if (m.id === skinMaskId) {
            return {
              ...m,
              adjustments: {
                ...m.adjustments,
                [key]: value,
              },
            };
          }
          return m;
        }),
      }));
    },
    [skinMaskId, setAdjustments, t],
  );

  const handleApplyOneClickBeauty = useCallback(() => {
    if (!skinMaskId) {
      toast.warning(t('editor.portrait.skinMaskRequired'));
      return;
    }

    setSkinSmoothness(30);
    setSkinBrightness(15);
    setSkinContrast(5);
    setSkinSaturation(10);

    setAdjustments((prev: Adjustments) => ({
      ...prev,
      masks: prev.masks.map((m: MaskContainer) => {
        if (m.id === skinMaskId) {
          return {
            ...m,
            adjustments: {
              ...m.adjustments,
              clarity: -30,
              brightness: 15,
              contrast: 5,
              saturation: 10,
              lumaNoiseReduction: 20,
            },
          };
        }
        return m;
      }),
    }));

    toast.success(t('editor.portrait.oneClickBeautyApplied'));
  }, [skinMaskId, setAdjustments, t]);

  const handleResetSkinAdjustments = useCallback(() => {
    if (!skinMaskId) return;

    setSkinSmoothness(0);
    setSkinBrightness(0);
    setSkinContrast(0);
    setSkinSaturation(0);

    setAdjustments((prev: Adjustments) => ({
      ...prev,
      masks: prev.masks.map((m: MaskContainer) => {
        if (m.id === skinMaskId) {
          return {
            ...m,
            adjustments: {
              ...m.adjustments,
              clarity: 0,
              brightness: 0,
              contrast: 0,
              saturation: 0,
              lumaNoiseReduction: 0,
            },
          };
        }
        return m;
      }),
    }));
  }, [skinMaskId, setAdjustments]);

  return (
    <div className="flex flex-col h-full">
      <div className="p-4 flex justify-between items-center shrink-0 border-b border-surface">
        <div className="flex items-center gap-2">
          <User size={20} />
          <Text variant={TextVariants.title}>{t('editor.portrait.title')}</Text>
        </div>
      </div>

      <div className="grow overflow-y-auto p-4 space-y-6">
        <div className="p-4 bg-bg-tertiary rounded-lg">
          <Text variant={TextVariants.heading} className="mb-4">
            {t('editor.portrait.skinMask')}
          </Text>
          <button
            onClick={handleGenerateSkinMask}
            disabled={!selectedImage?.isReady || isGeneratingSkinMask}
            className={`w-full py-3 rounded-lg flex items-center justify-center gap-2 transition-all ${
              skinMaskId
                ? 'bg-accent/20 text-accent border border-accent/30'
                : isGeneratingSkinMask
                ? 'bg-accent text-button-text opacity-70 cursor-wait'
                : 'bg-accent text-button-text hover:bg-accent/90'
            }`}
          >
            {isGeneratingSkinMask ? (
              <>
                <RefreshCw size={18} className="animate-spin" />
                {t('editor.portrait.generating')}
              </>
            ) : skinMaskId ? (
              <>
                <Check size={18} />
                {t('editor.portrait.skinMaskReady')}
              </>
            ) : (
              <>
                <Sparkles size={18} />
                {t('editor.portrait.generateSkinMask')}
              </>
            )}
          </button>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: skinMaskId ? 1 : 0, y: skinMaskId ? 0 : 10 }}
          className="space-y-4"
        >
          <div className="p-4 bg-bg-tertiary rounded-lg">
            <button
              onClick={handleApplyOneClickBeauty}
              className="w-full py-3 rounded-lg bg-gradient-to-r from-accent to-accent/80 text-button-text flex items-center justify-center gap-2 hover:opacity-90 transition-opacity"
            >
              <Sparkles size={18} />
              {t('editor.portrait.oneClickBeauty')}
            </button>
          </div>

          <div className="p-4 bg-bg-tertiary rounded-lg">
            <Text variant={TextVariants.heading} className="mb-4 flex items-center gap-2">
              <Brush size={16} />
              {t('editor.portrait.skinRefinement')}
            </Text>

            <Slider
              label={t('editor.portrait.smoothness')}
              min={0}
              max={100}
              step={1}
              value={skinSmoothness}
              onChange={(e: any) => {
                const val = parseFloat(e.target.value);
                setSkinSmoothness(val);
                handleSkinAdjustmentChange('clarity', -val);
              }}
            />

            <Slider
              label={t('editor.portrait.brightness')}
              min={-50}
              max={50}
              step={1}
              value={skinBrightness}
              onChange={(e: any) => {
                const val = parseFloat(e.target.value);
                setSkinBrightness(val);
                handleSkinAdjustmentChange('brightness', val);
              }}
            />

            <Slider
              label={t('editor.portrait.contrast')}
              min={-50}
              max={50}
              step={1}
              value={skinContrast}
              onChange={(e: any) => {
                const val = parseFloat(e.target.value);
                setSkinContrast(val);
                handleSkinAdjustmentChange('contrast', val);
              }}
            />

            <Slider
              label={t('editor.portrait.saturation')}
              min={-50}
              max={50}
              step={1}
              value={skinSaturation}
              onChange={(e: any) => {
                const val = parseFloat(e.target.value);
                setSkinSaturation(val);
                handleSkinAdjustmentChange('saturation', val);
              }}
            />

            <button
              onClick={handleResetSkinAdjustments}
              className="w-full mt-4 py-2 rounded-lg bg-surface text-text-secondary hover:text-text-primary transition-colors flex items-center justify-center gap-2"
            >
              <RefreshCw size={16} />
              {t('editor.portrait.reset')}
            </button>
          </div>
        </motion.div>

        {!skinMaskId && (
          <div className="p-4 bg-bg-tertiary/50 rounded-lg border border-dashed border-border-color">
            <Text variant={TextVariants.body} color={TextColors.secondary} className="text-center">
              {t('editor.portrait.guide')}
            </Text>
          </div>
        )}
      </div>
    </div>
  );
}