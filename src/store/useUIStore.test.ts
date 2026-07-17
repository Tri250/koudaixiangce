import { describe, it, expect, beforeEach } from 'vitest';
import { useUIStore } from './useUIStore';

describe('useUIStore', () => {
  beforeEach(() => {
    useUIStore.setState({
      activeView: 'library',
      isFullScreen: false,
      isWindowFullScreen: false,
      isInstantTransition: false,
      isLayoutReady: false,
      uiVisibility: { folderTree: true, filmstrip: true },
      isLibraryExportPanelVisible: false,
      leftPanelWidth: 256,
      rightPanelWidth: 320,
      bottomPanelHeight: 144,
      compactEditorPanelHeightOverride: null,
      activeRightPanel: 'adjustments',
      renderedRightPanel: 'adjustments',
      slideDirection: 1,
      collapsibleSectionsState: { basic: true, color: false, curves: true, details: false, effects: false },
      isCreateFolderModalOpen: false,
      isRenameFolderModalOpen: false,
      isRenameFileModalOpen: false,
      renameTargetPaths: [],
      isImportModalOpen: false,
      isCopyPasteSettingsModalOpen: false,
      importTargetFolder: null,
      importSourcePaths: [],
      folderActionTarget: null,
      confirmModalState: { isOpen: false },
      customEscapeHandler: null,
    });
  });

  describe('initial state', () => {
    it('has activeView as library', () => {
      expect(useUIStore.getState().activeView).toBe('library');
    });

    it('is not fullscreen', () => {
      expect(useUIStore.getState().isFullScreen).toBe(false);
    });

    it('has default panel widths', () => {
      expect(useUIStore.getState().leftPanelWidth).toBe(256);
      expect(useUIStore.getState().rightPanelWidth).toBe(320);
      expect(useUIStore.getState().bottomPanelHeight).toBe(144);
    });

    it('has default uiVisibility', () => {
      const vis = useUIStore.getState().uiVisibility;
      expect(vis.folderTree).toBe(true);
      expect(vis.filmstrip).toBe(true);
    });

    it('has default collapsible sections', () => {
      const sections = useUIStore.getState().collapsibleSectionsState;
      expect(sections.basic).toBe(true);
      expect(sections.color).toBe(false);
    });

    it('has default activeRightPanel as adjustments', () => {
      expect(useUIStore.getState().activeRightPanel).toBe('adjustments');
    });
  });

  describe('setUI', () => {
    it('updates state with partial object', () => {
      useUIStore.getState().setUI({ isFullScreen: true });
      expect(useUIStore.getState().isFullScreen).toBe(true);
    });

    it('updates state with updater function', () => {
      useUIStore.getState().setUI((state) => ({
        leftPanelWidth: state.leftPanelWidth + 10,
      }));
      expect(useUIStore.getState().leftPanelWidth).toBe(266);
    });

    it('updates multiple properties at once', () => {
      useUIStore.getState().setUI({
        activeView: 'editor',
        isLayoutReady: true,
      });
      expect(useUIStore.getState().activeView).toBe('editor');
      expect(useUIStore.getState().isLayoutReady).toBe(true);
    });
  });

  describe('setRightPanel', () => {
    it('switches to a different panel', () => {
      useUIStore.getState().setRightPanel('crop');
      expect(useUIStore.getState().activeRightPanel).toBe('crop');
    });

    it('toggles off when clicking same panel', () => {
      useUIStore.getState().setRightPanel('adjustments'); // same as current
      expect(useUIStore.getState().activeRightPanel).toBeNull();
    });

    it('sets slide direction based on panel order', () => {
      // Adjustments (index 1) -> Export (index 6) -> direction should be 1
      useUIStore.getState().setUI({ activeRightPanel: 'adjustments', renderedRightPanel: 'adjustments' });
      useUIStore.getState().setRightPanel('export');
      expect(useUIStore.getState().slideDirection).toBe(1);
    });

    it('sets negative slide direction for reverse order', () => {
      useUIStore.getState().setUI({ activeRightPanel: 'export', renderedRightPanel: 'export' });
      useUIStore.getState().setRightPanel('adjustments');
      expect(useUIStore.getState().slideDirection).toBe(-1);
    });
  });

  describe('setCustomEscapeHandler', () => {
    it('sets custom escape handler', () => {
      const handler = () => {};
      useUIStore.getState().setCustomEscapeHandler(handler);
      expect(useUIStore.getState().customEscapeHandler).toBe(handler);
    });

    it('allows setting null handler', () => {
      const handler = () => {};
      useUIStore.getState().setCustomEscapeHandler(handler);
      useUIStore.getState().setCustomEscapeHandler(null);
      expect(useUIStore.getState().customEscapeHandler).toBeNull();
    });
  });

  describe('modal states', () => {
    it('manages confirmModalState', () => {
      useUIStore.getState().setUI({
        confirmModalState: { isOpen: true, title: 'Test', message: 'Confirm?' },
      });
      const state = useUIStore.getState().confirmModalState;
      expect(state.isOpen).toBe(true);
      expect(state.title).toBe('Test');
    });

    it('manages import modal', () => {
      useUIStore.getState().setUI({
        isImportModalOpen: true,
        importTargetFolder: '/test',
        importSourcePaths: ['/img1.jpg'],
      });
      expect(useUIStore.getState().isImportModalOpen).toBe(true);
      expect(useUIStore.getState().importTargetFolder).toBe('/test');
    });
  });
});
