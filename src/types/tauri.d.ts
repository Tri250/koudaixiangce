// ============================================================================
// RapidRAW - Comprehensive Type Definitions
// ============================================================================
// Types derived from actual data shapes across stores, components, and
// Tauri invoke patterns. These replace `any` with precise types throughout
// the application.
// ============================================================================

import type { Adjustments, MaskContainer, AiPatch, Coord, Curves, MaskAdjustments, CopyPasteSettings } from '../utils/adjustments';
import type { Mask, SubMask, SubMaskMode, ToolType, MaskType } from '../components/panel/right/Masks';
import type { ImageDimensions, RenderSize } from '../hooks/useImageRenderSize';
import type {
  Panel,
  ExifOverlay,
  RawStatus,
  SortDirection,
  Theme,
  ThumbnailAspectRatio,
  LibraryViewMode,
  ThumbnailSize,
  FolderSortKey,
  FolderTreeSort,
} from '../components/ui/AppProperties';

// ============================================================================
// Tauri Invoke Result
// ============================================================================

/** Generic wrapper for Tauri invoke() responses */
export type TauriInvokeResult<T> = T;

// ============================================================================
// Window Extensions (modifier key tracking)
// ============================================================================

export interface RapidRawWindow extends Window {
  altKeyDown: boolean;
  ctrlKeyDown: boolean;
}

// ============================================================================
// EXIF & Image Metadata
// ============================================================================

export interface ExifData {
  [key: string]: string;
}

export interface ImageMetadata {
  width: number;
  height: number;
  bitsPerSample?: number;
  colorSpace?: string;
  compression?: string;
  make?: string;
  model?: string;
  lensModel?: string;
  lensMake?: string;
  focalLength?: number;
  focalLengthIn35mmFilm?: number;
  exposureTime?: string;
  fNumber?: number;
  iso?: number;
  dateTimeOriginal?: string;
  software?: string;
  whiteBalance?: string;
  flash?: string;
  meteringMode?: string;
  exposureProgram?: string;
  exposureCompensation?: number;
  sceneCaptureType?: string;
  subjectDistance?: string;
  gpsLatitude?: string;
  gpsLongitude?: string;
  orientation?: number;
}

// ============================================================================
// Selected Image
// ============================================================================

export interface ImageInfo {
  exif: ExifData;
  height: number;
  isRaw: boolean;
  isReady: boolean;
  metadata?: ImageMetadata;
  original_base64?: string;
  originalUrl: string | null;
  path: string;
  thumbnailUrl: string;
  width: number;
}

// ============================================================================
// Folder & Folder Tree
// ============================================================================

export interface FolderInfo {
  children: FolderInfo[];
  id?: string;
  name?: string;
  imageCount?: number;
}

export interface FolderState {
  expandedFolders: Set<string>;
  folderTrees: FolderInfo[];
  pinnedFolderTrees: FolderInfo[];
  currentFolderPath: string | null;
}

// ============================================================================
// Settings Types
// ============================================================================

export interface Decorations {
  titleBarStyle?: string;
  hiddenTitle?: boolean;
  transparent?: boolean;
}

export interface LastFolderState {
  expandedPaths: string[];
  scrollTop: number;
}

export interface LensProfile {
  maker: string;
  model: string;
}

export interface CameraProfile {
  maker: string;
  model: string;
}

export interface EditorSettings {
  editorPreviewResolution?: number;
  enableZoomHifi?: boolean;
  useFullDpiRendering?: boolean;
  highResZoomMultiplier?: number;
  enableLivePreviews?: boolean;
  livePreviewQuality?: string;
  canvasInputMode?: 'mouse' | 'trackpad';
  zoomSpeedMultiplier?: number;
  useWgpuRenderer?: boolean;
  linearRawMode?: string;
  tonemapperOverrideEnabled?: boolean;
  defaultRawTonemapper?: string;
  defaultNonRawTonemapper?: string;
}

export interface LibrarySettings {
  lastFolderState?: LastFolderState;
  pinnedFolders?: FolderInfo[];
  lastRootPath: string | null;
  libraryViewMode?: LibraryViewMode;
  thumbnailSize?: ThumbnailSize;
  thumbnailAspectRatio?: ThumbnailAspectRatio;
  enableFolderImageCounts?: boolean;
  folderTreeSort?: FolderTreeSort;
  folderIcons?: Record<string, string>;
  openTreeSections?: string[];
}

export interface ProcessingSettings {
  processingBackend?: string;
  linuxGpuOptimization?: boolean;
  rawHighlightCompression?: number;
}

export interface AiSettings {
  aiConnectorAddress?: string;
  aiProvider?: string;
  enableAiTagging?: boolean;
  aiVisionApiUrl?: string;
  aiVisionApiKey?: string;
  aiVisionModel?: string;
  aiRatingStrictness?: number;
}

export interface DisplaySettings {
  displayEditIcon?: boolean;
  exifOverlay?: ExifOverlay;
  isWaveformVisible?: boolean;
  waveformHeight?: number;
  activeWaveformChannel?: string;
  enableFocusMode?: boolean;
}

export interface ExportSettingsConfig {
  exportPresets?: import('../components/ui/ExportImportProperties').ExportPreset[];
}

export interface AdjustmentVisibility {
  [key: string]: boolean;
}

/** Full app settings as persisted to Tauri backend */
export interface AppSettings {
  aiConnectorAddress?: string;
  aiProvider?: string;
  decorations?: Decorations;
  editorPreviewResolution?: number;
  enableZoomHifi?: boolean;
  useFullDpiRendering?: boolean;
  highResZoomMultiplier?: number;
  enableLivePreviews?: boolean;
  livePreviewQuality?: string;
  enableAiTagging?: boolean;
  filterCriteria?: import('../components/ui/AppProperties').FilterCriteria;
  lastFolderState?: LastFolderState;
  pinnedFolders?: FolderInfo[];
  lastRootPath: string | null;
  libraryViewMode?: LibraryViewMode;
  sortCriteria?: import('../components/ui/AppProperties').SortCriteria;
  theme: Theme;
  thumbnailSize?: ThumbnailSize;
  thumbnailAspectRatio?: ThumbnailAspectRatio;
  uiVisibility?: import('../components/ui/AppProperties').UiVisibility;
  adjustmentVisibility?: AdjustmentVisibility;
  rawHighlightCompression?: number;
  processingBackend?: string;
  linuxGpuOptimization?: boolean;
  exportPresets?: import('../components/ui/ExportImportProperties').ExportPreset[];
  myLenses?: LensProfile[];
  enableFolderImageCounts?: boolean;
  displayEditIcon?: boolean;
  linearRawMode?: string;
  enableXmpSync?: boolean;
  createXmpIfMissing?: boolean;
  isWaveformVisible?: boolean;
  waveformHeight?: number;
  activeWaveformChannel?: string;
  useWgpuRenderer?: boolean;
  canvasInputMode?: 'mouse' | 'trackpad';
  zoomSpeedMultiplier?: number;
  keybinds?: Record<string, string[]>;
  tonemapperOverrideEnabled?: boolean;
  defaultRawTonemapper?: string;
  defaultNonRawTonemapper?: string;
  copyPasteSettings?: CopyPasteSettings;
  enableFocusMode?: boolean;
  openTreeSections?: string[];
  folderIcons?: Record<string, string>;
  exifOverlay?: ExifOverlay;
  language?: string;
  folderTreeSort?: FolderTreeSort;
  taggingShortcuts?: string[];
  aiVisionApiUrl?: string;
  aiVisionApiKey?: string;
  aiVisionModel?: string;
  aiRatingStrictness?: number;
}

// ============================================================================
// Adjustment Settings
// ============================================================================

export type AdjustmentSettings = Adjustments;

export type ColorSpace = 'srgb' | 'p3' | 'rec2020' | 'prophoto' | 'adobergb';

export type HdrHighlightMode = 'recover' | 'clip' | 'rolloff' | 'smart_blend';

export type SkinSmoothingMethod = 'neutral_gray' | 'bilateral' | 'frequency_separation';

export type RetouchingTab = 'face' | 'skin' | 'body' | 'hair' | 'creative';

// ============================================================================
// Crop & Transform
// ============================================================================

export interface CropSettings {
  aspectRatio: number | null;
  crop: import('react-image-crop').Crop | null;
  flipHorizontal: boolean;
  flipVertical: boolean;
  orientationSteps: number;
  rotation: number;
}

export type OverlayMode = string;

// ============================================================================
// Export Settings
// ============================================================================

export interface ExportSettings {
  filenameTemplate: string | null;
  jpegQuality: number;
  keepMetadata: boolean;
  preserveTimestamps: boolean;
  resize: {
    mode: string;
    value: number;
    dontEnlarge: boolean;
  } | null;
  stripGps: boolean;
  watermark: import('../components/ui/ExportImportProperties').WatermarkSettings | null;
  exportMasks?: boolean;
  preserveFolders?: boolean;
}

// ============================================================================
// Mask Data & Configuration
// ============================================================================

export interface MaskParameterConfig {
  key: string;
  min: number;
  max: number;
  step: number;
  multiplier?: number;
  defaultValue: number;
}

export interface SubMaskConfig {
  parameters?: MaskParameterConfig[];
  showBrushTools?: boolean;
  showFlowControl?: boolean;
}

export interface MaskData {
  containers: MaskContainer[];
  aiPatches: AiPatch[];
  activeMaskContainerId: string | null;
  activeMaskId: string | null;
  activeAiPatchContainerId: string | null;
  activeAiSubMaskId: string | null;
}

export interface AiMaskResult {
  maskDataBase64: string | null;
  grow: number;
  feather: number;
}

// ============================================================================
// Preset Data
// ============================================================================

export interface PresetData {
  adjustments: Partial<Adjustments>;
  folder?: FolderInfo;
  id: string;
  name: string;
  includeMasks?: boolean;
  includeCropTransform?: boolean;
  presetType?: 'tool' | 'style';
}

export interface PresetConfig {
  folder?: FolderInfo;
  id?: string;
  name?: string;
  preset?: PresetData;
}

// ============================================================================
// Process Result & Options
// ============================================================================

export interface ProcessResult {
  success: boolean;
  error?: string;
  path?: string;
  data?: unknown;
}

export interface ProcessingOptions {
  format: string;
  quality: number;
  resize?: { mode: string; value: number; dontEnlarge: boolean };
  keepMetadata: boolean;
  preserveTimestamps: boolean;
  stripGps: boolean;
  exportMasks?: boolean;
  preserveFolders?: boolean;
  watermark?: import('../components/ui/ExportImportProperties').WatermarkSettings | null;
}

// ============================================================================
// Keyboard Shortcuts
// ============================================================================

export interface KeyboardShortcut {
  action: string;
  description: string;
  defaultCombo: string[];
  section: 'library' | 'view' | 'rating' | 'panels' | 'editing';
}

/** Combined store state available to keyboard shortcut handlers */
export interface ShortcutStoreState {
  editor: import('../store/useEditorStore').EditorState;
  library: import('../store/useLibraryStore').LibraryState;
  ui: import('../store/useUIStore').UIState;
  settings: import('../store/useSettingsStore').SettingsState;
  process: import('../store/useProcessStore').ProcessState;
}

export interface ShortcutAction {
  shouldFire?: (state: ShortcutStoreState) => boolean;
  execute: (event: KeyboardEvent, state: ShortcutStoreState) => void;
}

export interface ShortcutHandler {
  match: (event: KeyboardEvent, state: ShortcutStoreState) => boolean;
  execute: (event: KeyboardEvent, state: ShortcutStoreState) => void;
}

// ============================================================================
// Retouching & Portrait
// ============================================================================

export interface FaceLandmark {
  x: number;
  y: number;
  type: string;
  confidence?: number;
}

export interface BodyKeypoint {
  x: number;
  y: number;
  score: number;
  name: string;
}

export interface LiquifyStroke {
  points: Coord[];
  radius: number;
  strength: number;
  type: 'push' | 'twirl' | 'pinch' | 'bloat' | 'reconstruct';
}

/** Section adjustments copied for paste operations */
export type CopiedSectionAdjustments = Partial<Adjustments> & {
  sections?: string[];
};

// ============================================================================
// Canvas & Viewport
// ============================================================================

export interface CanvasState {
  zoom: number;
  displaySize: ImageDimensions;
  previewSize: ImageDimensions;
  baseRenderSize: ImageDimensions;
  originalSize: ImageDimensions;
  showOriginal: boolean;
  isSliderDragging: boolean;
}

export interface ViewportState {
  positionX: number;
  positionY: number;
  scale: number;
}

// ============================================================================
// Konva Types (for refs and events)
// ============================================================================

export type KonvaStage = import('konva').Stage;
export type KonvaLayer = import('konva').Layer;
export type KonvaShape = import('konva').Shape;
export type KonvaTransformer = import('konva').Transformer;
export type KonvaEllipse = import('konva').Ellipse;
export type KonvaLine = import('konva').Line;
export type KonvaGroup = import('konva').Group;
export type KonvaRect = import('konva').Rect;
export type KonvaCircle = import('konva').Circle;

/** Event from Konva interactions */
export interface KonvaPointerEvent {
  target: KonvaShape;
  evt: MouseEvent;
  currentTarget: KonvaShape;
  pointerId: number;
}

// ============================================================================
// Image Canvas Types
// ============================================================================

export interface CursorPreview {
  visible: boolean;
  x: number;
  y: number;
}

export interface DrawnLine {
  brushSize: number;
  feather?: number;
  flow?: number;
  points: Coord[];
  tool: ToolType;
}

export interface CloneHealMarker {
  id: string;
  type: 'clone' | 'heal';
  sourceX: number;
  sourceY: number;
  destX: number;
  destY: number;
  normX: number;
  normY: number;
}

export interface DragStartParams {
  x: number;
  y: number;
  containerId: string;
  subMaskId: string | null;
}

export interface StraightenLine {
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface LocalDrawParams {
  subMaskId: string;
  points: Coord[];
  brushSize: number;
  feather: number;
  flow: number;
  tool: ToolType;
}

/** Live mask preview definition for on-the-fly mask overlay generation */
export interface LiveMaskPreviewDef {
  type: Mask;
  parameters?: Record<string, number | string | boolean | number[] | null>;
  maskDataBase64?: string | null;
}

// ============================================================================
// DnD (drag-and-drop) Types
// ============================================================================

export interface MaskDragData {
  type: 'Container' | 'SubMask' | 'Creation';
  item?: MaskContainer | SubMask;
  maskType?: Mask;
  parentId?: string;
}

// ============================================================================
// Context Menu
// ============================================================================

export interface MenuOption {
  color?: string;
  disabled?: boolean;
  icon?: React.ReactNode;
  isDestructive?: boolean;
  label?: string;
  onClick?(): void;
  onRightClick?(): void;
  submenu?: MenuOption[];
  type?: string;
}

// ============================================================================
// Collage
// ============================================================================

export interface CollageConfig {
  columns: number;
  rows: number;
  gap: number;
  padding: number;
  backgroundColor: string;
  borderRadius: number;
}

// ============================================================================
// Panorama
// ============================================================================

export interface PanoramaConfig {
  projection: string;
  blendMode: string;
}

// ============================================================================
// HDR Merge
// ============================================================================

export interface HdrMergeConfig {
  alignmentMethod: string;
  deghostingStrength: number;
}

// ============================================================================
// Denoise
// ============================================================================

export interface DenoiseConfig {
  strength: number;
  preserveDetail: number;
  colorDenoise: number;
}

// ============================================================================
// Smart Album
// ============================================================================

export interface SmartAlbumRule {
  field: string;
  operator: string;
  value: string | number | boolean;
}

export interface SmartAlbumDefinition {
  id: string;
  name: string;
  icon?: string;
  conjunction: 'AND' | 'OR';
  rules: SmartAlbumRule[];
}

// ============================================================================
// Community Presets
// ============================================================================

export interface CommunityPreset {
  id: string;
  name: string;
  author: string;
  description?: string;
  adjustments: Partial<Adjustments>;
  previewUrl?: string;
  downloadCount?: number;
  tags?: string[];
}

// ============================================================================
// Color Profiles (from Tauri backend)
// ============================================================================

export interface ColorProfile {
  name: string;
  path: string;
  description?: string;
}

// ============================================================================
// External Edit Session
// ============================================================================

export interface ExternalEditSession {
  source: string;
  output: string;
  format: string;
  jpegQuality: number;
}

// ============================================================================
// Waveform & Histogram
// ============================================================================

export interface WaveformData {
  blue: string;
  green: string;
  height: number;
  luma: string;
  red: string;
  rgb: string;
  parade: string;
  vectorscope: string;
  width: number;
}

export interface ChannelConfig {
  [index: string]: ColorData;
  [import('../utils/adjustments').ActiveChannel.Luma]: ColorData;
  [import('../utils/adjustments').ActiveChannel.Red]: ColorData;
  [import('../utils/adjustments').ActiveChannel.Green]: ColorData;
  [import('../utils/adjustments').ActiveChannel.Blue]: ColorData;
}

export interface ColorData {
  color: string;
  data: number[];
}

// ============================================================================
// Tauri Event Payloads
// ============================================================================

export interface IndexingProgressPayload {
  current: number;
  total: number;
  stage: string;
}

export interface ThumbnailProgressPayload {
  current: number;
  total: number;
  path: string;
}

export interface ExportProgressPayload {
  current: number;
  total: number;
  path: string;
}

export interface AiMaskGeneratedPayload {
  subMaskId: string;
  maskDataBase64: string;
}

export interface AiConnectorStatusPayload {
  connected: boolean;
  address: string;
}

// ============================================================================
// Store State Types (re-exported for convenience)
// ============================================================================

export type EditorState = import('../store/useEditorStore').EditorState;
export type LibraryState = import('../store/useLibraryStore').LibraryState;
export type ProcessState = import('../store/useProcessStore').ProcessState;
export type SettingsState = import('../store/useSettingsStore').SettingsState;
export type UIState = import('../store/useUIStore').UIState;

// ============================================================================
// UI Component Props (replacing `any` in component signatures)
// ============================================================================

/** Props for BrushTools sub-component in MasksPanel */
export interface BrushToolsProps {
  settings: import('../components/ui/AppProperties').BrushSettings;
  onSettingsChange: (updater: (prev: import('../components/ui/AppProperties').BrushSettings) => import('../components/ui/AppProperties').BrushSettings) => void;
  onDragStateChange?: (isDragging: boolean) => void;
}

/** Props for FlowBrushTool sub-component in MasksPanel */
export interface FlowBrushToolProps {
  flow: number;
  onFlowChange: (value: number) => void;
  settings: import('../components/ui/AppProperties').BrushSettings;
  onSettingsChange: (updater: (prev: import('../components/ui/AppProperties').BrushSettings) => import('../components/ui/AppProperties').BrushSettings) => void;
  onDragStateChange?: (isDragging: boolean) => void;
}

/** Props for SettingsPanel component */
export interface SettingsPanelProps {
  appSettings: AppSettings;
  onBack(): void;
  onLibraryRefresh(): void;
  onSettingsChange(settings: AppSettings): Promise<void>;
  rootPaths: string[];
}

/** Props for DataActionItem in SettingsPanel */
export interface DataActionItemProps {
  buttonAction(): void;
  buttonText: string;
  description: React.ReactNode;
  disabled?: boolean;
  icon: React.ReactNode;
  isProcessing: boolean;
  message: string;
  title: string;
}

/** Props for SettingItem wrapper in SettingsPanel */
export interface SettingItemProps {
  children: React.ReactNode;
  description?: string;
  label: string;
}

/** Props for DroppableFolderItem in PresetsPanel */
export interface DroppableFolderItemProps {
  children: React.ReactNode;
  folder: FolderInfo;
  isExpanded: boolean;
  onContextMenu(event: React.MouseEvent, folder: FolderInfo): void;
  onToggle(id: string): void;
}

/** Props for DraggablePresetItem in PresetsPanel */
export interface DraggablePresetItemProps {
  isGeneratingPreviews: boolean;
  onApply(preset: PresetData): void;
  onContextMenu(event: React.MouseEvent, preset: PresetData): void;
  preset: PresetData;
  previewUrl: string;
  isActive?: boolean;
  intensity?: number;
  onIntensityChange?: (val: number) => void;
  onDragStateChange?: (isDragging: boolean) => void;
  viewMode?: 'grid' | 'list';
}

/** Props for Folder in PresetsPanel */
export interface PresetFolderProps {
  folder: FolderInfo;
}

/** State for a single folder in PresetsPanel */
export interface PresetFolderState {
  isOpen: boolean;
  folder: FolderInfo;
}

// ============================================================================
// Utility Types
// ============================================================================

/** State updater - can be partial state or a function receiving previous state */
export type StateUpdater<T> = Partial<T> | ((state: T) => Partial<T>);

/** For store set functions that accept updaters */
export type StoreSetter<T> = (updater: StateUpdater<T>) => void;
