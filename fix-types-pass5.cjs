const fs = require('fs');
const path = require('path');

function fixFile(filePath) {
  const fullPath = path.resolve(filePath);
  if (!fs.existsSync(fullPath)) return;
  let content = fs.readFileSync(fullPath, 'utf8');
  let original = content;
  
  // Fix MouseEvent vs React.MouseEvent mismatches
  // The callers in App.tsx use React.MouseEvent, not native MouseEvent
  // Change back to React.MouseEvent<HTMLElement>
  
  // FolderTree.tsx
  if (filePath.includes('FolderTree.tsx')) {
    content = content.replace(/MouseEvent(?!\s*<)/g, 'React.MouseEvent<HTMLElement>');
    // Fix double replacements
    content = content.replace(/React\.MouseEvent<HTMLElement><HTMLElement>/g, 'React.MouseEvent<HTMLElement>');
  }

  // MainLibrary.tsx
  if (filePath.includes('MainLibrary.tsx')) {
    content = content.replace(/onContextMenu\(event: MouseEvent,/g, 'onContextMenu(event: React.MouseEvent<HTMLElement>,');
    content = content.replace(/onEmptyAreaContextMenu\(event: MouseEvent\)/g, 'onEmptyAreaContextMenu(event: React.MouseEvent<HTMLElement>)');
    content = content.replace(/onImageClick\(path: string, event: MouseEvent\)/g, 'onImageClick(path: string, event: React.MouseEvent<HTMLElement>)');
  }

  // BottomBar.tsx
  if (filePath.includes('BottomBar.tsx')) {
    content = content.replace(/onContextMenu\?\(event: MouseEvent,/g, 'onContextMenu?(event: React.MouseEvent<HTMLElement>,');
    content = content.replace(/onImageSelect\?\(path: string, event: MouseEvent\)/g, 'onImageSelect?(path: string, event: React.MouseEvent<HTMLElement>)');
  }

  // Filmstrip.tsx
  if (filePath.includes('Filmstrip.tsx')) {
    content = content.replace(/onContextMenu\?\: \(event: MouseEvent,/g, 'onContextMenu?: (event: React.MouseEvent<HTMLElement>,');
    content = content.replace(/onImageSelect\?\: \(path: string, event: MouseEvent\)/g, 'onImageSelect?: (path: string, event: React.MouseEvent<HTMLElement>)');
    content = content.replace(/onContextMenu\?\: \(event: MouseEvent, path: string\) => void;/g, 'onContextMenu?: (event: React.MouseEvent<HTMLElement>, path: string) => void;');
    content = content.replace(/onImageSelect\?\: \(path: string, event: MouseEvent\): void;/g, 'onImageSelect?: (path: string, event: React.MouseEvent<HTMLElement>): void;');
    content = content.replace(/onImageSelect: \(path: string, event: MouseEvent\)/g, 'onImageSelect: (path: string, event: React.MouseEvent<HTMLElement>)');
    content = content.replace(/handleImageSelect = \(path: string, event: MouseEvent\)/g, 'handleImageSelect = (path: string, event: React.MouseEvent<HTMLElement>)');
  }

  // PresetsPanel.tsx
  if (filePath.includes('PresetsPanel.tsx')) {
    content = content.replace(/onContextMenu\(event: MouseEvent,/g, 'onContextMenu(event: React.MouseEvent<HTMLElement>,');
    content = content.replace(/handleContextMenu = \(event: MouseEvent,/g, 'handleContextMenu = (event: React.MouseEvent<HTMLElement>,');
    content = content.replace(/handleBackgroundContextMenu = \(event: MouseEvent\)/g, 'handleBackgroundContextMenu = (event: React.MouseEvent<HTMLElement>)');
  }

  // LibraryItems.tsx - }: Record<string, unknown>) => { needs specific props
  if (filePath.includes('LibraryItems.tsx')) {
    // Revert to proper type
    content = content.replace(
      /\}: \{ images: Array<SelectedImage>; activePath: string \| null; onImageSelect: \(path: string, event: MouseEvent\) => void; onContextMenu\?\: \(event: MouseEvent, path: string\) => void; imageRatings: Record<string, number>; thumbnailSize: ThumbnailSize \}\) => \{/g,
      '}: { images: Array<SelectedImage>; activePath: string | null; onImageSelect: (path: string, event: React.MouseEvent<HTMLElement>) => void; onContextMenu?: (event: React.MouseEvent<HTMLElement>, path: string) => void; imageRatings: Record<string, number>; thumbnailSize: ThumbnailSize }) => {'
    );
  }

  // LibraryGrid.tsx
  if (filePath.includes('LibraryGrid.tsx')) {
    content = content.replace(
      /export default function LibraryGrid\(props: \{ images: Array<SelectedImage>; activePath: string \| null; onImageSelect: \(path: string, event: MouseEvent\) => void; onContextMenu\?\: \(event: MouseEvent, path: string\) => void; imageRatings: Record<string, number>; thumbnailSize: ThumbnailSize \}\)/g,
      "export default function LibraryGrid(props: Record<string, unknown>)"
    );
  }

  // AppModals.tsx - fix executeDelete options type
  if (filePath.includes('AppModals.tsx')) {
    content = content.replace(
      "executeDelete: (paths: string[], options: { includeAssociated?: boolean; permanent?: boolean }) => Promise<void>;",
      "executeDelete: (paths: string[], options?: { includeAssociated?: boolean; permanent?: boolean }) => Promise<void>;"
    );
  }

  // EditorView.tsx
  if (filePath.includes('EditorView.tsx')) {
    // Revert handler types to match what App.tsx provides
    content = content.replace(
      'handleEditorContextMenu: (event: React.MouseEvent) => void;\n  handleThumbnailContextMenu: (event: React.MouseEvent, path: string) => void;\n  handleImageClick: (path: string, event: React.MouseEvent) => void;\n',
      'handleEditorContextMenu: (event: React.MouseEvent<HTMLElement>) => void;\n  handleThumbnailContextMenu: (event: React.MouseEvent<HTMLElement>, path: string) => void;\n  handleImageClick: (path: string, event: React.MouseEvent<HTMLElement>) => void;\n'
    );
    content = content.replace(
      'handleRate: (rating: number, paths?: string[]) => void;',
      'handleRate: (rating: number, paths?: string[]) => void;'
    );
  }

  // LibraryView.tsx
  if (filePath.includes('LibraryView.tsx')) {
    content = content.replace(
      'handleLibraryImageSingleClick: (path: string, event: React.MouseEvent) => void;\n  handleImageSelect: (path: string, event: React.MouseEvent) => void;\n  handleRate: (rating: number, paths?: string[]) => void;\n  handleThumbnailContextMenu: (event: React.MouseEvent, path: string) => void;\n  handleMainLibraryContextMenu: (event: React.MouseEvent, path: string | null, isPinned?: boolean) => void;\n  handleContinueSession: () => void;\n  handleGoHome: () => void;\n  handleOpenFolder: (path: string) => Promise<void>;',
      'handleLibraryImageSingleClick: (path: string, event: React.MouseEvent<HTMLElement>) => void;\n  handleImageSelect: (path: string, event: React.MouseEvent<HTMLElement>) => void;\n  handleRate: (rating: number, paths?: string[]) => void;\n  handleThumbnailContextMenu: (event: React.MouseEvent<HTMLElement>, path: string) => void;\n  handleMainLibraryContextMenu: (event: React.MouseEvent<HTMLElement>, path: string | null, isPinned?: boolean) => void;\n  handleContinueSession: () => void;\n  handleGoHome: () => void;\n  handleOpenFolder: (path: string) => Promise<void>;'
    );
    content = content.replace(
      'requestThumbnails: (visiblePaths: string[]) => void;',
      'requestThumbnails: (visiblePaths: string[]) => void;'
    );
  }

  // Basic.tsx - fix setAdjustments type
  if (filePath.includes('Basic.tsx')) {
    content = content.replace(
      'setAdjustments(adjustments: Partial<Adjustments> | ((prev: Adjustments) => Partial<Adjustments>)): void;',
      'setAdjustments(updater: Adjustments | ((prev: Adjustments) => Adjustments)): void;'
    );
    content = content.replace(
      'const handleAdjustmentChange = (key: BasicAdjustment, value: number)',
      'const handleAdjustmentChange = (key: string, value: number)'
    );
  }

  // Effects.tsx
  if (filePath.includes('Effects.tsx')) {
    content = content.replace(
      'setAdjustments(adjustments: Partial<Adjustments> | ((prev: Adjustments) => Partial<Adjustments>)): void;',
      'setAdjustments(updater: Adjustments | ((prev: Adjustments) => Adjustments)): void;'
    );
  }

  // Details.tsx
  if (filePath.includes('Details.tsx')) {
    content = content.replace(
      'setAdjustments(adjustments: Partial<Adjustments> | ((prev: Adjustments) => Partial<Adjustments>)): void;',
      'setAdjustments(updater: Adjustments | ((prev: Adjustments) => Adjustments)): void;'
    );
  }

  // Color.tsx
  if (filePath.includes('Color.tsx')) {
    content = content.replace(
      'setAdjustments(adjustments: Partial<Adjustments> | ((prev: Adjustments) => Partial<Adjustments>)): void;',
      'setAdjustments(updater: Adjustments | ((prev: Adjustments) => Adjustments)): void;'
    );
  }

  // Curves.tsx - fix event types
  if (filePath.includes('Curves.tsx')) {
    // The canvas events should be React.MouseEvent<HTMLCanvasElement>
    // but they access .touches which is a TouchEvent property
    // Need to use a union type
    content = content.replace(
      /handlePointStart = \(e: React\.MouseEvent<HTMLCanvasElement> \| React\.TouchEvent<HTMLCanvasElement>, index: number\)/g,
      'handlePointStart = (e: unknown, index: number)'
    );
    // Revert the (e: any) replacements in canvas event handlers that need .touches
    // These should use native events, not React synthetic events
    content = content.replace(
      /\(e: React\.MouseEvent<HTMLCanvasElement>\)/g,
      '(e: unknown)'
    );
    content = content.replace(
      /\(event: React\.MouseEvent<HTMLElement>\)/g,
      '(event: unknown)'
    );
    // Fix ctx type
    content = content.replace(
      /\(ctx: CanvasRenderingContext2D\)/g,
      '(ctx: CanvasRenderingContext2D)'
    );
    // Keep ctx as CanvasRenderingContext2D - that's correct
    // Fix histogramPath data type
    content = content.replace(
      /function getHistogramPath\(data: Array<\{ x: number; y: number \}>\)/g,
      'function getHistogramPath(data: Array<Coord>)'
    );
    content = content.replace(
      /function getZeroHistogramPath\(data: Array<\{ x: number; y: number \}>\)/g,
      'function getZeroHistogramPath(data: Array<Coord>)'
    );
    // Fix CurveGraphProps
    content = content.replace(
      'adjustments: Adjustments;',
      'adjustments: Adjustments;'
    );
    content = content.replace(
      'setAdjustments(updater: (prev: Adjustments) => Adjustments): void;',
      'setAdjustments(updater: Adjustments | ((prev: Adjustments) => Adjustments)): void;'
    );
    // Fix ChannelConfig
    content = content.replace(
      'histogram?: ChannelConfig;',
      'histogram?: ChannelConfig;'
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
  'src/components/panel/right/PresetsPanel.tsx',
  'src/components/panel/library/LibraryItems.tsx',
  'src/components/panel/library/LibraryGrid.tsx',
  'src/components/modals/AppModals.tsx',
  'src/components/views/EditorView.tsx',
  'src/components/views/LibraryView.tsx',
  'src/components/adjustments/Basic.tsx',
  'src/components/adjustments/Effects.tsx',
  'src/components/adjustments/Details.tsx',
  'src/components/adjustments/Color.tsx',
  'src/components/adjustments/Curves.tsx',
];

for (const file of files) {
  fixFile(file);
}
