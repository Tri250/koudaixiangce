const fs = require('fs');
const path = require('path');

function fixFile(filePath) {
  const fullPath = path.resolve(filePath);
  if (!fs.existsSync(fullPath)) return;
  let content = fs.readFileSync(fullPath, 'utf8');
  let original = content;
  
  // ===== LibraryHeader.tsx =====
  if (filePath.includes('LibraryHeader.tsx')) {
    content = content.replace(
      "function DropdownMenu({ buttonContent, buttonTitle, children, contentClassName = 'w-56' }: any)",
      "function DropdownMenu({ buttonContent, buttonTitle, children, contentClassName = 'w-56' }: { buttonContent: React.ReactNode; buttonTitle: string; children: React.ReactNode; contentClassName?: string })"
    );
    content = content.replace(
      'const handleClickOutside = (event: any)',
      'const handleClickOutside = (event: MouseEvent)'
    );
    content = content.replace(
      "export function SearchInput({ indexingProgress, isIndexing }: any)",
      "export function SearchInput({ indexingProgress, isIndexing }: { indexingProgress: { completed: number; total: number } | null; isIndexing: boolean })"
    );
    content = content.replace(
      'function handleClickOutside(event: any)',
      'function handleClickOutside(event: MouseEvent)'
    );
    content = content.replace(
      '}: any) {\n  const {',
      '}: { filterCriteria: FilterCriteria; setFilterCriteria: (criteria: FilterCriteria) => void; sortCriteria: SortCriteria; setSortCriteria: (criteria: SortCriteria) => void; thumbnailSize: ThumbnailSize; setThumbnailSize: (size: ThumbnailSize) => void; thumbnailAspectRatio: ThumbnailAspectRatio; setThumbnailAspectRatio: (ratio: ThumbnailAspectRatio) => void; viewMode: LibraryViewMode; setViewMode: (mode: LibraryViewMode) => void; searchQuery: string; setSearchQuery: (query: string) => void }) {\n  const {'
    );
    content = content.replace(
      'const handleColorClick = (colorName: string, event: any)',
      'const handleColorClick = (colorName: string, event: React.MouseEvent<HTMLElement>)'
    );
    content = content.replace(/\.map\(\(option: any\)/g, '.map((option: { value: unknown; label: string })');
    content = content.replace(/\.filter\(\(option: any\)/g, '.filter((option: { value: unknown })');
    content = content.replace(/\.find\(\(o: any\)/g, '.find((o: { value: unknown; label?: string })');
  }

  // ===== Editor.tsx =====
  if (filePath.includes('/Editor.tsx')) {
    content = content.replace(
      '  onContextMenu(event: any): void;',
      '  onContextMenu(event: React.MouseEvent<HTMLElement>): void;'
    );
    content = content.replace(
      '  transformWrapperRef: any;',
      '  transformWrapperRef: React.RefObject<{ zoomIn: () => void; zoomOut: () => void; resetTransform: () => void } | null>;'
    );
    content = content.replace(
      '    (size: any)',
      '    (size: { width: number; height: number })'
    );
    content = content.replace(
      '    (subMaskId: string, updatedData: any)',
      '    (subMaskId: string, updatedData: Partial<SubMask>)'
    );
    content = content.replace(
      '.forEach((m: any)',
      '.forEach((m: MaskContainer)'
    );
    content = content.replace(
      '.forEach((p: any)',
      '.forEach((p: AiPatch)'
    );
    content = content.replace(
      '(maskDef: any, renderSize: any, currentAdjustments: any)',
      '(maskDef: MaskContainer, renderSize: { width: number; height: number }, currentAdjustments: Adjustments)'
    );
    content = content.replace(
      '(maskDef: any)',
      '(maskDef: MaskContainer | null)'
    );
    content = content.replace(
      'let activeMaskDef: any = null',
      'let activeMaskDef: MaskContainer | null = null'
    );
    content = content.replace(
      'const geometry: any = {}',
      'const geometry: Record<string, unknown> = {}'
    );
    content = content.replace(
      '?.map((sm: any)',
      '?.map((sm: SubMask)'
    );
    content = content.replace(
      '    (_: any, pc: PercentCrop)',
      '    (_: unknown, pc: PercentCrop)'
    );
  }

  // ===== LibraryGrid.tsx =====
  if (filePath.includes('LibraryGrid.tsx')) {
    content = content.replace(
      "function ListHeader({ widths, setWidths, containerRef, sortCriteria, onSortChange }: any)",
      "function ListHeader({ widths, setWidths, containerRef, sortCriteria, onSortChange }: { widths: Record<string, number>; setWidths: (w: Record<string, number> | ((prev: Record<string, number>) => Record<string, number>)) => void; containerRef: React.RefObject<HTMLElement | null>; sortCriteria: SortCriteria; onSortChange: (criteria: SortCriteria) => void })"
    );
    content = content.replace('setWidths((prev: any)', 'setWidths((prev: Record<string, number>)');
    content = content.replace(
      "const Column = ({ title, widthKey, nextKey, sortKey }: any)",
      "const Column = ({ title, widthKey, nextKey, sortKey }: { title: string; widthKey: string; nextKey?: string; sortKey: string })"
    );
    content = content.replace(
      'const groups: Record<string, unknown[]> = {};',
      'const groups: Record<string, Array<{ path: string; thumbnailUrl: string; rating: number; name: string }>> = {};'
    );
    content = content.replace(
      'export default function LibraryGrid(props: any)',
      'export default function LibraryGrid(props: Record<string, unknown>)'
    );
    content = content.replace('const handleWheel = (event: any)', 'const handleWheel = (event: WheelEvent)');
    content = content.replace(/\.findIndex\(\(o: any\)/g, '.findIndex((o: { id: string })');
    content = content.replace(/\.find\(\(o: any\)/g, '.find((o: { id: string; size?: number })');
    content = content.replace('.findIndex((img: any)', '.findIndex((img: { path: string })');
    content = content.replace('setSortCriteria((prev: any)', 'setSortCriteria((prev: SortCriteria)');
    content = content.replace('setWidths={(w: any)', 'setWidths={(w: Record<string, number> | ((prev: Record<string, number>) => Record<string, number>))');
  }

  // ===== Filmstrip.tsx =====
  if (filePath.includes('Filmstrip.tsx')) {
    content = content.replace('  imageRatings: any;\n', '  imageRatings: Record<string, number>;\n',);
    content = content.replace(/onContextMenu\?\: \(event: any, path: string\)/g, 'onContextMenu?: (event: React.MouseEvent<HTMLElement>, path: string)');
    content = content.replace(/onImageSelect\?\: \(path: string, event: any\)/g, 'onImageSelect?: (path: string, event: React.MouseEvent<HTMLElement>)');
    content = content.replace(/imageRatings: any;/g, 'imageRatings: Record<string, number>;');
    content = content.replace(/onContextMenu\?\: \(event: any, path: string\) => void;/g, 'onContextMenu?: (event: React.MouseEvent<HTMLElement>, path: string) => void;');
    content = content.replace(/onImageSelect\?\: \(path: string, event: any\) => void;/g, 'onImageSelect?: (path: string, event: React.MouseEvent<HTMLElement>) => void;');
    // FilmstripItemProps
    content = content.replace(
      '}: any) => {',
      '}: { images: Array<{ path: string; thumbnailUrl: string; name: string }>; activePath: string | null; onImageSelect: (path: string, event: React.MouseEvent<HTMLElement>) => void; onContextMenu?: (event: React.MouseEvent<HTMLElement>, path: string) => void; imageRatings: Record<string, number> }) => {'
    );
    // FilmstripInner props
    content = content.replace(
      '  onContextMenu?(event: any, path: string): void;',
      '  onContextMenu?(event: React.MouseEvent<HTMLElement>, path: string): void;'
    );
    content = content.replace(
      '  onImageSelect?(path: string, event: any): void;',
      '  onImageSelect?(path: string, event: React.MouseEvent<HTMLElement>): void;'
    );
    content = content.replace(
      'const handleImageSelect = (path: string, event: any)',
      'const handleImageSelect = (path: string, event: React.MouseEvent<HTMLElement>)'
    );
  }

  // ===== LensCorrectionModal.tsx =====
  if (filePath.includes('LensCorrectionModal.tsx')) {
    content = content.replace(
      'const parseFocalLength = (exif: any): number | null',
      'const parseFocalLength = (exif: Record<string, string | number | null>): number | null'
    );
    content = content.replace(
      'const parseAperture = (exif: any): number | null',
      'const parseAperture = (exif: Record<string, string | number | null>): number | null'
    );
    content = content.replace(
      'const parseDistance = (exif: any): number | null',
      'const parseDistance = (exif: Record<string, string | number | null>): number | null'
    );
    content = content.replace(
      "const distParams: any = await invoke('get_lens_distortion_params'",
      "const distParams: Record<string, number> = await invoke<Record<string, number>>('get_lens_distortion_params'"
    );
    content = content.replace(
      "invoke('load_settings').then((settings: any)",
      "invoke<Record<string, unknown>>('load_settings').then((settings: Record<string, unknown>)"
    );
    content = content.replace(/\.then\(\(m: any\) => setMakers\(m\)\)/g, '.then((m: string[]) => setMakers(m))');
    content = content.replace(/\.then\(\(l: any\) => setLenses\(l\)\)/g, '.then((l: string[]) => setLenses(l))');
    content = content.replace(
      "}).then((result: any) => setPreviewUrl(result))",
      "}).then((result: string) => setPreviewUrl(result))"
    );
  }

  // ===== LibraryView.tsx =====
  if (filePath.includes('LibraryView.tsx')) {
    content = content.replace(/handleLibraryImageSingleClick: \(\.\.\.args: any\) => void;/g, 'handleLibraryImageSingleClick: (...args: unknown[]) => void;');
    content = content.replace(/handleImageSelect: \(\.\.\.args: any\) => void;/g, 'handleImageSelect: (...args: unknown[]) => void;');
    content = content.replace(/handleRate: \(\.\.\.args: any\) => void;/g, 'handleRate: (...args: unknown[]) => void;');
    content = content.replace(/handleThumbnailContextMenu: \(\.\.\.args: any\) => void;/g, 'handleThumbnailContextMenu: (...args: unknown[]) => void;');
    content = content.replace(/handleMainLibraryContextMenu: \(\.\.\.args: any\) => void;/g, 'handleMainLibraryContextMenu: (...args: unknown[]) => void;');
    content = content.replace(/handleContinueSession: \(\.\.\.args: any\) => void;/g, 'handleContinueSession: (...args: unknown[]) => void;');
    content = content.replace(/handleGoHome: \(\.\.\.args: any\) => void;/g, 'handleGoHome: (...args: unknown[]) => void;');
    content = content.replace(/handleOpenFolder: \(\.\.\.args: any\) => void;/g, 'handleOpenFolder: (...args: unknown[]) => void;');
    content = content.replace('  requestThumbnails: any;', '  requestThumbnails: (...args: unknown[]) => void;');
  }

  // ===== ControlsPanel.tsx =====
  if (filePath.includes('ControlsPanel.tsx')) {
    content = content.replace(
      '(val: any) => setEditor({ copiedSectionAdjustments: val })',
      '(val: { section: string; values: Record<string, unknown> } | null) => setEditor({ copiedSectionAdjustments: val })'
    );
    content = content.replace(
      '    (updater: any)',
      '    (updater: Adjustments | ((prev: Adjustments) => Adjustments))'
    );
    content = content.replace(
      '.reduce((acc: any, key: string)',
      '.reduce((acc: Record<string, unknown>, key: string)'
    );
    content = content.replace(
      'setCollapsibleState((prev: any)',
      'setCollapsibleState((prev: Record<string, boolean>)'
    );
    content = content.replace(
      'const handleSectionContextMenu = (event: any, sectionName: string)',
      'const handleSectionContextMenu = (event: React.MouseEvent<HTMLElement>, sectionName: string)'
    );
    content = content.replace(
      'const adjustmentsToCopy: any = {}',
      'const adjustmentsToCopy: Record<string, unknown> = {}'
    );
    content = content.replace(
      'const resetValues: any = {}',
      'const resetValues: Record<string, unknown> = {}'
    );
    content = content.replace(
      'const options: any = [',
      'const options: Array<Record<string, unknown>> = ['
    );
    content = content.replace(
      'const SectionComponent: any = {',
      'const SectionComponent: React.ComponentType<Record<string, unknown>> | undefined = {'
    );
  }

  // ===== EditorView.tsx =====
  if (filePath.includes('EditorView.tsx')) {
    content = content.replace(
      'const panelVariants: any = {',
      'const panelVariants: Record<string, unknown> = {'
    );
    content = content.replace(
      'transformWrapperRef: RefObject<any>;',
      'transformWrapperRef: RefObject<HTMLElement | null>;'
    );
    content = content.replace(/handleEditorContextMenu: \(\.\.\.args: any\) => void;/g, 'handleEditorContextMenu: (...args: unknown[]) => void;');
    content = content.replace(/handleThumbnailContextMenu: \(\.\.\.args: any\) => void;/g, 'handleThumbnailContextMenu: (...args: unknown[]) => void;');
    content = content.replace(/handleImageClick: \(\.\.\.args: any\) => void;/g, 'handleImageClick: (...args: unknown[]) => void;');
    content = content.replace(/handleRate: \(\.\.\.args: any\) => void;/g, 'handleRate: (...args: unknown[]) => void;');
    content = content.replace('  requestThumbnails: any;', '  requestThumbnails: (...args: unknown[]) => void;');
  }

  // ===== CollapsibleSection.tsx =====
  if (filePath.includes('CollapsibleSection.tsx')) {
    content = content.replace('  children: any;\n', '  children: React.ReactNode;\n');
    content = content.replace('  onContextMenu?: any;\n', '  onContextMenu?: (e: React.MouseEvent<HTMLElement>) => void;\n');
    content = content.replace('  onToggle: any;\n', '  onToggle: () => void;\n');
    content = content.replace('  onToggleVisibility?: any;\n', '  onToggleVisibility?: () => void;\n');
    content = content.replace('const handleVisibilityClick = (e: any)', 'const handleVisibilityClick = (e: React.MouseEvent<HTMLElement>)');
  }

  // ===== ExportPanel.tsx =====
  if (filePath.includes('ExportPanel.tsx')) {
    content = content.replace(
      '  setExportState(state: any): void;',
      '  setExportState(state: Record<string, unknown>): void;'
    );
    content = content.replace('  children: any;\n', '  children: React.ReactNode;\n');
    content = content.replace(
      'const formatBytes = (bytes: number, t: any, decimals = 2)',
      'const formatBytes = (bytes: number, t: (key: string) => string, decimals = 2)'
    );
    content = content.replace(
      "const dims: any = await invoke('get_image_dimensions'",
      "const dims: { width: number; height: number } = await invoke<{ width: number; height: number }>('get_image_dimensions'"
    );
    content = content.replace(
      'const selectedFormat: any = FILE_FORMATS.find',
      'const selectedFormat: { id: string; label: string; extension: string } | undefined = FILE_FORMATS.find'
    );
  }

  // ===== Input.tsx =====
  if (filePath.includes('/Input.tsx')) {
  }

  // ===== AIPanel.tsx remaining =====
  if (filePath.includes('AIPanel.tsx')) {
    // Component props }: any) patterns
    // Need more context for each component
  }

  // ===== EditorToolbar.tsx =====
  if (filePath.includes('EditorToolbar.tsx')) {
  }

  // ===== CollageModal.tsx =====
  if (filePath.includes('CollageModal.tsx')) {
  }

  // ===== LibraryItems.tsx =====
  if (filePath.includes('LibraryItems.tsx')) {
  }

  // ===== MainLibrary.tsx =====
  if (filePath.includes('MainLibrary.tsx')) {
  }

  // ===== Basic.tsx =====
  if (filePath.includes('Basic.tsx')) {
  }

  if (content !== original) {
    fs.writeFileSync(fullPath, content, 'utf8');
    console.log(`FIXED: ${filePath}`);
  } else {
    console.log(`NO CHANGE: ${filePath}`);
  }
}

const files = [
  'src/components/panel/library/LibraryHeader.tsx',
  'src/components/panel/Editor.tsx',
  'src/components/panel/library/LibraryGrid.tsx',
  'src/components/panel/Filmstrip.tsx',
  'src/components/modals/LensCorrectionModal.tsx',
  'src/components/views/LibraryView.tsx',
  'src/components/panel/right/ControlsPanel.tsx',
  'src/components/views/EditorView.tsx',
  'src/components/ui/CollapsibleSection.tsx',
  'src/components/panel/right/ExportPanel.tsx',
];

for (const file of files) {
  fixFile(file);
}
