import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useProcessStore } from './useProcessStore';

describe('useProcessStore', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    useProcessStore.setState({
      exportState: { errorMessage: '', progress: { current: 0, total: 0 }, status: 'idle' },
      importState: { errorMessage: '', progress: { current: 0, total: 0 }, status: 'idle' },
      isIndexing: false,
      indexingProgress: { current: 0, total: 0 },
      thumbnails: {},
      thumbnailProgress: { current: 0, total: 0 },
      aiModelDownloadStatus: null,
      copiedFilePaths: [],
      isCopied: false,
      isPasted: false,
      initialFileToOpen: null,
      externalEditSession: null,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('initial state', () => {
    it('has idle export state', () => {
      expect(useProcessStore.getState().exportState.status).toBe('idle');
    });

    it('has idle import state', () => {
      expect(useProcessStore.getState().importState.status).toBe('idle');
    });

    it('is not indexing', () => {
      expect(useProcessStore.getState().isIndexing).toBe(false);
    });

    it('has empty thumbnails', () => {
      expect(useProcessStore.getState().thumbnails).toEqual({});
    });

    it('isCopied is false', () => {
      expect(useProcessStore.getState().isCopied).toBe(false);
    });

    it('isPasted is false', () => {
      expect(useProcessStore.getState().isPasted).toBe(false);
    });

    it('initialFileToOpen is null', () => {
      expect(useProcessStore.getState().initialFileToOpen).toBeNull();
    });

    it('externalEditSession is null', () => {
      expect(useProcessStore.getState().externalEditSession).toBeNull();
    });
  });

  describe('setProcess', () => {
    it('updates state with partial object', () => {
      useProcessStore.getState().setProcess({ isIndexing: true });
      expect(useProcessStore.getState().isIndexing).toBe(true);
    });

    it('updates state with updater function', () => {
      useProcessStore.getState().setProcess((state) => ({
        copiedFilePaths: [...state.copiedFilePaths, '/new/file.jpg'],
      }));
      expect(useProcessStore.getState().copiedFilePaths).toEqual(['/new/file.jpg']);
    });

    it('auto-resets isCopied after timeout', () => {
      useProcessStore.getState().setProcess({ isCopied: true });
      expect(useProcessStore.getState().isCopied).toBe(true);
      vi.advanceTimersByTime(1000);
      expect(useProcessStore.getState().isCopied).toBe(false);
    });

    it('auto-resets isPasted after timeout', () => {
      useProcessStore.getState().setProcess({ isPasted: true });
      expect(useProcessStore.getState().isPasted).toBe(true);
      vi.advanceTimersByTime(1000);
      expect(useProcessStore.getState().isPasted).toBe(false);
    });
  });

  describe('setExportState', () => {
    it('merges partial export state', () => {
      useProcessStore.getState().setExportState({ status: 'exporting' });
      expect(useProcessStore.getState().exportState.status).toBe('exporting');
    });

    it('accepts updater function', () => {
      useProcessStore.getState().setExportState((prev) => ({
        ...prev,
        progress: { current: 50, total: 100 },
      }));
      expect(useProcessStore.getState().exportState.progress.current).toBe(50);
    });

    it('resets to idle after success timeout', () => {
      useProcessStore.getState().setExportState({ status: 'success' });
      expect(useProcessStore.getState().exportState.status).toBe('success');
      vi.advanceTimersByTime(5000);
      expect(useProcessStore.getState().exportState.status).toBe('idle');
    });

    it('resets to idle after error timeout', () => {
      useProcessStore.getState().setExportState({ status: 'error', errorMessage: 'test error' });
      vi.advanceTimersByTime(5000);
      expect(useProcessStore.getState().exportState.status).toBe('idle');
      expect(useProcessStore.getState().exportState.errorMessage).toBe('');
    });

    it('resets to idle after cancelled timeout', () => {
      useProcessStore.getState().setExportState({ status: 'cancelled' });
      vi.advanceTimersByTime(5000);
      expect(useProcessStore.getState().exportState.status).toBe('idle');
    });

    it('does not reset while exporting', () => {
      useProcessStore.getState().setExportState({ status: 'exporting' });
      vi.advanceTimersByTime(5000);
      expect(useProcessStore.getState().exportState.status).toBe('exporting');
    });
  });

  describe('setImportState', () => {
    it('merges partial import state', () => {
      useProcessStore.getState().setImportState({ status: 'importing' });
      expect(useProcessStore.getState().importState.status).toBe('importing');
    });

    it('accepts updater function', () => {
      useProcessStore.getState().setImportState((prev) => ({
        ...prev,
        progress: { current: 25, total: 100 },
      }));
      expect(useProcessStore.getState().importState.progress.current).toBe(25);
    });

    it('resets to idle after success timeout', () => {
      useProcessStore.getState().setImportState({ status: 'success' });
      vi.advanceTimersByTime(5000);
      expect(useProcessStore.getState().importState.status).toBe('idle');
    });

    it('resets to idle after error timeout', () => {
      useProcessStore.getState().setImportState({ status: 'error', errorMessage: 'fail' });
      vi.advanceTimersByTime(5000);
      expect(useProcessStore.getState().importState.status).toBe('idle');
      expect(useProcessStore.getState().importState.errorMessage).toBe('');
    });
  });
});
