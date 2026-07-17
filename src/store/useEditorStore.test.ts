import { describe, it, expect, beforeEach } from 'vitest';
import { useEditorStore } from './useEditorStore';
import { INITIAL_ADJUSTMENTS } from '../utils/adjustments';

describe('useEditorStore', () => {
  beforeEach(() => {
    // Reset store to initial state
    useEditorStore.setState({
      selectedImage: null,
      adjustments: INITIAL_ADJUSTMENTS,
      previewOverride: null,
      history: [INITIAL_ADJUSTMENTS],
      historyIndex: 0,
      finalPreviewUrl: null,
      uncroppedAdjustedPreviewUrl: null,
      showOriginal: false,
      histogram: null,
      waveform: null,
      isWaveformVisible: false,
      activeWaveformChannel: 'luma',
      waveformHeight: 220,
      isSliderDragging: false,
      interactivePatch: null,
      activeMaskContainerId: null,
      activeMaskId: null,
      activeAiPatchContainerId: null,
      activeAiSubMaskId: null,
      zoom: 1,
      displaySize: { width: 0, height: 0 },
      previewSize: { width: 0, height: 0 },
      baseRenderSize: { width: 0, height: 0 },
      originalSize: { width: 0, height: 0 },
      isRotationActive: false,
      overlayMode: 'thirds',
      overlayRotation: 0,
      transformedOriginalUrl: null,
      isStraightenActive: false,
      isWbPickerActive: false,
      liveRotation: null,
      copiedSectionAdjustments: null,
      copiedMask: null,
      copiedAdjustments: null,
      isGeneratingAiMask: false,
      isAIConnectorConnected: false,
      isGeneratingAi: false,
      isMaskControlHovered: false,
      hasRenderedFirstFrame: false,
      patchesSentToBackend: new Set<string>(),
    });
  });

  describe('initial state', () => {
    it('has null selectedImage', () => {
      expect(useEditorStore.getState().selectedImage).toBeNull();
    });

    it('has INITIAL_ADJUSTMENTS', () => {
      expect(useEditorStore.getState().adjustments).toEqual(INITIAL_ADJUSTMENTS);
    });

    it('has default zoom of 1', () => {
      expect(useEditorStore.getState().zoom).toBe(1);
    });

    it('has showOriginal false', () => {
      expect(useEditorStore.getState().showOriginal).toBe(false);
    });

    it('has empty history with initial adjustments', () => {
      const state = useEditorStore.getState();
      expect(state.history).toHaveLength(1);
      expect(state.historyIndex).toBe(0);
    });
  });

  describe('setEditor', () => {
    it('updates state with partial object', () => {
      useEditorStore.getState().setEditor({ zoom: 2 });
      expect(useEditorStore.getState().zoom).toBe(2);
    });

    it('updates state with updater function', () => {
      useEditorStore.getState().setEditor((state) => ({ zoom: state.zoom + 1 }));
      expect(useEditorStore.getState().zoom).toBe(2);
    });

    it('updates multiple properties', () => {
      useEditorStore.getState().setEditor({
        showOriginal: true,
        isSliderDragging: true,
        zoom: 3,
      });
      const state = useEditorStore.getState();
      expect(state.showOriginal).toBe(true);
      expect(state.isSliderDragging).toBe(true);
      expect(state.zoom).toBe(3);
    });
  });

  describe('pushHistory', () => {
    it('adds new adjustments to history', () => {
      const newAdj = { ...INITIAL_ADJUSTMENTS, exposure: 5 };
      useEditorStore.getState().pushHistory(newAdj);
      const state = useEditorStore.getState();
      expect(state.history).toHaveLength(2);
      expect(state.historyIndex).toBe(1);
    });

    it('truncates future history when pushing after undo', () => {
      const adj1 = { ...INITIAL_ADJUSTMENTS, exposure: 1 };
      const adj2 = { ...INITIAL_ADJUSTMENTS, exposure: 2 };
      useEditorStore.getState().pushHistory(adj1);
      useEditorStore.getState().pushHistory(adj2);
      useEditorStore.getState().undo();
      expect(useEditorStore.getState().historyIndex).toBe(1);

      const adj3 = { ...INITIAL_ADJUSTMENTS, exposure: 3 };
      useEditorStore.getState().pushHistory(adj3);
      const state = useEditorStore.getState();
      expect(state.history).toHaveLength(3);
      expect(state.historyIndex).toBe(2);
    });

    it('limits history to 50 entries', () => {
      // Fill history with 51 entries (initial + 50 pushes)
      for (let i = 0; i < 51; i++) {
        useEditorStore.getState().pushHistory({ ...INITIAL_ADJUSTMENTS, exposure: i });
      }
      expect(useEditorStore.getState().history.length).toBeLessThanOrEqual(50);
    });
  });

  describe('undo', () => {
    it('moves history index back', () => {
      const newAdj = { ...INITIAL_ADJUSTMENTS, exposure: 5 };
      useEditorStore.getState().pushHistory(newAdj);
      useEditorStore.getState().undo();
      const state = useEditorStore.getState();
      expect(state.historyIndex).toBe(0);
      expect(state.adjustments).toEqual(INITIAL_ADJUSTMENTS);
    });

    it('does nothing at the beginning of history', () => {
      useEditorStore.getState().undo();
      expect(useEditorStore.getState().historyIndex).toBe(0);
    });

    it('restores previous adjustments', () => {
      const adj1 = { ...INITIAL_ADJUSTMENTS, exposure: 10 };
      useEditorStore.getState().pushHistory(adj1);
      useEditorStore.getState().setEditor({ adjustments: adj1 });
      expect(useEditorStore.getState().adjustments.exposure).toBe(10);
      useEditorStore.getState().undo();
      expect(useEditorStore.getState().adjustments).toEqual(INITIAL_ADJUSTMENTS);
    });
  });

  describe('redo', () => {
    it('moves history index forward after undo', () => {
      const newAdj = { ...INITIAL_ADJUSTMENTS, exposure: 5 };
      useEditorStore.getState().pushHistory(newAdj);
      useEditorStore.getState().undo();
      useEditorStore.getState().redo();
      const state = useEditorStore.getState();
      expect(state.historyIndex).toBe(1);
      expect(state.adjustments.exposure).toBe(5);
    });

    it('does nothing at the end of history', () => {
      useEditorStore.getState().redo();
      expect(useEditorStore.getState().historyIndex).toBe(0);
    });
  });

  describe('resetHistory', () => {
    it('resets history to initial state', () => {
      const adj1 = { ...INITIAL_ADJUSTMENTS, exposure: 5 };
      useEditorStore.getState().pushHistory(adj1);
      useEditorStore.getState().resetHistory(INITIAL_ADJUSTMENTS);
      const state = useEditorStore.getState();
      expect(state.history).toHaveLength(1);
      expect(state.historyIndex).toBe(0);
      expect(state.adjustments).toEqual(INITIAL_ADJUSTMENTS);
    });
  });

  describe('goToHistoryIndex', () => {
    it('jumps to specified history index', () => {
      const adj1 = { ...INITIAL_ADJUSTMENTS, exposure: 1 };
      const adj2 = { ...INITIAL_ADJUSTMENTS, exposure: 2 };
      const adj3 = { ...INITIAL_ADJUSTMENTS, exposure: 3 };
      useEditorStore.getState().pushHistory(adj1);
      useEditorStore.getState().pushHistory(adj2);
      useEditorStore.getState().pushHistory(adj3);

      useEditorStore.getState().goToHistoryIndex(1);
      expect(useEditorStore.getState().historyIndex).toBe(1);
      expect(useEditorStore.getState().adjustments.exposure).toBe(1);
    });

    it('does nothing for invalid negative index', () => {
      useEditorStore.getState().goToHistoryIndex(-1);
      expect(useEditorStore.getState().historyIndex).toBe(0);
    });

    it('does nothing for index beyond history length', () => {
      useEditorStore.getState().goToHistoryIndex(999);
      expect(useEditorStore.getState().historyIndex).toBe(0);
    });
  });
});
