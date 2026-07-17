import React, { useState, useCallback, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Move,
  Shrink,
  Maximize2,
  Wind,
  RotateCw,
  Undo2,
  RefreshCcw,
  Loader2,
  Circle,
} from 'lucide-react';
import clsx from 'clsx';
import Slider from '../../ui/Slider';
import Button from '../../ui/Button';
import Text from '../../ui/Text';
import { TextColors, TextVariants } from '../../../types/typography';
import { BrushType, LiquifyStroke, useRetouching } from '../../../hooks/useRetouching';
import { useEditorStore } from '../../../store/useEditorStore';

interface BrushTypeConfig {
  id: BrushType;
  icon: typeof Move;
  labelKey: string;
}

const BRUSH_TYPES: BrushTypeConfig[] = [
  { id: 'push', icon: Move, labelKey: 'editor.liquify.brushTypes.push' },
  { id: 'pull', icon: Shrink, labelKey: 'editor.liquify.brushTypes.pull' },
  { id: 'pucker', icon: MinimizeIcon, labelKey: 'editor.liquify.brushTypes.pucker' },
  { id: 'bloat', icon: Maximize2, labelKey: 'editor.liquify.brushTypes.bloat' },
  { id: 'twirl', icon: Wind, labelKey: 'editor.liquify.brushTypes.twirl' },
  { id: 'reconstruct', icon: RotateCw, labelKey: 'editor.liquify.brushTypes.reconstruct' },
];

function MinimizeIcon(props: React.SVGProps<SVGSVGElement> & { size?: number }) {
  const { size = 24, ...rest } = props;
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" {...rest}>
      <polyline points="4 14 10 14 10 20" />
      <polyline points="20 10 14 10 14 4" />
      <line x1="14" y1="10" x2="21" y2="3" />
      <line x1="3" y1="21" x2="10" y2="14" />
    </svg>
  );
}

export default function LiquifyPanel() {
  const { t } = useTranslation();
  const selectedImage = useEditorStore((s) => s.selectedImage);
  const {
    liquifyStrokes,
    addLiquifyStroke,
    undoLiquifyStroke,
    clearLiquifyStrokes,
    applyLiquify,
    resetLiquifyMesh,
  } = useRetouching();

  const [activeBrushType, setActiveBrushType] = useState<BrushType>('push');
  const [brushSize, setBrushSize] = useState(80);
  const [brushPressure, setBrushPressure] = useState(50);
  const [isApplying, setIsApplying] = useState(false);

  const setEditor = useEditorStore((s) => s.setEditor);

  useEffect(() => {
    setEditor({
      liquifyBrushType: activeBrushType,
      liquifyBrushSize: brushSize,
      liquifyBrushPressure: brushPressure,
    });
  }, [activeBrushType, brushSize, brushPressure, setEditor]);

  const handleResetMesh = useCallback(() => {
    resetLiquifyMesh();
    clearLiquifyStrokes();
  }, [resetLiquifyMesh, clearLiquifyStrokes]);

  const handleUndoStroke = useCallback(() => {
    undoLiquifyStroke();
  }, [undoLiquifyStroke]);

  const handleApply = useCallback(async () => {
    if (liquifyStrokes.length === 0) return;
    setIsApplying(true);
    try {
      await applyLiquify(liquifyStrokes);
    } finally {
      setIsApplying(false);
    }
  }, [liquifyStrokes, applyLiquify]);

  return (
    <div className="flex flex-col h-full select-none overflow-hidden">
      <div className="p-4 flex justify-between items-center shrink-0 border-b border-surface">
        <Text variant={TextVariants.title}>{t('editor.liquify.title')}</Text>
        <button
          className="p-2 rounded-full hover:bg-surface transition-colors"
          onClick={handleResetMesh}
          data-tooltip={t('editor.liquify.resetMeshTooltip')}
        >
          <RefreshCcw size={18} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 space-y-4">
        {/* Brush Type Selector */}
        <div>
          <Text variant={TextVariants.label} className="mb-2 block">
            {t('editor.liquify.brushType')}
          </Text>
          <div className="grid grid-cols-3 gap-2">
            {BRUSH_TYPES.map(({ id, icon: Icon, labelKey }) => (
              <button
                key={id}
                className={clsx(
                  'flex flex-col items-center justify-center gap-1 p-2 rounded-md text-xs font-medium transition-colors',
                  activeBrushType === id
                    ? 'bg-accent text-button-text'
                    : 'bg-surface text-text-secondary hover:bg-card-active hover:text-text-primary',
                )}
                onClick={() => setActiveBrushType(id)}
              >
                <Icon size={18} />
                <span>{t(labelKey)}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Brush Parameters */}
        <Slider
          label={t('editor.liquify.brushSize')}
          min={10}
          max={500}
          step={1}
          value={brushSize}
          onChange={(e) => setBrushSize(Number(e.target.value))}
          fillOrigin="min"
        />
        <Slider
          label={t('editor.liquify.brushPressure')}
          min={1}
          max={100}
          step={1}
          value={brushPressure}
          onChange={(e) => setBrushPressure(Number(e.target.value))}
          fillOrigin="min"
        />

        {/* Stroke info */}
        <div className="flex items-center justify-between text-sm">
          <Text variant={TextVariants.small} color={TextColors.secondary}>
            {t('editor.liquify.strokeCount', { count: liquifyStrokes.length })}
          </Text>
        </div>

        {/* Actions */}
        <div className="space-y-2">
          <Button
            className="w-full bg-surface"
            onClick={handleUndoStroke}
            disabled={liquifyStrokes.length === 0}
          >
            <Undo2 size={16} />
            <span className="ml-2">{t('editor.liquify.undoStroke')}</span>
          </Button>

          <Button
            className="w-full"
            onClick={handleApply}
            disabled={isApplying || liquifyStrokes.length === 0}
          >
            {isApplying ? <Loader2 size={16} className="animate-spin" /> : <Circle size={16} />}
            <span className="ml-2">{t('editor.liquify.apply')}</span>
          </Button>
        </div>
      </div>
    </div>
  );
}
