import { useState, useCallback, useRef, useEffect } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { Invokes } from '../components/ui/AppProperties';

export interface SemanticSearchResult {
  path: string;
  score: number;
  thumbnail_path: string | null;
}

export interface AiRatingResult {
  rating: number;
  reason: string;
  confidence: number;
}

export interface BatchProgress {
  current: number;
  total: number;
}

export function useSemanticSearch() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SemanticSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [ratingLoading, setRatingLoading] = useState(false);
  const [batchLoading, setBatchLoading] = useState(false);
  const [batchProgress, setBatchProgress] = useState<BatchProgress | null>(null);
  const [currentRating, setCurrentRating] = useState<AiRatingResult | null>(null);
  const [embeddings, setEmbeddings] = useState<Map<string, number[]>>(new Map());

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isMountedRef = useRef(true);

  useEffect(() => {
    return () => {
      isMountedRef.current = false;
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
        debounceRef.current = null;
      }
    };
  }, []);

  // Listen for batch progress events
  useEffect(() => {
    const unlisten = listen<BatchProgress>('semantic-search-batch-progress', (event) => {
      setBatchProgress(event.payload);
    });
    return () => {
      unlisten.then((fn) => fn());
    };
  }, []);

  const search = useCallback(async (searchQuery: string, imagePaths: string[], topK?: number) => {
    if (!searchQuery.trim() || imagePaths.length === 0) {
      setResults([]);
      return;
    }
    setLoading(true);
    try {
      const searchResults = await invoke<SemanticSearchResult[]>(Invokes.SemanticSearch, {
        query: searchQuery,
        imagePaths,
        topK: topK ?? 20,
      });
      if (!isMountedRef.current) return;
      setResults(searchResults);
    } catch (err) {
      if (!isMountedRef.current) return;
      console.error('Semantic search failed:', err);
      setResults([]);
    } finally {
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }, []);

  const debouncedSearch = useCallback((searchQuery: string, imagePaths: string[], topK?: number) => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }
    debounceRef.current = setTimeout(() => {
      search(searchQuery, imagePaths, topK);
    }, 300);
  }, [search]);

  const rateImage = useCallback(async (path: string) => {
    setRatingLoading(true);
    try {
      const rating = await invoke<AiRatingResult>(Invokes.AiRateImage, { path });
      if (!isMountedRef.current) return null;
      setCurrentRating(rating);
      return rating;
    } catch (err) {
      if (!isMountedRef.current) return null;
      console.error('AI rating failed:', err);
      return null;
    } finally {
      if (isMountedRef.current) {
        setRatingLoading(false);
      }
    }
  }, []);

  const computeImageEmbedding = useCallback(async (path: string) => {
    try {
      const embedding = await invoke<number[]>(Invokes.ComputeImageEmbedding, { path });
      if (!isMountedRef.current) return null;
      setEmbeddings((prev) => {
        const next = new Map(prev);
        next.set(path, embedding);
        return next;
      });
      return embedding;
    } catch (err) {
      if (!isMountedRef.current) return null;
      console.error('Compute image embedding failed:', err);
      return null;
    }
  }, []);

  const computeTextEmbedding = useCallback(async (textQuery: string) => {
    try {
      const embedding = await invoke<number[]>(Invokes.ComputeTextEmbedding, { query: textQuery });
      return embedding;
    } catch (err) {
      if (!isMountedRef.current) return null;
      console.error('Compute text embedding failed:', err);
      return null;
    }
  }, []);

  const batchComputeEmbeddings = useCallback(async (imagePaths: string[]) => {
    if (imagePaths.length === 0) return;
    setBatchLoading(true);
    setBatchProgress({ current: 0, total: imagePaths.length });
    try {
      const result = await invoke<Record<string, number[]>>(Invokes.BatchComputeEmbeddings, {
        imagePaths,
      });
      if (!isMountedRef.current) return;
      const newMap = new Map(Object.entries(result));
      setEmbeddings(newMap);
    } catch (err) {
      if (!isMountedRef.current) return;
      console.error('Batch compute embeddings failed:', err);
    } finally {
      if (isMountedRef.current) {
        setBatchLoading(false);
        setBatchProgress(null);
      }
    }
  }, []);

  return {
    query,
    setQuery,
    results,
    loading,
    ratingLoading,
    batchLoading,
    batchProgress,
    currentRating,
    embeddings,
    search,
    debouncedSearch,
    rateImage,
    computeImageEmbedding,
    computeTextEmbedding,
    batchComputeEmbeddings,
  };
}
