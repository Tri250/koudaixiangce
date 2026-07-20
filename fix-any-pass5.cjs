const fs = require('fs');
const path = require('path');

function fixFile(filePath) {
  const fullPath = path.resolve(filePath);
  if (!fs.existsSync(fullPath)) return;
  let content = fs.readFileSync(fullPath, 'utf8');
  let original = content;
  
  // ===== UNIVERSAL PATTERNS =====
  
  // setAdjustments((prev: any) => (prev: Adjustments)
  content = content.replace(/setAdjustments\(\(prev: any\)/g, 'setAdjustments((prev: Adjustments)');
  
  // Array<any> → Array<unknown> (safe fallback when specific type unknown)
  content = content.replace(/Array<any>/g, 'Array<unknown>');
  
  // ===== Curves.tsx =====
  if (filePath.includes('Curves.tsx')) {
    // getHistogramPath data param
    content = content.replace(
      'function getHistogramPath(data: Array<unknown>)',
      'function getHistogramPath(data: Array<{ x: number; y: number }>)'
    );
    content = content.replace(
      'function getZeroHistogramPath(data: Array<unknown>)',
      'function getZeroHistogramPath(data: Array<{ x: number; y: number }>)'
    );
    // Back to the ones that weren't caught
    content = content.replace(
      'function getHistogramPath(data: Array<any>)',
      'function getHistogramPath(data: Array<{ x: number; y: number }>)'
    );
    content = content.replace(
      'function getZeroHistogramPath(data: Array<any>)',
      'function getZeroHistogramPath(data: Array<{ x: number; y: number }>)'
    );
    
    // handlePointStart - this is a touch/mouse event on canvas
    content = content.replace(
      'const handlePointStart = (e: any, index: number)',
      'const handlePointStart = (e: React.MouseEvent<HTMLCanvasElement> | React.TouchEvent<HTMLCanvasElement>, index: number)'
    );
    
    // (e: any) in canvas mouse/touch handler callbacks that aren't JSX attributes
    // These are Konva-like event objects or native events
    // For curves, these are canvas events
  }
  
  // ===== PresetsPanel.tsx =====
  if (filePath.includes('PresetsPanel.tsx')) {
    // Read the remaining any lines
    // line 137: invoke call
    // line 144: callback
    // line 483: state
    // etc.
    
    // The remaining any's in PresetsPanel are mostly in invoke calls and event handlers
    // that are in callback form (not JSX attributes)
  }
  
  // ===== SettingsPanel.tsx =====
  if (filePath.includes('SettingsPanel.tsx')) {
    // Mostly invoke calls and settings objects
  }
  
  // ===== FolderTree.tsx =====
  if (filePath.includes('FolderTree.tsx')) {
    // invoke calls and folder tree operations
  }
  
  // ===== AIPanel.tsx =====
  if (filePath.includes('AIPanel.tsx')) {
    // AI-specific invoke calls and state
  }

  if (content !== original) {
    fs.writeFileSync(fullPath, content, 'utf8');
    console.log(`FIXED: ${filePath}`);
  } else {
    console.log(`NO CHANGE: ${filePath}`);
  }
}

// Process all remaining files with any errors
const files = [
  'src/components/adjustments/Curves.tsx',
  'src/components/panel/right/PresetsPanel.tsx',
  'src/components/panel/SettingsPanel.tsx',
  'src/components/panel/FolderTree.tsx',
  'src/components/panel/right/AIPanel.tsx',
  'src/components/panel/library/LibraryHeader.tsx',
  'src/components/panel/Editor.tsx',
  'src/context/ContextMenuContext.tsx',
  'src/components/panel/library/LibraryGrid.tsx',
  'src/components/panel/Filmstrip.tsx',
  'src/components/modals/LensCorrectionModal.tsx',
  'src/components/views/LibraryView.tsx',
  'src/components/ui/AppProperties.tsx',
  'src/components/panel/right/ControlsPanel.tsx',
  'src/components/views/EditorView.tsx',
  'src/components/ui/CollapsibleSection.tsx',
  'src/components/panel/right/ExportPanel.tsx',
  'src/components/ui/Input.tsx',
  'src/components/panel/editor/EditorToolbar.tsx',
  'src/components/modals/CollageModal.tsx',
  'src/components/panel/library/LibraryItems.tsx',
  'src/components/panel/MainLibrary.tsx',
  'src/components/adjustments/Basic.tsx',
  'src/components/ui/Button.tsx',
  'src/components/panel/right/MetadataPanel.tsx',
  'src/components/panel/editor/Waveform.tsx',
  'src/components/panel/BottomBar.tsx',
  'src/components/modals/RenameFileModal.tsx',
  'src/components/modals/NegativeConversionModal.tsx',
  'src/components/modals/ImportSettingsModal.tsx',
  'src/components/modals/AppModals.tsx',
  'src/components/managers/ImageProcessingManager.tsx',
  'src/context/TaggingSubMenu.tsx',
  'src/components/ui/Switch.tsx',
  'src/components/panel/right/CropPanel.tsx',
  'src/components/modals/RenameFolderModal.tsx',
  'src/components/modals/DenoiseModal.tsx',
  'src/components/modals/CullingModal.tsx',
  'src/components/modals/CreateFolderModal.tsx',
  'src/components/modals/ConfigurePresetModal.tsx',
  'src/components/managers/ImageLoaderManager.tsx',
  'src/components/adjustments/Effects.tsx',
  'src/components/adjustments/Details.tsx',
  'src/components/adjustments/Color.tsx',
];

for (const file of files) {
  fixFile(file);
}
