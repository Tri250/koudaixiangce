const fs = require('fs');
const path = require('path');

function fixFile(filePath) {
  const fullPath = path.resolve(filePath);
  if (!fs.existsSync(fullPath)) return;
  let content = fs.readFileSync(fullPath, 'utf8');
  let original = content;
  
  // ===== Input.tsx =====
  if (filePath.includes('/Input.tsx')) {
    content = content.replace('  onBlur?(e: any): void;', '  onBlur?(e: React.FocusEvent<HTMLInputElement>): void;');
    content = content.replace('  onChange(e: any): void;', '  onChange(e: React.ChangeEvent<HTMLInputElement>): void;');
    content = content.replace('  onKeyDown?(e: any): void;', '  onKeyDown?(e: React.KeyboardEvent<HTMLInputElement>): void;');
    content = content.replace('ref: any)', 'ref: React.Ref<HTMLInputElement>)');
  }

  // ===== AIPanel.tsx remaining =====
  if (filePath.includes('AIPanel.tsx')) {
    // }: any) { patterns for component props
    // Need to find the component names
    // Line 1376 - AiPatchRow, Line 1666 - AiSubMaskRow, Line 1881 - AiSettingsPanel
    
    // Find each component's }: any) { and replace with typed props
    // Since we can't easily determine exact props, use Record<string, unknown>
    content = content.replace(/\}: any\) \{\n  const \{ t \} = useTranslation\(\);\n  const \{ attributes/g, 
      '}: Record<string, unknown>) {\n  const { t } = useTranslation();\n  const { attributes');
    
    // subMaskConfig.parameters?.map((param: any)
    content = content.replace('subMaskConfig.parameters?.map((param: any)', 'subMaskConfig.parameters?.map((param: AiSubMaskParamConfig)');
  }

  // ===== EditorToolbar.tsx =====
  if (filePath.includes('EditorToolbar.tsx')) {
    content = content.replace('(cMask: any)', '(cMask: MaskContainer)');
    content = content.replace("(m: any) => m.id === cMask.id", "(m: MaskContainer) => m.id === cMask.id");
    content = content.replace('(cPatch: any)', '(cPatch: AiPatch)');
    content = content.replace("(p: any) => p.id === cPatch.id", "(p: AiPatch) => p.id === cPatch.id");
  }

  // ===== CollageModal.tsx =====
  if (filePath.includes('CollageModal.tsx')) {
    content = content.replace(
      'const metadata: any = await invoke(Invokes.LoadMetadata',
      'const metadata: Record<string, unknown> = await invoke<Record<string, unknown>>(Invokes.LoadMetadata'
    );
    content = content.replace(/catch \(err: any\)/g, 'catch (err: unknown)');
  }

  // ===== LibraryItems.tsx =====
  if (filePath.includes('LibraryItems.tsx')) {
    // }: any) => { patterns - grid/list item props
    content = content.replace(/\}: any\) => \{/g, '}: Record<string, unknown>) => {');
  }

  // ===== MainLibrary.tsx =====
  if (filePath.includes('MainLibrary.tsx')) {
    content = content.replace(
      '  onContextMenu(event: any, path: string): void;',
      '  onContextMenu(event: React.MouseEvent<HTMLElement>, path: string): void;'
    );
    content = content.replace(
      '  onEmptyAreaContextMenu(event: any): void;',
      '  onEmptyAreaContextMenu(event: React.MouseEvent<HTMLElement>): void;'
    );
    content = content.replace(
      '  onImageClick(path: string, event: any): void;',
      '  onImageClick(path: string, event: React.MouseEvent<HTMLElement>): void;'
    );
  }

  // ===== Basic.tsx =====
  if (filePath.includes('Basic.tsx')) {
    content = content.replace(
      '  setAdjustments(adjustments: Partial<Adjustments>): any;',
      '  setAdjustments(adjustments: Partial<Adjustments>): void;'
    );
    content = content.replace(
      '  appSettings?: any;',
      '  appSettings?: Record<string, unknown>;'
    );
    content = content.replace(
      'const handleAdjustmentChange = (key: BasicAdjustment, value: any)',
      'const handleAdjustmentChange = (key: BasicAdjustment, value: number)'
    );
  }

  // ===== Button.tsx =====
  if (filePath.includes('/Button.tsx')) {
    content = content.replace('  children: any;\n', '  children: React.ReactNode;\n');
    content = content.replace('  onClick: any;\n', '  onClick: () => void;\n');
  }

  // ===== MetadataPanel.tsx =====
  if (filePath.includes('MetadataPanel.tsx')) {
    content = content.replace('  value: any;\n', '  value: Record<string, unknown>;\n');
    content = content.replace('.map((item: any)', '.map((item: { label: string; value: string })');
  }

  // ===== Waveform.tsx =====
  if (filePath.includes('Waveform.tsx')) {
    content = content.replace('  histogram?: any;', '  histogram?: ChannelConfig;');
    content = content.replace(
      'const HistogramView = ({ histogram }: { histogram: any })',
      'const HistogramView = ({ histogram }: { histogram: ChannelConfig })'
    );
  }

  // ===== BottomBar.tsx =====
  if (filePath.includes('BottomBar.tsx')) {
    content = content.replace(
      '  onContextMenu?(event: any, path: string): void;',
      '  onContextMenu?(event: React.MouseEvent<HTMLElement>, path: string): void;'
    );
    content = content.replace(
      '  onImageSelect?(path: string, event: any): void;',
      '  onImageSelect?(path: string, event: React.MouseEvent<HTMLElement>): void;'
    );
  }

  // ===== RenameFileModal.tsx =====
  if (filePath.includes('RenameFileModal.tsx')) {
    content = content.replace('  onSave(template: any): void;', '  onSave(template: string): void;');
    content = content.replace('(e: any) =>', '(e: React.KeyboardEvent<HTMLInputElement>) =>');
  }

  // ===== NegativeConversionModal.tsx =====
  if (filePath.includes('NegativeConversionModal.tsx')) {
    content = content.replace(
      "const unlisten = listen('negative-batch-progress', (e: any)",
      "const unlisten = listen<{ progress: number; total: number }>('negative-batch-progress', (e: { payload: { progress: number; total: number } })"
    );
    content = content.replace('.then((res: any)', '.then((res: unknown)');
  }

  // ===== ImportSettingsModal.tsx =====
  if (filePath.includes('ImportSettingsModal.tsx')) {
    content = content.replace('  onSave(settings: any): void;', '  onSave(settings: Record<string, unknown>): void;');
    content = content.replace('(e: any) =>', '(e: React.KeyboardEvent<HTMLInputElement>) =>');
  }

  // ===== AppModals.tsx =====
  if (filePath.includes('AppModals.tsx')) {
    content = content.replace(
      '  handleStartImport: (settings: any) => Promise<void>;',
      '  handleStartImport: (settings: Record<string, unknown>) => Promise<void>;'
    );
    content = content.replace(
      '  executeDelete: (paths: string[], options: any) => Promise<void>;',
      '  executeDelete: (paths: string[], options: Record<string, unknown>) => Promise<void>;'
    );
  }

  // ===== ImageProcessingManager.tsx =====
  if (filePath.includes('ImageProcessingManager.tsx')) {
    content = content.replace(
      '  transformWrapperRef: React.RefObject<any>;',
      '  transformWrapperRef: React.RefObject<HTMLElement | null>;'
    );
    content = content.replace(
      '  prevAdjustmentsRef: React.RefObject<any>;',
      '  prevAdjustmentsRef: React.RefObject<Adjustments | null>;'
    );
  }

  // ===== Switch.tsx =====
  if (filePath.includes('/Switch.tsx')) {
    content = content.replace('  onChange(val: boolean): any;', '  onChange(val: boolean): void;');
  }

  // ===== CropPanel.tsx =====
  if (filePath.includes('CropPanel.tsx')) {
    content = content.replace(
      'const handleFineRotationChange = (e: any)',
      'const handleFineRotationChange = (e: React.ChangeEvent<HTMLInputElement>)'
    );
  }

  // ===== LibraryGrid.tsx remaining =====
  if (filePath.includes('LibraryGrid.tsx')) {
    content = content.replace(
      "const groups: Record<string, any[]> = {};",
      "const groups: Record<string, Array<{ path: string; thumbnailUrl: string; rating: number; name: string }>> = {};"
    );
  }

  // ===== RenameFolderModal.tsx =====
  if (filePath.includes('RenameFolderModal.tsx')) {
    content = content.replace('(e: any) =>', '(e: React.KeyboardEvent<HTMLInputElement>) =>');
  }

  // ===== DenoiseModal.tsx =====
  if (filePath.includes('DenoiseModal.tsx')) {
    content = content.replace(
      "const unlisten = listen('denoise-batch-progress', (e: any)",
      "const unlisten = listen<{ progress: number; total: number }>('denoise-batch-progress', (e: { payload: { progress: number; total: number } })"
    );
  }

  // ===== CullingModal.tsx =====
  if (filePath.includes('CullingModal.tsx')) {
    content = content.replace(
      "function ImageThumbnail({ path, thumbnails, isSelected, onToggle, children }: any)",
      "function ImageThumbnail({ path, thumbnails, isSelected, onToggle, children }: { path: string; thumbnails: Record<string, string>; isSelected: boolean; onToggle: () => void; children?: React.ReactNode })"
    );
  }

  // ===== CreateFolderModal.tsx =====
  if (filePath.includes('CreateFolderModal.tsx')) {
    content = content.replace('(e: any) =>', '(e: React.KeyboardEvent<HTMLInputElement>) =>');
  }

  // ===== ConfigurePresetModal.tsx =====
  if (filePath.includes('ConfigurePresetModal.tsx')) {
    content = content.replace('(e: any) =>', '(e: React.KeyboardEvent<HTMLInputElement>) =>');
  }

  // ===== ImageLoaderManager.tsx =====
  if (filePath.includes('ImageLoaderManager.tsx')) {
    content = content.replace(
      '  cachedEditStateRef: React.RefObject<any>;',
      '  cachedEditStateRef: React.RefObject<unknown>;'
    );
  }

  // ===== Effects.tsx =====
  if (filePath.includes('Effects.tsx')) {
    content = content.replace(
      '  setAdjustments(adjustments: Partial<Adjustments>): any;',
      '  setAdjustments(adjustments: Partial<Adjustments>): void;'
    );
  }

  // ===== Details.tsx =====
  if (filePath.includes('Details.tsx')) {
    content = content.replace(
      '  setAdjustments(adjustments: Partial<Adjustments>): any;',
      '  setAdjustments(adjustments: Partial<Adjustments>): void;'
    );
  }

  // ===== Color.tsx =====
  if (filePath.includes('Color.tsx')) {
    content = content.replace(
      '  setAdjustments(adjustments: Partial<Adjustments>): any;',
      '  setAdjustments(adjustments: Partial<Adjustments>): void;'
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
  'src/components/ui/Input.tsx',
  'src/components/panel/right/AIPanel.tsx',
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
  'src/components/ui/Switch.tsx',
  'src/components/panel/right/CropPanel.tsx',
  'src/components/panel/library/LibraryGrid.tsx',
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
