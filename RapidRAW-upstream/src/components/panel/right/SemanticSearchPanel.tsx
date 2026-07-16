import { useState, useEffect, useMemo, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { Search, Loader2, Star, Cpu, Image as ImageIcon } from 'lucide-react';

import Input from '../../ui/Input';
import Button from '../../ui/Button';
import Text from '../../ui/Text';
import { TextColors, TextVariants, TextWeights } from '../../../types/typography';
import { useSemanticSearch, SemanticSearchResult, AiRatingResult } from '../../../hooks/useSemanticSearch';
import { useLibraryStore } from '../../../store/useLibraryStore';

function StarRating({ rating }: { rating: number }) {
  return (
    <div className="flex items-center gap-0.5">
      {Array.from({ length: 5 }, (_, i) => (
        <Star
          key={i}
          size={12}
          className={i < rating ? 'fill-yellow-400 text-yellow-400' : 'text-text-secondary/40'}
        />
      ))}
    </div>
  );
}

function SearchResultItem({
  result,
  onRate,
  ratingLoading,
}: {
  result: SemanticSearchResult;
  onRate: (path: string) => void;
  ratingLoading: boolean;
}) {
  const { t } = useTranslation();
  const [rating, setRating] = useState<AiRatingResult | null>(null);
  const fileName = useMemo(() => {
    const parts = result.path.replace(/\\/g, '/').split('/');
    return parts[parts.length - 1] || result.path;
  }, [result.path]);

  const handleRate = useCallback(async () => {
    const r = await onRate(result.path);
    if (r) setRating(r);
  }, [onRate, result.path]);

  const scorePercent = Math.round(result.score * 100);

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -8 }}
      transition={{ duration: 0.2 }}
      className="bg-surface rounded-md p-2 hover:bg-card-active transition-colors group"
    >
      <div className="flex items-start gap-2">
        <div className="w-10 h-10 rounded bg-bg-tertiary flex items-center justify-center shrink-0">
          <ImageIcon size={20} className="text-text-secondary" />
        </div>
        <div className="flex-1 min-w-0">
          <Text
            variant={TextVariants.small}
            weight={TextWeights.medium}
            className="truncate block"
          >
            {fileName}
          </Text>
          <Text
            variant={TextVariants.small}
            color={TextColors.secondary}
            className="block"
          >
            {t('settings.semanticSearch.title')}: {scorePercent}%
          </Text>
        </div>
      </div>

      <div className="flex items-center justify-between mt-1.5">
        {rating ? (
          <div className="flex items-center gap-2">
            <StarRating rating={rating.rating} />
            <Text
              variant={TextVariants.small}
              color={TextColors.secondary}
              className="truncate"
            >
              {rating.reason}
            </Text>
          </div>
        ) : (
          <button
            className="text-text-secondary hover:text-text-primary text-xs transition-colors opacity-0 group-hover:opacity-100"
            onClick={handleRate}
            disabled={ratingLoading}
          >
            {ratingLoading ? (
              <Loader2 size={12} className="animate-spin inline" />
            ) : (
              <Star size={12} className="inline" />
            )}
            <span className="ml-1">{t('settings.semanticSearch.rating')}</span>
          </button>
        )}
        {rating && (
          <Text variant={TextVariants.small} color={TextColors.secondary}>
            {Math.round(rating.confidence * 100)}%
          </Text>
        )}
      </div>
    </motion.div>
  );
}

export default function SemanticSearchPanel() {
  const { t } = useTranslation();
  const {
    query,
    setQuery,
    results,
    loading,
    batchLoading,
    batchProgress,
    debouncedSearch,
    rateImage,
    batchComputeEmbeddings,
  } = useSemanticSearch();

  const imageList = useLibraryStore((s) => s.imageList);

  const imagePaths = useMemo(() => {
    return imageList.map((img) => img.path);
  }, [imageList]);

  useEffect(() => {
    if (query.trim().length >= 2 && imagePaths.length > 0) {
      debouncedSearch(query, imagePaths);
    }
  }, [query, imagePaths, debouncedSearch]);

  const handleBatchCompute = useCallback(() => {
    if (imagePaths.length > 0) {
      batchComputeEmbeddings(imagePaths);
    }
  }, [imagePaths, batchComputeEmbeddings]);

  const hasResults = results.length > 0;

  return (
    <div className="flex flex-col h-full select-none overflow-hidden">
      <div className="p-4 flex justify-between items-center shrink-0 border-b border-surface">
        <Text variant={TextVariants.title}>{t('settings.semanticSearch.title')}</Text>
      </div>

      <div className="flex-1 overflow-y-auto overflow-x-hidden flex flex-col min-h-0 p-4 gap-4">
        <Text variant={TextVariants.small} color={TextColors.secondary}>
          {t('settings.semanticSearch.description')}
        </Text>

        <div className="relative">
          <Search
            size={16}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary pointer-events-none"
          />
          <Input
            className="pl-9 w-full"
            value={query}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setQuery(e.target.value)}
            placeholder={t('settings.semanticSearch.searchPlaceholder')}
            type="text"
          />
          {loading && (
            <Loader2
              size={16}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-text-secondary animate-spin"
            />
          )}
        </div>

        <Button
          className="w-full"
          disabled={batchLoading || imagePaths.length === 0}
          onClick={handleBatchCompute}
        >
          {batchLoading ? (
            <Loader2 size={16} className="animate-spin" />
          ) : (
            <Cpu size={16} />
          )}
          <span className="ml-2">
            {batchLoading && batchProgress
              ? t('settings.semanticSearch.batchProgress', {
                  current: batchProgress.current,
                  total: batchProgress.total,
                })
              : t('settings.semanticSearch.computeEmbeddings')}
          </span>
        </Button>

        {batchLoading && batchProgress && (
          <div className="w-full bg-bg-tertiary rounded-full h-1.5 border border-border-color">
            <motion.div
              className="bg-accent h-1.5 rounded-full"
              initial={{ width: '0%' }}
              animate={{
                width: `${Math.round((batchProgress.current / Math.max(batchProgress.total, 1)) * 100)}%`,
              }}
              transition={{ duration: 0.3, ease: 'easeOut' }}
            />
          </div>
        )}

        <div className="flex-1 min-h-0 overflow-y-auto">
          <AnimatePresence mode="popLayout">
            {hasResults ? (
              <motion.div
                key="results"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="flex flex-col gap-2"
              >
                {results.map((result) => (
                  <SearchResultItem
                    key={result.path}
                    result={result}
                    onRate={rateImage}
                    ratingLoading={false}
                  />
                ))}
              </motion.div>
            ) : query.trim().length >= 2 && !loading ? (
              <motion.div
                key="no-results"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="flex flex-col items-center justify-center py-8"
              >
                <Search size={32} className="text-text-secondary/40 mb-2" />
                <Text
                  variant={TextVariants.small}
                  color={TextColors.secondary}
                >
                  {imagePaths.length === 0
                    ? t('editor.ai.noImageSelected')
                    : t('settings.semanticSearch.title')}
                </Text>
              </motion.div>
            ) : null}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}
