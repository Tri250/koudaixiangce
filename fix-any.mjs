import fs from 'fs';
import path from 'path';

const files = [
  'src/components/panel/right/MasksPanel.tsx',
  'src/components/panel/editor/ImageCanvas.tsx',
  'src/components/panel/right/PresetsPanel.tsx',
  'src/components/panel/SettingsPanel.tsx',
  'src/components/panel/right/AIPanel.tsx',
  'src/components/adjustments/Curves.tsx',
  'src/components/panel/FolderTree.tsx',
  'src/components/panel/Editor.tsx',
  'src/context/ContextMenuContext.tsx',
  'src/components/panel/library/LibraryHeader.tsx',
  'src/components/panel/library/LibraryGrid.tsx',
  'src/components/panel/Filmstrip.tsx',
  'src/components/panel/library/LibraryItems.tsx',
  'src/components/adjustments/Color.tsx',
  'src/components/panel/right/ControlsPanel.tsx',
  'src/components/adjustments/Basic.tsx',
  'src/components/modals/LensCorrectionModal.tsx',
  'src/components/adjustments/Effects.tsx',
  'src/components/adjustments/Details.tsx',
  'src/components/views/LibraryView.tsx',
  'src/components/ui/AppProperties.tsx',
  'src/components/views/EditorView.tsx',
  'src/components/ui/CollapsibleSection.tsx',
  'src/components/panel/right/ExportPanel.tsx',
  'src/components/panel/editor/EditorToolbar.tsx',
  'src/components/modals/ImportSettingsModal.tsx',
  'src/components/ui/Input.tsx',
  'src/components/panel/right/PortraitPanel.tsx',
  'src/components/panel/MainLibrary.tsx',
  'src/components/modals/RenameFileModal.tsx',
  'src/components/modals/CollageModal.tsx',
  'src/components/panel/right/LiquifyPanel.tsx',
  'src/components/panel/right/CropPanel.tsx',
  'src/components/panel/editor/Waveform.tsx',
  'src/components/modals/RenameFolderModal.tsx',
  'src/components/modals/CreateFolderModal.tsx',
  'src/components/modals/ConfigurePresetModal.tsx',
  'src/components/ui/Switch.tsx',
  'src/components/ui/Button.tsx',
  'src/components/panel/right/SkyPanel.tsx',
  'src/components/panel/right/MonochromePanel.tsx',
  'src/components/panel/right/MetadataPanel.tsx',
  'src/components/panel/right/CreativePanel.tsx',
  'src/components/panel/right/ColorSpacePanel.tsx',
  'src/components/panel/CommunityPage.tsx',
  'src/components/panel/BottomBar.tsx',
  'src/components/modals/NegativeConversionModal.tsx',
  'src/components/modals/AppModals.tsx',
  'src/components/managers/ImageProcessingManager.tsx',
  'src/context/TaggingSubMenu.tsx',
  'src/components/panel/right/RightPanelSwitcher.tsx',
  'src/components/modals/LiquifyModal.tsx',
  'src/components/modals/DenoiseModal.tsx',
  'src/components/modals/CullingModal.tsx',
  'src/components/modals/CopyPasteSettingsModal.tsx',
  'src/components/modals/ConfirmModal.tsx',
  'src/components/managers/ImageLoaderManager.tsx',
];

let totalFixed = 0;

for (const file of files) {
  const filePath = path.resolve(file);
  if (!fs.existsSync(filePath)) {
    console.log(`SKIP: ${file} not found`);
    continue;
  }
  
  let content = fs.readFileSync(filePath, 'utf8');
  let original = content;
  
  // Pattern 1: (e: any) => in onChange/onInput handlers → (e: React.ChangeEvent<HTMLInputElement>)
  content = content.replace(
    /onChange=\{\(e: any\)/g,
    'onChange={(e: React.ChangeEvent<HTMLInputElement>)'
  );
  
  // Pattern 2: (e: any) => in onClick/onDoubleClick handlers → (e: React.MouseEvent<HTMLElement>)
  content = content.replace(
    /onClick=\{\(e: any\)/g,
    'onClick={(e: React.MouseEvent<HTMLElement>)'
  );
  
  // Pattern 3: onContextMenu={(e: any) → (e: React.MouseEvent<HTMLElement>)
  content = content.replace(
    /onContextMenu=\{\(e: any\)/g,
    'onContextMenu={(e: React.MouseEvent<HTMLElement>)'
  );
  
  // Pattern 4: (e: any) => onDrag/onMouseDown/onPointerDown etc.
  content = content.replace(
    /onDrag=\{\(e: any\)/g,
    'onDrag={(e: React.DragEvent<HTMLElement>)'
  );
  content = content.replace(
    /onDragStart=\{\(e: any\)/g,
    'onDragStart={(e: React.DragEvent<HTMLElement>)'
  );
  content = content.replace(
    /onDragOver=\{\(e: any\)/g,
    'onDragOver={(e: React.DragEvent<HTMLElement>)'
  );
  content = content.replace(
    /onDragEnd=\{\(e: any\)/g,
    'onDragEnd={(e: React.DragEvent<HTMLElement>)'
  );
  content = content.replace(
    /onDrop=\{\(e: any\)/g,
    'onDrop={(e: React.DragEvent<HTMLElement>)'
  );
  content = content.replace(
    /onMouseDown=\{\(e: any\)/g,
    'onMouseDown={(e: React.MouseEvent<HTMLElement>)'
  );
  content = content.replace(
    /onMouseEnter=\{\(e: any\)/g,
    'onMouseEnter={(e: React.MouseEvent<HTMLElement>)'
  );
  content = content.replace(
    /onMouseLeave=\{\(e: any\)/g,
    'onMouseLeave={(e: React.MouseEvent<HTMLElement>)'
  );
  content = content.replace(
    /onMouseMove=\{\(e: any\)/g,
    'onMouseMove={(e: React.MouseEvent<HTMLElement>)'
  );
  content = content.replace(
    /onMouseUp=\{\(e: any\)/g,
    'onMouseUp={(e: React.MouseEvent<HTMLElement>)'
  );
  content = content.replace(
    /onPointerDown=\{\(e: any\)/g,
    'onPointerDown={(e: React.PointerEvent<HTMLElement>)'
  );
  content = content.replace(
    /onKeyDown=\{\(e: any\)/g,
    'onKeyDown={(e: React.KeyboardEvent<HTMLElement>)'
  );
  content = content.replace(
    /onKeyUp=\{\(e: any\)/g,
    'onKeyUp={(e: React.KeyboardEvent<HTMLElement>)'
  );
  content = content.replace(
    /onSubmit=\{\(e: any\)/g,
    'onSubmit={(e: React.FormEvent<HTMLElement>)'
  );
  content = content.replace(
    /onFocus=\{\(e: any\)/g,
    'onFocus={(e: React.FocusEvent<HTMLElement>)'
  );
  content = content.replace(
    /onBlur=\{\(e: any\)/g,
    'onBlur={(e: React.FocusEvent<HTMLElement>)'
  );
  
  // Pattern 5: useState<any> → useState<Record<string, unknown>> or specific
  // Keep as-is for now - too context-dependent
  
  // Pattern 6: Record<string, any> → Record<string, unknown>
  content = content.replace(/Record<string,\s*any>/g, 'Record<string, unknown>');
  
  // Pattern 7: as any → as unknown (safe fallback)
  // Only replace in expressions, not type positions
  content = content.replace(/\bas any\b/g, 'as unknown');
  
  // Pattern 8: any[] → unknown[] (when we can't determine the type)
  // Only standalone any[] not preceded by specific type info
  content = content.replace(/:\s*any\[\]/g, ': unknown[]');
  content = content.replace(/<any\[\]>/g, '<unknown[]>');
  
  // Pattern 9: : any; in interface/type positions → : unknown;
  // Be careful not to replace in function params that need specific types
  
  // Pattern 10: useRef<any> → useRef<HTMLElement>
  content = content.replace(/useRef<any>/g, 'useRef<HTMLElement>');
  
  if (content !== original) {
    fs.writeFileSync(filePath, content, 'utf8');
    const diff = content.split('\n').filter((line, i) => line !== original.split('\n')[i]).length;
    console.log(`FIXED: ${file} (${diff} lines changed)`);
    totalFixed += diff;
  } else {
    console.log(`NO CHANGE: ${file}`);
  }
}

console.log(`\nTotal lines changed: ${totalFixed}`);
