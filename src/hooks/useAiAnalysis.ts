import { useState, useCallback } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { Invokes } from '../components/ui/AppProperties';

export interface AiImageAnalysisResult {
    description: string;
    tags: string[];
    rating: number;
    reasons: string;
}

export interface AiAnalysisProgress {
    completed: number;
    total: number;
}

export function useAiAnalysis() {
    const [isAnalyzing, setIsAnalyzing] = useState(false);
    const [analysisResult, setAnalysisResult] = useState<AiImageAnalysisResult | null>(null);
    const [batchProgress, setBatchProgress] = useState<AiAnalysisProgress>({ completed: 0, total: 0 });
    const [batchResults, setBatchResults] = useState<AiImageAnalysisResult[]>([]);

    const analyzeImage = useCallback(async (
        imagePath: string,
        task: 'describe' | 'score' | 'analyze' = 'analyze',
    ): Promise<AiImageAnalysisResult | null> => {
        setIsAnalyzing(true);
        try {
            const result: AiImageAnalysisResult = await invoke(Invokes.AnalyzeImage, {
                image_path: imagePath,
                task,
            });
            setAnalysisResult(result);
            return result;
        } catch (err) {
            console.error('AI analysis failed:', err);
            return null;
        } finally {
            setIsAnalyzing(false);
        }
    }, []);

    const analyzeImagesBatch = useCallback(async (
        imagePaths: string[],
        task: 'describe' | 'score' | 'analyze' = 'analyze',
        onProgress?: (progress: AiAnalysisProgress) => void,
        onApplyResult?: (indexPath: string, result: AiImageAnalysisResult) => void,
    ): Promise<AiImageAnalysisResult[]> => {
        setIsAnalyzing(true);
        setBatchProgress({ completed: 0, total: imagePaths.length });
        setBatchResults([]);

        let unlisten: (() => void) | null = null;
        try {
            unlisten = await listen<AiAnalysisProgress>('ai-analysis-progress', (event) => {
                setBatchProgress(event.payload);
                if (onProgress) {
                    onProgress(event.payload);
                }
            });

            const results: AiImageAnalysisResult[] = await invoke(Invokes.AnalyzeImagesBatch, {
                image_paths: imagePaths,
                task,
            });
            setBatchResults(results);

            if (onApplyResult && imagePaths.length === results.length) {
                for (let i = 0; i < results.length; i++) {
                    onApplyResult(imagePaths[i], results[i]);
                }
            }

            return results;
        } catch (err) {
            console.error('Batch AI analysis failed:', err);
            return [];
        } finally {
            if (unlisten) unlisten();
            setIsAnalyzing(false);
        }
    }, []);

    return {
        isAnalyzing,
        analysisResult,
        batchProgress,
        batchResults,
        analyzeImage,
        analyzeImagesBatch,
        setAnalysisResult,
    };
}
