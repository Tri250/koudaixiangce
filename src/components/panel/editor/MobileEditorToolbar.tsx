import { memo, useCallback } from 'react';
import { Undo2, Redo2, Eye, EyeOff, Maximize2, Minimize2, RotateCcw, FlipHorizontal2, FlipVertical2, Crop, ZoomIn, ZoomOut, SlidersHorizontal } from 'lucide-react';
import clsx from 'clsx';
import { useTranslation } from 'react-i18next';

interface MobileEditorToolbarProps {
  canRedo: boolean;
  canUndo: boolean;
  isFullScreen: boolean;
  showOriginal: boolean;
  activeRightPanel: string | null;
  onUndo(): void;
  onRedo(): void;
  onToggleShowOriginal(): void;
  onToggleFullScreen(): void;
  onToggleRightPanel(): void;
  onRotateLeft?(): void;
  onFlipHorizontal?(): void;
  onFlipVertical?(): void;
  onCrop?(): void;
  onZoomIn?(): void;
  onZoomOut?(): void;
}

const MobileEditorToolbar = memo(
  ({
    canRedo,
    canUndo,
    isFullScreen,
    showOriginal,
    activeRightPanel,
    onUndo,
    onRedo,
    onToggleShowOriginal,
    onToggleFullScreen,
    onToggleRightPanel,
    onRotateLeft,
    onFlipHorizontal,
    onFlipVertical,
    onCrop,
    onZoomIn,
    onZoomOut,
  }: MobileEditorToolbarProps) => {
    const { t } = useTranslation();

    const handleTouchStart = useCallback((e: React.TouchEvent) => {
      e.preventDefault();
    }, []);

    return (
      <div
        className="fixed bottom-0 left-0 right-0 z-50 bg-surface/95 backdrop-blur-md border-t border-text-secondary/10 safe-area-bottom"
        style={{ touchAction: 'none' }}
        onTouchStart={handleTouchStart}
      >
        {/* Primary toolbar row */}
        <div className="flex items-center justify-around px-2 h-14">
          <ToolbarButton
            icon={<Undo2 size={22} />}
            label={t('editor.toolbar.tooltips.undo', 'Undo')}
            disabled={!canUndo}
            onClick={onUndo}
          />
          <ToolbarButton
            icon={<Redo2 size={22} />}
            label={t('editor.toolbar.tooltips.redo', 'Redo')}
            disabled={!canRedo}
            onClick={onRedo}
          />
          <ToolbarButton
            icon={showOriginal ? <EyeOff size={22} /> : <Eye size={22} />}
            label={showOriginal ? t('editor.toolbar.tooltips.showEdited', 'Edited') : t('editor.toolbar.tooltips.showOriginal', 'Original')}
            active={showOriginal}
            onClick={onToggleShowOriginal}
          />
          <ToolbarButton
            icon={isFullScreen ? <Minimize2 size={22} /> : <Maximize2 size={22} />}
            label={t('editor.toolbar.tooltips.fullscreen', 'Fullscreen')}
            onClick={onToggleFullScreen}
          />
          <ToolbarButton
            icon={<SlidersHorizontal size={22} />}
            label={t('editor.adjustments', 'Adjustments')}
            active={activeRightPanel !== null}
            onClick={onToggleRightPanel}
          />
        </div>

        {/* Secondary toolbar row (transform tools) */}
        <div className="flex items-center justify-around px-2 h-12 border-t border-text-secondary/5">
          {onZoomOut && (
            <ToolbarButton
              icon={<ZoomOut size={18} />}
              label={t('editor.zoomOut', 'Zoom Out')}
              small
              onClick={onZoomOut}
            />
          )}
          {onZoomIn && (
            <ToolbarButton
              icon={<ZoomIn size={18} />}
              label={t('editor.zoomIn', 'Zoom In')}
              small
              onClick={onZoomIn}
            />
          )}
          {onRotateLeft && (
            <ToolbarButton
              icon={<RotateCcw size={18} />}
              label={t('editor.rotateLeft', 'Rotate')}
              small
              onClick={onRotateLeft}
            />
          )}
          {onFlipHorizontal && (
            <ToolbarButton
              icon={<FlipHorizontal2 size={18} />}
              label={t('editor.flipH', 'Flip H')}
              small
              onClick={onFlipHorizontal}
            />
          )}
          {onFlipVertical && (
            <ToolbarButton
              icon={<FlipVertical2 size={18} />}
              label={t('editor.flipV', 'Flip V')}
              small
              onClick={onFlipVertical}
            />
          )}
          {onCrop && (
            <ToolbarButton
              icon={<Crop size={18} />}
              label={t('editor.crop', 'Crop')}
              small
              onClick={onCrop}
            />
          )}
        </div>
      </div>
    );
  },
);

interface ToolbarButtonProps {
  icon: React.ReactNode;
  label: string;
  active?: boolean;
  disabled?: boolean;
  small?: boolean;
  onClick(): void;
}

const ToolbarButton = memo(({ icon, label, active, disabled, small, onClick }: ToolbarButtonProps) => (
  <button
    className={clsx(
      'flex flex-col items-center justify-center rounded-lg transition-colors select-none',
      small ? 'px-2 py-1 min-w-[44px] min-h-[36px]' : 'px-3 py-1 min-w-[52px] min-h-[48px]',
      active
        ? 'bg-accent text-button-text'
        : disabled
          ? 'text-text-secondary/40 cursor-not-allowed'
          : 'text-text-primary active:bg-card-active',
    )}
    disabled={disabled}
    onClick={onClick}
    aria-label={label}
  >
    {icon}
    <span className={clsx('mt-0.5 leading-none', small ? 'text-[9px]' : 'text-[10px]')}>{label}</span>
  </button>
));

export default MobileEditorToolbar;
