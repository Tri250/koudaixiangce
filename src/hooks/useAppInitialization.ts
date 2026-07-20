import { useEffect, useRef } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { useShallow } from 'zustand/react/shallow';
import { useSettingsStore } from '../store/useSettingsStore';
import { useUIStore } from '../store/useUIStore';
import { useLibraryStore } from '../store/useLibraryStore';
import { useEditorStore } from '../store/useEditorStore';
import { useProcessStore } from '../store/useProcessStore';
import { THEMES, DEFAULT_THEME_ID, ThemeProps } from '../utils/themes';
import { COPYABLE_ADJUSTMENT_KEYS } from '../utils/adjustments';
import {
  FilterCriteria,
  Invokes,
  LibraryViewMode,
  RawStatus,
  EditedStatus,
  Theme,
  ThumbnailSize,
  ThumbnailAspectRatio,
  SupportedTypes,
  AppSettings,
} from '../components/ui/AppProperties';
import type { FolderTree } from '../components/panel/FolderTree';
import { useTranslation } from 'react-i18next';
import type { ExternalEditSession } from '../store/useProcessStore';

interface UseAppInitializationProps {
  preloadedDataRef: React.RefObject<unknown>;
  thumbnailSize: ThumbnailSize;
  setThumbnailSize: (size: ThumbnailSize) => void;
  thumbnailAspectRatio: ThumbnailAspectRatio;
  setThumbnailAspectRatio: (ratio: ThumbnailAspectRatio) => void;
  libraryViewMode: LibraryViewMode;
  setLibraryViewMode: (mode: LibraryViewMode) => void;
}

const getDefaultLanguage = (i18nInstance: { language?: string; options?: Record<string, unknown> }): string => {
  const browserLang = navigator.language || (navigator as unknown as Record<string, unknown>).userLanguage as string || 'en';
  const shortLang = browserLang.split('-')[0].toLowerCase();
  const supportedLanguages = Object.keys((i18nInstance.options as Record<string, unknown>)?.resources as Record<string, unknown> || {});
  const fallbackLng = (i18nInstance.options as Record<string, unknown>)?.fallbackLng;
  const fallbackLang =
    typeof fallbackLng === 'string'
      ? fallbackLng
      : Array.isArray(fallbackLng) ? fallbackLng[0] as string || 'en' : 'en';

  return supportedLanguages.includes(browserLang)
    ? browserLang
    : supportedLanguages.includes(shortLang)
      ? shortLang
      : fallbackLang;
};

export const useAppInitialization = ({
  preloadedDataRef,
  thumbnailSize,
  setThumbnailSize,
  thumbnailAspectRatio,
  setThumbnailAspectRatio,
  libraryViewMode,
  setLibraryViewMode,
}: UseAppInitializationProps) => {
  const isInitialMount = useRef(true);
  const { i18n } = useTranslation();

  const {
    appSettings,
    theme,
    osPlatform,
    setAppSettings,
    setTheme,
    setSupportedTypes,
    initPlatform,
    handleSettingsChange,
  } = useSettingsStore(
    useShallow((state) => ({
      appSettings: state.appSettings,
      theme: state.theme,
      osPlatform: state.osPlatform,
      setAppSettings: state.setAppSettings,
      setTheme: state.setTheme,
      setSupportedTypes: state.setSupportedTypes,
      initPlatform: state.initPlatform,
      handleSettingsChange: state.handleSettingsChange,
    })),
  );

  const { uiVisibility, setUI } = useUIStore(
    useShallow((state) => ({
      uiVisibility: state.uiVisibility,
      setUI: state.setUI,
    })),
  );

  const {
    sortCriteria,
    filterCriteria,
    currentFolderPath,
    expandedFolders,
    activeAlbumId,
    expandedAlbumGroups,
    setSortCriteria,
    setFilterCriteria,
    setLibrary,
  } = useLibraryStore(
    useShallow((state) => ({
      sortCriteria: state.sortCriteria,
      filterCriteria: state.filterCriteria,
      currentFolderPath: state.currentFolderPath,
      expandedFolders: state.expandedFolders,
      activeAlbumId: state.activeAlbumId,
      expandedAlbumGroups: state.expandedAlbumGroups,
      setSortCriteria: state.setSortCriteria,
      setFilterCriteria: state.setFilterCriteria,
      setLibrary: state.setLibrary,
    })),
  );

  const { setEditor } = useEditorStore(
    useShallow((state) => ({
      setEditor: state.setEditor,
    })),
  );

  const isAndroid = osPlatform === 'android';
  const defaultThumbnailSize = isAndroid ? ThumbnailSize.Small : ThumbnailSize.Medium;
  const defaultLibraryViewMode = isAndroid ? LibraryViewMode.Recursive : LibraryViewMode.Flat;
  const prevImageCountsNeed = useRef<boolean | undefined>(undefined);

  useEffect(() => {
    initPlatform();
  }, [initPlatform]);

  useEffect(() => {
    invoke<unknown>(Invokes.GetSupportedFileTypes)
      .then((types) => setSupportedTypes(types as SupportedTypes))
      .catch((err) => console.error('Failed to load supported file types:', err));
  }, [setSupportedTypes]);

  useEffect(() => {
    invoke<unknown>(Invokes.LoadSettings)
      .then(async (settingsRaw) => {
        const settings = settingsRaw as AppSettings & Record<string, unknown>;
        if (
          !settings.copyPasteSettings ||
          !settings.copyPasteSettings.includedAdjustments ||
          settings.copyPasteSettings.includedAdjustments.length === 0
        ) {
          settings.copyPasteSettings = { mode: 'merge', includedAdjustments: COPYABLE_ADJUSTMENT_KEYS };
        }

        if (!settings.language) {
          settings.language = getDefaultLanguage(i18n);
          handleSettingsChange(settings as AppSettings);
        }

        setAppSettings(settings as AppSettings);
        i18n.changeLanguage(settings.language as string);

        if (settings?.sortCriteria) setSortCriteria(settings.sortCriteria as import('../components/ui/AppProperties').SortCriteria);

        if (settings?.filterCriteria) {
          const fc = settings.filterCriteria as Record<string, unknown>;
          setFilterCriteria((prev: FilterCriteria) => ({
            ...prev,
            ...fc,
            rawStatus: (fc.rawStatus as RawStatus) || RawStatus.All,
            editedStatus: (fc.editedStatus as EditedStatus) || EditedStatus.All,
            colors: (fc.colors as string[]) || [],
          }));
        }

        if (settings?.theme) setTheme(settings.theme as Theme);

        if (settings?.uiVisibility)
          setUI((state) => ({ uiVisibility: { ...state.uiVisibility, ...(settings.uiVisibility as Record<string, unknown>) } }));

        if (settings?.isWaveformVisible != null) setEditor({ isWaveformVisible: settings.isWaveformVisible as boolean });
        if (settings?.activeWaveformChannel) setEditor({ activeWaveformChannel: settings.activeWaveformChannel as string });
        if (typeof settings?.waveformHeight === 'number') setEditor({ waveformHeight: settings.waveformHeight as number });

        setLibraryViewMode((settings?.libraryViewMode ?? defaultLibraryViewMode) as LibraryViewMode);
        setThumbnailSize((settings?.thumbnailSize ?? defaultThumbnailSize) as ThumbnailSize);
        if (settings?.thumbnailAspectRatio) setThumbnailAspectRatio(settings.thumbnailAspectRatio as ThumbnailAspectRatio);

        const pinnedFolders = (settings as Record<string, unknown>).pinnedFolders as string[] | undefined;
        if (pinnedFolders && pinnedFolders.length > 0) {
          try {
            const trees = await invoke<unknown>(Invokes.GetPinnedFolderTrees, {
              paths: pinnedFolders,
              expanded_folders: settings.lastFolderState?.expandedFolders || [],
              show_image_counts: settings.enableFolderImageCounts || (settings.folderTreeSort as Record<string, unknown>)?.key === 'imageCount',
            });
            setLibrary({ pinnedFolderTrees: trees as FolderTree[] });
          } catch (err) {
            console.error('Failed to load pinned folder trees:', err);
          }
        }

        const rootFolders = ((settings as Record<string, unknown>).rootFolders as string[] | undefined)?.length
          ? (settings as Record<string, unknown>).rootFolders as string[]
          : settings.lastRootPath
            ? [settings.lastRootPath]
            : [];

        if (!isAndroid && rootFolders.length > 0) {
          const currentPath = (settings.lastFolderState?.currentFolderPath as string) || rootFolders[0];
          const isAlbum = currentPath.startsWith('Album: ');
          const command =
            settings.libraryViewMode === LibraryViewMode.Recursive
              ? Invokes.ListImagesRecursive
              : Invokes.ListImagesInDir;

          preloadedDataRef.current = {
            rootPaths: rootFolders,
            currentPath: currentPath,
            trees: invoke<unknown>(Invokes.GetPinnedFolderTrees, {
              paths: rootFolders,
              expanded_folders: settings.lastFolderState?.expandedFolders ?? rootFolders,
              show_image_counts: settings.enableFolderImageCounts || (settings.folderTreeSort as Record<string, unknown>)?.key === 'imageCount',
            }),
            images: isAlbum ? undefined : invoke<unknown>(command, { path: currentPath }),
          };
        }

        if (settings?.lastFolderState) {
          setLibrary({
            expandedFolders: new Set(settings.lastFolderState.expandedFolders || []),
            expandedAlbumGroups: new Set(settings.lastFolderState.expandedAlbumGroups || []),
          });
        }

        invoke<unknown>('frontend_ready')
          .then((launchRaw) => {
            const launch = launchRaw as Record<string, unknown>;
            if (launch?.editSession) {
              useProcessStore.getState().setProcess({ externalEditSession: launch.editSession as ExternalEditSession });
            } else if (launch?.openWithFile) {
              useProcessStore.getState().setProcess({ initialFileToOpen: launch.openWithFile as string });
            }
          })
          .catch((e) => console.error('Failed to notify backend of readiness:', e));
      })
      .catch((err) => {
        console.error('Failed to load settings:', err);
        setAppSettings({
          lastRootPath: null,
          theme: DEFAULT_THEME_ID as Theme,
          thumbnailSize: defaultThumbnailSize,
          libraryViewMode: defaultLibraryViewMode,
        });
      })
      .finally(() => {
        isInitialMount.current = false;
      });
  }, [
    isAndroid,
    setAppSettings,
    setTheme,
    setUI,
    defaultLibraryViewMode,
    defaultThumbnailSize,
    setSortCriteria,
    setFilterCriteria,
    setEditor,
    setLibrary,
    preloadedDataRef,
    setLibraryViewMode,
    setThumbnailSize,
    setThumbnailAspectRatio,
  ]);

  useEffect(() => {
    if (isInitialMount.current || !appSettings) return;
    if (JSON.stringify(appSettings.uiVisibility) !== JSON.stringify(uiVisibility)) {
      handleSettingsChange({ ...appSettings, uiVisibility });
    }
  }, [uiVisibility, appSettings, handleSettingsChange]);

  useEffect(() => {
    if (isInitialMount.current || !appSettings) return;
    if (appSettings.thumbnailSize !== thumbnailSize) {
      handleSettingsChange({ ...appSettings, thumbnailSize });
    }
  }, [thumbnailSize, appSettings, handleSettingsChange]);

  useEffect(() => {
    if (isInitialMount.current || !appSettings) return;
    if (appSettings.thumbnailAspectRatio !== thumbnailAspectRatio) {
      handleSettingsChange({ ...appSettings, thumbnailAspectRatio });
    }
  }, [thumbnailAspectRatio, appSettings, handleSettingsChange]);

  useEffect(() => {
    if (isInitialMount.current || !appSettings) return;
    if (appSettings.libraryViewMode !== libraryViewMode) {
      handleSettingsChange({ ...appSettings, libraryViewMode });
    }
  }, [libraryViewMode, appSettings, handleSettingsChange]);

  useEffect(() => {
    if (isInitialMount.current || !appSettings) return;
    if (JSON.stringify(appSettings.sortCriteria) !== JSON.stringify(sortCriteria)) {
      handleSettingsChange({ ...appSettings, sortCriteria });
    }
  }, [sortCriteria, appSettings, handleSettingsChange]);

  useEffect(() => {
    if (isInitialMount.current || !appSettings) return;
    if (JSON.stringify(appSettings.filterCriteria) !== JSON.stringify(filterCriteria)) {
      handleSettingsChange({ ...appSettings, filterCriteria });
    }
  }, [filterCriteria, appSettings, handleSettingsChange]);

  useEffect(() => {
    if (isInitialMount.current || !appSettings) return;
    if (appSettings.language && appSettings.language !== i18n.language) {
      i18n.changeLanguage(appSettings.language);
    }
  }, [appSettings?.language, i18n.language]);

  useEffect(() => {
    if (isInitialMount.current || !appSettings) return;
    if (!currentFolderPath && !activeAlbumId) return;

    const currentExpanded = Array.from(expandedFolders);
    const currentExpandedAlbums = Array.from(expandedAlbumGroups);

    const prevFolderState = appSettings.lastFolderState || {
      currentFolderPath: null,
      expandedFolders: [],
      activeAlbumId: null,
      expandedAlbumGroups: [],
    };

    const pathChanged = prevFolderState.currentFolderPath !== currentFolderPath;
    const expandedChanged = JSON.stringify(prevFolderState.expandedFolders || []) !== JSON.stringify(currentExpanded);
    const albumChanged = prevFolderState.activeAlbumId !== activeAlbumId;
    const albumExpandedChanged =
      JSON.stringify(prevFolderState.expandedAlbumGroups || []) !== JSON.stringify(currentExpandedAlbums);

    if (pathChanged || expandedChanged || albumChanged || albumExpandedChanged) {
      handleSettingsChange({
        ...appSettings,
        lastFolderState: {
          currentFolderPath,
          expandedFolders: currentExpanded,
          activeAlbumId,
          expandedAlbumGroups: currentExpandedAlbums,
        },
      });
    }
  }, [currentFolderPath, expandedFolders, activeAlbumId, expandedAlbumGroups, appSettings, handleSettingsChange]);

  useEffect(() => {
    if (!appSettings) return;

    const needsImageCounts = Boolean(
      appSettings.enableFolderImageCounts || appSettings.folderTreeSort?.key === 'imageCount',
    );

    if (prevImageCountsNeed.current === undefined) {
      prevImageCountsNeed.current = needsImageCounts;
      return;
    }

    if (prevImageCountsNeed.current !== needsImageCounts) {
      prevImageCountsNeed.current = needsImageCounts;

      const rootFolders = ((appSettings as Record<string, unknown>).rootFolders as string[] | undefined)?.length
        ? (appSettings as Record<string, unknown>).rootFolders as string[]
        : appSettings.lastRootPath
          ? [appSettings.lastRootPath]
          : [];
      const pinnedFolders = (appSettings as Record<string, unknown>).pinnedFolders as string[] || [];

      const currentExpanded = Array.from(useLibraryStore.getState().expandedFolders);

      setLibrary({ isTreeLoading: true });

      const promises = [];

      if (pinnedFolders.length > 0) {
        promises.push(
          invoke<unknown>(Invokes.GetPinnedFolderTrees, {
            paths: pinnedFolders,
            expanded_folders: currentExpanded,
            show_image_counts: needsImageCounts,
          }).then((trees) => ({ type: 'pinned' as const, trees: trees as FolderTree[] })),
        );
      }

      if (rootFolders.length > 0) {
        promises.push(
          invoke<unknown>(Invokes.GetPinnedFolderTrees, {
            paths: rootFolders,
            expanded_folders: currentExpanded,
            show_image_counts: needsImageCounts,
          }).then((trees) => ({ type: 'root' as const, trees: trees as FolderTree[] })),
        );
      }

      Promise.all(promises)
        .then((results) => {
          useLibraryStore.getState().setLibrary((_state) => {
            const updates: Record<string, unknown> = { isTreeLoading: false };
            results.forEach((res) => {
              if (res.type === 'pinned') updates.pinnedFolderTrees = res.trees;
              if (res.type === 'root') updates.folderTrees = res.trees;
            });
            return updates;
          });
        })
        .catch((err) => {
          console.error('Failed to re-fetch trees for image counts:', err);
          setLibrary({ isTreeLoading: false });
        });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [appSettings?.enableFolderImageCounts, appSettings?.folderTreeSort?.key]);

  useEffect(() => {
    const root = document.documentElement;
    const currentThemeId = theme || DEFAULT_THEME_ID;

    const baseTheme =
      THEMES.find((t: ThemeProps) => t.id === currentThemeId) ||
      THEMES.find((t: ThemeProps) => t.id === DEFAULT_THEME_ID);
    if (!baseTheme) return;

    const finalCssVariables: Record<string, string> = { ...baseTheme.cssVariables };

    Object.entries(finalCssVariables).forEach(([key, value]) => {
      root.style.setProperty(key, value as string);
    });

    const fontFamily = (appSettings as Record<string, unknown>)?.fontFamily as string || 'poppins';
    const fontStack =
      fontFamily === 'system'
        ? '-apple-system, BlinkMacSystemFont, system-ui, sans-serif'
        : "'Poppins', system-ui, sans-serif";
    root.style.setProperty('--font-family', fontStack);
  }, [theme, (appSettings as Record<string, unknown>)?.fontFamily]);
};
