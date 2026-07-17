import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useProcessStore } from '../../store/useProcessStore';
import { useEditorStore } from '../../store/useEditorStore';
import { INITIAL_ADJUSTMENTS } from '../../utils/adjustments';

// Mock Tauri invoke
const mockInvoke = vi.fn();
vi.mock('@tauri-apps/api/core', () => ({
  invoke: (...args: any[]) => mockInvoke(...args), // eslint-disable-line @typescript-eslint/no-explicit-any
}));

describe('Export Flow', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockInvoke.mockReset();

    useProcessStore.setState({
      exportState: { errorMessage: '', progress: { current: 0, total: 0 }, status: 'idle' },
      importState: { errorMessage: '', progress: { current: 0, total: 0 }, status: 'idle' },
      isCopied: false,
      isPasted: false,
    });

    useEditorStore.setState({
      selectedImage: null,
      adjustments: INITIAL_ADJUSTMENTS,
      history: [INITIAL_ADJUSTMENTS],
      historyIndex: 0,
      finalPreviewUrl: null,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('full export workflow', () => {
    it('goes from idle -> exporting -> success -> idle', () => {
      // Step 1: Start with idle state
      expect(useProcessStore.getState().exportState.status).toBe('idle');

      // Step 2: Set image and adjustments
      useEditorStore.getState().setEditor({
        selectedImage: { path: '/test/photo.jpg' } as any, // eslint-disable-line @typescript-eslint/no-explicit-any
      });
      const adj = { ...INITIAL_ADJUSTMENTS, exposure: 2 };
      useEditorStore.getState().pushHistory(adj);
      useEditorStore.getState().setEditor({ adjustments: adj });

      // Step 3: Start export
      useProcessStore.getState().setExportState({
        status: 'exporting',
        progress: { current: 0, total: 10 },
      });
      expect(useProcessStore.getState().exportState.status).toBe('exporting');

      // Step 4: Progress updates
      useProcessStore.getState().setExportState({
        progress: { current: 5, total: 10 },
      });
      expect(useProcessStore.getState().exportState.progress.current).toBe(5);

      // Step 5: Complete
      useProcessStore.getState().setExportState({
        status: 'success',
        progress: { current: 10, total: 10 },
      });
      expect(useProcessStore.getState().exportState.status).toBe('success');

      // Step 6: Auto-reset to idle after timeout
      vi.advanceTimersByTime(5000);
      expect(useProcessStore.getState().exportState.status).toBe('idle');
      expect(useProcessStore.getState().exportState.progress.current).toBe(0);
    });

    it('handles export error and auto-resets', () => {
      useProcessStore.getState().setExportState({ status: 'exporting' });

      useProcessStore.getState().setExportState({
        status: 'error',
        errorMessage: 'Disk full',
      });

      expect(useProcessStore.getState().exportState.status).toBe('error');
      expect(useProcessStore.getState().exportState.errorMessage).toBe('Disk full');

      vi.advanceTimersByTime(5000);
      expect(useProcessStore.getState().exportState.status).toBe('idle');
      expect(useProcessStore.getState().exportState.errorMessage).toBe('');
    });

    it('handles export cancellation and auto-resets', () => {
      useProcessStore.getState().setExportState({ status: 'exporting' });

      useProcessStore.getState().setExportState({ status: 'cancelled' });
      expect(useProcessStore.getState().exportState.status).toBe('cancelled');

      vi.advanceTimersByTime(5000);
      expect(useProcessStore.getState().exportState.status).toBe('idle');
    });
  });

  describe('import workflow', () => {
    it('goes from idle -> importing -> success -> idle', () => {
      expect(useProcessStore.getState().importState.status).toBe('idle');

      // Start import
      useProcessStore.getState().setImportState({
        status: 'importing',
        progress: { current: 0, total: 5 },
      });
      expect(useProcessStore.getState().importState.status).toBe('importing');

      // Progress
      useProcessStore.getState().setImportState({
        progress: { current: 3, total: 5 },
      });

      // Complete
      useProcessStore.getState().setImportState({
        status: 'success',
        progress: { current: 5, total: 5 },
      });

      vi.advanceTimersByTime(5000);
      expect(useProcessStore.getState().importState.status).toBe('idle');
    });
  });

  describe('Tauri invoke for export', () => {
    it('calls invoke with correct export parameters', async () => {
      mockInvoke.mockResolvedValue('/output/exported.jpg');

      const { invoke } = await import('@tauri-apps/api/core');
      const result = await invoke('export_image', {
        path: '/test/photo.jpg',
        format: 'jpeg',
        quality: 95,
      });

      expect(mockInvoke).toHaveBeenCalledWith('export_image', {
        path: '/test/photo.jpg',
        format: 'jpeg',
        quality: 95,
      });
      expect(result).toBe('/output/exported.jpg');
    });

    it('handles export invoke error', async () => {
      mockInvoke.mockRejectedValue(new Error('Write failed'));

      const { invoke } = await import('@tauri-apps/api/core');
      await expect(invoke('export_image', { path: '/test.jpg' })).rejects.toThrow('Write failed');
    });
  });

  describe('copy/paste file operations', () => {
    it('tracks copy and paste state with auto-reset', () => {
      // Copy
      useProcessStore.getState().setProcess({
        isCopied: true,
        copiedFilePaths: ['/img1.jpg', '/img2.jpg'],
      });
      expect(useProcessStore.getState().isCopied).toBe(true);
      expect(useProcessStore.getState().copiedFilePaths).toEqual(['/img1.jpg', '/img2.jpg']);

      // Auto-reset
      vi.advanceTimersByTime(1000);
      expect(useProcessStore.getState().isCopied).toBe(false);
      expect(useProcessStore.getState().copiedFilePaths).toEqual(['/img1.jpg', '/img2.jpg']); // paths persist

      // Paste
      useProcessStore.getState().setProcess({ isPasted: true });
      expect(useProcessStore.getState().isPasted).toBe(true);

      vi.advanceTimersByTime(1000);
      expect(useProcessStore.getState().isPasted).toBe(false);
    });
  });

  describe('concurrent export states', () => {
    it('maintains separate export and import states', () => {
      useProcessStore.getState().setExportState({ status: 'exporting' });
      useProcessStore.getState().setImportState({ status: 'importing' });

      expect(useProcessStore.getState().exportState.status).toBe('exporting');
      expect(useProcessStore.getState().importState.status).toBe('importing');

      useProcessStore.getState().setExportState({ status: 'success' });

      // Import should still be importing
      expect(useProcessStore.getState().importState.status).toBe('importing');
    });
  });
});
