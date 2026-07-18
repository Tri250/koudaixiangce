import { useEffect } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { useUIStore } from '../store/useUIStore';
import { useSettingsStore } from '../store/useSettingsStore';
import { useEditorStore } from '../store/useEditorStore';
import { Invokes } from '../components/ui/AppProperties';

export function useAndroidBackHandler() {
  useEffect(() => {
    const osPlatform = useSettingsStore.getState().osPlatform;
    if (osPlatform !== 'android') return;

    (window as any).__handleAndroidBack = () => {
      const ui = useUIStore.getState();

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

      // Close right panel if open in editor
      if (ui.activeRightPanel !== null) {
        ui.setUI({ activeRightPanel: null });
        return;
      }

      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true, cancelable: true }));
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
      clearInterval(periodicFlushInterval);
    };
  }, []);
}
