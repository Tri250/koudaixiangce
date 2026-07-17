import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useLibraryStore } from '../../store/useLibraryStore';
import { INITIAL_ADJUSTMENTS } from '../../utils/adjustments';

// Mock Tauri invoke
const mockInvoke = vi.fn();
vi.mock('@tauri-apps/api/core', () => ({
  invoke: (...args: any[]) => mockInvoke(...args), // eslint-disable-line @typescript-eslint/no-explicit-any
}));

describe('File Operations Chain', () => {
  beforeEach(() => {
    mockInvoke.mockReset();

    useLibraryStore.setState({
      rootPaths: [],
      currentFolderPath: null,
      expandedFolders: new Set<string>(),
      folderTrees: [],
      imageList: [],
      imageRatings: {},
      multiSelectedPaths: [],
      selectionAnchorPath: null,
      libraryActivePath: null,
      libraryActiveAdjustments: INITIAL_ADJUSTMENTS,
      isTreeLoading: false,
      isViewLoading: false,
    });
  });

  describe('folder navigation -> file listing -> selection', () => {
    it('navigates to a folder and loads images', () => {
      // Step 1: Set current folder
      useLibraryStore.getState().setLibrary({ currentFolderPath: '/photos/2024' });
      expect(useLibraryStore.getState().currentFolderPath).toBe('/photos/2024');

      // Step 2: Load images into the list
      const images = [
        { path: '/photos/2024/img1.jpg', name: 'img1.jpg' },
        { path: '/photos/2024/img2.jpg', name: 'img2.jpg' },
      ] as any[]; // eslint-disable-line @typescript-eslint/no-explicit-any
      useLibraryStore.getState().setLibrary({ imageList: images });
      expect(useLibraryStore.getState().imageList).toHaveLength(2);

      // Step 3: Select an image
      useLibraryStore.getState().setLibrary({
        libraryActivePath: '/photos/2024/img1.jpg',
        multiSelectedPaths: ['/photos/2024/img1.jpg'],
      });
      expect(useLibraryStore.getState().libraryActivePath).toBe('/photos/2024/img1.jpg');
    });

    it('handles multi-select with anchor', () => {
      const images = [
        { path: '/photos/img1.jpg', name: 'img1.jpg' },
        { path: '/photos/img2.jpg', name: 'img2.jpg' },
        { path: '/photos/img3.jpg', name: 'img3.jpg' },
      ] as any[]; // eslint-disable-line @typescript-eslint/no-explicit-any
      useLibraryStore.getState().setLibrary({ imageList: images });

      // Select first image as anchor
      useLibraryStore.getState().setLibrary({
        selectionAnchorPath: '/photos/img1.jpg',
        libraryActivePath: '/photos/img1.jpg',
        multiSelectedPaths: ['/photos/img1.jpg'],
      });

      // Add more to selection
      useLibraryStore.getState().setLibrary({
        multiSelectedPaths: ['/photos/img1.jpg', '/photos/img2.jpg', '/photos/img3.jpg'],
      });

      expect(useLibraryStore.getState().multiSelectedPaths).toHaveLength(3);
    });

    it('clears selection', () => {
      useLibraryStore.getState().setLibrary({
        multiSelectedPaths: ['/img1.jpg', '/img2.jpg'],
        libraryActivePath: '/img1.jpg',
      });

      useLibraryStore.getState().clearSelection();

      expect(useLibraryStore.getState().multiSelectedPaths).toEqual([]);
      expect(useLibraryStore.getState().libraryActivePath).toBeNull();
    });
  });

  describe('sorting and filtering chain', () => {
    it('applies sort criteria then filter criteria', () => {
      // Sort by date descending
      useLibraryStore.getState().setSortCriteria({ key: 'date', order: 'descending' });
      expect(useLibraryStore.getState().sortCriteria.key).toBe('date');

      // Filter by rating
      useLibraryStore.getState().setFilterCriteria({ rating: 3 });
      expect(useLibraryStore.getState().filterCriteria.rating).toBe(3);

      // Search
      useLibraryStore.getState().setSearchCriteria({ text: 'sunset', mode: 'AND' });
      expect(useLibraryStore.getState().searchCriteria.text).toBe('sunset');
      expect(useLibraryStore.getState().searchCriteria.mode).toBe('AND');
    });

    it('combines search with filter', () => {
      useLibraryStore.getState().setFilterCriteria({ colors: ['red'], rating: 4 });
      useLibraryStore.getState().setSearchCriteria({ text: 'beach' });

      const state = useLibraryStore.getState();
      expect(state.filterCriteria.colors).toEqual(['red']);
      expect(state.searchCriteria.text).toBe('beach');
    });
  });

  describe('root paths management', () => {
    it('adds root paths', () => {
      useLibraryStore.getState().setLibrary({ rootPaths: ['/photos', '/screenshots'] });
      expect(useLibraryStore.getState().rootPaths).toEqual(['/photos', '/screenshots']);
    });

    it('handles loading state during tree loading', () => {
      useLibraryStore.getState().setLibrary({ isTreeLoading: true });
      expect(useLibraryStore.getState().isTreeLoading).toBe(true);

      useLibraryStore.getState().setLibrary({ isTreeLoading: false });
      expect(useLibraryStore.getState().isTreeLoading).toBe(false);
    });
  });

  describe('Tauri invoke for file operations', () => {
    it('calls invoke to list directory', async () => {
      mockInvoke.mockResolvedValue(['/img1.jpg', '/img2.jpg']);

      const { invoke } = await import('@tauri-apps/api/core');
      const result = await invoke('list_directory', { path: '/photos' });

      expect(mockInvoke).toHaveBeenCalledWith('list_directory', { path: '/photos' });
      expect(result).toEqual(['/img1.jpg', '/img2.jpg']);
    });

    it('calls invoke to delete files', async () => {
      mockInvoke.mockResolvedValue(undefined);

      const { invoke } = await import('@tauri-apps/api/core');
      await invoke('delete_files', { paths: ['/img1.jpg'] });

      expect(mockInvoke).toHaveBeenCalledWith('delete_files', { paths: ['/img1.jpg'] });
    });

    it('handles file operation errors', async () => {
      mockInvoke.mockRejectedValue(new Error('Permission denied'));

      const { invoke } = await import('@tauri-apps/api/core');
      await expect(invoke('delete_files', { paths: ['/protected.jpg'] })).rejects.toThrow('Permission denied');
    });
  });
});
