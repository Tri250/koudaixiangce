import { useState, useEffect, useCallback } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { Invokes } from '../components/ui/AppProperties';

export interface ModelInfo {
  id: string;
  name: string;
  filename: string;
  url: string;
  sha256: string;
  sizeBytes: number | null;
  downloaded: boolean;
  required: boolean;
  category: string;
}

export interface DownloadProgress {
  modelId: string;
  downloadedBytes: number;
  totalBytes: number;
  percentage: number;
}

export function useModelManager() {
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [downloadingModelIds, setDownloadingModelIds] = useState<Set<string>>(new Set());
  const [downloadProgress, setDownloadProgress] = useState<Map<string, DownloadProgress>>(new Map());
  const [errors, setErrors] = useState<Map<string, string>>(new Map());
  const [modelsDirectory, setModelsDirectory] = useState<string>('');

  const fetchModels = useCallback(async () => {
    try {
      const result = await invoke<ModelInfo[]>(Invokes.ListAiModels);
      setModels(result);
    } catch (e) {
      console.error('Failed to list AI models:', e);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const fetchModelsDirectory = useCallback(async () => {
    try {
      const dir = await invoke<string>(Invokes.GetModelsDirectory);
      setModelsDirectory(dir);
    } catch (e) {
      console.error('Failed to get models directory:', e);
    }
  }, []);

  const downloadModel = useCallback(async (modelId: string) => {
    setDownloadingModelIds((prev) => new Set(prev).add(modelId));
    setErrors((prev) => {
      const next = new Map(prev);
      next.delete(modelId);
      return next;
    });
    try {
      await invoke(Invokes.DownloadAiModel, { modelId });
    } catch (e: any) {
      setErrors((prev) => new Map(prev).set(modelId, String(e)));
    } finally {
      setDownloadingModelIds((prev) => {
        const next = new Set(prev);
        next.delete(modelId);
        return next;
      });
      await fetchModels();
    }
  }, [fetchModels]);

  const deleteModel = useCallback(async (modelId: string) => {
    try {
      await invoke(Invokes.DeleteAiModel, { modelId });
      await fetchModels();
    } catch (e: any) {
      setErrors((prev) => new Map(prev).set(modelId, String(e)));
    }
  }, [fetchModels]);

  const downloadAll = useCallback(async () => {
    try {
      await invoke(Invokes.DownloadAllModels);
    } catch (e: any) {
      console.error('Failed to download all models:', e);
    } finally {
      await fetchModels();
    }
  }, [fetchModels]);

  // Listen for download progress events
  useEffect(() => {
    const unlisten = listen<DownloadProgress>('model-download-progress', (event) => {
      const payload = event.payload;
      setDownloadProgress((prev) => new Map(prev).set(payload.modelId, payload));
      setDownloadingModelIds((prev) => new Set(prev).add(payload.modelId));
    });

    return () => {
      unlisten.then((fn) => fn());
    };
  }, []);

  // Fetch models on mount
  useEffect(() => {
    fetchModels();
    fetchModelsDirectory();
  }, [fetchModels, fetchModelsDirectory]);

  // Group models by category
  const modelsByCategory = models.reduce<Record<string, ModelInfo[]>>((acc, model) => {
    if (!acc[model.category]) {
      acc[model.category] = [];
    }
    acc[model.category].push(model);
    return acc;
  }, {});

  const totalSizeBytes = models.reduce((sum, m) => sum + (m.sizeBytes || 0), 0);
  const downloadedCount = models.filter((m) => m.downloaded).length;
  const totalCount = models.length;

  return {
    models,
    modelsByCategory,
    isLoading,
    downloadingModelIds,
    downloadProgress,
    errors,
    modelsDirectory,
    totalSizeBytes,
    downloadedCount,
    totalCount,
    fetchModels,
    downloadModel,
    deleteModel,
    downloadAll,
  };
}
