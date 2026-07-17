import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Copy, ClipboardPaste, Eye, Loader2, CheckCircle, XCircle, ArrowRight } from 'lucide-react';
import { AnimatePresence, motion } from 'framer-motion';
import Button from '../ui/Button';
import Text from '../ui/Text';
import { TextColors, TextVariants } from '../../types/typography';
import { useAdjustmentTransfer, TransferMode, AdjustmentDiff, TransferResult } from '../../hooks/useAdjustmentTransfer';
import { useLibraryStore } from '../../store/useLibraryStore';
import { useProcessStore } from '../../store/useProcessStore';

interface AdjustmentTransferModalProps {
  isOpen: boolean;
  onClose(): void;
  sourcePath: string | null;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function formatValue(value: any): string {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'number') return Number(value.toFixed(2)).toString();
  if (typeof value === 'boolean') return value ? '✓' : '✗';
  if (typeof value === 'string') return value || '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function DiffTable({ diffs }: { diffs: AdjustmentDiff[] }) {
  const { t: _t } = useTranslation();

  if (diffs.length === 0) {
    return (
      <Text variant={TextVariants.small} color={TextColors.secondary} className="text-center py-4">
        No differences found
      </Text>
    );
  }

  return (
    <div className="max-h-60 overflow-y-auto custom-scrollbar">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-white/10">
            <th className="text-left py-1.5 px-2 font-medium">
              <Text variant={TextVariants.small} color={TextColors.secondary}>Field</Text>
            </th>
            <th className="text-right py-1.5 px-2 font-medium">
              <Text variant={TextVariants.small} color={TextColors.secondary}>Source</Text>
            </th>
            <th className="text-center py-1.5 px-1 font-medium w-6">
              <ArrowRight size={12} className="text-muted mx-auto" />
            </th>
            <th className="text-right py-1.5 px-2 font-medium">
              <Text variant={TextVariants.small} color={TextColors.secondary}>Target</Text>
            </th>
          </tr>
        </thead>
        <tbody>
          {diffs.map((diff) => (
            <tr key={diff.field} className={`border-b border-white/5 ${diff.isDefault ? 'opacity-50' : ''}`}>
              <td className="py-1.5 px-2">
                <Text variant={TextVariants.small} color={TextColors.primary}>{diff.field}</Text>
              </td>
              <td className="py-1.5 px-2 text-right font-mono">
                <Text variant={TextVariants.small} color={diff.isDefault ? TextColors.secondary : TextColors.accent}>
                  {formatValue(diff.sourceValue)}
                </Text>
              </td>
              <td className="py-1.5 px-1 text-center">
                <ArrowRight size={10} className="text-muted mx-auto" />
              </td>
              <td className="py-1.5 px-2 text-right font-mono">
                <Text variant={TextVariants.small} color={TextColors.primary}>
                  {formatValue(diff.targetValue)}
                </Text>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ResultSummary({ result }: { result: TransferResult }) {
  const { t } = useTranslation();

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2">
        <CheckCircle size={16} className="text-green-400" />
        <Text variant={TextVariants.body} color={TextColors.primary}>
          {t('modals.adjustmentTransfer.applied', { count: result.successCount })}
        </Text>
      </div>
      {result.failureCount > 0 && (
        <div className="flex items-center gap-2">
          <XCircle size={16} className="text-red-400" />
          <Text variant={TextVariants.body} color={TextColors.error}>
            {t('modals.adjustmentTransfer.failed', { count: result.failureCount })}
          </Text>
        </div>
      )}
    </div>
  );
}

export default function AdjustmentTransferModal({
  isOpen,
  onClose,
  sourcePath,
}: AdjustmentTransferModalProps) {
  const { t } = useTranslation();
  const [isMounted, setIsMounted] = useState(false);
  const [show, setShow] = useState(false);
  const [showDiffs, setShowDiffs] = useState(false);

  const {
    sourcePath: copiedSourcePath,
    sourceAdjustments,
    transferMode,
    diffs,
    isPreviewLoading,
    isApplyLoading,
    result,
    copyAdjustments,
    pasteAdjustments,
    previewDiff,
    setTransferMode,
    reset,
  } = useAdjustmentTransfer();

  const multiSelectedPaths = useLibraryStore((s) => s.multiSelectedPaths);
  const _libraryActivePath = useLibraryStore((s) => s.libraryActivePath);
  const thumbnails = useProcessStore((s) => s.thumbnails);

  const targetPaths = multiSelectedPaths.filter((p) => p !== sourcePath);

  useEffect(() => {
    if (isOpen && sourcePath) {
      setIsMounted(true);
      copyAdjustments(sourcePath);
      const raf = requestAnimationFrame(() => setShow(true));
      return () => cancelAnimationFrame(raf);
    } else if (!isOpen) {
      setShow(false);
      const timer = setTimeout(() => {
        setIsMounted(false);
        reset();
        setShowDiffs(false);
      }, 300);
      return () => clearTimeout(timer);
    }
  }, [isOpen, sourcePath, copyAdjustments, reset]);

  const handlePreview = useCallback(async () => {
    if (targetPaths.length === 0) return;
    setShowDiffs(true);
    await previewDiff(targetPaths[0]);
  }, [targetPaths, previewDiff]);

  const handleApply = useCallback(async () => {
    if (targetPaths.length === 0 || !sourceAdjustments) return;
    await pasteAdjustments(targetPaths);
  }, [targetPaths, sourceAdjustments, pasteAdjustments]);

  const transferModes: { value: TransferMode; label: string; desc: string }[] = [
    { value: 'overwrite', label: t('modals.adjustmentTransfer.mode.overwrite'), desc: t('modals.adjustmentTransfer.modeDesc.overwrite') },
    { value: 'merge', label: t('modals.adjustmentTransfer.mode.merge'), desc: t('modals.adjustmentTransfer.modeDesc.merge') },
    { value: 'mergeAdditive', label: t('modals.adjustmentTransfer.mode.mergeAdditive'), desc: t('modals.adjustmentTransfer.modeDesc.mergeAdditive') },
  ];

  const sourceThumbnail = sourcePath ? thumbnails[sourcePath] : null;
  const sourceFilename = sourcePath ? sourcePath.split(/[\\/]/).pop() : '';

  if (!isMounted) return null;

  return (
    <AnimatePresence>
      {show && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
          onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 20 }}
            transition={{ duration: 0.2 }}
            className="liquid-glass-subtle rounded-xl p-6 w-full max-w-lg mx-4 shadow-2xl"
          >
            {/* Header */}
            <div className="flex items-center justify-between mb-5">
              <Text variant={TextVariants.title} color={TextColors.primary}>
                {t('modals.adjustmentTransfer.title')}
              </Text>
              <button
                onClick={onClose}
                className="text-muted hover:text-default transition-colors p-1"
              >
                <XCircle size={20} />
              </button>
            </div>

            {/* Source Image */}
            <div className="liquid-glass-subtle rounded-lg p-3 mb-4">
              <Text variant={TextVariants.small} color={TextColors.secondary} className="mb-2">
                Source
              </Text>
              <div className="flex items-center gap-3">
                {sourceThumbnail ? (
                  <img
                    src={sourceThumbnail}
                    alt={sourceFilename}
                    className="w-12 h-12 rounded-md object-cover"
                  />
                ) : (
                  <div className="w-12 h-12 rounded-md bg-white/10 flex items-center justify-center">
                    <Copy size={16} className="text-muted" />
                  </div>
                )}
                <div className="flex-1 min-w-0">
                  <Text variant={TextVariants.body} color={TextColors.primary} className="truncate">
                    {sourceFilename}
                  </Text>
                  <Text variant={TextVariants.small} color={TextColors.secondary}>
                    {sourceAdjustments ? `${Object.keys(sourceAdjustments).length} fields` : 'No adjustments'}
                  </Text>
                </div>
                {copiedSourcePath && (
                  <CheckCircle size={16} className="text-green-400 shrink-0" />
                )}
              </div>
            </div>

            {/* Target Images */}
            <div className="liquid-glass-subtle rounded-lg p-3 mb-4">
              <Text variant={TextVariants.small} color={TextColors.secondary} className="mb-2">
                Targets ({targetPaths.length})
              </Text>
              {targetPaths.length === 0 ? (
                <Text variant={TextVariants.small} color={TextColors.secondary}>
                  Select images in the library to paste adjustments to
                </Text>
              ) : (
                <div className="flex flex-wrap gap-2 max-h-24 overflow-y-auto">
                  {targetPaths.slice(0, 20).map((path) => {
                    const thumb = thumbnails[path];
                    const name = path.split(/[\\/]/).pop() || path;
                    return (
                      <div key={path} className="flex items-center gap-1.5 bg-white/5 rounded px-2 py-1">
                        {thumb ? (
                          <img src={thumb} alt={name} className="w-6 h-6 rounded object-cover" />
                        ) : (
                          <ClipboardPaste size={12} className="text-muted" />
                        )}
                        <Text variant={TextVariants.small} color={TextColors.primary} className="truncate max-w-32">
                          {name}
                        </Text>
                      </div>
                    );
                  })}
                  {targetPaths.length > 20 && (
                    <Text variant={TextVariants.small} color={TextColors.secondary} className="self-center">
                      +{targetPaths.length - 20} more
                    </Text>
                  )}
                </div>
              )}
            </div>

            {/* Transfer Mode */}
            <div className="liquid-glass-subtle rounded-lg p-3 mb-4">
              <Text variant={TextVariants.small} color={TextColors.secondary} className="mb-2">
                {t('modals.adjustmentTransfer.mode.overwrite')} / {t('modals.adjustmentTransfer.mode.merge')} / {t('modals.adjustmentTransfer.mode.mergeAdditive')}
              </Text>
              <div className="space-y-2">
                {transferModes.map((mode) => (
                  <label
                    key={mode.value}
                    className={`flex items-start gap-3 p-2 rounded-lg cursor-pointer transition-colors ${
                      transferMode === mode.value ? 'bg-accent/15 ring-1 ring-accent/30' : 'hover:bg-white/5'
                    }`}
                  >
                    <input
                      type="radio"
                      name="transferMode"
                      value={mode.value}
                      checked={transferMode === mode.value}
                      onChange={() => setTransferMode(mode.value)}
                      className="mt-1 accent-accent"
                    />
                    <div>
                      <Text variant={TextVariants.body} color={TextColors.primary}>
                        {mode.label}
                      </Text>
                      <Text variant={TextVariants.small} color={TextColors.secondary}>
                        {mode.desc}
                      </Text>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            {/* Diff Preview */}
            {showDiffs && (
              <div className="liquid-glass-subtle rounded-lg p-3 mb-4">
                <Text variant={TextVariants.small} color={TextColors.secondary} className="mb-2">
                  {t('modals.adjustmentTransfer.diffTitle')}
                </Text>
                {isPreviewLoading ? (
                  <div className="flex items-center justify-center py-4">
                    <Loader2 size={20} className="animate-spin text-accent" />
                  </div>
                ) : (
                  <DiffTable diffs={diffs} />
                )}
              </div>
            )}

            {/* Result Summary */}
            {result && (
              <div className="liquid-glass-subtle rounded-lg p-3 mb-4">
                <ResultSummary result={result} />
              </div>
            )}

            {/* Action Buttons */}
            <div className="flex items-center gap-3 mt-5">
              {!result ? (
                <>
                  <Button
                    onClick={handlePreview}
                    disabled={targetPaths.length === 0 || isPreviewLoading}
                    glass
                    className="flex items-center gap-2"
                  >
                    <Eye size={16} />
                    <Text variant={TextVariants.body} color={TextColors.primary}>
                      Preview
                    </Text>
                  </Button>
                  <Button
                    onClick={handleApply}
                    disabled={targetPaths.length === 0 || isApplyLoading || !sourceAdjustments}
                    glass
                    className="flex items-center gap-2"
                  >
                    {isApplyLoading ? (
                      <Loader2 size={16} className="animate-spin" />
                    ) : (
                      <ClipboardPaste size={16} />
                    )}
                    <Text variant={TextVariants.body} color={TextColors.primary}>
                      {t('modals.adjustmentTransfer.paste')}
                    </Text>
                  </Button>
                </>
              ) : (
                <Button onClick={onClose} glass>
                  <Text variant={TextVariants.body} color={TextColors.primary}>
                    Close
                  </Text>
                </Button>
              )}
              <div className="flex-1" />
              <Button onClick={onClose} glass>
                <Text variant={TextVariants.body} color={TextColors.secondary}>
                  Cancel
                </Text>
              </Button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
