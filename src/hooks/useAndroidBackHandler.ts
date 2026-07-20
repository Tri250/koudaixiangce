import { useEffect } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { useUIStore } from '../store/useUIStore';
import { useSettingsStore } from '../store/useSettingsStore';
import { useEditorStore } from '../store/useEditorStore';
import { useLibraryStore } from '../store/useLibraryStore';
import { Invokes } from '../components/ui/AppProperties';

export function useAndroidBackHandler() {
  useEffect(() => {
    const osPlatform = useSettingsStore.getState().osPlatform;
    if (osPlatform !== 'android') return;

    (window as any).__handleAndroidBack = () => {
      const ui = useUIStore.getState();
      const editor = useEditorStore.getState();
      const library = useLibraryStore.getState();

      // Priority 1: Close any open modal
      if (ui.confirmModalState.isOpen) {
        ui.setUI((state: any) => ({ confirmModalState: { ...state.confirmModalState, isOpen: false } }));
        return;
      }
      if (ui.isCreateFolderModalOpen) {
        ui.setUI({ isCreateFolderModalOpen: false });
        return;
      }
      if (ui.isRenameFolderModalOpen) {
        ui.setUI({ isRenameFolderModalOpen: false });
        return;
      }
      if (ui.isRenameFileModalOpen) {
        ui.setUI({ isRenameFileModalOpen: false });
        return;
      }
      if (ui.isImportModalOpen) {
        ui.setUI({ isImportModalOpen: false });
        return;
      }
      if (ui.isCopyPasteSettingsModalOpen) {
        ui.setUI({ isCopyPasteSettingsModalOpen: false });
        return;
      }
      if (ui.isCreateAlbumModalOpen) {
        ui.setUI({ isCreateAlbumModalOpen: false });
        return;
      }
      if (ui.isCreateAlbumGroupModalOpen) {
        ui.setUI({ isCreateAlbumGroupModalOpen: false });
        return;
      }
      if (ui.isRenameAlbumModalOpen) {
        ui.setUI({ isRenameAlbumModalOpen: false });
        return;
      }
      if (ui.panoramaModalState.isOpen) {
        ui.setUI({
          panoramaModalState: {
            isOpen: false,
            isProcessing: false,
            progressMessage: '',
            finalImageBase64: null,
            error: null,
            stitchingSourcePaths: [],
          },
        });
        return;
      }
      if (ui.hdrModalState.isOpen) {
        ui.setUI({
          hdrModalState: {
            isOpen: false,
            isProcessing: false,
            progressMessage: '',
            finalImageBase64: null,
            error: null,
            stitchingSourcePaths: [],
          },
        });
        return;
      }
      if (ui.negativeModalState.isOpen) {
        ui.setUI((state: any) => ({ negativeModalState: { ...state.negativeModalState, isOpen: false } }));
        return;
      }
      if (ui.denoiseModalState.isOpen) {
        ui.setUI((state: any) => ({ denoiseModalState: { ...state.denoiseModalState, isOpen: false } }));
        return;
      }
      if (ui.cullingModalState.isOpen) {
        ui.setUI({
          cullingModalState: { isOpen: false, progress: null, suggestions: null, error: null, pathsToCull: [] },
        });
        return;
      }
      if (ui.collageModalState.isOpen) {
        ui.setUI({ collageModalState: { isOpen: false, sourceImages: [] } });
        return;
      }
      if (editor.isLiquifyModalOpen) {
        editor.setEditor({ isLiquifyModalOpen: false });
        return;
      }

      // Priority 2: Exit special editor modes
      if (editor.isStraightenActive) {
        editor.setEditor({ isStraightenActive: false });
        return;
      }
      if (editor.isWbPickerActive) {
        editor.setEditor({ isWbPickerActive: false });
        return;
      }
      if (editor.isRotationActive) {
        editor.setEditor({ isRotationActive: false });
        return;
      }

      // Priority 3: Close right panel if open in editor
      if (ui.activeRightPanel !== null) {
        ui.setUI({ activeRightPanel: null });
        return;
      }

      // Priority 4: Exit full screen mode
      if (ui.isFullScreen) {
        ui.setUI({ isFullScreen: false });
        return;
      }

      // Priority 5: If in editor view with a selected image, go back to library
      if (library.libraryActivePath && editor.selectedImage?.path) {
        // Dispatch Escape to trigger the existing back-to-library flow
        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true, cancelable: true }));
        return;
      }

      // Fallback: dispatch Escape for any remaining cases
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true, cancelable: true }));
    };

    // State serialization for Android process death recovery
    (window as any).__getAndroidState = () => {
      const { selectedImage, adjustments } = useEditorStore.getState();
      const { libraryActivePath } = useLibraryStore.getState();
      const { activeRightPanel, isFullScreen } = useUIStore.getState();
      return JSON.stringify({
        selectedImagePath: selectedImage?.path || null,
        libraryActivePath,
        activeRightPanel,
        isFullScreen,
      });
    };

    (window as any).__restoreAndroidState = (stateJson: any) => {
      try {
        const state = typeof stateJson === 'string' ? JSON.parse(stateJson) : stateJson;
        if (state.libraryActivePath) {
          useLibraryStore.getState().setLibrary({ libraryActivePath: state.libraryActivePath });
        }
        if (state.isFullScreen !== undefined) {
          useUIStore.getState().setUI({ isFullScreen: state.isFullScreen });
        }
        if (state.activeRightPanel !== undefined) {
          useUIStore.getState().setUI({ activeRightPanel: state.activeRightPanel });
        }
        // Note: selectedImage will be re-loaded from the libraryActivePath
        // after the app initializes, so we don't need to manually restore it.
      } catch (e) {
        console.error('Failed to restore Android state:', e);
      }
    };

    // Memory pressure handler — called from Android onTrimMemory / onLowMemory
    (window as any).__onAndroidMemoryPressure = (level: string) => {
      // Use dynamic import to avoid circular dependency
      import('../utils/ImageLRUCache').then(({ globalImageCache }) => {
        if (level === 'critical') {
          globalImageCache.clear();
          console.info('[Android] Critical memory pressure — image cache cleared');
        } else if (level === 'low') {
          const currentSize = globalImageCache.size();
          const targetSize = Math.max(1, Math.floor(currentSize / 2));
          while (globalImageCache.size() > targetSize) {
            const oldestKey = globalImageCache.getOldestKey();
            if (oldestKey) globalImageCache.delete(oldestKey);
            else break;
          }
          console.info('[Android] Low memory pressure — cache reduced');
        }
      }).catch(() => {});
    };

    // Flush sidecar data to disk — called from Android lifecycle events (onPause/onStop/onSaveInstanceState)
    (window as any).__flushSidecar = () => {
      const { selectedImage, adjustments } = useEditorStore.getState();
      if (selectedImage?.path) {
        invoke(Invokes.SaveMetadataAndUpdateThumbnail, { path: selectedImage.path, adjustments }).catch(
          (err: any) => console.error('Sidecar flush failed:', err),
        );
      }
    };

    // Periodic sidecar flush every 30 seconds on Android to guard against process kills
    const periodicFlushInterval = setInterval(() => {
      const { selectedImage, adjustments } = useEditorStore.getState();
      if (selectedImage?.path) {
        invoke(Invokes.SaveMetadataAndUpdateThumbnail, { path: selectedImage.path, adjustments }).catch(
          (err: any) => console.error('Periodic sidecar flush failed:', err),
        );
      }
    }, 30000);

    return () => {
      delete (window as any).__handleAndroidBack;
      delete (window as any).__flushSidecar;
      delete (window as any).__getAndroidState;
      delete (window as any).__restoreAndroidState;
      delete (window as any).__onAndroidMemoryPressure;
      clearInterval(periodicFlushInterval);
    };
  }, []);
}
