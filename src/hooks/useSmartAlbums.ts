import { useState, useCallback, useEffect } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { Invokes } from '../components/ui/AppProperties';
import { v4 as uuidv4 } from 'uuid';

export interface SmartAlbumCriteria {
    ai_tags?: string[] | null;
    user_tags?: string[] | null;
    min_rating?: number | null;
    max_rating?: number | null;
    color_labels?: string[] | null;
    date_from?: string | null;
    date_to?: string | null;
    camera_models?: string[] | null;
    lenses?: string[] | null;
    raw_only?: boolean | null;
    edited_only?: boolean | null;
    description_search?: string | null;
}

export interface SmartAlbum {
    id: string;
    name: string;
    criteria: SmartAlbumCriteria;
    enabled: boolean;
}

export function useSmartAlbums() {
    const [smartAlbums, setSmartAlbums] = useState<SmartAlbum[]>([]);
    const [isResolving, setIsResolving] = useState(false);
    const [resolvedPaths, setResolvedPaths] = useState<string[]>([]);

    useEffect(() => {
        loadSmartAlbums();
    }, []);

    const loadSmartAlbums = useCallback(async () => {
        try {
            const albums: SmartAlbum[] = await invoke(Invokes.LoadSmartAlbums);
            setSmartAlbums(albums);
        } catch (err) {
            console.error('Failed to load smart albums:', err);
        }
    }, []);

    const saveSmartAlbums = useCallback(async (albums: SmartAlbum[]) => {
        try {
            await invoke(Invokes.SaveSmartAlbums, { smart_albums: albums });
            setSmartAlbums(albums);
        } catch (err) {
            console.error('Failed to save smart albums:', err);
        }
    }, []);

    const createSmartAlbum = useCallback(async (name: string, criteria: SmartAlbumCriteria) => {
        const newAlbum: SmartAlbum = {
            id: uuidv4(),
            name,
            criteria,
            enabled: true,
        };
        const updated = [...smartAlbums, newAlbum];
        await saveSmartAlbums(updated);
        return newAlbum;
    }, [smartAlbums, saveSmartAlbums]);

    const updateSmartAlbum = useCallback(async (id: string, updates: Partial<SmartAlbum>) => {
        const updated = smartAlbums.map(album =>
            album.id === id ? { ...album, ...updates } : album
        );
        await saveSmartAlbums(updated);
    }, [smartAlbums, saveSmartAlbums]);

    const deleteSmartAlbum = useCallback(async (id: string) => {
        const updated = smartAlbums.filter(album => album.id !== id);
        await saveSmartAlbums(updated);
    }, [smartAlbums, saveSmartAlbums]);

    const resolveSmartAlbum = useCallback(async (album: SmartAlbum, rootPaths: string[]) => {
        setIsResolving(true);
        try {
            const paths: string[] = await invoke(Invokes.ResolveSmartAlbum, {
                criteria: album.criteria,
                root_paths: rootPaths,
            });
            setResolvedPaths(paths);
            return paths;
        } catch (err) {
            console.error('Failed to resolve smart album:', err);
            return [];
        } finally {
            setIsResolving(false);
        }
    }, []);

    return {
        smartAlbums,
        isResolving,
        resolvedPaths,
        loadSmartAlbums,
        saveSmartAlbums,
        createSmartAlbum,
        updateSmartAlbum,
        deleteSmartAlbum,
        resolveSmartAlbum,
    };
}
