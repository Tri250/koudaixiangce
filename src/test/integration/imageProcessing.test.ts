import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useEditorStore } from '../../store/useEditorStore';
import { INITIAL_ADJUSTMENTS, normalizeLoadedAdjustments } from '../../utils/adjustments';
import { ImageLRUCache } from '../../utils/ImageLRUCache';

// Mock Tauri invoke
const mockInvoke = vi.fn();
vi.mock('@tauri-apps/api/core', () => ({
  invoke: (...args: any[]) => mockInvoke(...args), // eslint-disable-line @typescript-eslint/no-explicit-any
}));

describe('Image Processing Chain', () => {
  let cache: ImageLRUCache;

  beforeEach(() => {
    cache = new ImageLRUCache(5);
    mockInvoke.mockReset();

    // Reset editor store
    useEditorStore.setState({
      selectedImage: null,
      adjustments: INITIAL_ADJUSTMENTS,
      previewOverride: null,
      history: [INITIAL_ADJUSTMENTS],
      historyIndex: 0,
      finalPreviewUrl: null,
      showOriginal: false,
      zoom: 1,
    });
  });

  describe('image loading -> adjustments -> display', () => {
    it('loads image, applies adjustments, and caches result', () => {
      // Step 1: Load image - set selectedImage
      const testImage = { path: '/test/photo.jpg', name: 'photo.jpg' } as any; // eslint-disable-line @typescript-eslint/no-explicit-any
      useEditorStore.getState().setEditor({ selectedImage: testImage });

      expect(useEditorStore.getState().selectedImage).toEqual(testImage);

      // Step 2: Apply adjustments
      const newAdj = { ...INITIAL_ADJUSTMENTS, exposure: 2, contrast: 5 };
      useEditorStore.getState().pushHistory(newAdj);
      useEditorStore.getState().setEditor({ adjustments: newAdj });

      expect(useEditorStore.getState().adjustments.exposure).toBe(2);
      expect(useEditorStore.getState().adjustments.contrast).toBe(5);

      // Step 3: Cache the result
      const entry = {
        adjustments: newAdj,
        histogram: null,
        waveform: null,
        finalPreviewUrl: 'blob:preview-1',
        uncroppedPreviewUrl: null,
        selectedImage: testImage,
        originalSize: { width: 4000, height: 3000 },
        previewSize: { width: 800, height: 600 },
      };
      cache.set('/test/photo.jpg', entry);

      expect(cache.get('/test/photo.jpg')).toBeDefined();
      expect(cache.get('/test/photo.jpg')!.finalPreviewUrl).toBe('blob:preview-1');
    });

    it('processes adjustment history correctly (undo/redo chain)', () => {
      // Make multiple adjustments
      const adj1 = { ...INITIAL_ADJUSTMENTS, exposure: 1 };
      const adj2 = { ...INITIAL_ADJUSTMENTS, exposure: 2 };
      const adj3 = { ...INITIAL_ADJUSTMENTS, exposure: 3 };

      useEditorStore.getState().pushHistory(adj1);
      useEditorStore.getState().pushHistory(adj2);
      useEditorStore.getState().pushHistory(adj3);

      expect(useEditorStore.getState().historyIndex).toBe(3);

      // Undo twice
      useEditorStore.getState().undo();
      expect(useEditorStore.getState().adjustments.exposure).toBe(2);

      useEditorStore.getState().undo();
      expect(useEditorStore.getState().adjustments.exposure).toBe(1);

      // Redo
      useEditorStore.getState().redo();
      expect(useEditorStore.getState().adjustments.exposure).toBe(2);

      // Push new adjustment (should truncate future)
      const adj4 = { ...INITIAL_ADJUSTMENTS, exposure: 10 };
      useEditorStore.getState().pushHistory(adj4);
      // pushHistory doesn't update adjustments, so we need to go to the new index
      useEditorStore.getState().goToHistoryIndex(useEditorStore.getState().historyIndex);
      expect(useEditorStore.getState().adjustments.exposure).toBe(10);

      // Can't redo to the old future
      useEditorStore.getState().redo();
      // Should stay at adj4
      expect(useEditorStore.getState().historyIndex).toBe(useEditorStore.getState().history.length - 1);
    });

    it('handles cache eviction during image processing', () => {
      const smallCache = new ImageLRUCache(2);

      // Load and process image 1
      smallCache.set('/img1.jpg', {
        adjustments: INITIAL_ADJUSTMENTS,
        histogram: null,
        waveform: null,
        finalPreviewUrl: 'blob:1',
        uncroppedPreviewUrl: null,
        selectedImage: null,
        originalSize: { width: 100, height: 100 },
        previewSize: { width: 50, height: 50 },
      });

      // Load and process image 2
      smallCache.set('/img2.jpg', {
        adjustments: INITIAL_ADJUSTMENTS,
        histogram: null,
        waveform: null,
        finalPreviewUrl: 'blob:2',
        uncroppedPreviewUrl: null,
        selectedImage: null,
        originalSize: { width: 100, height: 100 },
        previewSize: { width: 50, height: 50 },
      });

      // Load and process image 3 - should evict image 1
      smallCache.set('/img3.jpg', {
        adjustments: INITIAL_ADJUSTMENTS,
        histogram: null,
        waveform: null,
        finalPreviewUrl: 'blob:3',
        uncroppedPreviewUrl: null,
        selectedImage: null,
        originalSize: { width: 100, height: 100 },
        previewSize: { width: 50, height: 50 },
      });

      expect(smallCache.get('/img1.jpg')).toBeUndefined();
      expect(smallCache.get('/img2.jpg')).toBeDefined();
      expect(smallCache.get('/img3.jpg')).toBeDefined();
    });

    it('handles normalized adjustments from loaded data', () => {
      // Simulate loading adjustments from a saved file
      const loadedData = {
        exposure: 3,
        contrast: -2,
        masks: [
          { adjustments: { exposure: 1 }, subMasks: [{ type: 'brush' }] },
        ],
      };

      const normalized = normalizeLoadedAdjustments(loadedData as any); // eslint-disable-line @typescript-eslint/no-explicit-any

      expect(normalized.exposure).toBe(3);
      expect(normalized.contrast).toBe(-2);
      expect(normalized.brightness).toBe(0); // filled from defaults
      expect(normalized.masks).toHaveLength(1);
      // normalizeSubMasks adds defaults: visible=true, mode=additive, etc.
      expect(normalized.masks[0].subMasks[0].visible).toBe(true);
      expect(normalized.masks[0].subMasks[0].mode).toBe('additive');
    });
  });

  describe('Tauri invoke integration', () => {
    it('calls invoke for image processing commands', async () => {
      mockInvoke.mockResolvedValue({ base64: 'test_data' });

      const { invoke } = await import('@tauri-apps/api/core');
      const result = await invoke('process_image', { path: '/test.jpg' });

      expect(mockInvoke).toHaveBeenCalledWith('process_image', { path: '/test.jpg' });
      expect(result).toEqual({ base64: 'test_data' });
    });

    it('handles invoke errors gracefully', async () => {
      mockInvoke.mockRejectedValue(new Error('Processing failed'));

      const { invoke } = await import('@tauri-apps/api/core');
      await expect(invoke('process_image', { path: '/test.jpg' })).rejects.toThrow('Processing failed');
    });
  });
});
