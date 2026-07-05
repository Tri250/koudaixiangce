import { useEffect, useRef } from 'react';
import { listen } from '@tauri-apps/api/event';
import { invoke } from '@tauri-apps/api/core';
import { exit } from '@tauri-apps/plugin-process';
import { toast } from 'react-toastify';
import { Status } from '../components/ui/ExportImportProperties';
import { useProcessStore } from '../store/useProcessStore';
import { useEditorStore } from '../store/useEditorStore';
import { useUIStore } from '../store/useUIStore';
import { useLibraryStore } from '../store/useLibraryStore';
import i18n from '../i18n';
import { debouncedSave } from './useEditorActions';

interface TauriListenerProps {
  refreshAllFolderTrees: () => void;
  handleSelectSubfolder: (path: string, isNewRoot?: boolean, preloadedImages?: any[], expandParents?: boolean) => void;
  refreshImageList: () => void;
  markGenerated: (path: string) => void;
  handleBackToLibrary: () => void;
}

export function useTauriListeners({
  refreshAllFolderTrees,
  handleSelectSubfolder,
  refreshImageList,
  markGenerated,
  handleBackToLibrary,
}: TauriListenerProps) {
  const refs = useRef({ refreshAllFolderTrees, handleSelectSubfolder, refreshImageList, markGenerated, handleBackToLibrary });

  useEffect(() => {
    refs.current = { refreshAllFolderTrees, handleSelectSubfolder, refreshImageList, markGenerated, handleBackToLibrary };
  });

  const thumbnailBuffer = useRef<Record<string, string>>({});
  const ratingBuffer = useRef<Record<string, number>>({});
  const editStatusBuffer = useRef<Record<string, boolean>>({});
  const flushHandle = useRef<number | null>(null);

  useEffect(() => {
    let isEffectActive = true;

    const flushThumbnailBatch = () => {
      flushHandle.current = null;
      if (!isEffectActive) return;

      const pendingThumbs = thumbnailBuffer.current;
      const pendingRatings = ratingBuffer.current;
      const pendingEdits = editStatusBuffer.current;

      thumbnailBuffer.current = {};
      ratingBuffer.current = {};
      editStatusBuffer.current = {};

      if (Object.keys(pendingThumbs).length > 0) {
        useProcessStore.getState().setProcess((state) => ({
          thumbnails: { ...state.thumbnails, ...pendingThumbs },
        }));
      }

      if (Object.keys(pendingRatings).length > 0 || Object.keys(pendingEdits).length > 0) {
        useLibraryStore.getState().setLibrary((state) => ({
          imageRatings: { ...state.imageRatings, ...pendingRatings },
          imageList:
            Object.keys(pendingEdits).length > 0
              ? state.imageList.map((img) =>
                  pendingEdits[img.path] !== undefined ? { ...img, is_edited: pendingEdits[img.path] } : img,
                )
              : state.imageList,
        }));
      }
    };

    const scheduleFlush = () => {
      if (flushHandle.current !== null) return;
      flushHandle.current = requestAnimationFrame(flushThumbnailBatch);
    };

    const listeners = [
      listen('preview-update-uncropped', (event: any) => {
        if (isEffectActive) useEditorStore.getState().setEditor({ uncroppedAdjustedPreviewUrl: event.payload });
      }),
      listen('histogram-update', (event: any) => {
        if (isEffectActive && event.payload.path === useEditorStore.getState().selectedImage?.path) {
          useEditorStore.getState().setEditor({ histogram: event.payload.data });
        }
      }),
      listen('open-with-file', (event: any) => {
        if (isEffectActive) useProcessStore.getState().setProcess({ initialFileToOpen: event.payload as string });
      }),
      listen('external-edit-session', (event: any) => {
        if (isEffectActive) useProcessStore.getState().setProcess({ externalEditSession: event.payload });
      }),
      listen('waveform-update', (event: any) => {
        if (isEffectActive && event.payload.path === useEditorStore.getState().selectedImage?.path) {
          useEditorStore.getState().setEditor({ waveform: event.payload.data });
        }
      }),
      listen('thumbnail-progress', (event: any) => {
        if (isEffectActive)
          useProcessStore
            .getState()
            .setProcess({ thumbnailProgress: { current: event.payload.current, total: event.payload.total } });
      }),
      listen('thumbnail-generation-complete', () => {
        if (isEffectActive) useProcessStore.getState().setProcess({ thumbnailProgress: { current: 0, total: 0 } });
      }),
      listen('thumbnail-generated', (event: any) => {
        if (!isEffectActive) return;
        const { path, data, rating, is_edited } = event.payload;

        if (data) {
          thumbnailBuffer.current[path] = data;
          refs.current.markGenerated(path);
        }
        if (rating !== undefined) {
          ratingBuffer.current[path] = rating;
        }
        if (is_edited !== undefined) {
          editStatusBuffer.current[path] = is_edited;
        }
        if (data || rating !== undefined || is_edited !== undefined) {
          scheduleFlush();
        }
      }),
      listen('ai-model-download-start', (event: any) => {
        if (isEffectActive) useProcessStore.getState().setProcess({ aiModelDownloadStatus: event.payload });
      }),
      listen('ai-model-download-finish', () => {
        if (isEffectActive) useProcessStore.getState().setProcess({ aiModelDownloadStatus: null });
      }),
      listen('indexing-started', () => {
        if (isEffectActive)
          useProcessStore.getState().setProcess({ isIndexing: true, indexingProgress: { current: 0, total: 0 } });
      }),
      listen('indexing-progress', (event: any) => {
        if (isEffectActive) useProcessStore.getState().setProcess({ indexingProgress: event.payload });
      }),
      listen('indexing-finished', () => {
        if (isEffectActive) {
          useProcessStore.getState().setProcess({ isIndexing: false, indexingProgress: { current: 0, total: 0 } });
          const currentPath = useLibraryStore.getState().currentFolderPath;
          if (currentPath) {
            refs.current.refreshImageList();
          }
        }
      }),
      listen('batch-export-progress', (event: any) => {
        if (isEffectActive) useProcessStore.getState().setExportState({ progress: event.payload });
      }),
      listen('export-complete', () => {
        if (isEffectActive) useProcessStore.getState().setExportState({ status: Status.Success });
      }),
      listen('export-error', (event: any) => {
        if (isEffectActive)
          useProcessStore.getState().setExportState({
            status: Status.Error,
            errorMessage: typeof event.payload === 'string' ? event.payload : 'Unknown error',
          });
      }),
      listen('export-cancelled', () => {
        if (isEffectActive) useProcessStore.getState().setExportState({ status: Status.Cancelled });
      }),
      listen('import-start', (event: any) => {
        if (isEffectActive)
          useProcessStore.getState().setImportState({
            errorMessage: '',
            path: '',
            progress: { current: 0, total: event.payload.total },
            status: Status.Importing,
          });
      }),
      listen('import-progress', (event: any) => {
        if (isEffectActive)
          useProcessStore.getState().setImportState({
            path: event.payload.path,
            progress: { current: event.payload.current, total: event.payload.total },
          });
      }),
      listen('import-complete', () => {
        if (isEffectActive) {
          useProcessStore.getState().setImportState({ status: Status.Success });
          refs.current.refreshAllFolderTrees();
          const currentPath = useLibraryStore.getState().currentFolderPath;
          if (currentPath) {
            refs.current.handleSelectSubfolder(currentPath, false);
          }
        }
      }),
      listen('import-error', (event: any) => {
        if (isEffectActive)
          useProcessStore.getState().setImportState({
            status: Status.Error,
            errorMessage: typeof event.payload === 'string' ? event.payload : 'Unknown error',
          });
      }),
      listen('denoise-progress', (event: any) => {
        if (isEffectActive)
          useUIStore.getState().setUI((state) => ({
            denoiseModalState: { ...state.denoiseModalState, progressMessage: event.payload as string },
          }));
      }),
      listen('denoise-complete', (event: any) => {
        if (isEffectActive) {
          const payload = event.payload;
          const isObject = typeof payload === 'object' && payload !== null;
          useUIStore.getState().setUI((state) => ({
            denoiseModalState: {
              ...state.denoiseModalState,
              isProcessing: false,
              previewBase64: isObject ? payload.denoised : payload,
              originalBase64: isObject ? payload.original : null,
              progressMessage: null,
            },
          }));
        }
      }),
      listen('denoise-error', (event: any) => {
        if (isEffectActive) {
          useUIStore.getState().setUI((state) => ({
            denoiseModalState: {
              ...state.denoiseModalState,
              isProcessing: false,
              error: String(event.payload),
              progressMessage: null,
            },
          }));
        }
      }),
      listen('wgpu-frame-ready', (event: any) => {
        if (isEffectActive && event.payload?.path === useEditorStore.getState().selectedImage?.path) {
          useEditorStore.getState().setEditor({ hasRenderedFirstFrame: true });
        }
      }),
      listen('panorama-progress', (event: any) => {
        if (isEffectActive) {
          useUIStore.getState().setUI((state) => {
            if (state.panoramaModalState.finalImageBase64 || state.panoramaModalState.error) return state;
            return { panoramaModalState: { ...state.panoramaModalState, progressMessage: event.payload } };
          });
        }
      }),
      listen('panorama-complete', (event: any) => {
        if (isEffectActive) {
          useUIStore.getState().setUI((state) => ({
            panoramaModalState: {
              ...state.panoramaModalState,
              error: null,
              finalImageBase64: event.payload.base64,
              isProcessing: false,
              progressMessage: null,
            },
          }));
        }
      }),
      listen('panorama-error', (event: any) => {
        if (isEffectActive) {
          useUIStore.getState().setUI((state) => ({
            panoramaModalState: {
              ...state.panoramaModalState,
              error: String(event.payload),
              finalImageBase64: null,
              isProcessing: false,
              progressMessage: null,
            },
          }));
        }
      }),
      listen('hdr-progress', (event: any) => {
        if (isEffectActive) {
          useUIStore.getState().setUI((state) => ({
            hdrModalState: {
              ...state.hdrModalState,
              error: null,
              finalImageBase64: null,
              isOpen: true,
              progressMessage: event.payload,
            },
          }));
        }
      }),
      listen('hdr-complete', (event: any) => {
        if (isEffectActive) {
          useUIStore.getState().setUI((state) => ({
            hdrModalState: {
              ...state.hdrModalState,
              error: null,
              finalImageBase64: event.payload.base64,
              isProcessing: false,
              progressMessage: 'Hdr Ready',
            },
          }));
        }
      }),
      listen('hdr-error', (event: any) => {
        if (isEffectActive) {
          useUIStore.getState().setUI((state) => ({
            hdrModalState: {
              ...state.hdrModalState,
              error: String(event.payload),
              finalImageBase64: null,
              isProcessing: false,
              progressMessage: 'An error occurred.',
            },
          }));
        }
      }),
      listen('culling-start', (event: any) => {
        if (isEffectActive) {
          useUIStore.getState().setUI((state) => ({
            cullingModalState: {
              ...state.cullingModalState,
              isOpen: true,
              progress: { current: 0, total: event.payload, stage: 'Initializing...' },
              suggestions: null,
              error: null,
            },
          }));
        }
      }),
      listen('culling-progress', (event: any) => {
        if (isEffectActive) {
          useUIStore
            .getState()
            .setUI((state) => ({ cullingModalState: { ...state.cullingModalState, progress: event.payload } }));
        }
      }),
      listen('culling-complete', (event: any) => {
        if (isEffectActive) {
          useUIStore.getState().setUI((state) => ({
            cullingModalState: { ...state.cullingModalState, progress: null, suggestions: event.payload },
          }));
        }
      }),
      listen('culling-error', (event: any) => {
        if (isEffectActive) {
          useUIStore.getState().setUI((state) => ({
            cullingModalState: { ...state.cullingModalState, progress: null, error: String(event.payload) },
          }));
        }
      }),
    ];

    // --- H2a: Android hardware back button ---
    //
    // MainActivity.kt intercepts the hardware back press and dispatches a
    // custom `android-back-press` DOM event (instead of finishing the
    // activity). Here we decide what "back" means based on the current UI
    // state, in priority order: close overlay → leave editor → leave
    // community → exit app (double-press).
    let lastBackPressTimestamp = 0;
    const BACK_PRESS_EXIT_WINDOW_MS = 2000;

    const anyOverlayOpen = (uiState: ReturnType<typeof useUIStore.getState>) => {
      return (
        uiState.confirmModalState.isOpen ||
        uiState.panoramaModalState.isOpen ||
        uiState.hdrModalState.isOpen ||
        uiState.negativeModalState.isOpen ||
        uiState.denoiseModalState.isOpen ||
        uiState.cullingModalState.isOpen ||
        uiState.collageModalState.isOpen ||
        uiState.isCreateFolderModalOpen ||
        uiState.isRenameFolderModalOpen ||
        uiState.isRenameFileModalOpen ||
        uiState.isImportModalOpen ||
        uiState.isCopyPasteSettingsModalOpen ||
        uiState.isCreateAlbumModalOpen ||
        uiState.isCreateAlbumGroupModalOpen ||
        uiState.isRenameAlbumModalOpen ||
        uiState.isLibraryExportPanelVisible
      );
    };

    const handleBackPress = () => {
      if (!isEffectActive) return;

      const uiState = useUIStore.getState();
      const editorState = useEditorStore.getState();

      // 1. Close any open context menu. The ContextMenuContext listens for
      //    Escape on document — dispatching a synthetic one hides it.
      if (document.querySelector('[role="menu"]')) {
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        return;
      }

      // 2. Close any open modal/panel the same way (most modals handle Escape
      //    to dismiss themselves).
      if (anyOverlayOpen(uiState)) {
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        return;
      }

      // 3. In the editor — go back to the library (this also flushes any
      //    pending debounced save via handleBackToLibrary).
      if (editorState.selectedImage) {
        refs.current.handleBackToLibrary();
        return;
      }

      // 4. On a secondary library view (e.g. community presets) — return to
      //    the main library.
      if (uiState.activeView !== 'library') {
        useUIStore.getState().setUI({ activeView: 'library' });
        return;
      }

      // 5. Already on the main library — require a second press within the
      //    window to actually exit, so a stray tap doesn't kill the app.
      const now = Date.now();
      if (now - lastBackPressTimestamp < BACK_PRESS_EXIT_WINDOW_MS) {
        exit(0);
        return;
      }
      lastBackPressTimestamp = now;
      toast.info(i18n.t('android.pressBackAgainToExit'));
    };

    window.addEventListener('android-back-press', handleBackPress);

    // --- H2b: App lifecycle (pause/resume) ---
    //
    // On Android, `visibilitychange` (and pagehide/pageshow) map to
    // onPause/onResume. We flush pending edits and cancel heavy background
    // work on pause, then refresh the library on resume so any external file
    // changes are picked up. The `isPaused` guard prevents double-handling
    // when multiple events fire for the same transition.
    let isPaused = false;
    let pausedAt: number | null = null;
    const RESUME_REFRESH_THRESHOLD_MS = 5000;

    const performPause = () => {
      if (isPaused) return;
      isPaused = true;
      pausedAt = Date.now();

      // Persist any pending debounced edits before the OS may kill us.
      try {
        debouncedSave.flush();
      } catch (err) {
        console.error('Failed to flush debounced save on pause:', err);
      }

      // Stop heavy background thumbnail work so the system doesn't waste
      // CPU/battery while we're not visible.
      invoke('cancel_thumbnail_generation').catch((err) => {
        console.error('Failed to cancel thumbnail generation on pause:', err);
      });
    };

    const performResume = () => {
      if (!isPaused) return;
      isPaused = false;

      const pausedDuration = pausedAt !== null ? Date.now() - pausedAt : 0;
      pausedAt = null;

      // Only trigger a library reload for pauses longer than the threshold —
      // brief overlays (notifications, permission dialogs) shouldn't force a
      // full refresh.
      if (pausedDuration >= RESUME_REFRESH_THRESHOLD_MS) {
        const currentPath = useLibraryStore.getState().currentFolderPath;
        if (currentPath) {
          refs.current.refreshImageList();
        }
      }
    };

    const handleVisibilityChange = () => {
      if (!isEffectActive) return;
      if (document.hidden) {
        performPause();
      } else {
        performResume();
      }
    };

    const handlePageHide = () => {
      if (!isEffectActive) return;
      // `pagehide` can fire on Android when the activity is being stopped or
      // destroyed. Treat it as a pause so edits are always flushed.
      performPause();
    };

    const handlePageShow = () => {
      if (!isEffectActive) return;
      performResume();
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('pagehide', handlePageHide);
    window.addEventListener('pageshow', handlePageShow);

    return () => {
      isEffectActive = false;
      if (flushHandle.current !== null) {
        cancelAnimationFrame(flushHandle.current);
        flushHandle.current = null;
      }
      thumbnailBuffer.current = {};
      ratingBuffer.current = {};
      listeners.forEach((p) => p.then((unlisten) => unlisten()));
      window.removeEventListener('android-back-press', handleBackPress);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('pagehide', handlePageHide);
      window.removeEventListener('pageshow', handlePageShow);
    };
  }, []);
}
