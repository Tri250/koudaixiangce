import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Download, Trash2, CheckCircle, XCircle, Loader2, HardDrive, X } from 'lucide-react';
import { motion } from 'framer-motion';
import Button from '../ui/Button';
import Text from '../ui/Text';
import { TextColors, TextVariants, TextWeights } from '../../types/typography';
import { useModelManager, ModelInfo } from '../../hooks/useModelManager';

interface ModelManagerModalProps {
  isOpen: boolean;
  onClose(): void;
}

const CATEGORY_ORDER = ['masking', 'tagging', 'denoising', 'inpainting', 'depth'];

function formatSize(bytes: number | null): string {
  if (bytes === null || bytes === 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

export default function ModelManagerModal({ isOpen, onClose }: ModelManagerModalProps) {
  const { t } = useTranslation();
  const [isMounted, setIsMounted] = useState(false);
  const [show, setShow] = useState(false);
  const mouseDownTarget = useRef<EventTarget | null>(null);

  const {
    modelsByCategory,
    isLoading,
    downloadingModelIds,
    downloadProgress,
    errors,
    totalSizeBytes,
    downloadedCount,
    totalCount,
    downloadModel,
    deleteModel,
    downloadAll,
  } = useModelManager();

  useEffect(() => {
    if (isOpen) {
      setIsMounted(true);
      requestAnimationFrame(() => setShow(true));
    } else {
      setShow(false);
      const timer = setTimeout(() => setIsMounted(false), 300);
      return () => clearTimeout(timer);
    }
  }, [isOpen]);

  const handleBackdropMouseDown = (e: React.MouseEvent) => {
    mouseDownTarget.current = e.target;
  };

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (mouseDownTarget.current === e.target) {
      onClose();
    }
  };

  const handleDownloadAll = async () => {
    await downloadAll();
  };

  const renderModelRow = (model: ModelInfo) => {
    const isDownloading = downloadingModelIds.has(model.id);
    const progress = downloadProgress.get(model.id);
    const error = errors.get(model.id);
    const hasHashMismatch = error?.includes('Hash verification failed');

    return (
      <div
        key={model.id}
        className="flex items-center justify-between py-3 border-b border-border-color last:border-b-0"
      >
        <div className="flex-1 min-w-0 mr-4">
          <div className="flex items-center gap-2">
            <Text color={TextColors.primary} weight={TextWeights.medium} className="truncate">
              {model.name}
            </Text>
            {model.required && (
              <span className="px-1.5 py-0.5 text-xs bg-accent/20 text-accent rounded-sm">
                {t('settings.processing.modelManager.required', { defaultValue: 'Required' })}
              </span>
            )}
          </div>
          <Text variant={TextVariants.small} className="truncate text-text-secondary">
            {model.filename}
            {model.sizeBytes !== null && ` — ${formatSize(model.sizeBytes)}`}
          </Text>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          {/* Status badge */}
          {model.downloaded && !isDownloading && (
            <span className="flex items-center gap-1 px-2 py-1 text-xs bg-green-500/20 text-green-400 rounded-sm">
              <CheckCircle size={12} />
              {t('settings.processing.modelManager.downloaded')}
            </span>
          )}
          {!model.downloaded && !isDownloading && (
            <span className="flex items-center gap-1 px-2 py-1 text-xs bg-surface text-text-secondary rounded-sm border border-border-color">
              <XCircle size={12} />
              {t('settings.processing.modelManager.notDownloaded')}
            </span>
          )}
          {isDownloading && progress && (
            <span className="flex items-center gap-1 px-2 py-1 text-xs bg-blue-500/20 text-blue-400 rounded-sm">
              <Loader2 size={12} className="animate-spin" />
              {progress.percentage >= 100
                ? t('settings.processing.modelManager.verifying')
                : t('settings.processing.modelManager.progress', {
                    percentage: progress.percentage.toFixed(1),
                  })}
            </span>
          )}
          {isDownloading && !progress && (
            <span className="flex items-center gap-1 px-2 py-1 text-xs bg-blue-500/20 text-blue-400 rounded-sm">
              <Loader2 size={12} className="animate-spin" />
              {t('settings.processing.modelManager.downloading')}
            </span>
          )}
          {hasHashMismatch && (
            <span className="flex items-center gap-1 px-2 py-1 text-xs bg-red-500/20 text-red-400 rounded-sm">
              <XCircle size={12} />
              {t('settings.processing.modelManager.hashMismatch')}
            </span>
          )}

          {/* Progress bar */}
          {isDownloading && progress && progress.totalBytes > 0 && (
            <div className="w-24 h-1.5 bg-bg-primary rounded-full overflow-hidden">
              <div
                className="h-full bg-accent rounded-full transition-all duration-150"
                style={{ width: `${Math.min(100, progress.percentage)}%` }}
              />
            </div>
          )}

          {/* Action buttons */}
          {!model.downloaded && !isDownloading && (
            <Button
              variant="primary"
              size="sm"
              onClick={() => downloadModel(model.id)}
              className="min-w-[80px]"
            >
              <Download size={14} className="mr-1" />
              {t('settings.processing.modelManager.download')}
            </Button>
          )}
          {model.downloaded && !isDownloading && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => deleteModel(model.id)}
              className="min-w-[80px] text-text-secondary hover:text-red-400"
            >
              <Trash2 size={14} className="mr-1" />
              {t('settings.processing.modelManager.delete')}
            </Button>
          )}
          {isDownloading && (
            <Button variant="ghost" size="sm" disabled className="min-w-[80px] opacity-50">
              <Loader2 size={14} className="mr-1 animate-spin" />
              {t('settings.processing.modelManager.downloading')}
            </Button>
          )}
        </div>
      </div>
    );
  };

  const renderCategory = (category: string, categoryModels: ModelInfo[]) => {
    return (
      <div key={category} className="mb-5 last:mb-0">
        <div className="flex items-center gap-2 mb-2">
          <HardDrive size={16} className="text-accent" />
          <Text variant={TextVariants.heading} color={TextColors.accent}>
            {t(`settings.processing.modelManager.category.${category}`, {
              defaultValue: category,
            })}
          </Text>
          <Text variant={TextVariants.small} className="text-text-secondary">
            ({categoryModels.filter((m) => m.downloaded).length}/{categoryModels.length})
          </Text>
        </div>
        <div className="bg-bg-primary rounded-lg p-3 border border-border-color">
          {categoryModels.map(renderModelRow)}
        </div>
      </div>
    );
  };

  if (!isMounted) return null;

  return (
    <div
      className={`fixed inset-0 flex items-center justify-center z-50 bg-black/40 backdrop-blur-xs transition-opacity duration-300 ease-in-out ${
        show ? 'opacity-100' : 'opacity-0'
      }`}
      onMouseDown={handleBackdropMouseDown}
      onClick={handleBackdropClick}
    >
      <div
        className={`bg-surface rounded-xl shadow-2xl p-6 w-full max-w-2xl max-h-[80vh] transform transition-all duration-300 ease-out ${
          show ? 'scale-100 opacity-100 translate-y-0' : 'scale-95 opacity-0 -translate-y-4'
        }`}
      >
        <div className="flex flex-col h-full">
          {/* Header */}
          <div className="flex items-center justify-between mb-4">
            <div>
              <Text variant={TextVariants.title} color={TextColors.accent}>
                {t('settings.processing.modelManager.title')}
              </Text>
              <Text variant={TextVariants.small} className="text-text-secondary mt-1">
                {t('settings.processing.modelManager.description')}
              </Text>
            </div>
            <button
              onClick={onClose}
              className="p-2 text-text-secondary hover:text-text-primary hover:bg-surface rounded-md transition-colors"
            >
              <X size={20} />
            </button>
          </div>

          {/* Summary & Download All */}
          <div className="flex items-center justify-between mb-4 p-3 liquid-glass-subtle rounded-lg">
            <div className="flex items-center gap-3">
              <Text variant={TextVariants.small}>
                {downloadedCount}/{totalCount} {t('settings.processing.modelManager.downloaded', { defaultValue: 'Downloaded' }).toLowerCase()}
                {totalSizeBytes > 0 && (
                  <span className="ml-2 text-text-secondary">
                    {t('settings.processing.modelManager.totalSize', {
                      size: formatSize(totalSizeBytes),
                    })}
                  </span>
                )}
              </Text>
            </div>
            <Button
              onClick={handleDownloadAll}
              disabled={downloadingModelIds.size > 0 || downloadedCount === totalCount}
              size="sm"
            >
              <Download size={14} className="mr-1" />
              {t('settings.processing.modelManager.downloadAll')}
            </Button>
          </div>

          {/* Model list grouped by category */}
          <div className="flex-1 overflow-y-auto custom-scrollbar -mr-2 pr-2">
            {isLoading ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 size={24} className="animate-spin text-accent" />
              </div>
            ) : (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3 }}
              >
                {CATEGORY_ORDER.filter((cat) => modelsByCategory[cat]).map((cat) =>
                  renderCategory(cat, modelsByCategory[cat]),
                )}
              </motion.div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
