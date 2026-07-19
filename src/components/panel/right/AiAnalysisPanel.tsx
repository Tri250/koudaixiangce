import { useState, useCallback } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { useTranslation } from 'react-i18next';
import { Star, Loader2, Tag, FileText, BarChart3, Sparkles } from 'lucide-react';
import clsx from 'clsx';
import CollapsibleSection from '../../ui/CollapsibleSection';
import Button from '../../ui/Button';
import Text from '../../ui/Text';
import { TextColors, TextVariants, TextWeights } from '../../../types/typography';
import { Invokes } from '../../ui/AppProperties';
import { useSettingsStore } from '../../../store/useSettingsStore';
import { useEditorStore } from '../../../store/useEditorStore';
import { useLibraryStore } from '../../../store/useLibraryStore';
import { useAiAnalysis, AiImageAnalysisResult } from '../../../hooks/useAiAnalysis';

type AnalysisTask = 'describe' | 'score' | 'analyze';

const USER_TAG_PREFIX = 'user:';

export default function AiAnalysisPanel() {
  const { t } = useTranslation();
  const selectedImage = useEditorStore((s) => s.selectedImage);
  const multiSelectedPaths = useLibraryStore((s) => s.multiSelectedPaths);
  const imageList = useLibraryStore((s) => s.imageList);
  const appSettings = useSettingsStore((s) => s.appSettings);

  const {
    isAnalyzing,
    analysisResult,
    batchProgress,
    analyzeImage,
    analyzeImagesBatch,
    setAnalysisResult,
  } = useAiAnalysis();

  const [taskType, setTaskType] = useState<AnalysisTask>('analyze');
  const [collapsibleState, setCollapsibleState] = useState({
    task: true,
    results: true,
    batch: false,
  });

  const isConfigured = !!(appSettings?.aiVisionApiUrl);
  const targetPaths = multiSelectedPaths?.length > 0 ? multiSelectedPaths : selectedImage ? [selectedImage.path] : [];

  const handleAnalyzeCurrent = useCallback(async () => {
    if (!selectedImage) return;
    setAnalysisResult(null);
    await analyzeImage(selectedImage.path, taskType);
  }, [selectedImage, taskType, analyzeImage, setAnalysisResult]);

  const handleAnalyzeBatch = useCallback(async () => {
    if (targetPaths.length === 0) return;
    await analyzeImagesBatch(targetPaths, taskType);
  }, [targetPaths, taskType, analyzeImagesBatch]);

  const handleApplyRating = useCallback(async (result: AiImageAnalysisResult) => {
    if (!result || targetPaths.length === 0) return;
    const ratingValue = Math.round(result.rating);
    if (ratingValue < 1 || ratingValue > 5) return;
    try {
      await invoke(Invokes.SetRatingForPaths, { paths: targetPaths, rating: ratingValue });
      await Promise.all(targetPaths.map((path) =>
        invoke(Invokes.WriteRatingToSidecar, { image_path: path, rating: ratingValue })
      ));
    } catch (err) {
      console.error('Failed to apply rating:', err);
    }
  }, [targetPaths]);

  const handleApplyTags = useCallback(async (result: AiImageAnalysisResult) => {
    if (!result?.tags || targetPaths.length === 0) return;
    try {
      for (const tagItem of result.tags) {
        const cleanTag = tagItem.trim().toLowerCase();
        if (cleanTag) {
          const prefixedTag = `${USER_TAG_PREFIX}${cleanTag}`;
          await invoke(Invokes.AddTagForPaths, { paths: targetPaths, tag: prefixedTag });
        }
      }
    } catch (err) {
      console.error('Failed to apply tags:', err);
    }
  }, [targetPaths]);

  const handleToggleSection = (section: keyof typeof collapsibleState) => {
    setCollapsibleState((prev) => ({ ...prev, [section]: !prev[section] }));
  };

  const taskOptions: { key: AnalysisTask; label: string }[] = [
    { key: 'describe', label: t('editor.aiAnalysis.describe') },
    { key: 'score', label: t('editor.aiAnalysis.score') },
    { key: 'analyze', label: t('editor.aiAnalysis.analyze') },
  ];

  const renderStars = (rating: number) => {
    const clampedRating = Math.max(0, Math.min(5, rating));
    return (
      <div className="flex items-center gap-1">
        {[1, 2, 3, 4, 5].map((star) => (
          <Star
            key={star}
            size={18}
            className={clsx(
              'transition-colors duration-200',
              star <= clampedRating
                ? 'fill-accent text-accent'
                : 'fill-transparent text-text-secondary',
            )}
          />
        ))}
        <Text variant={TextVariants.small} color={TextColors.secondary} className="ml-1">
          ({clampedRating.toFixed(1)})
        </Text>
      </div>
    );
  };

  const renderTags = (tags: string[]) => {
    if (tags.length === 0) return null;
    return (
      <div className="flex flex-wrap gap-1">
        {tags.map((tag, index) => (
          <span
            key={`${tag}-${index}`}
            className="inline-flex items-center gap-1 bg-bg-primary px-2 py-0.5 rounded-md border border-surface hover:border-text-tertiary/50 transition-colors"
          >
            <Tag size={10} className="text-text-secondary shrink-0" />
            <Text as="span" variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.medium}>
              {tag}
            </Text>
          </span>
        ))}
      </div>
    );
  };

  const renderResult = (result: AiImageAnalysisResult) => (
    <div className="space-y-3">
      {result.description && (
        <div>
          <Text variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.semibold} className="uppercase tracking-wider mb-1 block">
            {t('editor.aiAnalysis.description')}
          </Text>
          <Text variant={TextVariants.small} color={TextColors.primary} className="leading-relaxed">
            {result.description}
          </Text>
        </div>
      )}
      {result.tags && result.tags.length > 0 && (
        <div>
          <Text variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.semibold} className="uppercase tracking-wider mb-1 block">
            {t('editor.aiAnalysis.tags')}
          </Text>
          {renderTags(result.tags)}
        </div>
      )}
      {result.rating > 0 && (
        <div>
          <Text variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.semibold} className="uppercase tracking-wider mb-1 block">
            {t('editor.aiAnalysis.rating')}
          </Text>
          {renderStars(result.rating)}
        </div>
      )}
      {result.reasons && (
        <div>
          <Text variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.semibold} className="uppercase tracking-wider mb-1 block">
            {t('editor.aiAnalysis.reasons')}
          </Text>
          <Text variant={TextVariants.small} color={TextColors.secondary} className="leading-relaxed">
            {result.reasons}
          </Text>
        </div>
      )}
      <div className="flex gap-2 pt-1">
        {result.rating > 0 && (
          <Button className="flex-1" onClick={() => handleApplyRating(result)} disabled={isAnalyzing}>
            <BarChart3 size={14} />
            <span className="ml-1">{t('editor.aiAnalysis.applyRating')}</span>
          </Button>
        )}
        {result.tags && result.tags.length > 0 && (
          <Button className="flex-1" onClick={() => handleApplyTags(result)} disabled={isAnalyzing}>
            <Tag size={14} />
            <span className="ml-1">{t('editor.aiAnalysis.applyTags')}</span>
          </Button>
        )}
      </div>
    </div>
  );

  return (
    <div className="flex flex-col h-full select-none overflow-hidden">
      <div className="p-4 flex justify-between items-center shrink-0 border-b border-surface">
        <Text variant={TextVariants.title}>{t('editor.aiAnalysis.title')}</Text>
      </div>

      <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 flex flex-col gap-2 custom-scrollbar">
        {!isConfigured ? (
          <div className="flex flex-col items-center justify-center py-8 gap-3">
            <Sparkles size={32} className="text-text-secondary" />
            <Text variant={TextVariants.small} color={TextColors.secondary} className="text-center">
              {t('editor.aiAnalysis.notConfigured')}
            </Text>
          </div>
        ) : !selectedImage ? (
          <Text variant={TextVariants.heading} color={TextColors.secondary} weight={TextWeights.normal} className="text-center mt-4">
            {t('editor.aiAnalysis.notConfigured').split('.')[0] || 'No image selected'}
          </Text>
        ) : (
          <>
            <div className="shrink-0">
              <CollapsibleSection
                title={t('editor.aiAnalysis.analyze')}
                isOpen={collapsibleState.task}
                onToggle={() => handleToggleSection('task')}
                canToggleVisibility={false}
                isContentVisible={true}
              >
                <div className="space-y-3 pt-2">
                  <div className="grid grid-cols-3 gap-2">
                    {taskOptions.map(({ key, label }) => (
                      <button
                        key={key}
                        className={clsx(
                          'p-2 rounded-md text-sm font-medium transition-colors flex items-center justify-center gap-1',
                          taskType === key
                            ? 'text-primary bg-surface ring-1 ring-accent'
                            : 'bg-surface text-text-secondary hover:bg-card-active',
                        )}
                        onClick={() => setTaskType(key)}
                      >
                        {label}
                      </button>
                    ))}
                  </div>

                  <Button className="w-full" onClick={handleAnalyzeCurrent} disabled={isAnalyzing || !selectedImage}>
                    {isAnalyzing ? <Loader2 size={16} className="animate-spin" /> : <FileText size={16} />}
                    <span className="ml-2">{t('editor.aiAnalysis.analyzeButton')}</span>
                  </Button>

                  {multiSelectedPaths?.length > 1 && (
                    <Button className="w-full bg-surface" onClick={handleAnalyzeBatch} disabled={isAnalyzing}>
                      {isAnalyzing ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
                      <span className="ml-2">{t('editor.aiAnalysis.batchButton')}</span>
                    </Button>
                  )}
                </div>
              </CollapsibleSection>
            </div>

            {analysisResult && (
              <div className="shrink-0">
                <CollapsibleSection
                  title={t('editor.aiAnalysis.results', { defaultValue: 'Results' })}
                  isOpen={collapsibleState.results}
                  onToggle={() => handleToggleSection('results')}
                  canToggleVisibility={false}
                  isContentVisible={true}
                >
                  <div className="pt-2">
                    {renderResult(analysisResult)}
                  </div>
                </CollapsibleSection>
              </div>
            )}

            {(batchProgress.total > 0 && isAnalyzing) && (
              <div className="shrink-0">
                <CollapsibleSection
                  title={t('editor.aiAnalysis.progress')}
                  isOpen={collapsibleState.batch}
                  onToggle={() => handleToggleSection('batch')}
                  canToggleVisibility={false}
                  isContentVisible={true}
                >
                  <div className="space-y-2 pt-2">
                    <div className="flex items-center gap-2">
                      <Loader2 size={14} className="animate-spin text-accent" />
                      <Text variant={TextVariants.small} color={TextColors.primary}>
                        {batchProgress.completed} / {batchProgress.total}
                      </Text>
                    </div>
                    <div className="w-full bg-bg-tertiary rounded-full h-2 border border-border-color">
                      <div
                        className="bg-accent h-2 rounded-full transition-all duration-300"
                        style={{
                          width: batchProgress.total > 0
                            ? `${(batchProgress.completed / batchProgress.total) * 100}%`
                            : '0%',
                        }}
                      />
                    </div>
                  </div>
                </CollapsibleSection>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
