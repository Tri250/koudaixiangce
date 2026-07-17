import { describe, it, expect, beforeEach } from 'vitest';
import { useLibraryStore } from './useLibraryStore';
import { INITIAL_ADJUSTMENTS } from '../utils/adjustments';

describe('useLibraryStore', () => {
  beforeEach(() => {
    useLibraryStore.setState({
      rootPaths: [],
      currentFolderPath: null,
      expandedFolders: new Set<string>(),
      folderTrees: [],
      pinnedFolderTrees: [],
      albumTree: [],
      activeAlbumId: null,
      expandedAlbumGroups: new Set<string>(),
      imageList: [],
      imageRatings: {},
      multiSelectedPaths: [],
      selectionAnchorPath: null,
      libraryActivePath: null,
      libraryActiveAdjustments: INITIAL_ADJUSTMENTS,
      sortCriteria: { key: 'name', order: 'ascending' },
      filterCriteria: { colors: [], rating: 0, rawStatus: 'all' },
      searchCriteria: { tags: [], text: '', mode: 'OR' },
      isTreeLoading: false,
      isViewLoading: false,
      libraryScrollTop: 0,
    });
  });

  describe('initial state', () => {
    it('has empty rootPaths', () => {
      expect(useLibraryStore.getState().rootPaths).toEqual([]);
    });

    it('has null currentFolderPath', () => {
      expect(useLibraryStore.getState().currentFolderPath).toBeNull();
    });

    it('has empty imageList', () => {
      expect(useLibraryStore.getState().imageList).toEqual([]);
    });

    it('has empty multiSelectedPaths', () => {
      expect(useLibraryStore.getState().multiSelectedPaths).toEqual([]);
    });

    it('has default sortCriteria', () => {
      const sort = useLibraryStore.getState().sortCriteria;
      expect(sort.key).toBe('name');
      expect(sort.order).toBe('ascending');
    });
  });

  describe('setLibrary', () => {
    it('updates state with partial object', () => {
      useLibraryStore.getState().setLibrary({ currentFolderPath: '/test/path' });
      expect(useLibraryStore.getState().currentFolderPath).toBe('/test/path');
    });

    it('updates state with updater function', () => {
      useLibraryStore.getState().setLibrary((state) => ({
        rootPaths: [...state.rootPaths, '/new/path'],
      }));
      expect(useLibraryStore.getState().rootPaths).toEqual(['/new/path']);
    });
  });

  describe('clearSelection', () => {
    it('clears multiSelectedPaths and libraryActivePath', () => {
      useLibraryStore.getState().setLibrary({
        multiSelectedPaths: ['/img1', '/img2'],
        libraryActivePath: '/img1',
      });
      useLibraryStore.getState().clearSelection();
      expect(useLibraryStore.getState().multiSelectedPaths).toEqual([]);
      expect(useLibraryStore.getState().libraryActivePath).toBeNull();
    });
  });

  describe('setFilterCriteria', () => {
    it('merges partial criteria', () => {
      useLibraryStore.getState().setFilterCriteria({ rating: 3 });
      expect(useLibraryStore.getState().filterCriteria.rating).toBe(3);
    });

    it('accepts updater function', () => {
      useLibraryStore.getState().setFilterCriteria((prev) => ({
        ...prev,
        colors: ['red', 'blue'],
      }));
      expect(useLibraryStore.getState().filterCriteria.colors).toEqual(['red', 'blue']);
    });
  });

  describe('setSearchCriteria', () => {
    it('merges partial criteria', () => {
      useLibraryStore.getState().setSearchCriteria({ text: 'sunset' });
      expect(useLibraryStore.getState().searchCriteria.text).toBe('sunset');
    });

    it('accepts updater function', () => {
      useLibraryStore.getState().setSearchCriteria((prev) => ({
        ...prev,
        mode: 'AND',
      }));
      expect(useLibraryStore.getState().searchCriteria.mode).toBe('AND');
    });
  });

  describe('setSortCriteria', () => {
    it('merges partial criteria', () => {
      useLibraryStore.getState().setSortCriteria({ key: 'date' });
      expect(useLibraryStore.getState().sortCriteria.key).toBe('date');
      expect(useLibraryStore.getState().sortCriteria.order).toBe('ascending');
    });

    it('accepts updater function', () => {
      useLibraryStore.getState().setSortCriteria((prev) => ({
        ...prev,
        order: 'descending',
      }));
      expect(useLibraryStore.getState().sortCriteria.order).toBe('descending');
    });
  });
});
