const fs = require('fs');
const path = require('path');

function fixFile(filePath) {
  const fullPath = path.resolve(filePath);
  if (!fs.existsSync(fullPath)) return;
  let content = fs.readFileSync(fullPath, 'utf8');
  let original = content;
  
  // Fix (...args: unknown[]) => void -> use specific types or Function
  // The problem is (...args: unknown[]) => void doesn't accept specific function types
  // We need to either use specific types or a different approach
  // Using ((...args: never[]) => void) | ((...args: unknown[]) => void) won't work either
  // The safest approach is to use specific function signatures based on what App.tsx passes
  
  // LibraryView.tsx - fix the handler types
  if (filePath.includes('LibraryView.tsx')) {
    content = content.replace(
      '  handleLibraryImageSingleClick: (...args: unknown[]) => void;\n  handleImageSelect: (...args: unknown[]) => void;\n  handleRate: (...args: unknown[]) => void;\n  handleThumbnailContextMenu: (...args: unknown[]) => void;\n  handleMainLibraryContextMenu: (...args: unknown[]) => void;\n  handleContinueSession: (...args: unknown[]) => void;\n  handleGoHome: (...args: unknown[]) => void;\n  handleOpenFolder: (...args: unknown[]) => void;',
      `  handleLibraryImageSingleClick: (path: string, event: React.MouseEvent) => void;
  handleImageSelect: (path: string, event: React.MouseEvent) => void;
  handleRate: (rating: number, paths?: string[]) => void;
  handleThumbnailContextMenu: (event: React.MouseEvent, path: string) => void;
  handleMainLibraryContextMenu: (event: React.MouseEvent, path: string | null, isPinned?: boolean) => void;
  handleContinueSession: () => void;
  handleGoHome: () => void;
  handleOpenFolder: (path: string) => Promise<void>;`
    );
    content = content.replace(
      '  requestThumbnails: (...args: unknown[]) => void;',
      '  requestThumbnails: (visiblePaths: string[]) => void;'
    );
  }
  
  // EditorView.tsx - fix handler types
  if (filePath.includes('EditorView.tsx')) {
    content = content.replace(
      '  handleEditorContextMenu: (...args: unknown[]) => void;\n  handleThumbnailContextMenu: (...args: unknown[]) => void;\n  handleImageClick: (...args: unknown[]) => void;\n',
      `  handleEditorContextMenu: (event: React.MouseEvent) => void;
  handleThumbnailContextMenu: (event: React.MouseEvent, path: string) => void;
  handleImageClick: (path: string, event: React.MouseEvent) => void;\n`
    );
    content = content.replace(
      '  handleRate: (...args: unknown[]) => void;',
      '  handleRate: (rating: number, paths?: string[]) => void;'
    );
    content = content.replace(
      '  requestThumbnails: (...args: unknown[]) => void;',
      '  requestThumbnails: (visiblePaths: string[]) => void;'
    );
  }
  
  // AppModals.tsx - fix types
  if (filePath.includes('AppModals.tsx')) {
    content = content.replace(
      '  executeDelete: (paths: string[], options: Record<string, unknown>) => Promise<void>;',
      '  executeDelete: (paths: string[], options: { includeAssociated?: boolean; permanent?: boolean }) => Promise<void>;'
    );
  }
  
  // Basic.tsx - fix setAdjustments type
  if (filePath.includes('Basic.tsx')) {
    content = content.replace(
      '  setAdjustments(adjustments: Partial<Adjustments>): void;',
      '  setAdjustments(adjustments: Partial<Adjustments> | ((prev: Adjustments) => Partial<Adjustments>)): void;'
    );
  }
  
  // Effects.tsx
  if (filePath.includes('Effects.tsx')) {
    content = content.replace(
      '  setAdjustments(adjustments: Partial<Adjustments>): void;',
      '  setAdjustments(adjustments: Partial<Adjustments> | ((prev: Adjustments) => Partial<Adjustments>)): void;'
    );
  }
  
  // Details.tsx
  if (filePath.includes('Details.tsx')) {
    content = content.replace(
      '  setAdjustments(adjustments: Partial<Adjustments>): void;',
      '  setAdjustments(adjustments: Partial<Adjustments> | ((prev: Adjustments) => Partial<Adjustments>)): void;'
    );
  }
  
  // Color.tsx
  if (filePath.includes('Color.tsx')) {
    content = content.replace(
      '  setAdjustments(adjustments: Partial<Adjustments>): void;',
      '  setAdjustments(adjustments: Partial<Adjustments> | ((prev: Adjustments) => Partial<Adjustments>)): void;'
    );
  }
  
  // FolderTree.tsx - fix onContextMenu to use React.MouseEvent instead of MouseEvent
  if (filePath.includes('FolderTree.tsx')) {
    // The issue is that React.MouseEvent<HTMLElement> is not assignable to MouseEvent
    // The callers in App.tsx use (event: MouseEvent, ...) but we typed as React.MouseEvent<HTMLElement>
    // Need to check what App.tsx actually passes
  }

  if (content !== original) {
    fs.writeFileSync(fullPath, content, 'utf8');
    console.log(`FIXED: ${filePath}`);
  } else {
    console.log(`NO CHANGE: ${filePath}`);
  }
}

const files = [
  'src/components/views/LibraryView.tsx',
  'src/components/views/EditorView.tsx',
  'src/components/modals/AppModals.tsx',
  'src/components/adjustments/Basic.tsx',
  'src/components/adjustments/Effects.tsx',
  'src/components/adjustments/Details.tsx',
  'src/components/adjustments/Color.tsx',
];

for (const file of files) {
  fixFile(file);
}
