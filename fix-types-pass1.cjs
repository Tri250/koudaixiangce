const fs = require('fs');
const path = require('path');

function fixFile(filePath) {
  const fullPath = path.resolve(filePath);
  if (!fs.existsSync(fullPath)) return;
  let content = fs.readFileSync(fullPath, 'utf8');
  let original = content;
  
  // ===== FolderTree.tsx =====
  // The onContextMenu uses native MouseEvent, not React.MouseEvent<HTMLElement>
  // because it's used in native event handlers, not JSX
  if (filePath.includes('FolderTree.tsx')) {
    content = content.replace(
      'onContextMenu(event: React.MouseEvent<HTMLElement>, path: string | null, isPinned?: boolean): void;',
      'onContextMenu(event: MouseEvent, path: string | null, isPinned?: boolean): void;'
    );
    content = content.replace(
      'onAlbumContextMenu(event: React.MouseEvent<HTMLElement>, item: AlbumItem | null): void;',
      'onAlbumContextMenu(event: MouseEvent, item: AlbumItem | null): void;'
    );
    content = content.replace(
      'onContextMenu(event: React.MouseEvent<HTMLElement>, path: string, isPinned?: boolean): void;',
      'onContextMenu(event: MouseEvent, path: string, isPinned?: boolean): void;'
    );
    content = content.replace(
      'onContextMenu: (e: React.MouseEvent<HTMLElement>, item: AlbumItem)',
      'onContextMenu: (e: MouseEvent, item: AlbumItem)'
    );
    content = content.replace(
      'const handleEmptyAreaContextMenu = (e: React.MouseEvent<HTMLElement>)',
      'const handleEmptyAreaContextMenu = (e: MouseEvent)'
    );
    // The handleFolderIconClick may also need to be MouseEvent
    content = content.replace(
      'const handleFolderIconClick = (e: React.MouseEvent<HTMLElement>)',
      'const handleFolderIconClick = (e: MouseEvent)'
    );
  }

  // ===== MainLibrary.tsx =====
  if (filePath.includes('MainLibrary.tsx')) {
    content = content.replace(
      '  onContextMenu(event: React.MouseEvent<HTMLElement>, path: string): void;',
      '  onContextMenu(event: MouseEvent, path: string): void;'
    );
    content = content.replace(
      '  onEmptyAreaContextMenu(event: React.MouseEvent<HTMLElement>): void;',
      '  onEmptyAreaContextMenu(event: MouseEvent): void;'
    );
    content = content.replace(
      '  onImageClick(path: string, event: React.MouseEvent<HTMLElement>): void;',
      '  onImageClick(path: string, event: MouseEvent): void;'
    );
  }

  // ===== BottomBar.tsx =====
  if (filePath.includes('BottomBar.tsx')) {
    content = content.replace(
      '  onContextMenu?(event: React.MouseEvent<HTMLElement>, path: string): void;',
      '  onContextMenu?(event: MouseEvent, path: string): void;'
    );
    content = content.replace(
      '  onImageSelect?(path: string, event: React.MouseEvent<HTMLElement>): void;',
      '  onImageSelect?(path: string, event: MouseEvent): void;'
    );
  }

  // ===== Filmstrip.tsx =====
  if (filePath.includes('Filmstrip.tsx')) {
    content = content.replace(
      /onContextMenu\?\: \(event: React\.MouseEvent<HTMLElement>, path: string\)/g,
      'onContextMenu?: (event: MouseEvent, path: string)'
    );
    content = content.replace(
      /onImageSelect\?\: \(path: string, event: React\.MouseEvent<HTMLElement>\)/g,
      'onImageSelect?: (path: string, event: MouseEvent)'
    );
    content = content.replace(
      /onContextMenu\?\: \(event: React\.MouseEvent<HTMLElement>, path: string\) => void;/g,
      'onContextMenu?: (event: MouseEvent, path: string) => void;'
    );
    content = content.replace(
      /onImageSelect\?\: \(path: string, event: React\.MouseEvent<HTMLElement>\): void;/g,
      'onImageSelect?: (path: string, event: MouseEvent): void;'
    );
    content = content.replace(
      'onImageSelect: (path: string, event: React.MouseEvent<HTMLElement>)',
      'onImageSelect: (path: string, event: MouseEvent)'
    );
    content = content.replace(
      'const handleImageSelect = (path: string, event: React.MouseEvent<HTMLElement>)',
      'const handleImageSelect = (path: string, event: MouseEvent)'
    );
  }

  // ===== LibraryItems.tsx =====
  // }: Record<string, unknown>) => { needs to be more specific
  if (filePath.includes('LibraryItems.tsx')) {
    // Replace generic Record<string, unknown> with proper props
    content = content.replace(/\}: Record<string, unknown>\) => \{/g, '}: { images: Array<SelectedImage>; activePath: string | null; onImageSelect: (path: string, event: MouseEvent) => void; onContextMenu?: (event: MouseEvent, path: string) => void; imageRatings: Record<string, number>; thumbnailSize: ThumbnailSize }) => {');
  }

  // ===== LibraryHeader.tsx =====
  if (filePath.includes('LibraryHeader.tsx')) {
    // The handleClickOutside should use native MouseEvent, not React.MouseEvent
    content = content.replace(
      'const handleClickOutside = (event: MouseEvent)',
      'const handleClickOutside = (event: Event)'
    );
    content = content.replace(
      'function handleClickOutside(event: MouseEvent)',
      'function handleClickOutside(event: Event)'
    );
  }

  // ===== CollapsibleSection.tsx =====
  if (filePath.includes('CollapsibleSection.tsx')) {
    content = content.replace(
      "  onContextMenu?: (e: React.MouseEvent<HTMLElement>) => void;\n",
      "  onContextMenu?: (e: React.MouseEvent) => void;\n"
    );
  }

  // ===== ControlsPanel.tsx =====
  if (filePath.includes('ControlsPanel.tsx')) {
    content = content.replace(
      'const handleSectionContextMenu = (event: React.MouseEvent<HTMLElement>, sectionName: string)',
      'const handleSectionContextMenu = (event: React.MouseEvent, sectionName: string)'
    );
  }

  // ===== PresetsPanel.tsx =====
  if (filePath.includes('PresetsPanel.tsx')) {
    content = content.replace(
      'onContextMenu(event: React.MouseEvent<HTMLElement>, folder: Folder): void;',
      'onContextMenu(event: MouseEvent, folder: Folder): void;'
    );
    content = content.replace(
      'onContextMenu(event: React.MouseEvent<HTMLElement>, preset: Preset): void;',
      'onContextMenu(event: MouseEvent, preset: Preset): void;'
    );
    content = content.replace(
      'const handleContextMenu = (event: React.MouseEvent<HTMLElement>, item: UserPreset)',
      'const handleContextMenu = (event: MouseEvent, item: UserPreset)'
    );
    content = content.replace(
      'const handleBackgroundContextMenu = (event: React.MouseEvent<HTMLElement>)',
      'const handleBackgroundContextMenu = (event: MouseEvent)'
    );
  }

  // ===== ExportPanel.tsx =====
  if (filePath.includes('ExportPanel.tsx')) {
    content = content.replace(
      '  setExportState(state: Record<string, unknown>): void;',
      '  setExportState(state: Partial<Record<string, unknown>>): void;'
    );
    content = content.replace(
      "const dims: { width: number; height: number } = await invoke<{ width: number; height: number }>('get_image_dimensions'",
      "const dims = await invoke<{ width: number; height: number }>('get_image_dimensions'"
    );
  }

  // ===== LibraryGrid.tsx =====
  if (filePath.includes('LibraryGrid.tsx')) {
    content = content.replace(
      'export default function LibraryGrid(props: Record<string, unknown>)',
      'export default function LibraryGrid(props: { images: Array<SelectedImage>; activePath: string | null; onImageSelect: (path: string, event: MouseEvent) => void; onContextMenu?: (event: MouseEvent, path: string) => void; imageRatings: Record<string, number>; thumbnailSize: ThumbnailSize })'
    );
    content = content.replace(
      'const handleWheel = (event: WheelEvent)',
      'const handleWheel = (event: React.WheelEvent<HTMLElement>)'
    );
  }

  // ===== AppProperties.tsx =====
  // The Folder.children: Array<Folder | Preset> might cause issues since Folder.children is recursive
  if (filePath.includes('AppProperties.tsx')) {
    // Fix Folder.children to be self-referential properly
    content = content.replace(
      '  children?: Array<Folder | Preset>;\n',
      '  children?: Array<UserPreset>;\n'
    );
    // Need to import UserPreset
    if (!content.includes("import { UserPreset }")) {
      content = content.replace(
        "import { ToolType } from '../panel/right/Masks';",
        "import { ToolType } from '../panel/right/Masks';\nimport type { UserPreset } from '../../hooks/usePresets';"
      );
    }
  }

  // ===== Adjustments index signature =====
  // The [index: string]: unknown in Adjustments and MaskAdjustments breaks everything
  // because it makes all properties type `unknown` when accessed via string index
  // Revert to more specific index signatures
  if (filePath.includes('adjustments.ts')) {
    content = content.replace(
      '  [index: string]: unknown;\n  aiPatches',
      '  [index: string]: number | string | boolean | Array<unknown> | Record<string, unknown> | null | undefined;\n  aiPatches'
    );
    content = content.replace(
      '  [index: string]: unknown;\n  blacks',
      '  [index: string]: number | string | boolean | Array<unknown> | Record<string, unknown> | null | undefined;\n  blacks'
    );
  }

  if (content !== original) {
    fs.writeFileSync(fullPath, content, 'utf8');
    console.log(`FIXED: ${filePath}`);
  } else {
    console.log(`NO CHANGE: ${filePath}`);
  }
}

const files = [
  'src/components/panel/FolderTree.tsx',
  'src/components/panel/MainLibrary.tsx',
  'src/components/panel/BottomBar.tsx',
  'src/components/panel/Filmstrip.tsx',
  'src/components/panel/library/LibraryItems.tsx',
  'src/components/panel/library/LibraryHeader.tsx',
  'src/components/ui/CollapsibleSection.tsx',
  'src/components/panel/right/ControlsPanel.tsx',
  'src/components/panel/right/PresetsPanel.tsx',
  'src/components/panel/right/ExportPanel.tsx',
  'src/components/panel/library/LibraryGrid.tsx',
  'src/components/ui/AppProperties.tsx',
  'src/utils/adjustments.ts',
];

for (const file of files) {
  fixFile(file);
}
