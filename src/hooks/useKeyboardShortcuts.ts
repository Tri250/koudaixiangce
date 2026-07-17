import { useEffect, useRef } from 'react';
import { ImageFile, Panel, ExifOverlay } from '../components/ui/AppProperties';
import { KEYBIND_DEFINITIONS, normalizeCombo } from '../utils/keyboardUtils';
import { useEditorStore } from '../store/useEditorStore';
import { useLibraryStore } from '../store/useLibraryStore';
import { useSettingsStore } from '../store/useSettingsStore';
import { useUIStore } from '../store/useUIStore';
import { useProcessStore } from '../store/useProcessStore';
import { useEditorActions } from './useEditorActions';
import { useLibraryActions } from './useLibraryActions';

interface KeyboardShortcutsProps {
  sortedImageList: Array<ImageFile>;
  handleBackToLibrary(): void;
  handleDeleteSelected(): void;
  handleImageSelect(path: string): void;
  handlePasteFiles(str: string): void;
  handleToggleFullScreen(): void;
  handleZoomChange(zoomValue: number, fitToWindow?: boolean): void;
}

export const useKeyboardShortcuts = ({
  sortedImageList,
  handleBackToLibrary,
  handleDeleteSelected,
  handleImageSelect,
  handlePasteFiles,
  handleToggleFullScreen,
  handleZoomChange,
}: KeyboardShortcutsProps) => {
  const { handleRotate, handleCopyAdjustments, handlePasteAdjustments } = useEditorActions();
  const { handleRate, handleSetColorLabel } = useLibraryActions();

  const sortedListRef = useRef(sortedImageList);
  useEffect(() => {
    sortedListRef.current = sortedImageList;
  }, [sortedImageList]);

  useEffect(() => {
    const getStoreState = () => ({
      editor: useEditorStore.getState(),
      library: useLibraryStore.getState(),
      ui: useUIStore.getState(),
      settings: useSettingsStore.getState(),
      process: useProcessStore.getState(),
    });

    const comboMap = new Map<string, string>();
    const keybinds = useSettingsStore.getState().appSettings?.keybinds;

    for (const def of KEYBIND_DEFINITIONS) {
      const userCombo = keybinds?.[def.action];
      const effective = userCombo && userCombo.length > 0 ? userCombo : def.defaultCombo;
      if (effective) {
        comboMap.set(effective.join('+'), def.action);
      }
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const actions: Record<string, any> = {
      open_image: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !s.editor.selectedImage && s.library.libraryActivePath !== null,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          handleImageSelect(s.library.libraryActivePath!);
        },
      },
      copy_adjustments: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleCopyAdjustments();
        },
      },
      paste_adjustments: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handlePasteAdjustments();
        },
      },
      copy_files: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => s.library.multiSelectedPaths.length > 0,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.process.setProcess({ copiedFilePaths: s.library.multiSelectedPaths });
        },
      },
      paste_files: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handlePasteFiles('copy');
        },
      },
      select_all: {
        shouldFire: () => sortedListRef.current.length > 0,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.library.setLibrary({ multiSelectedPaths: sortedListRef.current.map((f: ImageFile) => f.path) });
          if (!s.editor.selectedImage) {
            s.library.setLibrary({ libraryActivePath: sortedListRef.current[sortedListRef.current.length - 1].path });
          }
        },
      },
      delete_selected: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !s.editor.activeMaskContainerId && !s.editor.activeAiPatchContainerId,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleDeleteSelected();
        },
      },
      preview_prev: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          const currentIndex = sortedListRef.current.findIndex((img) => img.path === s.editor.selectedImage!.path);
          if (currentIndex === -1) return;
          const nextIndex = currentIndex - 1 < 0 ? sortedListRef.current.length - 1 : currentIndex - 1;
          handleImageSelect(sortedListRef.current[nextIndex].path);
        },
      },
      preview_next: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          const currentIndex = sortedListRef.current.findIndex((img) => img.path === s.editor.selectedImage!.path);
          if (currentIndex === -1) return;
          const nextIndex = currentIndex + 1 >= sortedListRef.current.length ? 0 : currentIndex + 1;
          handleImageSelect(sortedListRef.current[nextIndex].path);
        },
      },
      zoom_in_step: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          const dpr = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1;
          const currentPercent =
            s.editor.originalSize?.width > 0 && s.editor.displaySize?.width > 0
              ? (s.editor.displaySize.width * dpr) / s.editor.originalSize.width
              : 1.0;
          handleZoomChange(Math.min(currentPercent + 0.1, 2.0));
        },
      },
      zoom_out_step: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          const dpr = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1;
          const currentPercent =
            s.editor.originalSize?.width > 0 && s.editor.displaySize?.width > 0
              ? (s.editor.displaySize.width * dpr) / s.editor.originalSize.width
              : 1.0;
          handleZoomChange(Math.max(currentPercent - 0.1, 0.1));
        },
      },
      cycle_zoom: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          const dpr = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1;
          const { originalSize, displaySize, baseRenderSize } = s.editor;
          const currentPercent =
            originalSize?.width > 0 && displaySize?.width > 0
              ? Math.round(((displaySize.width * dpr) / originalSize.width) * 100)
              : 100;
          let fitPercent = 100;

          if (originalSize?.width > 0 && baseRenderSize?.width > 0) {
            const originalAspect = originalSize.width / originalSize.height;
            const baseAspect = baseRenderSize.width / baseRenderSize.height;
            fitPercent =
              originalAspect > baseAspect
                ? Math.round(((baseRenderSize.width * dpr) / originalSize.width) * 100)
                : Math.round(((baseRenderSize.height * dpr) / originalSize.height) * 100);
          }

          const doubleFitPercent = fitPercent * 2;
          if (Math.abs(currentPercent - fitPercent) < 5) {
            handleZoomChange(doubleFitPercent < 100 ? doubleFitPercent / 100 : 1.0);
          } else if (Math.abs(currentPercent - doubleFitPercent) < 5 && doubleFitPercent < 100) {
            handleZoomChange(1.0);
          } else {
            handleZoomChange(0, true);
          }
        },
      },
      zoom_in: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          const dpr = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1;
          const currentPercent =
            s.editor.originalSize?.width > 0 && s.editor.displaySize?.width > 0
              ? (s.editor.displaySize.width * dpr) / s.editor.originalSize.width
              : 1.0;
          handleZoomChange(Math.min(currentPercent * 1.2, 2.0));
        },
      },
      zoom_out: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          const dpr = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1;
          const currentPercent =
            s.editor.originalSize?.width > 0 && s.editor.displaySize?.width > 0
              ? (s.editor.displaySize.width * dpr) / s.editor.originalSize.width
              : 1.0;
          handleZoomChange(Math.max(currentPercent / 1.2, 0.1));
        },
      },
      zoom_fit: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleZoomChange(0, true);
        },
      },
      zoom_100: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleZoomChange(1.0);
        },
      },
      rotate_left: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleRotate(-90);
        },
      },
      rotate_right: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleRotate(90);
        },
      },
      undo: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage && s.editor.historyIndex > 0,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.editor.undo();
        },
      },
      redo: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage && s.editor.historyIndex < s.editor.history.length - 1,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.editor.redo();
        },
      },
      toggle_fullscreen: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleToggleFullScreen();
        },
      },
      show_original: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.editor.setEditor({ showOriginal: !s.editor.showOriginal });
        },
      },
      toggle_adjustments: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.ui.setRightPanel(Panel.Adjustments);
        },
      },
      toggle_crop_panel: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.ui.setRightPanel(Panel.Crop);
        },
      },
      toggle_masks: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.ui.setRightPanel(Panel.Masks);
        },
      },
      toggle_ai: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.ui.setRightPanel(Panel.Ai);
        },
      },
      toggle_presets: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.ui.setRightPanel(Panel.Presets);
        },
      },
      toggle_metadata: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.ui.setRightPanel(Panel.Metadata);
        },
      },
      toggle_analytics: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.editor.setEditor({ isWaveformVisible: !s.editor.isWaveformVisible });
        },
      },
      toggle_export: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          s.ui.setRightPanel(Panel.Export);
        },
      },
      toggle_library_exif: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          const current = s.settings.appSettings?.exifOverlay || ExifOverlay.Off;
          const nextState = {
            [ExifOverlay.Off]: ExifOverlay.Hover,
            [ExifOverlay.Hover]: ExifOverlay.Always,
            [ExifOverlay.Always]: ExifOverlay.Off,
          }[current as ExifOverlay];
          s.settings.handleSettingsChange({ ...s.settings.appSettings, exifOverlay: nextState });
        },
      },
      toggle_crop: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) => !!s.editor.selectedImage,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          if (s.ui.activeRightPanel === Panel.Crop) {
            s.editor.setEditor({ isStraightenActive: !s.editor.isStraightenActive });
          } else {
            s.ui.setRightPanel(Panel.Crop);
            s.editor.setEditor({ isStraightenActive: true });
          }
        },
      },
      rate_0: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleRate(0);
        },
      },
      rate_1: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleRate(1);
        },
      },
      rate_2: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleRate(2);
        },
      },
      rate_3: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleRate(3);
        },
      },
      rate_4: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleRate(4);
        },
      },
      rate_5: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleRate(5);
        },
      },
      color_label_none: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleSetColorLabel(null);
        },
      },
      color_label_red: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleSetColorLabel('red');
        },
      },
      color_label_yellow: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleSetColorLabel('yellow');
        },
      },
      color_label_green: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleSetColorLabel('green');
        },
      },
      color_label_blue: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleSetColorLabel('blue');
        },
      },
      color_label_purple: {
        shouldFire: () => true,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any) => {
          e.preventDefault();
          handleSetColorLabel('purple');
        },
      },
      brush_size_up: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) =>
          !!s.editor.selectedImage && !!s.editor.brushSettings && s.ui.activeRightPanel === Panel.Masks,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          const newSize = Math.min((s.editor.brushSettings.size || 50) + 10, 200);
          s.editor.setEditor({ brushSettings: { ...s.editor.brushSettings, size: newSize } });
        },
      },
      brush_size_down: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        shouldFire: (s: any) =>
          !!s.editor.selectedImage && !!s.editor.brushSettings && s.ui.activeRightPanel === Panel.Masks,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: any, s: any) => {
          e.preventDefault();
          const newSize = Math.max((s.editor.brushSettings.size || 50) - 10, 1);
          s.editor.setEditor({ brushSettings: { ...s.editor.brushSettings, size: newSize } });
        },
      },
    };

    const builtinShortcuts = [
      {
        match: (e: KeyboardEvent) => e.code === 'Escape',
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: KeyboardEvent, s: any) => {
          e.preventDefault();
          if (s.editor.isStraightenActive) s.editor.setEditor({ isStraightenActive: false });
          else if (s.ui.customEscapeHandler) s.ui.customEscapeHandler();
          else if (s.editor.activeAiSubMaskId) s.editor.setEditor({ activeAiSubMaskId: null });
          else if (s.editor.activeAiPatchContainerId) s.editor.setEditor({ activeAiPatchContainerId: null });
          else if (s.editor.activeMaskId) s.editor.setEditor({ activeMaskId: null });
          else if (s.editor.activeMaskContainerId) s.editor.setEditor({ activeMaskContainerId: null });
          else if (s.ui.activeRightPanel === Panel.Crop) s.ui.setRightPanel(Panel.Adjustments);
          else if (s.ui.isFullScreen) handleToggleFullScreen();
          else if (s.editor.selectedImage) handleBackToLibrary();
        },
      },
      {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        match: (e: KeyboardEvent, s: any) => {
          const isDeleteKey = s.settings.osPlatform === 'macos' ? e.code === 'Backspace' : e.code === 'Delete';
          return isDeleteKey && (!!s.editor.activeMaskContainerId || !!s.editor.activeAiPatchContainerId);
        },
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: KeyboardEvent, s: any) => {
          e.preventDefault();
          if (s.editor.activeMaskContainerId) {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            s.editor.setEditor((state: any) => ({
              adjustments: {
                ...state.adjustments,
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                masks: state.adjustments.masks.filter((c: any) => c.id !== s.editor.activeMaskContainerId),
              },
              activeMaskContainerId: null,
              activeMaskId: null,
            }));
          } else if (s.editor.activeAiPatchContainerId) {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            s.editor.setEditor((state: any) => ({
              adjustments: {
                ...state.adjustments,
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                aiPatches: state.adjustments.aiPatches.filter((c: any) => c.id !== s.editor.activeAiPatchContainerId),
              },
              activeAiPatchContainerId: null,
              activeAiSubMaskId: null,
            }));
          }
        },
      },
      {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        match: (e: KeyboardEvent, s: any) =>
          !s.editor.selectedImage && ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.code),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        execute: (e: KeyboardEvent, s: any) => {
          e.preventDefault();
          const isNext = e.code === 'ArrowRight' || e.code === 'ArrowDown';
          const activePath = s.library.libraryActivePath;
          if (!activePath || sortedListRef.current.length === 0) return;
          const currentIndex = sortedListRef.current.findIndex((img) => img.path === activePath);
          if (currentIndex === -1) return;
          let nextIndex = isNext ? currentIndex + 1 : currentIndex - 1;
          if (nextIndex >= sortedListRef.current.length) nextIndex = 0;
          if (nextIndex < 0) nextIndex = sortedListRef.current.length - 1;
          const nextImage = sortedListRef.current[nextIndex];
          if (nextImage) {
            s.library.setLibrary({ libraryActivePath: nextImage.path, multiSelectedPaths: [nextImage.path] });
          }
        },
      },
    ];

    const handleKeyDown = (event: KeyboardEvent) => {
      const state = getStoreState();

      const isModalOpen =
        state.ui.isCreateFolderModalOpen ||
        state.ui.isRenameFolderModalOpen ||
        state.ui.isRenameFileModalOpen ||
        state.ui.isImportModalOpen ||
        state.ui.isCopyPasteSettingsModalOpen ||
        state.ui.confirmModalState.isOpen ||
        state.ui.panoramaModalState.isOpen ||
        state.ui.cullingModalState.isOpen ||
        state.ui.collageModalState.isOpen ||
        state.ui.denoiseModalState.isOpen ||
        state.ui.negativeModalState.isOpen;

      if (isModalOpen) return;

      const isInputFocused =
        document.activeElement?.tagName === 'INPUT' || document.activeElement?.tagName === 'TEXTAREA';
      if (isInputFocused) return;

      for (const builtin of builtinShortcuts) {
        if (builtin.match(event, state)) {
          builtin.execute(event, state);
          return;
        }
      }

      const normalized = normalizeCombo(event, state.settings.osPlatform);
      const action = comboMap.get(normalized.join('+'));

      if (action) {
        const handler = actions[action];
        if (handler && (!handler.shouldFire || handler.shouldFire(state))) {
          handler.execute(event, state);
          return;
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [
    handleBackToLibrary,
    handleDeleteSelected,
    handleImageSelect,
    handlePasteFiles,
    handleToggleFullScreen,
    handleZoomChange,
    handleRotate,
    handleCopyAdjustments,
    handlePasteAdjustments,
    handleRate,
    handleSetColorLabel,
  ]);
};
