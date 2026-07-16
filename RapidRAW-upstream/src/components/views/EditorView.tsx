import { type RefObject, type PointerEvent as ReactPointerEvent, useState, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useShallow } from 'zustand/react/shallow';
import clsx from 'clsx';
import { ArrowLeftRight } from 'lucide-react';

import Editor from '../panel/Editor';
import BottomBar from '../panel/BottomBar';
import RightPanelSwitcher from '../panel/right/RightPanelSwitcher';
import Resizer from '../ui/Resizer';
import BottomSheet from '../ui/BottomSheet';
import Controls from '../panel/right/ControlsPanel';
import MetadataPanel from '../panel/right/MetadataPanel';
import CropPanel from '../panel/right/CropPanel';
import MasksPanel from '../panel/right/MasksPanel';
import AIPanel from '../panel/right/AIPanel';
import PresetsPanel from '../panel/right/PresetsPanel';
import ExportPanel from '../panel/right/ExportPanel';
import PortraitPanel from '../panel/right/PortraitPanel';

import { useEditorStore } from '../../store/useEditorStore';
import { useUIStore } from '../../store/useUIStore';
import { useLibraryStore } from '../../store/useLibraryStore';
import { useProcessStore } from '../../store/useProcessStore';
import { useSettingsStore } from '../../store/useSettingsStore';

import { ImageFile, Orientation, Panel, ThumbnailAspectRatio } from '../ui/AppProperties';

// Android panel tab order matching RightPanelSwitcher.androidPanelTabs
const ANDROID_PANEL_ORDER: Panel[] = [
  Panel.Adjustments,
  Panel.Metadata,
  Panel.Portrait,
  Panel.Crop,
  Panel.Masks,
  Panel.Ai,
  Panel.Presets,
  Panel.Export,
];

const panelVariants: any = {
  animate: (direction: number) => ({
    opacity: 1,
    y: 0,
    transition: { duration: direction === 0 ? 0 : 0.2, ease: 'circOut' },
  }),
  exit: (direction: number) => ({
    opacity: direction === 0 ? 1 : 0.2,
    y: direction === 0 ? 0 : direction > 0 ? -20 : 20,
    transition: { duration: direction === 0 ? 0 : 0.1, ease: 'circIn' },
  }),
  initial: (direction: number) => ({
    opacity: direction === 0 ? 1 : 0.2,
    y: direction === 0 ? 0 : direction > 0 ? 20 : -20,
  }),
};

interface EditorViewProps {
  transformWrapperRef: RefObject<any>;
  isResizing: boolean;
  isCompactPortrait: boolean;
  isAndroid: boolean;
  isAndroidCompact: boolean;
  compactEditorPanelHeight: number;
  compactEditorPanelCollapsedHeight: number;
  thumbnailAspectRatio: ThumbnailAspectRatio;
  sortedImageList: ImageFile[];
  createResizeHandler: (stateKey: string, startSize: number) => (e: ReactPointerEvent<HTMLDivElement>) => void;
  handleBackToLibrary: () => void;
  handleEditorContextMenu: (...args: any) => void;
  handleThumbnailContextMenu: (...args: any) => void;
  handleImageClick: (...args: any) => void;
  handleClearSelection: () => void;
  handleCopyAdjustments: () => void;
  handlePasteAdjustments: () => void;
  handleRate: (...args: any) => void;
  handleZoomChange: (zoom: number) => void;
  handleRightPanelSelect: (panelId: Panel) => void;
  requestThumbnails: any;
}

export default function EditorView({
  transformWrapperRef,
  isResizing,
  isCompactPortrait,
  isAndroid,
  isAndroidCompact,
  compactEditorPanelHeight,
  compactEditorPanelCollapsedHeight,
  thumbnailAspectRatio,
  sortedImageList,
  createResizeHandler,
  handleBackToLibrary,
  handleEditorContextMenu,
  handleThumbnailContextMenu,
  handleImageClick,
  handleClearSelection,
  handleCopyAdjustments,
  handlePasteAdjustments,
  handleRate,
  handleZoomChange,
  handleRightPanelSelect,
  requestThumbnails,
}: EditorViewProps) {
  const { selectedImage } = useEditorStore(
    useShallow((state) => ({
      selectedImage: state.selectedImage,
    })),
  );

  const {
    isFullScreen,
    isInstantTransition,
    uiVisibility,
    bottomPanelHeight,
    rightPanelWidth,
    activeRightPanel,
    renderedRightPanel,
    slideDirection,
    setUI,
  } = useUIStore(
    useShallow((state) => ({
      isFullScreen: state.isFullScreen,
      isInstantTransition: state.isInstantTransition,
      uiVisibility: state.uiVisibility,
      bottomPanelHeight: state.bottomPanelHeight,
      rightPanelWidth: state.rightPanelWidth,
      activeRightPanel: state.activeRightPanel,
      renderedRightPanel: state.renderedRightPanel,
      slideDirection: state.slideDirection,
      setUI: state.setUI,
    })),
  );

  const { multiSelectedPaths, imageRatings, isViewLoading, rootPaths } = useLibraryStore(
    useShallow((state) => ({
      multiSelectedPaths: state.multiSelectedPaths,
      imageRatings: state.imageRatings,
      isViewLoading: state.isViewLoading,
      rootPaths: state.rootPaths,
    })),
  );

  const { exportState, isCopied, isPasted, setExportState } = useProcessStore(
    useShallow((state) => ({
      exportState: state.exportState,
      isCopied: state.isCopied,
      isPasted: state.isPasted,
      setExportState: state.setExportState,
    })),
  );

  const { appSettings, handleSettingsChange } = useSettingsStore(
    useShallow((state) => ({
      appSettings: state.appSettings,
      handleSettingsChange: state.handleSettingsChange,
    })),
  );

  // Android swipe gesture: track touch start position for panel switching
  const touchStartXRef = useRef<number>(0);
  const touchStartYRef = useRef<number>(0);
  const SWIPE_THRESHOLD = 60;

  const handleTouchStart = useCallback((e: React.TouchEvent) => {
    touchStartXRef.current = e.touches[0].clientX;
    touchStartYRef.current = e.touches[0].clientY;
  }, []);

  const handleTouchEnd = useCallback(
    (e: React.TouchEvent) => {
      const deltaX = e.changedTouches[0].clientX - touchStartXRef.current;
      const deltaY = e.changedTouches[0].clientY - touchStartYRef.current;

      // Only handle horizontal swipes (ignore vertical scrolling)
      if (Math.abs(deltaX) < Math.abs(deltaY) || Math.abs(deltaX) < SWIPE_THRESHOLD) return;

      const currentIndex = activeRightPanel ? ANDROID_PANEL_ORDER.indexOf(activeRightPanel) : -1;
      if (deltaX > 0) {
        // Swipe right: go to previous panel
        const prevIndex = currentIndex > 0 ? currentIndex - 1 : ANDROID_PANEL_ORDER.length - 1;
        handleRightPanelSelect(ANDROID_PANEL_ORDER[prevIndex]);
      } else {
        // Swipe left: go to next panel
        const nextIndex = currentIndex < ANDROID_PANEL_ORDER.length - 1 ? currentIndex + 1 : 0;
        handleRightPanelSelect(ANDROID_PANEL_ORDER[nextIndex]);
      }
    },
    [activeRightPanel, handleRightPanelSelect],
  );

  const editorNode = (
    <Editor
      onBackToLibrary={handleBackToLibrary}
      onContextMenu={handleEditorContextMenu}
      transformWrapperRef={transformWrapperRef}
    />
  );

  const editorBottomBarComponent = (
    <BottomBar
      filmstripHeight={bottomPanelHeight}
      imageList={sortedImageList}
      imageRatings={imageRatings}
      isCopied={isCopied}
      isCopyDisabled={!selectedImage}
      isFilmstripVisible={uiVisibility.filmstrip}
      isLoading={isViewLoading}
      isPasted={isPasted}
      isPasteDisabled={useEditorStore.getState().copiedAdjustments === null}
      isRatingDisabled={!selectedImage}
      isResizing={isResizing}
      multiSelectedPaths={multiSelectedPaths}
      onClearSelection={handleClearSelection}
      onContextMenu={handleThumbnailContextMenu}
      onCopy={handleCopyAdjustments}
      onOpenCopyPasteSettings={() => setUI({ isCopyPasteSettingsModalOpen: true })}
      onImageSelect={handleImageClick}
      onPaste={() => handlePasteAdjustments()}
      onRate={handleRate}
      onRequestThumbnails={requestThumbnails}
      onZoomChange={handleZoomChange}
      rating={imageRatings[selectedImage?.path || ''] || 0}
      selectedImage={selectedImage}
      setIsFilmstripVisible={(value: boolean) =>
        setUI((state) => ({ uiVisibility: { ...state.uiVisibility, filmstrip: value } }))
      }
      showFilmstrip={!isCompactPortrait}
      showZoomControls={!isAndroid}
      thumbnailAspectRatio={thumbnailAspectRatio}
      totalImages={sortedImageList.length}
      isAndroid={isAndroid}
    />
  );

  const editorBottomBarNode = (
    <div
      className={clsx(
        'flex flex-col w-full overflow-hidden shrink-0',
        !isResizing && !isInstantTransition && 'transition-all duration-300 ease-in-out',
      )}
      style={{
        maxHeight: isFullScreen ? '0px' : '500px',
        opacity: isFullScreen ? 0 : 1,
      }}
    >
      {!isCompactPortrait && (
        <Resizer direction={Orientation.Horizontal} onMouseDown={createResizeHandler('bottom', bottomPanelHeight)} />
      )}
      {editorBottomBarComponent}
    </div>
  );

  const editorRightPanelContent = (
    <AnimatePresence mode="wait" custom={slideDirection}>
      {activeRightPanel && (
        <motion.div
          animate="animate"
          className="h-full w-full"
          custom={slideDirection}
          exit="exit"
          initial="initial"
          key={renderedRightPanel}
          variants={panelVariants}
        >
          {renderedRightPanel === Panel.Adjustments && <Controls />}
          {renderedRightPanel === Panel.Metadata && <MetadataPanel />}
          {renderedRightPanel === Panel.Portrait && <PortraitPanel />}
          {renderedRightPanel === Panel.Crop && <CropPanel />}
          {renderedRightPanel === Panel.Masks && <MasksPanel />}
          {renderedRightPanel === Panel.Presets && (
            <PresetsPanel
              onNavigateToCommunity={() => {
                handleBackToLibrary();
                setUI({ activeView: 'community' });
              }}
            />
          )}
          {renderedRightPanel === Panel.Export && (
            <ExportPanel
              exportState={exportState}
              multiSelectedPaths={multiSelectedPaths}
              selectedImage={selectedImage}
              setExportState={setExportState}
              appSettings={appSettings}
              onSettingsChange={handleSettingsChange}
              rootPaths={rootPaths}
            />
          )}
          {renderedRightPanel === Panel.Ai && <AIPanel />}
        </motion.div>
      )}
    </AnimatePresence>
  );

  // Android compact: bottom panel with draggable sheet
  if (isAndroidCompact) {
    return (
      <div className="flex flex-col grow h-full min-h-0 relative">
        {/* Preview area */}
        <div className="flex-1 min-h-0 relative">
          {editorNode}
          {/* Before/After comparison floating button */}
          <button
            className="absolute bottom-4 right-4 w-10 h-10 rounded-full bg-bg-secondary/80 border border-surface flex items-center justify-center text-text-secondary hover:text-text-primary transition-colors z-30"
            onClick={() => {
              const el = document.querySelector('.react-transform-wrapper');
              if (el) {
                (el as HTMLElement).style.filter =
                  (el as HTMLElement).style.filter === 'grayscale(100%)' ? '' : 'grayscale(100%)';
              }
            }}
            aria-label="Before/After"
          >
            <ArrowLeftRight size={18} />
          </button>
        </div>

        {/* Bottom sheet panel */}
        <BottomSheet isOpen={!!activeRightPanel && !isFullScreen} defaultHeight={280} minHeight={120}>
          <div className="flex flex-col h-full">
            {/* Horizontal tab bar */}
            <RightPanelSwitcher
              activePanel={activeRightPanel}
              onPanelSelect={handleRightPanelSelect}
              isInstantTransition={isInstantTransition}
              layout="horizontal"
            />
            {/* Panel content */}
            <div
              className="flex-1 min-h-0 overflow-y-auto"
              onTouchStart={handleTouchStart}
              onTouchEnd={handleTouchEnd}
            >
              {editorRightPanelContent}
            </div>
          </div>
        </BottomSheet>
      </div>
    );
  }

  return (
    <div className={clsx('flex grow h-full min-h-0', isCompactPortrait ? 'flex-col gap-2' : 'flex-row')}>
      <div className={clsx('flex-1 flex flex-col min-w-0', isCompactPortrait && 'min-h-0')}>
        {editorNode}
        {!isCompactPortrait && editorBottomBarNode}
      </div>
      <div
        className={clsx(
          'flex overflow-hidden shrink-0',
          isCompactPortrait ? 'flex-col bg-bg-secondary rounded-lg' : 'h-full bg-transparent',
          !isResizing && !isInstantTransition && 'transition-all duration-300 ease-in-out',
        )}
        style={
          isCompactPortrait
            ? {
                height: isFullScreen
                  ? '0px'
                  : `${activeRightPanel ? compactEditorPanelHeight : compactEditorPanelCollapsedHeight}px`,
                opacity: isFullScreen ? 0 : 1,
              }
            : {
                maxWidth: isFullScreen ? '0px' : '1000px',
                opacity: isFullScreen ? 0 : 1,
              }
        }
      >
        {isCompactPortrait ? (
          <>
            {activeRightPanel && !isFullScreen && (
              <Resizer
                direction={Orientation.Horizontal}
                onMouseDown={createResizeHandler('compact', compactEditorPanelHeight)}
              />
            )}
            <div className="min-h-0 flex-1 overflow-hidden">{editorRightPanelContent}</div>
            <div className="shrink-0 border-t border-surface">
              <RightPanelSwitcher
                activePanel={activeRightPanel}
                onPanelSelect={handleRightPanelSelect}
                isInstantTransition={isInstantTransition}
                layout="horizontal"
              />
            </div>
            <div className="shrink-0 border-t border-surface">{editorBottomBarComponent}</div>
          </>
        ) : (
          <>
            <Resizer direction={Orientation.Vertical} onMouseDown={createResizeHandler('right', rightPanelWidth)} />
            <div className="flex bg-bg-secondary rounded-lg h-full">
              <div
                className={clsx(
                  'h-full overflow-hidden',
                  !isResizing && !isInstantTransition && 'transition-all duration-300 ease-in-out',
                )}
                style={{ width: activeRightPanel ? `${rightPanelWidth}px` : '0px' }}
              >
                <div style={{ width: `${rightPanelWidth}px` }} className="h-full">
                  {editorRightPanelContent}
                </div>
              </div>
              <div
                className={clsx(
                  'h-full border-l transition-colors',
                  activeRightPanel ? 'border-surface' : 'border-transparent',
                )}
              >
                <RightPanelSwitcher
                  activePanel={activeRightPanel}
                  onPanelSelect={handleRightPanelSelect}
                  isInstantTransition={isInstantTransition}
                />
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
