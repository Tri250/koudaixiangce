import { useState, useCallback } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { Invokes } from '../components/ui/AppProperties';
import { SmartAlbumCriteria } from './useSmartAlbums';

export interface SearchResultItem {
    path: string;
    rating: number;
    tags: string[];
    date?: string;
    camera_model?: string;
}

export interface SearchOptions {
    sortBy?: string;
    sortDirection?: string;
    limit?: number;
}

export function useAdvancedSearch() {
    const [isSearching, setIsSearching] = useState(false);
    const [results, setResults] = useState<SearchResultItem[]>([]);
    const [totalCount, setTotalCount] = useState(0);

    const search = useCallback(async (
        criteria: SmartAlbumCriteria,
        rootPaths: string[],
        options?: SearchOptions,
    ) => {
        setIsSearching(true);
        try {
            const searchResults: SearchResultItem[] = await invoke(Invokes.SearchImages, {
                criteria,
                root_paths: rootPaths,
                sort_by: options?.sortBy || null,
                sort_direction: options?.sortDirection || null,
                limit: options?.limit || null,
            });
            setResults(searchResults);
            setTotalCount(searchResults.length);
            return searchResults;
        } catch (err) {
            console.error('Search failed:', err);
            setResults([]);
            setTotalCount(0);
            return [];
        } finally {
            setIsSearching(false);
        }
    }, []);

    const clearResults = useCallback(() => {
        setResults([]);
        setTotalCount(0);
    }, []);

    return {
        isSearching,
        results,
        totalCount,
        search,
        clearResults,
    };
}
