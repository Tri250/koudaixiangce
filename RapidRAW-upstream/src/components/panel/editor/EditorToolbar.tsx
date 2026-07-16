import { memo, useState, useEffect, useRef, useMemo } from 'react';
import { Eye, EyeOff, ArrowLeft, Maximize, Loader2, Undo, Redo, Waves, RotateCcw, RotateCw, PanelRight } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import clsx from 'clsx';
import { useTranslation } from 'react-i18next';
import { Panel, SelectedImage } from '../../ui/AppProperties';
import { IconAperture, IconCalendar, IconClock, IconFocalLength, IconIso, IconShutter } from './ExifIcons';
import Text from '../../ui/Text';
import { TextColors, TextVariants, TextWeights } from '../../../types/typography';
import { useEditorActions } from '../../../hooks/useEditorActions';
import { useUIStore } from '../../../store/useUIStore';
import { formatExifDate, formatExifTime, resolveIntlLocale } from '../../../utils/exifDateUtils';

interface EditorToolbarProps {
  canRedo: boolean;
  canUndo: boolean;
  isAndroid: boolean;
  isLoading: boolean;
  onBackToLibrary(): void;
  onRedo(): void;
  onToggleFullScreen(): void;
  onToggleShowOriginal(): void;
  onUndo(): void;
  selectedImage: SelectedImage;
  showOriginal: boolean;
  showDateView: boolean;
  onToggleDateView(): void;
  adjustmentsHistory: any[];
  adjustmentsHistoryIndex: number;
  goToAdjustmentsHistoryIndex(index: number): void;
}

const EditorToolbar = memo(
  ({
    canRedo,
    canUndo,
    isAndroid,
    isLoading,
    onBackToLibrary,
    onRedo,
    onToggleFullScreen,
    onToggleShowOriginal,
    onUndo,
    selectedImage,
    showOriginal,
    showDateView,
    onToggleDateView,
    adjustmentsHistory,
    adjustmentsHistoryIndex,
    goToAdjustmentsHistoryIndex,
  }: EditorToolbarProps) => {
    const { t, i18n } = useTranslation();
    const { handleRotate } = useEditorActions();
    const activeRightPanel = useUIStore((s) => s.activeRightPanel);
    const setRightPanel = useUIStore((s) => s.setRightPanel);
    const isAnyLoading = isLoading;
    const [isLoaderVisible, setIsLoaderVisible] = useState(false);
    const [disableLoaderTransition, setDisableLoaderTransition] = useState(false);
    const hideTimeoutRef = useRef<number | null>(null);
    const prevIsLoadingRef = useRef(isLoading);
    const [isVcHovered, setIsVcHovered] = useState(false);
    const [isInfoHovered, setIsInfoHovered] = useState(false);
    const [isHistoryVisible, setIsHistoryVisible] = useState(false);
    const historyContainerRef = useRef<HTMLDivElement>(null);
    const historyButtonRef = useRef<HTMLDivElement>(null);
    const lastRightPanelRef = useRef<Panel | null>(activeRightPanel ?? Panel.Adjustments);

    useEffect(() => {
      if (activeRightPanel) {
        lastRightPanelRef.current = activeRightPanel;
      }
    }, [activeRightPanel]);

    // Touch handler for Android — toggles right panel visibility (the touch equivalent
    // of the Tab "toggle panel" shortcut). Hides when visible, restores the last panel when hidden.
    const handleTogglePanel = () => {
      if (activeRightPanel) {
        setRightPanel(activeRightPanel);
      } else {
        setRightPanel(lastRightPanelRef.current ?? Panel.Adjustments);
      }
    };

    const showResolution = !isAndroid && selectedImage.width > 0 && selectedImage.height > 0;
    const [displayedResolution, setDisplayedResolution] = useState('');

    const { baseName, isVirtualCopy, vcId, exifData, hasExif } = useMemo(() => {
      const path = selectedImage.path;
      const parts = path.split('?vc=');
      const fullFileName = parts[0].split(/[\\/]/).pop() || '';

      const exif = selectedImage.exif || {};

      let fNum = exif.FNumber;
      if (fNum) {
        const fStr = String(fNum);
        fNum = fStr.toLowerCase().startsWith('f') ? fStr : `f/${fStr}`;
      }

      // Locale-aware EXIF date/time formatting (H6). Falls back to null when the
      // tag is missing or unparseable, so downstream "hasExif" logic is preserved.
      const intlLocale = resolveIntlLocale(i18n.language);
      const captureDate = exif.DateTimeOriginal
        ? formatExifDate(exif.DateTimeOriginal, intlLocale, '')
        : '';
      const captureTime = exif.DateTimeOriginal
        ? formatExifTime(exif.DateTimeOriginal, intlLocale, '')
        : '';

      const data = {
        iso: exif.PhotographicSensitivity || exif.ISO,
        fNumber: fNum,
        shutter: exif.ExposureTime,
        focal: exif.FocalLengthIn35mmFilm,
        captureDate: captureDate,
        captureTime: captureTime,
      };

      const hasData = !!(data.iso || data.fNumber || data.shutter || data.focal || data.captureDate);

      return {
        baseName: fullFileName,
        isVirtualCopy: parts.length > 1,
        vcId: parts.length > 1 ? parts[1] : null,
        exifData: data,
        hasExif: hasData,
      };
    }, [selectedImage.path, selectedImage.exif, i18n.language]);

    useEffect(() => {
      if (showResolution) {
        setDisplayedResolution(` - ${selectedImage.width} × ${selectedImage.height}`);
      }
    }, [showResolution, selectedImage.width, selectedImage.height]);

    useEffect(() => {
      const wasLoadingResolution = prevIsLoadingRef.current && !isLoading;

      if (isAnyLoading) {
        if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
        setDisableLoaderTransition(false);
        setIsLoaderVisible(true);
      } else if (isLoaderVisible) {
        if (wasLoadingResolution) {
          setDisableLoaderTransition(true);
          setIsLoaderVisible(false);
        } else {
          setDisableLoaderTransition(false);
          hideTimeoutRef.current = window.setTimeout(() => {
            setIsLoaderVisible(false);
          }, 300);
        }
      }

      prevIsLoadingRef.current = isLoading;

      return () => {
        if (hideTimeoutRef.current) clearTimeout(hideTimeoutRef.current);
      };
    }, [isAnyLoading, isLoading, isLoaderVisible]);

    useEffect(() => {
      if (!isHistoryVisible) return;
      const handleClickOutside = (e: MouseEvent) => {
        if (
          historyContainerRef.current &&
          !historyContainerRef.current.contains(e.target as Node) &&
          historyButtonRef.current &&
          !historyButtonRef.current.contains(e.target as Node)
        ) {
          setIsHistoryVisible(false);
        }
      };
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [isHistoryVisible]);

    const prevNamesRef = useRef<string[]>([t('editor.history.initialState')]);

    const historyNames = useMemo(() => {
      if (!adjustmentsHistory || adjustmentsHistory.length === 0) return [];

      const formatKey = (k: string) => {
        const special: Record<string, string> = {
          aiPatches: t('editor.history.aiPatches'),
          aspectRatio: t('editor.history.aspectRatio'),
          flipHorizontal: t('editor.history.flipHorizontal'),
          flipVertical: t('editor.history.flipVertical'),
          orientationSteps: t('editor.history.orientationSteps'),
          lutPath: t('editor.history.lutPath'),
          lutIntensity: t('editor.history.lutIntensity'),
          lutData: t('editor.history.lutData'),
          lutName: t('editor.history.lutName'),
          lutSize: t('editor.history.lutSize'),
          chromaticAberrationBlueYellow: t('editor.history.chromaticAberrationBlueYellow'),
          chromaticAberrationRedCyan: t('editor.history.chromaticAberrationRedCyan'),
          centré: t('editor.history.centre'),
          lumaNoiseReduction: t('editor.history.lumaNoiseReduction'),
          colorNoiseReduction: t('editor.history.colorNoiseReduction'),
          lensMaker: t('editor.history.lensMaker'),
          lensModel: t('editor.history.lensModel'),
          lensDistortionAmount: t('editor.history.lensDistortionAmount'),
          lensVignetteAmount: t('editor.history.lensVignetteAmount'),
          lensTcaAmount: t('editor.history.lensTcaAmount'),
          lensDistortionEnabled: t('editor.history.lensDistortionEnabled'),
          lensTcaEnabled: t('editor.history.lensTcaEnabled'),
          lensVignetteEnabled: t('editor.history.lensVignetteEnabled'),
          transformDistortion: t('editor.history.transformDistortion'),
          transformVertical: t('editor.history.transformVertical'),
          transformHorizontal: t('editor.history.transformHorizontal'),
          transformRotate: t('editor.history.transformRotate'),
          transformAspect: t('editor.history.transformAspect'),
          transformScale: t('editor.history.transformScale'),
          transformXOffset: t('editor.history.transformXOffset'),
          transformYOffset: t('editor.history.transformYOffset'),
          colorGrading: t('editor.history.colorGrading'),
          colorCalibration: t('editor.history.colorCalibration'),
          toneMapper: t('editor.history.toneMapper'),
          showClipping: t('editor.history.showClipping'),
          sectionVisibility: t('editor.history.sectionVisibility'),
          flareAmount: t('editor.history.flareAmount'),
          glowAmount: t('editor.history.glowAmount'),
          halationAmount: t('editor.history.halationAmount'),
          grainAmount: t('editor.history.grainAmount'),
          grainRoughness: t('editor.history.grainRoughness'),
          grainSize: t('editor.history.grainSize'),
          vignetteAmount: t('editor.history.vignetteAmount'),
          vignetteFeather: t('editor.history.vignetteFeather'),
          vignetteMidpoint: t('editor.history.vignetteMidpoint'),
          vignetteRoundness: t('editor.history.vignetteRoundness'),
          dehaze: t('editor.history.dehaze'),
          exposure: t('editor.history.exposure'),
          blacks: t('editor.history.blacks'),
          whites: t('editor.history.whites'),
          shadows: t('editor.history.shadows'),
          highlights: t('editor.history.highlights'),
          contrast: t('editor.history.contrast'),
          brightness: t('editor.history.brightness'),
          clarity: t('editor.history.clarity'),
          structure: t('editor.history.structure'),
          sharpness: t('editor.history.sharpness'),
          saturation: t('editor.history.saturation'),
          temperature: t('editor.history.temperature'),
          tint: t('editor.history.tint'),
          vibrance: t('editor.history.vibrance'),
          hsl: t('editor.history.hsl'),
          curves: t('editor.history.curves'),
          crop: t('editor.history.crop'),
          masks: t('editor.history.masks'),
          rating: t('editor.history.rating'),
        };
        if (special[k]) return special[k];
        return k.replace(/([A-Z])/g, ' $1').replace(/^./, (str) => str.toUpperCase());
      };

      const cachedNames = prevNamesRef.current;
      const newNames = [...cachedNames];

      if (newNames.length > adjustmentsHistory.length) {
        newNames.length = adjustmentsHistory.length;
      }

      for (let i = newNames.length; i < adjustmentsHistory.length; i++) {
        if (i === 0) {
          newNames[i] = t('editor.history.initialState');
          continue;
        }

        const curr = adjustmentsHistory[i];
        const prev = adjustmentsHistory[i - 1];
        const changed: string[] = [];

        for (const key of Object.keys(curr)) {
          if (prev[key] === curr[key]) continue;

          if (key === 'masks') {
            const prevMasks = prev.masks || [];
            const currMasks = curr.masks || [];

            if (currMasks.length > prevMasks.length) changed.push(t('editor.history.addedMask'));
            else if (currMasks.length < prevMasks.length) changed.push(t('editor.history.deletedMask'));
            else {
              currMasks.forEach((cMask: any) => {
                const pMask = prevMasks.find((m: any) => m.id === cMask.id);
                if (pMask) {
                  if (pMask.opacity !== cMask.opacity) changed.push(t('editor.history.maskOpacity'));
                  if (pMask.invert !== cMask.invert) changed.push(t('editor.history.maskInvert'));
                  if (pMask.visible !== cMask.visible) changed.push(t('editor.history.maskVisibility'));
                  if (pMask.subMasks !== cMask.subMasks) changed.push(t('editor.history.maskAreaBrush'));

                  if (pMask.adjustments !== cMask.adjustments) {
                    for (const adjKey of Object.keys(cMask.adjustments || {})) {
                      if (pMask.adjustments[adjKey] !== cMask.adjustments[adjKey]) {
                        changed.push(t('editor.history.maskAdjustment', { name: formatKey(adjKey) }));
                      }
                    }
                  }
                }
              });
            }
          } else if (key === 'aiPatches') {
            const prevPatches = prev.aiPatches || [];
            const currPatches = curr.aiPatches || [];

            if (currPatches.length > prevPatches.length) changed.push(t('editor.history.addedAiPatch'));
            else if (currPatches.length < prevPatches.length) changed.push(t('editor.history.deletedAiPatch'));
            else {
              currPatches.forEach((cPatch: any) => {
                const pPatch = prevPatches.find((p: any) => p.id === cPatch.id);
                if (pPatch) {
                  if (pPatch.visible !== cPatch.visible) changed.push(t('editor.history.aiPatchVisibility'));
                  if (pPatch.subMasks !== cPatch.subMasks) changed.push(t('editor.history.aiPatchArea'));
                  if (pPatch.patchData !== cPatch.patchData || pPatch.prompt !== cPatch.prompt) {
                    changed.push(t('editor.history.aiGeneration'));
                  }
                }
              });
            }
          } else {
            changed.push(formatKey(key));
          }
        }

        const uniqueChanged = Array.from(new Set(changed));

        if (uniqueChanged.length === 0) newNames[i] = t('editor.history.adjustment');
        else if (uniqueChanged.length > 2) newNames[i] = `${uniqueChanged.slice(0, 2).join(', ')}...`;
        else newNames[i] = uniqueChanged.join(', ');
      }

      prevNamesRef.current = newNames;
      return newNames;
    }, [adjustmentsHistory]);

    useEffect(() => {
      if (isHistoryVisible && historyContainerRef.current) {
        const timer = setTimeout(() => {
          const activeEl = historyContainerRef.current?.querySelector('[data-active="true"]');
          if (activeEl) {
            activeEl.scrollIntoView({ block: 'nearest', behavior: 'auto' });
          }
        }, 10);
        return () => clearTimeout(timer);
      }
    }, [isHistoryVisible, adjustmentsHistoryIndex]);

    const handleButtonKeyDown = (e: React.KeyboardEvent<HTMLButtonElement>) => {
      if (e.key === 'Tab') return;
      e.currentTarget.blur();
    };

    const isExpanded = isInfoHovered && (hasExif || isLoading);

    return (
      <div className="relative shrink-0 flex items-center justify-between px-4 h-14 gap-4 z-40">
        <div className="flex items-center gap-2 shrink-0 z-40">
          <button
            className="bg-surface text-text-primary p-2 rounded-full hover:bg-card-active transition-colors shrink-0"
            onClick={onBackToLibrary}
            onKeyDown={handleButtonKeyDown}
            data-tooltip={t('editor.toolbar.tooltips.backToLibrary')}
          >
            <ArrowLeft size={20} />
          </button>

          {isAndroid && (
            <div className="flex items-center gap-1 shrink-0">
              <button
                className="w-11 h-11 flex items-center justify-center rounded-full bg-surface text-text-primary hover:bg-card-active transition-colors"
                onClick={() => handleRotate(-90)}
                onKeyDown={handleButtonKeyDown}
                data-tooltip={t('editor.toolbar.tooltips.rotateLeft')}
              >
                <RotateCcw size={20} />
              </button>
              <button
                className="w-11 h-11 flex items-center justify-center rounded-full bg-surface text-text-primary hover:bg-card-active transition-colors"
                onClick={() => handleRotate(90)}
                onKeyDown={handleButtonKeyDown}
                data-tooltip={t('editor.toolbar.tooltips.rotateRight')}
              >
                <RotateCw size={20} />
              </button>
              <button
                className="w-11 h-11 flex items-center justify-center rounded-full bg-surface text-text-primary hover:bg-card-active transition-colors"
                onClick={handleTogglePanel}
                onKeyDown={handleButtonKeyDown}
                data-tooltip={t('editor.toolbar.tooltips.togglePanel')}
              >
                <PanelRight size={20} />
              </button>
            </div>
          )}

          <div className="hidden 2xl:flex items-center gap-2" aria-hidden="true">
            <div className="p-2 invisible pointer-events-none">
              <Undo size={20} />
            </div>
            <div className="p-2 invisible pointer-events-none">
              <Undo size={20} />
            </div>
            <div className="p-2 invisible pointer-events-none">
              <Undo size={20} />
            </div>
            <div className="p-2 invisible pointer-events-none">
              <Undo size={20} />
            </div>
          </div>
        </div>

        <div className="flex-1 flex justify-center min-w-0 relative h-full">
          <div
            className={clsx(
              'bg-surface flex flex-col items-center overflow-hidden transition-all duration-200 ease-out pt-2',
              isExpanded
                ? 'h-18 px-8 rounded-2xl absolute min-w-[340px] whitespace-nowrap shadow-2xl shadow-black/50'
                : 'h-9 px-4 rounded-[18px] absolute min-w-0 w-auto max-w-full shadow-none',
            )}
            onMouseEnter={() => setIsInfoHovered(true)}
            onMouseLeave={() => setIsInfoHovered(false)}
            style={{
              top: '10px',
              transform: 'translateX(-50%)',
              left: '50%',
              zIndex: isExpanded ? 50 : 0,
            }}
          >
            <div className="flex items-center justify-center max-w-full h-5 shrink-0">
              <Text
                as="span"
                variant={TextVariants.small}
                color={TextColors.primary}
                weight={TextWeights.medium}
                className="truncate min-w-0 shrink"
              >
                {baseName}
              </Text>

              {isVirtualCopy && (
                <Text
                  as="div"
                  variant={TextVariants.small}
                  color={TextColors.accent}
                  weight={TextWeights.bold}
                  className="ml-2 shrink-0 bg-accent/20 px-2 py-0.5 rounded-full flex items-center overflow-hidden cursor-default"
                  onMouseEnter={() => setIsVcHovered(true)}
                  onMouseLeave={() => setIsVcHovered(false)}
                >
                  <span>{t('editor.toolbar.vc')}</span>
                  <div
                    className={clsx(
                      'transition-all duration-300 ease-out overflow-hidden whitespace-nowrap',
                      isVcHovered ? 'max-w-20 opacity-100' : 'max-w-0 opacity-0',
                    )}
                  >
                    <span>-{vcId}</span>
                  </div>
                </Text>
              )}

              <div
                className={clsx(
                  'transition-all duration-300 ease-out overflow-hidden whitespace-nowrap shrink-0',
                  showResolution ? 'max-w-40 opacity-100 ml-2' : 'max-w-0 opacity-0 ml-0',
                )}
              >
                <Text
                  as="span"
                  variant={TextVariants.small}
                  className={clsx(
                    'block transition-transform duration-200 delay-100',
                    showResolution ? 'scale-100' : 'scale-95',
                  )}
                >
                  {displayedResolution}
                </Text>
              </div>

              <div
                className={clsx(
                  'overflow-hidden shrink-0',
                  isLoaderVisible ? 'max-w-4 opacity-100 ml-2' : 'max-w-0 opacity-0 ml-0',
                  disableLoaderTransition ? 'transition-none' : 'transition-all duration-300',
                )}
              >
                <Loader2 size={12} className="text-text-secondary animate-spin" />
              </div>
            </div>

            <div
              className={clsx(
                'relative mt-2 w-full grow justify-center border-t border-text-secondary/10 pt-2 transition-opacity duration-200',
                isExpanded ? 'opacity-100 delay-75' : 'opacity-0 hidden',
                hasExif && 'cursor-pointer',
              )}
              onClick={() => hasExif && onToggleDateView()}
            >
              <div
                className={clsx(
                  'absolute inset-0 flex items-center justify-center gap-6 transition-opacity duration-200',
                  showDateView ? 'opacity-0 pointer-events-none' : 'opacity-100',
                )}
              >
                {exifData.shutter && (
                  <div className="flex items-center gap-1.5" data-tooltip={t('editor.toolbar.tooltips.shutterSpeed')}>
                    <Text as="span">
                      <IconShutter />
                    </Text>
                    <Text as="span" variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.medium}>
                      {exifData.shutter}
                    </Text>
                  </div>
                )}
                {exifData.fNumber && (
                  <div className="flex items-center gap-1.5" data-tooltip={t('editor.toolbar.tooltips.aperture')}>
                    <Text as="span">
                      <IconAperture />
                    </Text>
                    <Text as="span" variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.medium}>
                      {exifData.fNumber}
                    </Text>
                  </div>
                )}
                {exifData.iso && (
                  <div className="flex items-center gap-1.5" data-tooltip={t('editor.toolbar.tooltips.iso')}>
                    <Text as="span">
                      <IconIso />
                    </Text>
                    <Text as="span" variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.medium}>
                      {exifData.iso}
                    </Text>
                  </div>
                )}
                {exifData.focal && (
                  <div className="flex items-center gap-1.5" data-tooltip={t('editor.toolbar.tooltips.focalLength')}>
                    <Text as="span">
                      <IconFocalLength />
                    </Text>
                    <Text as="span" variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.medium}>
                      {String(exifData.focal).endsWith('mm') ? exifData.focal : `${exifData.focal}mm`}
                    </Text>
                  </div>
                )}
              </div>

              <div
                className={clsx(
                  'absolute inset-0 flex items-center justify-center gap-6 transition-opacity duration-200',
                  showDateView ? 'opacity-100' : 'opacity-0 pointer-events-none',
                )}
              >
                {exifData.captureDate && (
                  <div className="flex items-center gap-2">
                    <Text as="span">
                      <IconCalendar />
                    </Text>
                    <Text as="span" variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.medium}>
                      {exifData.captureDate}
                    </Text>
                  </div>
                )}
                {exifData.captureTime && (
                  <div className="flex items-center gap-2">
                    <Text as="span">
                      <IconClock />
                    </Text>
                    <Text as="span" variant={TextVariants.small} color={TextColors.primary} weight={TextWeights.medium}>
                      {exifData.captureTime}
                    </Text>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2 shrink-0 z-40">
          <div className="relative flex items-center gap-2" ref={historyButtonRef}>
            <button
              className={clsx(
                'bg-surface text-text-primary p-2 rounded-full hover:bg-card-active transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center',
                isAndroid && 'w-11 h-11',
              )}
              disabled={!canUndo}
              onClick={onUndo}
              onKeyDown={handleButtonKeyDown}
              onContextMenu={(e) => {
                e.preventDefault();
                setIsHistoryVisible((prev) => !prev);
              }}
              data-tooltip={t('editor.toolbar.tooltips.undo')}
            >
              <Undo size={20} />
            </button>
            <button
              className={clsx(
                'bg-surface text-text-primary p-2 rounded-full hover:bg-card-active transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center',
                isAndroid && 'w-11 h-11',
              )}
              disabled={!canRedo}
              onClick={onRedo}
              onKeyDown={handleButtonKeyDown}
              onContextMenu={(e) => {
                e.preventDefault();
                setIsHistoryVisible((prev) => !prev);
              }}
              data-tooltip={t('editor.toolbar.tooltips.redo')}
            >
              <Redo size={20} />
            </button>

            <AnimatePresence>
              {isHistoryVisible && adjustmentsHistory && adjustmentsHistory.length > 1 && (
                <motion.div
                  ref={historyContainerRef}
                  initial={{ opacity: 0, y: -10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  transition={{ duration: 0.15, ease: 'easeOut' }}
                  className="absolute top-full right-0 mt-3 w-56 max-h-80 bg-surface/90 backdrop-blur-md border border-text-secondary/10 shadow-xl rounded-lg overflow-y-auto custom-scrollbar z-50 flex flex-col py-1.5 px-0.5"
                >
                  {historyNames.map((name, i) => {
                    const isCurrent = i === adjustmentsHistoryIndex;
                    const isFuture = i > adjustmentsHistoryIndex;

                    const textColor = isCurrent
                      ? TextColors.button
                      : isFuture
                        ? TextColors.secondary
                        : TextColors.primary;
                    const textWeight = isCurrent ? TextWeights.medium : TextWeights.normal;

                    return (
                      <button
                        key={i}
                        data-active={isCurrent}
                        onClick={() => goToAdjustmentsHistoryIndex(i)}
                        onKeyDown={handleButtonKeyDown}
                        className={clsx(
                          'text-left px-3 py-2 transition-colors mx-1 my-0.5 rounded-md',
                          isCurrent
                            ? 'bg-accent'
                            : isFuture
                              ? 'opacity-50 hover:bg-bg-primary hover:opacity-100'
                              : 'hover:bg-bg-primary',
                        )}
                      >
                        <div className="flex justify-between items-center gap-2">
                          <Text as="span" color={textColor} weight={textWeight} className="truncate">
                            {name}
                          </Text>
                          <Text
                            as="span"
                            variant={TextVariants.small}
                            color={textColor}
                            weight={textWeight}
                            className="opacity-50 shrink-0"
                          >
                            {i === 0 ? '' : i}
                          </Text>
                        </div>
                      </button>
                    );
                  })}
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          <button
            className={clsx(
              'p-2 rounded-full transition-colors',
              showOriginal
                ? 'bg-accent text-button-text hover:bg-accent/90 hover:text-button-text'
                : 'bg-surface hover:bg-card-active text-text-primary',
            )}
            onClick={onToggleShowOriginal}
            onKeyDown={handleButtonKeyDown}
            data-tooltip={
              showOriginal ? t('editor.toolbar.tooltips.showEdited') : t('editor.toolbar.tooltips.showOriginal')
            }
          >
            {showOriginal ? <EyeOff size={20} /> : <Eye size={20} />}
          </button>
          <button
            className="bg-surface text-text-primary p-2 rounded-full hover:bg-card-active transition-colors disabled:opacity-50 disabled:cursor-not-allowed relative"
            onClick={onToggleFullScreen}
            onKeyDown={handleButtonKeyDown}
            data-tooltip={t('editor.toolbar.tooltips.fullscreen')}
          >
            <div className="relative w-5 h-5 flex items-center justify-center">
              <Maximize size={20} />
            </div>
          </button>
        </div>
      </div>
    );
  },
);

export default EditorToolbar;
