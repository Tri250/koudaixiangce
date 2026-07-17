import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Sparkles,
  User,
  Eye,
  Smile,
  Scissors,
  Brush,
  ChevronDown,
  Wand2,
} from 'lucide-react';
import Text from '../../ui/Text';
import { TextColors, TextVariants, TextWeights } from '../../../types/typography';
import Switch from '../../ui/Switch';
import Slider from '../../ui/Slider';

interface PortraitSection {
  id: string;
  icon: React.ElementType;
  title: string;
  features: PortraitFeature[];
}

interface PortraitFeature {
  id: string;
  label: string;
  description?: string;
  type: 'toggle' | 'slider';
  defaultValue?: number;
  min?: number;
  max?: number;
  step?: number;
}

const portraitSections: PortraitSection[] = [
  {
    id: 'skin',
    icon: Sparkles,
    title: 'editor.portrait.skin.title',
    features: [
      { id: 'skinSmoothing', label: 'editor.portrait.skin.smoothing', type: 'slider', defaultValue: 0, min: 0, max: 100, step: 1 },
      { id: 'skinTexture', label: 'editor.portrait.skin.texture', type: 'slider', defaultValue: 50, min: 0, max: 100, step: 1 },
      { id: 'skinTone', label: 'editor.portrait.skin.tone', type: 'slider', defaultValue: 0, min: -50, max: 50, step: 1 },
      { id: 'blemishRemoval', label: 'editor.portrait.skin.blemishRemoval', type: 'toggle' },
    ],
  },
  {
    id: 'face',
    icon: User,
    title: 'editor.portrait.face.title',
    features: [
      { id: 'faceSlimming', label: 'editor.portrait.face.slimming', type: 'slider', defaultValue: 0, min: 0, max: 100, step: 1 },
      { id: 'jawline', label: 'editor.portrait.face.jawline', type: 'slider', defaultValue: 0, min: 0, max: 100, step: 1 },
      { id: 'chin', label: 'editor.portrait.face.chin', type: 'slider', defaultValue: 0, min: -50, max: 50, step: 1 },
      { id: 'faceSymmetry', label: 'editor.portrait.face.symmetry', type: 'toggle' },
    ],
  },
  {
    id: 'eyes',
    icon: Eye,
    title: 'editor.portrait.eyes.title',
    features: [
      { id: 'eyeEnlargement', label: 'editor.portrait.eyes.enlargement', type: 'slider', defaultValue: 0, min: 0, max: 100, step: 1 },
      { id: 'eyeBrightening', label: 'editor.portrait.eyes.brightening', type: 'slider', defaultValue: 0, min: 0, max: 100, step: 1 },
      { id: 'darkCircles', label: 'editor.portrait.eyes.darkCircles', type: 'toggle' },
      { id: 'redEye', label: 'editor.portrait.eyes.redEye', type: 'toggle' },
    ],
  },
  {
    id: 'mouth',
    icon: Smile,
    title: 'editor.portrait.mouth.title',
    features: [
      { id: 'teethWhitening', label: 'editor.portrait.mouth.teethWhitening', type: 'slider', defaultValue: 0, min: 0, max: 100, step: 1 },
      { id: 'lipEnhancement', label: 'editor.portrait.mouth.lipEnhancement', type: 'slider', defaultValue: 0, min: 0, max: 100, step: 1 },
      { id: 'smile', label: 'editor.portrait.mouth.smile', type: 'slider', defaultValue: 0, min: 0, max: 100, step: 1 },
    ],
  },
  {
    id: 'makeup',
    icon: Brush,
    title: 'editor.portrait.makeup.title',
    features: [
      { id: 'makeupProtection', label: 'editor.portrait.makeup.protection', type: 'toggle' },
      { id: 'blush', label: 'editor.portrait.makeup.blush', type: 'slider', defaultValue: 0, min: 0, max: 100, step: 1 },
    ],
  },
  {
    id: 'body',
    icon: Scissors,
    title: 'editor.portrait.body.title',
    features: [
      { id: 'bodySlimming', label: 'editor.portrait.body.slimming', type: 'slider', defaultValue: 0, min: 0, max: 100, step: 1 },
      { id: 'neck', label: 'editor.portrait.body.neck', type: 'slider', defaultValue: 0, min: -50, max: 50, step: 1 },
      { id: 'shoulders', label: 'editor.portrait.body.shoulders', type: 'slider', defaultValue: 0, min: -50, max: 50, step: 1 },
    ],
  },
];

export default function PortraitPanel() {
  const { t } = useTranslation();
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set(['skin']));
  const [featureValues, setFeatureValues] = useState<Record<string, number | boolean>>({});

  const toggleSection = (id: string) => {
    setExpandedSections((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleFeatureChange = (id: string, value: number | boolean) => {
    setFeatureValues((prev) => ({ ...prev, [id]: value }));
  };

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="p-4 flex items-center justify-between shrink-0 border-b border-surface">
        <div className="flex items-center gap-2">
          <Wand2 size={18} className="text-accent" />
          <Text variant={TextVariants.title}>{t('editor.portrait.title')}</Text>
        </div>
      </div>

      {/* Subject selector */}
      <div className="px-4 pt-3 pb-1 shrink-0">
        <div className="flex items-center gap-1.5 overflow-x-auto scrollbar-hide pb-2">
          {(['all', 'female', 'male', 'child', 'senior'] as const).map((type, idx) => (
            <button
              key={type}
              className={`px-3 py-[5px] rounded-full text-xs font-semibold transition-all duration-200 whitespace-nowrap ${
                idx === 0
                  ? 'bg-accent text-white shadow-sm shadow-accent/25'
                  : 'bg-surface text-text-secondary hover:bg-surface-hover hover:text-text-primary'
              }`}
            >
              {t(`editor.portrait.subjects.${type}`)}
            </button>
          ))}
        </div>
      </div>

      {/* Sections */}
      <div className="grow overflow-y-auto px-3 pb-4 space-y-1">
        {portraitSections.map((section) => {
          const isExpanded = expandedSections.has(section.id);
          const Icon = section.icon;
          return (
            <div key={section.id} className="rounded-xl bg-surface/60 border border-border-color/30 overflow-hidden">
              <button
                className="w-full flex items-center gap-3 px-3.5 py-3 hover:bg-surface-hover/50 transition-colors"
                onClick={() => toggleSection(section.id)}
              >
                <div className="w-7 h-7 rounded-lg bg-accent/10 flex items-center justify-center shrink-0">
                  <Icon size={15} className="text-accent" />
                </div>
                <Text color={TextColors.primary} weight={TextWeights.medium} className="grow text-left text-[13px]">
                  {t(section.title as any)}
                </Text>
                <motion.div
                  animate={{ rotate: isExpanded ? 180 : 0 }}
                  transition={{ duration: 0.2 }}
                >
                  <ChevronDown size={16} className="text-text-secondary" />
                </motion.div>
              </button>

              <AnimatePresence initial={false}>
                {isExpanded && (
                  <motion.div
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: 'auto', opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    transition={{ duration: 0.25, ease: 'easeInOut' }}
                    className="overflow-hidden"
                  >
                    <div className="px-3.5 pb-3.5 pt-1 space-y-3">
                      {section.features.map((feature) => (
                        <div key={feature.id} className="flex flex-col gap-1.5">
                          <div className="flex items-center justify-between">
                            <Text variant={TextVariants.small} color={TextColors.primary} className="text-[12px]">
                              {t(feature.label as any)}
                            </Text>
                            {feature.type === 'slider' && (
                              <span className="text-[11px] font-medium text-accent tabular-nums">
                                {featureValues[feature.id] ?? feature.defaultValue ?? 0}
                              </span>
                            )}
                          </div>
                          {feature.type === 'toggle' ? (
                            <Switch
                              checked={!!featureValues[feature.id]}
                              id={`portrait-${feature.id}`}
                              label={t(feature.label as any)}
                              onChange={(checked) => handleFeatureChange(feature.id, checked)}
                            />
                          ) : (
                            <Slider
                              min={feature.min ?? 0}
                              max={feature.max ?? 100}
                              step={feature.step ?? 1}
                              defaultValue={feature.defaultValue ?? 0}
                              value={(featureValues[feature.id] as number) ?? feature.defaultValue ?? 0}
                              label={t(feature.label as any)}
                              onChange={(e: any) => handleFeatureChange(feature.id, Number(e.target.value))}
                            />
                          )}
                        </div>
                      ))}
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          );
        })}
      </div>
    </div>
  );
}
