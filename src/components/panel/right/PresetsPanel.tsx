import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { open as openDialog, save as saveDialog } from '@tauri-apps/plugin-dialog';
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import { useTranslation } from 'react-i18next';
import { toast } from 'react-toastify';
import { PresetListType, usePresets, UserPreset } from '../../../hooks/usePresets';
import { useContextMenu } from '../../../context/ContextMenuContext';
import {
  CopyPlus,
  Edit,
  FileDown,
  FileUp,
  Folder as FolderIcon,
  FolderOpen,
  FolderPlus,
  Loader2,
  Plus,
  SortAsc,
  Trash2,
  Users,
  Layers,
  Crop,
  Save,
  Wrench,
  Palette,
  Settings2,
  Search,
  LayoutGrid,
  List as ListIcon,
  RefreshCw,
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import ConfigurePresetModal from '../../modals/ConfigurePresetModal';
import CreateFolderModal from '../../modals/CreateFolderModal';
import RenameFolderModal from '../../modals/RenameFolderModal';
import Button from '../../ui/Button';
import Text from '../../ui/Text';
import Slider from '../../ui/Slider';
import { TextColors, TextVariants, TextWeights } from '../../../types/typography';
import { Adjustments, INITIAL_ADJUSTMENTS, ADJUSTMENT_GROUPS } from '../../../utils/adjustments';
import { Invokes, OPTION_SEPARATOR, Panel, Preset } from '../../ui/AppProperties';
import { useEditorStore } from '../../../store/useEditorStore';
import { useUIStore } from '../../../store/useUIStore';
import { useEditorActions } from '../../../hooks/useEditorActions';

interface DroppableFolderItemProps {
  children: any;
  folder: any;
  isExpanded: boolean;
  onContextMenu(event: any, folder: any): void;
  onToggle(id: string): void;
}

interface DraggablePresetItemProps {
  isGeneratingPreviews: boolean;
  onApply(preset: any): void;
  onContextMenu(event: any, preset: any): void;
  preset: any;
  previewUrl: string;
  isActive?: boolean;
  intensity?: number;
  onIntensityChange?: (val: number) => void;
  onDragStateChange?: (isDragging: boolean) => void;
  viewMode?: 'grid' | 'list';
}

interface FolderProps {
  folder: any;
}

interface FolderState {
  isOpen: boolean;
  folder: any;
}

interface ModalState {
  isOpen: boolean;
  preset: Preset | null;
}

interface PresetItemDisplayProps {
  isGeneratingPreviews: boolean;
  preset: Preset;
  previewUrl: string;
  isActive?: boolean;
  intensity?: number;
  onIntensityChange?: (val: number) => void;
  onDragStateChange?: (isDragging: boolean) => void;
  viewMode?: 'grid' | 'list';
}

interface PresetsPanelProps {
  onNavigateToCommunity(): void;
  isAndroid?: boolean;
}

const itemVariants = {
  hidden: { opacity: 0, x: -15 },
  visible: (i: number) => ({
    opacity: 1,
    x: 0,
    transition: {
      duration: 0.25,
      delay: i * 0.05,
    },
  }),
  exit: { opacity: 0, x: -15, transition: { duration: 0.2 } },
};

const evaluateCurveY = (curve: Array<{ x: number; y: number }>, targetX: number): number => {
  const len = curve.length;
  if (len === 1) return curve[0].y;
  if (targetX <= curve[0].x) return curve[0].y;
  if (targetX >= curve[len - 1].x) return curve[len - 1].y;

  for (let i = 0; i < len - 1; i++) {
    const p2 = curve[i + 1];
    if (targetX <= p2.x) {
      const p1 = curve[i];
      const range = p2.x - p1.x;
      return range === 0 ? p1.y : p1.y + ((targetX - p1.x) / range) * (p2.y - p1.y);
    }
  }
  return targetX;
};

const mixAdjustments = (presetObj: any, intensity: number, initialObj: any = INITIAL_ADJUSTMENTS): any => {
  const fraction = intensity / 100;

  if (fraction === 1) return { ...presetObj };
  if (fraction === 0) return { ...initialObj };

  const result: any = {};
  const keys = Object.keys(presetObj);

  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    const presetVal = presetObj[key];
    const initialVal = initialObj[key] !== undefined ? initialObj[key] : (INITIAL_ADJUSTMENTS as any)[key];

    if (typeof presetVal === 'number') {
      result[key] = typeof initialVal === 'number' ? initialVal + (presetVal - initialVal) * fraction : presetVal;
    } else if (Array.isArray(presetVal)) {
      if (!Array.isArray(initialVal)) {
        result[key] = fraction > 0 ? presetVal : initialVal;
        continue;
      }

      if (presetVal.length > 0 && presetVal[0].x !== undefined && presetVal[0].y !== undefined) {
        const xVals: number[] = [];
        let p1 = 0,
          p2 = 0;
        const len1 = initialVal.length,
          len2 = presetVal.length;

        while (p1 < len1 && p2 < len2) {
          const x1 = initialVal[p1].x,
            x2 = presetVal[p2].x;
          if (x1 < x2) {
            xVals.push(x1);
            p1++;
          } else if (x1 > x2) {
            xVals.push(x2);
            p2++;
          } else {
            xVals.push(x1);
            p1++;
            p2++;
          }
        }
        while (p1 < len1) xVals.push(initialVal[p1++].x);
        while (p2 < len2) xVals.push(presetVal[p2++].x);

        const newCurve = new Array(xVals.length);

        for (let j = 0; j < xVals.length; j++) {
          const x = xVals[j];
          const yInit = evaluateCurveY(initialVal, x);
          const yPreset = evaluateCurveY(presetVal, x);
          const yInterp = yInit + (yPreset - yInit) * fraction;

          newCurve[j] = {
            x,
            y: yInterp < 0 ? 0 : yInterp > 255 ? 255 : yInterp,
          };
        }
        result[key] = newCurve;
      } else {
        result[key] = fraction > 0 ? presetVal : initialVal;
      }
    } else if (presetVal !== null && typeof presetVal === 'object') {
      result[key] = mixAdjustments(presetVal, intensity, initialVal || {});
    } else {
      result[key] = fraction > 0 ? presetVal : initialVal;
    }
  }
  return result;
};

function PresetItemDisplay({
  preset,
  previewUrl,
  isGeneratingPreviews,
  isActive,
  intensity,
  onIntensityChange,
  onDragStateChange,
  viewMode = 'grid',
}: PresetItemDisplayProps) {
  const { t } = useTranslation();
  const geometryKeys = ADJUSTMENT_GROUPS.geometry.flatMap((g) => g.keys);

  const supportsMasks = preset.includeMasks ?? (preset.adjustments?.masks && preset.adjustments.masks.length > 0);
  const supportsGeometry =
    preset.includeCropTransform ?? geometryKeys.some((key) => preset.adjustments?.[key] !== undefined);
  const isTool = preset.presetType === 'tool';
  const tooltipContent = useMemo(() => {
    const features = [];
    if (supportsMasks) features.push(t('editor.presets.supports.masks'));
    if (supportsGeometry) features.push(t('editor.presets.supports.cropTransform'));

    if (features.length === 0) return undefined;
    return t('editor.presets.supports.label', { features: features.join(' + ') });
  }, [supportsMasks, supportsGeometry, t]);

  // Grid view: vertical card with large preview thumbnail on top.
  // List view: original horizontal layout (preview + text side by side).
  if (viewMode === 'grid') {
    return (
      <div
        className={`group relative flex flex-col rounded-xl overflow-hidden bg-surface border transition-all duration-200 cursor-grabbing ${
          isActive
            ? 'border-accent shadow-lg shadow-accent/20 ring-2 ring-accent/50'
            : 'border-border-color/40 hover:border-border-color/80 hover:shadow-sm'
        }`}
      >
        {/* Preview thumbnail (large, top) */}
        <div
          className="relative w-full aspect-[3/2] bg-bg-tertiary overflow-hidden"
          data-tooltip={tooltipContent}
        >
          {isGeneratingPreviews && !previewUrl ? (
            <div className="absolute inset-0 flex items-center justify-center">
              <Loader2 size={22} className="animate-spin text-text-secondary" />
            </div>
          ) : previewUrl ? (
            <img
              src={previewUrl}
              alt={`${preset.name} preview`}
              className="w-full h-full object-cover pointer-events-none transition-transform duration-300 group-hover:scale-[1.04]"
            />
          ) : (
            <div className="absolute inset-0 flex items-center justify-center">
              <Loader2 size={22} className="animate-spin text-text-secondary" />
            </div>
          )}

          {/* Top-right feature badges */}
          {(supportsMasks || supportsGeometry) && (
            <div className="absolute top-1.5 right-1.5 bg-black/55 backdrop-blur-md rounded-full px-1.5 py-0.5 flex items-center gap-1 shadow-sm z-10 pointer-events-none">
              {supportsMasks && <Layers size={11} className="text-white" />}
              {supportsGeometry && <Crop size={11} className="text-white" />}
            </div>
          )}

          {/* Top-left type badge */}
          <div
            className={`absolute top-1.5 left-1.5 backdrop-blur-md rounded-full px-2 py-0.5 flex items-center gap-1 shadow-sm z-10 pointer-events-none ${
              isTool ? 'bg-accent/85' : 'bg-primary/85'
            }`}
          >
            {isTool ? <Wrench size={9} className="text-white" /> : <Palette size={9} className="text-white" />}
            <span className="text-[9px] font-semibold uppercase tracking-wider text-white">
              {isTool ? t('editor.presets.types.tool') : t('editor.presets.types.style')}
            </span>
          </div>

          {/* Active overlay */}
          {isActive && (
            <div className="absolute inset-0 bg-accent/10 pointer-events-none" />
          )}

          {/* Bottom gradient overlay */}
          <div className="absolute bottom-0 left-0 right-0 h-8 bg-gradient-to-t from-black/30 to-transparent pointer-events-none" />
        </div>

        {/* Footer: name + (optional) intensity */}
        <div className="px-2.5 py-2 flex flex-col gap-1">
          <Text
            color={TextColors.primary}
            weight={TextWeights.semibold}
            className="truncate text-[12.5px] leading-tight"
            title={preset.name}
          >
            {preset.name}
          </Text>

          <AnimatePresence initial={false}>
            {isActive && onIntensityChange && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                transition={{ duration: 0.22, ease: 'easeInOut' }}
                className="w-full cursor-auto overflow-hidden"
                onClick={(e: any) => e.stopPropagation()}
                onPointerDown={(e: any) => e.stopPropagation()}
              >
                <div className="pt-1.5 pb-0.5">
                  <div className="flex items-center justify-between mb-1">
                    <Text variant={TextVariants.small} color={TextColors.secondary}>
                      {t('editor.presets.amount')}
                    </Text>
                    <span className="text-[10px] font-semibold text-accent tabular-nums">
                      {intensity ?? 100}%
                    </span>
                  </div>
                  <Slider
                    min={0}
                    max={200}
                    defaultValue={100}
                    value={intensity ?? 100}
                    onChange={(e: any) => onIntensityChange(Number(e.target.value))}
                    onDragStateChange={onDragStateChange}
                    label=""
                    step={1}
                  />
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    );
  }

  // List view (original compact layout)
  return (
    <div className="flex flex-col p-2.5 rounded-xl bg-surface border border-border-color/40 cursor-grabbing hover:border-border-color/70 transition-colors">
      <div className="flex items-center gap-3">
        <div
          className="w-[72px] h-[52px] bg-bg-tertiary rounded-lg flex items-center justify-center shrink-0 relative overflow-hidden shadow-sm"
          data-tooltip={tooltipContent}
        >
          {isGeneratingPreviews && !previewUrl ? (
            <Loader2 size={18} className="animate-spin text-text-secondary" />
          ) : previewUrl ? (
            <img
              src={previewUrl}
              alt={`${preset.name} preview`}
              className="w-full h-full object-cover rounded-lg pointer-events-none"
            />
          ) : (
            <Loader2 size={18} className="animate-spin text-text-secondary" />
          )}

          {(supportsMasks || supportsGeometry) && (
            <>
              <div className="absolute top-0 right-0 w-1/2 h-1/2 bg-linear-to-bl from-black/30 via-black/0 to-transparent pointer-events-none z-0" />
              <div className="absolute top-1 right-1 bg-primary/90 rounded-full px-1.5 py-0.5 flex items-center gap-1 backdrop-blur-sm shadow-sm z-10 pointer-events-none">
                {supportsMasks && <Layers size={10} className="text-white" />}
                {supportsGeometry && <Crop size={10} className="text-white" />}
              </div>
            </>
          )}
        </div>

        <div className="grow min-w-0 flex flex-col justify-center">
          <Text color={TextColors.primary} weight={TextWeights.semibold} className="truncate text-[13px]">
            {preset.name}
          </Text>
          <div className="flex items-center gap-1.5 mt-1">
            <span
              className={`inline-flex items-center gap-1 px-1.5 py-[1px] rounded text-[10px] font-medium ${
                isTool ? 'bg-accent/10 text-accent' : 'bg-primary/10 text-primary'
              }`}
            >
              {isTool ? <Wrench size={9} /> : <Palette size={9} />}
              {isTool ? t('editor.presets.types.tool') : t('editor.presets.types.style')}
            </span>
          </div>
        </div>
      </div>

      <AnimatePresence initial={false}>
        {isActive && onIntensityChange && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.25, ease: 'easeInOut' }}
            className="w-full cursor-auto overflow-hidden"
            onClick={(e: any) => e.stopPropagation()}
            onPointerDown={(e: any) => e.stopPropagation()}
          >
            <div className="mt-3 px-1 pb-1">
              <div className="flex items-center gap-2 mb-1.5">
                <Text variant={TextVariants.small} color={TextColors.secondary}>
                  {t('editor.presets.amount')}
                </Text>
                <span className="text-[11px] font-semibold text-accent tabular-nums">
                  {intensity ?? 100}%
                </span>
              </div>
              <Slider
                min={0}
                max={200}
                defaultValue={100}
                value={intensity ?? 100}
                onChange={(e: any) => onIntensityChange(Number(e.target.value))}
                onDragStateChange={onDragStateChange}
                label=""
                step={1}
              />
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function FolderItemDisplay({ folder }: FolderProps) {
  return (
    <div className="flex items-center gap-2 p-2 rounded-lg bg-surface cursor-grabbing w-full">
      <div className="p-1">
        <FolderIcon size={18} />
      </div>
      <Text color={TextColors.primary} weight={TextWeights.medium} className="grow truncate select-none">
        {folder.name}
      </Text>
      <Text as="span" weight={TextWeights.medium} className="ml-auto pr-1">
        {folder.children?.length || 0}
      </Text>
    </div>
  );
}

function DraggablePresetItem({
  preset,
  onApply,
  onContextMenu,
  previewUrl,
  isGeneratingPreviews,
  isActive,
  intensity,
  onIntensityChange,
  onDragStateChange,
  viewMode = 'grid',
}: DraggablePresetItemProps) {
  const {
    attributes,
    listeners,
    setNodeRef: setDraggableNodeRef,
    isDragging,
  } = useDraggable({
    id: preset.id,
    data: { type: PresetListType.Preset, preset },
  });

  const { setNodeRef: setDroppableNodeRef, isOver } = useDroppable({
    data: { type: PresetListType.Preset, preset },
    id: preset.id,
  });

  const setCombinedRef = useCallback(
    (node: any) => {
      setDraggableNodeRef(node);
      setDroppableNodeRef(node);
    },
    [setDraggableNodeRef, setDroppableNodeRef],
  );

  const style = {
    borderRadius: '12px',
    opacity: isDragging ? 0.4 : 1,
    outline: isOver ? '2px solid var(--color-primary)' : '2px solid transparent',
    outlineOffset: '-2px',
    touchAction: 'none',
  };

  return (
    <div
      onClick={() => onApply(preset)}
      onContextMenu={(e: any) => onContextMenu(e, { preset })}
      ref={setCombinedRef}
      style={style}
    >
      <motion.div
        {...listeners}
        {...attributes}
        className="cursor-grab h-full"
        whileTap={{ scale: isActive ? 1 : 0.98 }}
        transition={{ type: 'spring', stiffness: 400, damping: 17 }}
      >
        <PresetItemDisplay
          preset={preset}
          previewUrl={previewUrl}
          isGeneratingPreviews={isGeneratingPreviews}
          isActive={isActive}
          intensity={intensity}
          onIntensityChange={onIntensityChange}
          onDragStateChange={onDragStateChange}
          viewMode={viewMode}
        />
      </motion.div>
    </div>
  );
}

function DroppableFolderItem({ folder, onContextMenu, children, onToggle, isExpanded }: DroppableFolderItemProps) {
  const {
    attributes,
    listeners,
    setNodeRef: setDraggableNodeRef,
    isDragging,
  } = useDraggable({
    data: { type: PresetListType.Folder, folder },
    id: folder.id,
  });

  const { setNodeRef: setDroppableNodeRef, isOver } = useDroppable({
    data: { type: PresetListType.Folder, folder },
    id: folder.id,
  });

  const style = {
    opacity: isDragging ? 0.4 : 1,
    touchAction: 'none',
  };

  const hasChildren = folder.children && folder.children.length > 0;

  return (
    <div
      className={`rounded-lg transition-colors ${isOver ? 'bg-surface-hover' : ''}`}
      ref={setDroppableNodeRef}
      style={style}
    >
      <div
        className="flex items-center gap-2 p-2 rounded-lg bg-surface cursor-pointer"
        onContextMenu={(e: any) => onContextMenu(e, { folder })}
      >
        <div className="p-1 cursor-grab" ref={setDraggableNodeRef} {...listeners} {...attributes}>
          {isExpanded ? (
            <FolderOpen
              className="text-primary"
              onClick={(e: any) => {
                e.stopPropagation();
                onToggle(folder.id);
              }}
              size={18}
            />
          ) : (
            <FolderIcon
              className="text-text-secondary"
              onClick={(e: any) => {
                e.stopPropagation();
                onToggle(folder.id);
              }}
              size={18}
            />
          )}
        </div>
        <Text
          color={TextColors.primary}
          weight={TextWeights.medium}
          className="grow truncate select-none"
          onClick={() => onToggle(folder.id)}
        >
          {folder.name}
        </Text>
        <Text as="span" variant={TextVariants.small} color={TextColors.secondary} className="ml-auto pr-1">
          {folder.children?.length || 0}
        </Text>
      </div>
      <AnimatePresence>
        {isExpanded && hasChildren && (
          <motion.div
            animate={{ height: 'auto', opacity: 1 }}
            className="ml-5 pl-4 border-l-[1.5px] border-accent/30 space-y-2 overflow-hidden pt-2"
            exit={{ height: 0, opacity: 0 }}
            initial={{ height: 0, opacity: 0 }}
          >
            {children}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export default function PresetsPanel({ onNavigateToCommunity, isAndroid }: PresetsPanelProps) {
  const { t } = useTranslation();
  const selectedImage = useEditorStore((s) => s.selectedImage);
  const adjustments = useEditorStore((s) => s.adjustments);
  const activePanel = useUIStore((s) => s.activeRightPanel);
  const setEditor = useEditorStore((s) => s.setEditor);
  const { setAdjustments } = useEditorActions();

  // New UI state: filter type, search query, view mode (grid vs list)
  const [filterType, setFilterType] = useState<'all' | 'style' | 'tool'>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

  const {
    addFolder,
    addPreset,
    configurePreset,
    deleteItem,
    duplicatePreset,
    exportPresetsToFile,
    importPresetsFromFile,
    importLegacyPresetsFromFile,
    isLoading,
    movePreset,
    overwritePreset,
    presets,
    renameItem,
    reorderItems,
    sortAllPresetsAlphabetically,
  } = usePresets(adjustments);
  const { showContextMenu } = useContextMenu();
  const [previews, setPreviews] = useState<Record<string, string | null>>({});
  const [isGeneratingPreviews, setIsGeneratingPreviews] = useState(false);
  const [configureModalState, setConfigureModalState] = useState<ModalState>({ isOpen: false, preset: null });
  const [isAddFolderModalOpen, setIsAddFolderModalOpen] = useState(false);
  const [renameFolderState, setRenameFolderState] = useState<FolderState>({ isOpen: false, folder: null });
  const [expandedFolders, setExpandedFolders] = useState(new Set<string>());
  const [activeItem, setActiveItem] = useState<any>(null);
  const [folderPreviewsGenerated, setFolderPreviewsGenerated] = useState<Set<string>>(new Set());
  const [deletingItemId, setDeletingItemId] = useState<string | null>(null);

  const [activePresetId, setActivePresetId] = useState<string | null>(null);
  const [presetIntensity, setPresetIntensity] = useState<number>(100);
  const [baseAdjustments, setBaseAdjustments] = useState<Adjustments | null>(null);

  const previewsRef = useRef(previews);
  previewsRef.current = previews;
  const expandedFoldersRef = useRef(expandedFolders);
  expandedFoldersRef.current = expandedFolders;
  const previewQueue = useRef<Array<any>>([]);
  const isProcessingQueue = useRef(false);
  const currentImagePathRef = useRef<string | null>(selectedImage?.path || null);

  const handleDragStateChange = useCallback(
    (isDragging: boolean) => {
      setEditor({ isSliderDragging: isDragging });
    },
    [setEditor],
  );

  useEffect(() => {
    const allPresetIds = new Set();
    presets.forEach((item: UserPreset) => {
      if (item.preset) {
        allPresetIds.add(item.preset.id);
      } else if (item.folder) {
        item.folder.children.forEach((p: Preset) => allPresetIds.add(p.id));
      }
    });

    const currentPreviews = previewsRef.current;
    const previewsToDelete = Object.keys(currentPreviews).filter((id) => !allPresetIds.has(id));

    if (previewsToDelete.length > 0) {
      setPreviews((prev) => {
        const newPreviews = { ...prev };
        previewsToDelete.forEach((id) => {
          const url = newPreviews[id];
          if (url && url.startsWith('blob:')) {
            URL.revokeObjectURL(url);
          }
          delete newPreviews[id];
        });
        return newPreviews;
      });
    }
  }, [presets]);

  useEffect(() => {
    return () => {
      Object.values(previewsRef.current).forEach((url) => {
        if (url && url.startsWith('blob:')) {
          URL.revokeObjectURL(url);
        }
      });
      previewQueue.current = [];
      isProcessingQueue.current = false;
    };
  }, []);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 10,
      },
    }),
  );

  const { setNodeRef: setRootNodeRef, isOver: isRootOver } = useDroppable({ id: 'root' });

  const allItemsMap = useMemo(() => {
    const map = new Map();
    presets.forEach((item: any) => {
      if (item.preset) {
        map.set(item.preset.id, { type: PresetListType.Preset, data: item.preset });
      } else if (item.folder) {
        map.set(item.folder.id, { type: PresetListType.Folder, data: item.folder });
        item.folder.children.forEach((p: any) => map.set(p.id, { type: PresetListType.Preset, data: p }));
      }
    });
    return map;
  }, [presets]);

  const itemParentMap = useMemo(() => {
    const map = new Map();
    presets.forEach((item: UserPreset) => {
      if (item.preset) {
        map.set(item.preset.id, null);
      } else if (item.folder) {
        map.set(item.folder.id, null);
        item.folder.children.forEach((p: UserPreset) => {
          if (!item?.folder) {
            return;
          }
          map.set(p.id, item.folder.id);
        });
      }
    });
    return map;
  }, [presets]);

  const processPreviewQueue = useCallback(async () => {
    if (isProcessingQueue.current || previewQueue.current.length === 0) {
      return;
    }

    isProcessingQueue.current = true;
    setIsGeneratingPreviews(true);

    const pathAtStart = currentImagePathRef.current;

    while (previewQueue.current.length > 0) {
      if (pathAtStart !== currentImagePathRef.current) {
        previewQueue.current = [];
        break;
      }

      const item = previewQueue.current.shift();
      if (!item) break;
      const { preset, folderId } = item;

      if (folderId && !expandedFoldersRef.current.has(folderId)) {
        continue;
      }

      if (previewsRef.current[preset.id]) {
        continue;
      }

      try {
        const fullPresetAdjustments = { ...INITIAL_ADJUSTMENTS, ...preset.adjustments };
        const imageData: Uint8Array = await invoke(Invokes.GeneratePresetPreview, {
          js_adjustments: fullPresetAdjustments,
        });

        if (pathAtStart !== currentImagePathRef.current) {
          previewQueue.current = [];
          break;
        }

        const blob = new Blob([imageData.buffer as BlobPart], { type: 'image/jpeg' });
        const url = URL.createObjectURL(blob);
        setPreviews((prev: Record<string, string | null>) => {
          const oldUrl = prev[preset.id];
          if (oldUrl && oldUrl.startsWith('blob:')) {
            URL.revokeObjectURL(oldUrl);
          }
          return { ...prev, [preset.id]: url };
        });
      } catch (error) {
        console.error(`Failed to generate preview for preset ${preset.name}:`, error);
        if (pathAtStart === currentImagePathRef.current) {
          setPreviews((prev: Record<string, string | null>) => ({ ...prev, [preset.id]: null }));
        }
      }
    }

    isProcessingQueue.current = false;
    setIsGeneratingPreviews(false);
  }, []);

  const enqueuePreviews = useCallback(
    (presetsToGenerate: Array<UserPreset>, folderId: string | null = null) => {
      const newItems = presetsToGenerate
        .filter((p: any) => !previewsRef.current[p?.id])
        .map((p: UserPreset) => ({ preset: p, folderId }));
      if (newItems.length > 0) {
        previewQueue.current.push(...newItems);
        processPreviewQueue();
      }
    },
    [processPreviewQueue],
  );

  const toggleFolder = (folderId: string) => {
    setExpandedFolders((prev: Set<string>) => {
      const newSet = new Set(prev);
      if (newSet.has(folderId)) {
        newSet.delete(folderId);
      } else {
        newSet.add(folderId);
        if (!folderPreviewsGenerated.has(folderId)) {
          generateFolderPreviews(folderId);
        }
      }
      return newSet;
    });
  };

  const generateSinglePreview = useCallback(
    async (preset: Preset) => {
      if (!selectedImage?.isReady || !preset) {
        return;
      }

      setIsGeneratingPreviews(true);
      const pathAtStart = currentImagePathRef.current;

      try {
        const fullPresetAdjustments: any = { ...INITIAL_ADJUSTMENTS, ...preset.adjustments };
        const imageData: Uint8Array = await invoke(Invokes.GeneratePresetPreview, {
          js_adjustments: fullPresetAdjustments,
        });

        if (pathAtStart !== currentImagePathRef.current) return;

        const blob = new Blob([imageData.buffer as BlobPart], { type: 'image/jpeg' });
        const url = URL.createObjectURL(blob);

        setPreviews((prev: Record<string, string | null>) => {
          const oldUrl = prev[preset.id];
          if (oldUrl && oldUrl.startsWith('blob:')) {
            URL.revokeObjectURL(oldUrl);
          }
          return { ...prev, [preset.id]: url };
        });
      } catch (error) {
        console.error(`Failed to generate preview for preset ${preset.name}:`, error);
        if (pathAtStart === currentImagePathRef.current) {
          setPreviews((prev: Record<string, string | null>) => ({ ...prev, [preset.id]: null }));
        }
      } finally {
        if (pathAtStart === currentImagePathRef.current) {
          setIsGeneratingPreviews(false);
        }
      }
    },
    [selectedImage?.isReady],
  );

  const generateFolderPreviews = useCallback(
    async (folderId: string) => {
      if (!selectedImage?.isReady) {
        return;
      }

      const folder = presets.find((item: any) => item.folder && item.folder.id === folderId);
      if (!folder?.folder?.children?.length) {
        return;
      }

      const presetsToGenerate = folder.folder.children.filter((p: any) => !previewsRef.current[p.id]);
      if (presetsToGenerate.length > 0) {
        enqueuePreviews(presetsToGenerate, folderId);
      }
      setFolderPreviewsGenerated((prev: Set<string>) => new Set(prev).add(folderId));
    },
    [selectedImage?.isReady, presets, enqueuePreviews],
  );

  const generateRootPreviews = useCallback(async () => {
    if (!selectedImage?.isReady) {
      return;
    }

    const rootPresets = presets.filter((item: UserPreset) => item.preset).map((item) => item.preset);
    const presetsToGenerate: any = rootPresets.filter((p: any) => !previewsRef.current[p.id]);

    if (presetsToGenerate.length > 0) {
      enqueuePreviews(presetsToGenerate);
    }
  }, [selectedImage?.isReady, presets, enqueuePreviews]);

  useEffect(() => {
    const isPathChanged = selectedImage?.path !== currentImagePathRef.current;

    if (isPathChanged || !selectedImage?.isReady) {
      Object.values(previewsRef.current).forEach((url) => {
        if (url && url.startsWith('blob:')) {
          URL.revokeObjectURL(url);
        }
      });

      previewsRef.current = {};
      previewQueue.current = [];

      setPreviews({});
      setFolderPreviewsGenerated(new Set<string>());

      setActivePresetId(null);
      setBaseAdjustments(null);

      if (isPathChanged && selectedImage?.path) {
        currentImagePathRef.current = selectedImage.path;
      }
    }

    if (activePanel === Panel.Presets && selectedImage?.isReady && presets.length > 0) {
      generateRootPreviews();
      expandedFolders.forEach((folderId: string) => {
        generateFolderPreviews(folderId);
      });
    }
  }, [
    activePanel,
    selectedImage?.isReady,
    selectedImage?.path,
    presets.length,
    generateRootPreviews,
    generateFolderPreviews,
    expandedFolders,
  ]);

  const handleApplyPreset = (preset: Preset) => {
    if (activePresetId === preset.id) {
      setActivePresetId(null);
      if (baseAdjustments) {
        setAdjustments(baseAdjustments);
      }
      setBaseAdjustments(null);
      return;
    }

    setBaseAdjustments(adjustments);
    setActivePresetId(preset.id);
    setPresetIntensity(100);

    const isTool = preset.presetType === 'tool';

    if (isTool) {
      // Tool presets are additive: only overlay keys that the preset explicitly defines
      setAdjustments((prevAdjustments: Adjustments) => {
        const merged = { ...prevAdjustments };
        const presetAdj = preset.adjustments as Record<string, any>;
        for (const key of Object.keys(presetAdj)) {
          (merged as any)[key] = presetAdj[key];
        }
        return merged;
      });
    } else {
      // Style presets replace: spread over initial, then apply
      setAdjustments((prevAdjustments: Adjustments) => ({
        ...prevAdjustments,
        ...preset.adjustments,
      }));
    }
  };

  const handleIntensityChange = useCallback(
    (preset: Preset, intensity: number) => {
      setPresetIntensity(intensity);
      const mixed = mixAdjustments(preset.adjustments, intensity);
      setAdjustments((prev: Adjustments) => ({
        ...prev,
        ...mixed,
      }));
    },
    [setAdjustments],
  );

  const handleSaveConfiguredPreset = async (
    name: string,
    includeMasks: boolean,
    includeCropTransform: boolean,
    presetType: 'tool' | 'style',
  ) => {
    if (configureModalState.preset) {
      const updated = configurePreset(
        configureModalState.preset.id,
        name,
        includeMasks,
        includeCropTransform,
        presetType,
      );
      if (updated) {
        await generateSinglePreview(updated);
      }
    } else {
      const newPreset = addPreset(name, null, includeMasks, includeCropTransform, presetType);
      if (newPreset) {
        await generateSinglePreview(newPreset);
      }
    }
    setConfigureModalState({ isOpen: false, preset: null });
  };

  const handleAddFolder = (name: string) => {
    addFolder(name);
    setIsAddFolderModalOpen(false);
  };

  const handleRenameFolderSave = (newName: string) => {
    if (renameFolderState.folder) {
      renameItem(renameFolderState.folder.id, newName);
    }
    setRenameFolderState({ isOpen: false, folder: null });
  };

  const handleDeleteItem = (id: string | null, isFolder = false) => {
    setDeletingItemId(id);
    if (!id) {
      return;
    }

    setTimeout(() => {
      deleteItem(id);
      if (isFolder) {
        setExpandedFolders((prev: Set<string>) => {
          const newSet = new Set(prev);
          newSet.delete(id);
          return newSet;
        });
        setFolderPreviewsGenerated((prev: Set<string>) => {
          const newSet = new Set(prev);
          newSet.delete(id);
          return newSet;
        });
      }
    }, 300);
  };

  const handleDragStart = (event: any) => {
    setActiveItem(allItemsMap.get(event.active.id) ?? null);
  };

  const handleDragEnd = (event: any) => {
    const { active, over } = event;
    setActiveItem(null);

    const activeId = active.id;
    const activeParentId = itemParentMap.get(activeId);
    const activeType = active.data.current?.type;

    if (!over) {
      if (activeParentId !== null) {
        movePreset(activeId, null, null);
      }
      return;
    }

    if (active.id === over.id) {
      return;
    }

    const overId = over.id;
    const overParentId = itemParentMap.get(overId);
    const overType = over.data.current?.type;

    const targetFolderId = overType === PresetListType.Folder ? overId : overParentId;

    if (activeType === PresetListType.Preset && targetFolderId) {
      if (activeParentId !== targetFolderId) {
        movePreset(activeId, targetFolderId);
        setExpandedFolders((prev: Set<string>) => new Set(prev).add(targetFolderId));
        if (!folderPreviewsGenerated.has(targetFolderId)) {
          generateFolderPreviews(targetFolderId);
        }
      } else {
        reorderItems(activeId, overId);
      }
      return;
    }

    if (activeParentId !== null && !targetFolderId) {
      movePreset(activeId, null, overId);
      return;
    }

    if (activeParentId === null && !targetFolderId) {
      reorderItems(activeId, overId);
      return;
    }
  };

  const handleImportPresets = async () => {
    try {
      const selectedPath = await openDialog({
        filters: [
          { name: t('editor.presets.dialog.allPresetFiles'), extensions: ['rrpreset', 'xmp', 'lrtemplate'] },
          { name: t('editor.presets.dialog.rapidRawPreset'), extensions: ['rrpreset'] },
          { name: t('editor.presets.dialog.legacyPreset'), extensions: ['xmp', 'lrtemplate'] },
        ],
        multiple: false,
        title: t('editor.presets.dialog.importPresetsTitle'),
      });

      if (typeof selectedPath === 'string') {
        const isLegacy =
          selectedPath.toLowerCase().endsWith('.xmp') || selectedPath.toLowerCase().endsWith('.lrtemplate');

        if (isLegacy) {
          await importLegacyPresetsFromFile(selectedPath);
        } else {
          await importPresetsFromFile(selectedPath);
        }

        setFolderPreviewsGenerated(new Set<string>());
        setPreviews({});
      }
    } catch (error) {
      console.error('Failed to import presets:', error);
    }
  };

  const handleExport = async (item: UserPreset) => {
    const isFolder = !!item.folder;
    const name = isFolder ? item.folder?.name : item.preset?.name;
    const itemsToExport = [item];

    try {
      const filePath = await saveDialog({
        defaultPath: `${name}.rrpreset`.replace(/[<>:"/\\|?*]/g, '_'),
        filters: [{ name: t('editor.presets.dialog.presetFile'), extensions: ['rrpreset'] }],
        title: t('editor.presets.dialog.exportTitle', {
          type: isFolder ? t('editor.presets.types.folder') : t('editor.presets.types.preset'),
        }),
      });

      if (filePath) {
        await exportPresetsToFile(itemsToExport, filePath);
      }
    } catch (error) {
      console.error(`Failed to export ${isFolder ? PresetListType.Folder : PresetListType.Preset}:`, error);
    }
  };

  const handleExportAllPresets = async () => {
    if (presets.length === 0) {
      return;
    }
    try {
      const filePath = await saveDialog({
        defaultPath: 'all_presets.rrpreset',
        filters: [{ name: t('editor.presets.dialog.presetFile'), extensions: ['rrpreset'] }],
        title: t('editor.presets.dialog.exportAllTitle'),
      });

      if (filePath) {
        await exportPresetsToFile(presets, filePath);
      }
    } catch (error) {
      console.error('Failed to export all presets:', error);
    }
  };

  const handleBatchSyncPreset = useCallback(async () => {
    const presetIds = presets
      .map((item: any) => item.preset?.id || item.folder?.id)
      .filter(Boolean);
    if (presetIds.length === 0) return;
    try {
      await invoke(Invokes.BatchSyncPreset, { preset_ids: presetIds });
      toast.success(t('editor.presets.tooltips.sync'));
    } catch (error) {
      console.error('Failed to sync presets:', error);
    }
  }, [presets, t]);

  const handleContextMenu = (event: any, item: UserPreset) => {
    event.preventDefault();
    event.stopPropagation();

    const isFolder = !!item.folder;
    const data = isFolder ? item.folder : item.preset;

    let options = [];
    if (isFolder) {
      options = [
        {
          icon: Edit,
          label: t('editor.presets.menu.renameFolder'),
          onClick: () => setRenameFolderState({ isOpen: true, folder: data }),
        },
        {
          icon: FileDown,
          label: t('editor.presets.menu.exportFolder'),
          onClick: () => handleExport(item),
        },
        { type: OPTION_SEPARATOR },
        {
          icon: Trash2,
          isDestructive: true,
          label: t('editor.presets.menu.deleteFolder'),
          onClick: () => handleDeleteItem(data?.id ?? null, true),
        },
      ];
    } else {
      options = [
        {
          icon: Save,
          label: t('editor.presets.menu.overwrite'),
          onClick: async () => {
            const updated = overwritePreset(data?.id ?? null);
            if (updated) {
              await generateSinglePreview(updated);
            }
          },
        },
        {
          icon: Settings2,
          label: t('editor.presets.menu.configurePreset'),
          onClick: () => setConfigureModalState({ isOpen: true, preset: data as Preset }),
        },
        { type: OPTION_SEPARATOR },
        {
          icon: CopyPlus,
          label: t('editor.presets.menu.duplicatePreset'),
          onClick: async () => {
            const duplicated = duplicatePreset(data?.id ?? null);
            if (duplicated) {
              await generateSinglePreview(duplicated);
            }
          },
        },
        {
          icon: FileDown,
          label: t('editor.presets.menu.exportPreset'),
          onClick: () => handleExport(item),
        },
        { type: OPTION_SEPARATOR },
        {
          icon: Trash2,
          isDestructive: true,
          label: t('editor.presets.menu.deletePreset'),
          onClick: () => handleDeleteItem(data?.id ?? null, false),
        },
      ];
    }

    showContextMenu(event.clientX, event.clientY, options);
  };

  const handleBackgroundContextMenu = (event: any) => {
    if (!event.currentTarget.contains(event.target)) {
      return;
    }
    event.preventDefault();
    const options = [
      {
        icon: Plus,
        label: t('editor.presets.menu.newPreset'),
        onClick: () => setConfigureModalState({ isOpen: true, preset: null }),
      },
      {
        icon: FolderPlus,
        label: t('editor.presets.menu.newFolder'),
        onClick: () => setIsAddFolderModalOpen(true),
      },
      { type: OPTION_SEPARATOR },
      {
        disabled: presets.length === 0,
        icon: SortAsc,
        label: t('editor.presets.menu.sortAll'),
        onClick: sortAllPresetsAlphabetically,
      },
    ];
    showContextMenu(event.clientX, event.clientY, options);
  };

  const folders = useMemo(() => presets.filter((item: UserPreset) => item.folder), [presets]);
  const rootPresets = useMemo(() => presets.filter((item: UserPreset) => item.preset), [presets]);

  // Count presets by type for filter badges
  const presetCounts = useMemo(() => {
    let all = 0;
    let style = 0;
    let tool = 0;
    presets.forEach((item: UserPreset) => {
      if (item.preset) {
        all++;
        if (item.preset.presetType === 'style') style++;
        else if (item.preset.presetType === 'tool') tool++;
      } else if (item.folder) {
        item.folder.children.forEach((p: Preset) => {
          all++;
          if (p.presetType === 'style') style++;
          else if (p.presetType === 'tool') tool++;
        });
      }
    });
    return { all, style, tool };
  }, [presets]);

  // Filter helpers
  const filterPreset = useCallback(
    (preset: Preset) => {
      if (filterType !== 'all' && preset.presetType !== filterType) return false;
      if (searchQuery && !preset.name.toLowerCase().includes(searchQuery.toLowerCase())) return false;
      return true;
    },
    [filterType, searchQuery],
  );

  // Render a preset grid item
  const renderPresetItem = useCallback(
    (preset: Preset, index: number) => (
      <motion.div
        key={preset.id}
        animate="visible"
        custom={index}
        exit="exit"
        initial="hidden"
        layout="position"
        variants={itemVariants}
      >
        <DraggablePresetItem
          isGeneratingPreviews={isGeneratingPreviews}
          onApply={handleApplyPreset}
          onContextMenu={(e: any) => handleContextMenu(e, { preset })}
          preset={preset}
          previewUrl={previews[preset.id] || ''}
          isActive={preset.id === activePresetId}
          intensity={preset.id === activePresetId ? presetIntensity : 100}
          onIntensityChange={(val) => handleIntensityChange(preset, val)}
          onDragStateChange={handleDragStateChange}
          viewMode={viewMode}
        />
      </motion.div>
    ),
    [isGeneratingPreviews, handleApplyPreset, handleContextMenu, previews, activePresetId, presetIntensity, handleIntensityChange, handleDragStateChange, viewMode],
  );

  return (
    <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
      <div className="flex flex-col h-full">
        {/* Header */}
        <div className="p-4 flex justify-between items-center shrink-0 border-b border-surface">
          <Text variant={TextVariants.title}>{t('editor.presets.title')}</Text>
          <div className="flex items-center gap-1">
            {!isAndroid && (
              <button
                className="p-2 rounded-full hover:bg-surface transition-colors"
                onClick={onNavigateToCommunity}
                data-tooltip={t('editor.presets.tooltips.explore')}
              >
                <Users size={18} />
              </button>
            )}
            <button
              className="p-2 rounded-full hover:bg-surface transition-colors"
              disabled={isLoading}
              onClick={handleImportPresets}
              data-tooltip={t('editor.presets.tooltips.import')}
            >
              <FileUp size={18} />
            </button>
            <button
              className="p-2 rounded-full hover:bg-surface transition-colors"
              disabled={presets.length === 0 || isLoading}
              onClick={handleExportAllPresets}
              data-tooltip={t('editor.presets.tooltips.export')}
            >
              <FileDown size={18} />
            </button>
            <button
              className="p-2 rounded-full hover:bg-surface transition-colors"
              disabled={presets.length === 0 || isLoading}
              onClick={handleBatchSyncPreset}
              data-tooltip={t('editor.presets.tooltips.sync')}
            >
              <RefreshCw size={18} />
            </button>
            <button
              className="p-2 rounded-full hover:bg-surface transition-colors"
              disabled={isLoading}
              onClick={() => setConfigureModalState({ isOpen: true, preset: null })}
              data-tooltip={t('editor.presets.tooltips.saveNew')}
            >
              <Plus size={18} />
            </button>
          </div>
        </div>

        {/* Filter toolbar */}
        {presets.length > 0 && (
          <div className="px-4 pt-3 pb-2 flex items-center gap-2 shrink-0">
            {/* Type filter pills */}
            <div className="flex items-center gap-1.5 mr-auto overflow-x-auto scrollbar-hide">
              {(['all', 'style', 'tool'] as const).map((type) => (
                <button
                  key={type}
                  className={`px-3.5 py-1.5 rounded-full text-xs font-semibold transition-all duration-200 whitespace-nowrap ${
                    filterType === type
                      ? 'bg-accent text-white shadow-sm shadow-accent/25'
                      : 'bg-surface text-text-secondary hover:bg-surface-hover hover:text-text-primary'
                  }`}
                  onClick={() => setFilterType(type)}
                >
                  {t(`editor.presets.filters.${type}`)} ({presetCounts[type]})
                </button>
              ))}
            </div>
            {/* View mode toggle */}
            <div className="flex items-center bg-surface rounded-lg p-0.5">
              <button
                className={`p-2 rounded-md transition-colors ${viewMode === 'grid' ? 'bg-accent text-white' : 'text-text-secondary hover:text-text-primary'}`}
                onClick={() => setViewMode('grid')}
                data-tooltip="Grid"
              >
                <LayoutGrid size={14} />
              </button>
              <button
                className={`p-2 rounded-md transition-colors ${viewMode === 'list' ? 'bg-accent text-white' : 'text-text-secondary hover:text-text-primary'}`}
                onClick={() => setViewMode('list')}
                data-tooltip="List"
              >
                <ListIcon size={14} />
              </button>
            </div>
          </div>
        )}

        {/* Search bar (only when presets exist) */}
        {presets.length > 0 && (
          <div className="px-4 pb-2 shrink-0">
            <div className="relative">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-secondary pointer-events-none" />
              <input
                type="text"
                placeholder={t('editor.presets.searchPlaceholder')}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full bg-surface border border-border-color/40 rounded-lg pl-9 pr-3 py-2 text-sm text-text-primary placeholder:text-text-secondary outline-none focus:border-accent/50 focus:ring-1 focus:ring-accent/20 transition-all"
              />
              {searchQuery && (
                <button
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded-full hover:bg-surface-hover text-text-secondary"
                  onClick={() => setSearchQuery('')}
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                    <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              )}
            </div>
          </div>
        )}

        {/* Content area */}
        <div
          className={`grow overflow-y-auto p-4 rounded-lg transition-colors ${
            isRootOver ? 'bg-surface-hover' : ''
          }`}
          onContextMenu={handleBackgroundContextMenu}
          ref={setRootNodeRef}
        >
          {isLoading && presets.length === 0 && (
            <Text
              as="div"
              variant={TextVariants.heading}
              color={TextColors.secondary}
              weight={TextWeights.normal}
              className="text-center mt-4"
            >
              <Loader2 size={14} className="animate-spin inline-block mr-2" /> {t('editor.presets.status.loading')}
            </Text>
          )}
          {!isLoading && presets.length === 0 ? (
            <div className="text-center text-text-secondary flex flex-col items-center gap-4 pt-4">
              <Text className="max-w-xs">{t('editor.presets.status.empty')}</Text>
              {!isAndroid && (
                <Button variant="secondary" onClick={onNavigateToCommunity}>
                  <Users size={16} className="mr-2" />
                  {t('editor.presets.status.getCommunity')}
                </Button>
              )}
            </div>
          ) : viewMode === 'grid' ? (
            /* ── Grid layout ──────────────────────────────────── */
            <div className="flex flex-col gap-4">
              <AnimatePresence>
                {folders
                  .filter((item: UserPreset) => item.folder?.id !== deletingItemId)
                  .map((item: UserPreset, index: number) => {
                    const filteredChildren = item.folder?.children
                      ?.filter((p: Preset) => p.id !== deletingItemId && filterPreset(p)) ?? [];
                    return (
                      <motion.div
                        key={item.folder?.id}
                        animate="visible"
                        custom={index}
                        exit="exit"
                        initial="hidden"
                        layout="position"
                        variants={itemVariants}
                      >
                        <DroppableFolderItem
                          folder={item.folder}
                          isExpanded={item.folder?.id ? expandedFolders.has(item.folder?.id) : false}
                          onContextMenu={(e: any) => handleContextMenu(e, item)}
                          onToggle={toggleFolder}
                        >
                          <AnimatePresence>
                            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                              {filteredChildren.map((preset: Preset, ci: number) => (
                                renderPresetItem(preset, ci)
                              ))}
                            </div>
                          </AnimatePresence>
                        </DroppableFolderItem>
                      </motion.div>
                    );
                  })}
              </AnimatePresence>
              <AnimatePresence>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                  {rootPresets
                    .filter((item: UserPreset) => item.preset?.id !== deletingItemId && filterPreset(item.preset as Preset))
                    .map((item: UserPreset, index: number) => (
                      renderPresetItem(item.preset as Preset, folders.length + index)
                    ))}
                </div>
              </AnimatePresence>
            </div>
          ) : (
            /* ── List layout ──────────────────────────────────── */
            <>
              <AnimatePresence>
                {folders
                  .filter((item: UserPreset) => item.folder?.id !== deletingItemId)
                  .map((item: UserPreset, index: number) => (
                    <motion.div
                      key={item.folder?.id}
                      animate="visible"
                      custom={index}
                      exit="exit"
                      initial="hidden"
                      layout="position"
                      variants={itemVariants}
                    >
                      <DroppableFolderItem
                        folder={item.folder}
                        isExpanded={item.folder?.id ? expandedFolders.has(item.folder?.id) : false}
                        onContextMenu={(e: any) => handleContextMenu(e, item)}
                        onToggle={toggleFolder}
                      >
                        <AnimatePresence>
                          {item.folder?.children
                            .filter((p: Preset) => p.id !== deletingItemId && filterPreset(p))
                            .map((preset: Preset) => (
                              renderPresetItem(preset, 0)
                            ))}
                        </AnimatePresence>
                      </DroppableFolderItem>
                    </motion.div>
                  ))}
              </AnimatePresence>
              <AnimatePresence>
                {rootPresets
                  .filter((item: UserPreset) => item.preset?.id !== deletingItemId && filterPreset(item.preset as Preset))
                  .map((item: UserPreset, index: number) => (
                    renderPresetItem(item.preset as Preset, folders.length + index)
                  ))}
              </AnimatePresence>
            </>
          )}
        </div>

        <ConfigurePresetModal
          isOpen={configureModalState.isOpen}
          initialPreset={configureModalState.preset}
          onClose={() => setConfigureModalState({ isOpen: false, preset: null })}
          onSave={handleSaveConfiguredPreset}
        />
        <CreateFolderModal
          isOpen={isAddFolderModalOpen}
          onClose={() => setIsAddFolderModalOpen(false)}
          onSave={handleAddFolder}
        />
        <RenameFolderModal
          currentName={renameFolderState.folder?.name}
          isOpen={renameFolderState.isOpen}
          onClose={() => setRenameFolderState({ isOpen: false, folder: null })}
          onSave={handleRenameFolderSave}
        />
      </div>
      <DragOverlay>
        {activeItem ? (
          activeItem.type === 'preset' ? (
            <PresetItemDisplay
              isGeneratingPreviews={false}
              preset={activeItem.data}
              previewUrl={previews[activeItem.data.id] || ''}
              viewMode={viewMode}
            />
          ) : (
            <FolderItemDisplay folder={activeItem.data} />
          )
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}
