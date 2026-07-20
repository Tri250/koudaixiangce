import { type RefObject, type PointerEvent as ReactPointerEvent } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useShallow } from 'zustand/react/shallow';
import { ArrowLeftRight } from 'lucide-react';
import clsx from 'clsx';

import Editor from '../panel/Editor';
import BottomBar from '../panel/BottomBar';
import MobileEditorToolbar from '../panel/editor/MobileEditorToolbar';
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
import HDRPanel from '../panel/right/HDRPanel';
import MonochromePanel from '../panel/right/MonochromePanel';
import ColorSpacePanel from '../panel/right/ColorSpacePanel';
import PortraitPanel from '../panel/right/PortraitPanel';
import LiquifyPanel from '../panel/right/LiquifyPanel';
import SkyPanel from '../panel/right/SkyPanel';
import CreativePanel from '../panel/right/CreativePanel';
import AiAnalysisPanel from '../panel/right/AiAnalysisPanel';
import AdvancedSearchPanel from '../panel/right/AdvancedSearchPanel';

import { useEditorStore } from '../../store/useEditorStore';
import { useUIStore } from '../../store/useUIStore';
import { useLibraryStore } from '../../store/useLibraryStore';
import { useProcessStore } from '../../store/useProcessStore';
import { useSettingsStore } from '../../store/useSettingsStore';

import { ImageFile, Orientation, Panel, ThumbnailAspectRatio } from '../ui/AppProperties';

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
  isCompactLandscape: boolean;
  isAndroidCompact: boolean;
  isAndroid: boolean;
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
  isCompactLandscape,
  isAndroidCompact,
  isAndroid,
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
  const { selectedImage, adjustments, setEditor, history, historyIndex, undo, redo } = useEditorStore(
    useShallow((state) => ({
      selectedImage: state.selectedImage,
      adjustments: state.adjustments,
      setEditor: state.setEditor,
      history: state.history,
      historyIndex: state.historyIndex,
      undo: state.undo,
      redo: state.redo,
    })),
  );
  const canUndo = historyIndex > 0;
  const canRedo = historyIndex < history.length - 1;

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
      selectedImage={selectedImage ?? undefined}
      setIsFilmstripVisible={(value: boolean) =>
        setUI((state) => ({ uiVisibility: { ...state.uiVisibility, filmstrip: value } }))
      }
      showFilmstrip={!isCompactPortrait}
      showZoomControls={isAndroidCompact || !isAndroid}
      thumbnailAspectRatio={thumbnailAspectRatio}
      totalImages={sortedImageList.length}
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
          {renderedRightPanel === Panel.Crop && <CropPanel />}
          {renderedRightPanel === Panel.Masks && <MasksPanel />}
          {renderedRightPanel === Panel.Presets && (
            <PresetsPanel
              onNavigateToCommunity={() => {
                handleBackToLibrary();
                setUI({ activeView: 'community' });
              }}
              isAndroid={isAndroid}
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
          {renderedRightPanel === Panel.Portrait && <PortraitPanel />}
          {renderedRightPanel === Panel.Liquify && <LiquifyPanel />}
          {renderedRightPanel === Panel.Sky && <SkyPanel />}
          {renderedRightPanel === Panel.Creative && <CreativePanel />}
          {renderedRightPanel === Panel.AiAnalysis && <AiAnalysisPanel />}
          {renderedRightPanel === Panel.AdvancedSearch && <AdvancedSearchPanel />}
          {renderedRightPanel === Panel.Hdr && <HDRPanel />}
          {renderedRightPanel === Panel.Monochrome && <MonochromePanel />}
          {renderedRightPanel === Panel.ColorSpace && <ColorSpacePanel />}
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

        {/* Mobile editor toolbar — touch-friendly undo/redo/orientation/crop/zoom controls */}
        <MobileEditorToolbar
          canRedo={canRedo}
          canUndo={canUndo}
          isFullScreen={isFullScreen}
          showOriginal={false}
          activeRightPanel={activeRightPanel}
          onUndo={undo}
          onRedo={redo}
          onToggleShowOriginal={() => {
            const el = document.querySelector('.react-transform-wrapper');
            if (el) {
              (el as HTMLElement).style.filter =
                (el as HTMLElement).style.filter === 'grayscale(100%)' ? '' : 'grayscale(100%)';
            }
          }}
          onToggleFullScreen={() => setUI({ isFullScreen: !isFullScreen })}
          onToggleRightPanel={() => {
            if (activeRightPanel !== null) {
              setUI({ activeRightPanel: null });
            } else {
              handleRightPanelSelect(Panel.Adjustments);
            }
          }}
          onRotateLeft={() => {
            const currentSteps = adjustments.orientationSteps || 0;
            setEditor({ adjustments: { ...adjustments, orientationSteps: (currentSteps + 3) % 4 } });
          }}
          onFlipHorizontal={() => {
            setEditor({ adjustments: { ...adjustments, flipHorizontal: !adjustments.flipHorizontal } });
          }}
          onFlipVertical={() => {
            setEditor({ adjustments: { ...adjustments, flipVertical: !adjustments.flipVertical } });
          }}
          onCrop={() => {
            if (activeRightPanel === Panel.Crop) {
              setUI({ activeRightPanel: null });
            } else {
              handleRightPanelSelect(Panel.Crop);
            }
          }}
        />

        {/* Compact bottom bar for Android: rating, copy/paste, zoom */}
        <div className="shrink-0 border-t border-surface bg-bg-secondary relative z-[60]">
          <RightPanelSwitcher
            activePanel={activeRightPanel}
            onPanelSelect={handleRightPanelSelect}
            isInstantTransition={isInstantTransition}
            layout="horizontal"
          />
        </div>

        {/* Bottom bar with rating, copy/paste controls */}
        <div className="shrink-0 bg-bg-secondary relative z-[60]">
          {editorBottomBarComponent}
        </div>

        {/* Bottom sheet panel - only for content */}
        <BottomSheet isOpen={!!activeRightPanel && !isFullScreen} defaultHeight={280} minHeight={120}>
          <div className="flex-1 min-h-0 overflow-y-auto">
            {editorRightPanelContent}
          </div>
        </BottomSheet>
      </div>
    );
  }

  return (
    <div
      className={clsx(
        'flex grow h-full min-h-0',
        isCompactPortrait ? 'flex-col gap-2' : 'flex-row',
      )}
    >
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
                maxWidth: isFullScreen ? '0px' : isCompactLandscape ? '380px' : '1000px',
                opacity: isFullScreen ? 0 : 1,
                width: isCompactLandscape ? '380px' : undefined,
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
                style={{ width: activeRightPanel ? `${Math.max(rightPanelWidth, 320)}px` : '0px', minWidth: activeRightPanel ? '320px' : '0px' }}
              >
                <div style={{ width: `${Math.max(rightPanelWidth, 320)}px`, minWidth: '320px' }} className="h-full">
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
