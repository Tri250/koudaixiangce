import { useState, useCallback } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { toast } from 'react-toastify';
import { useTranslation } from 'react-i18next';
import { Invokes } from '../components/ui/AppProperties';

export type TransferMode = 'overwrite' | 'merge' | 'mergeAdditive';

export interface AdjustmentDiff {
  field: string;
  sourceValue: any;
  targetValue: any;
  isDefault: boolean;
}

export interface TransferResult {
  successCount: number;
  failureCount: number;
  failedPaths: string[];
  mode: TransferMode;
}

interface AdjustmentTransferState {
  sourcePath: string | null;
  sourceAdjustments: any | null;
  transferMode: TransferMode;
  diffs: AdjustmentDiff[];
  isPreviewLoading: boolean;
  isApplyLoading: boolean;
  result: TransferResult | null;
}

export function useAdjustmentTransfer() {
  const { t } = useTranslation();

  const [state, setState] = useState<AdjustmentTransferState>({
    sourcePath: null,
    sourceAdjustments: null,
    transferMode: 'overwrite',
    diffs: [],
    isPreviewLoading: false,
    isApplyLoading: false,
    result: null,
  });

  const copyAdjustments = useCallback(async (sourcePath: string) => {
    try {
      const adjustments = await invoke(Invokes.CopyAdjustmentsBatch, { sourcePath });
      setState((prev) => ({
        ...prev,
        sourcePath,
        sourceAdjustments: adjustments,
        diffs: [],
        result: null,
      }));
      return adjustments;
    } catch (err) {
      toast.error(t('errors.copyAdjustmentsFailed', { message: String(err) }));
      return null;
    }
  }, [t]);

  const previewDiff = useCallback(async (targetPath: string) => {
    if (!state.sourcePath) return;

    setState((prev) => ({ ...prev, isPreviewLoading: true }));
    try {
      const diffs = await invoke<AdjustmentDiff[]>(Invokes.GetAdjustmentDiff, {
        sourcePath: state.sourcePath,
        targetPath,
      });
      setState((prev) => ({ ...prev, diffs, isPreviewLoading: false }));
      return diffs;
    } catch (err) {
      setState((prev) => ({ ...prev, isPreviewLoading: false }));
      toast.error(t('errors.adjustmentDiffFailed', { message: String(err) }));
      return null;
    }
  }, [state.sourcePath, t]);

  const pasteAdjustments = useCallback(async (
    targetPaths: string[],
    transferMode?: TransferMode,
  ) => {
    if (!state.sourceAdjustments) return null;

    const mode = transferMode || state.transferMode;

    setState((prev) => ({ ...prev, isApplyLoading: true }));
    try {
      const result = await invoke<TransferResult>(Invokes.PasteAdjustmentsBatch, {
        adjustments: state.sourceAdjustments,
        targetPaths,
        transferMode: mode,
      });
      setState((prev) => ({ ...prev, result, isApplyLoading: false }));
      return result;
    } catch (err) {
      setState((prev) => ({ ...prev, isApplyLoading: false }));
      toast.error(t('errors.pasteAdjustmentsBatchFailed', { message: String(err) }));
      return null;
    }
  }, [state.sourceAdjustments, state.transferMode, t]);

  const setTransferMode = useCallback((mode: TransferMode) => {
    setState((prev) => ({ ...prev, transferMode: mode, result: null }));
  }, []);

  const reset = useCallback(() => {
    setState({
      sourcePath: null,
      sourceAdjustments: null,
      transferMode: 'overwrite',
      diffs: [],
      isPreviewLoading: false,
      isApplyLoading: false,
      result: null,
    });
  }, []);

  return {
    ...state,
    copyAdjustments,
    pasteAdjustments,
    previewDiff,
    setTransferMode,
    reset,
  };
}
