const fs = require('fs');
const path = require('path');

function fixFile(filePath) {
  const fullPath = path.resolve(filePath);
  if (!fs.existsSync(fullPath)) return;
  let content = fs.readFileSync(fullPath, 'utf8');
  let original = content;
  
  // ===== PresetsPanel.tsx =====
  if (filePath.includes('PresetsPanel.tsx')) {
    content = content.replace(
      'const mixAdjustments = (presetObj: any, intensity: number, initialObj: any = INITIAL_ADJUSTMENTS): any => {',
      'const mixAdjustments = (presetObj: Partial<Adjustments>, intensity: number, initialObj: Adjustments = INITIAL_ADJUSTMENTS): Adjustments => {'
    );
    content = content.replace('  const result: any = {};', '  const result: Record<string, unknown> = {};');
    content = content.replace('(node: any)', '(node: HTMLDivElement | null)');
    content = content.replace('useState<any>(null)', 'useState<UserPreset | null>(null)');
    content = content.replace('presets.forEach((item: any)', 'presets.forEach((item: UserPreset)');
    content = content.replace('item.folder.children.forEach((p: any)', 'item.folder.children.forEach((p: UserPreset)');
    content = content.replace('.filter((p: any)', '.filter((p: UserPreset)');
    content = content.replace('const fullPresetAdjustments: any =', 'const fullPresetAdjustments: Adjustments =');
    content = content.replace("presets.find((item: any)", "presets.find((item: UserPreset)");
    content = content.replace('folder.folder.children.filter((p: any)', 'folder.folder.children.filter((p: UserPreset)');
    content = content.replace('const presetsToGenerate: any = rootPresets.filter((p: any)', 'const presetsToGenerate: UserPreset[] = rootPresets.filter((p: UserPreset)');
    content = content.replace('const handleDragStart = (event: any)', 'const handleDragStart = (event: React.DragEvent<HTMLElement>)');
    content = content.replace('const handleDragEnd = (event: any)', 'const handleDragEnd = (event: React.DragEvent<HTMLElement>)');
    content = content.replace('.map((item: any) => item.preset', '.map((item: UserPreset) => item.preset');
    content = content.replace('const handleContextMenu = (event: any, item: UserPreset)', 'const handleContextMenu = (event: React.MouseEvent<HTMLElement>, item: UserPreset)');
    content = content.replace('const handleBackgroundContextMenu = (event: any)', 'const handleBackgroundContextMenu = (event: React.MouseEvent<HTMLElement>)');
  }

  // ===== SettingsPanel.tsx =====
  if (filePath.includes('SettingsPanel.tsx')) {
    // Fix interface props
    content = content.replace('  description: any;\n', '  description: React.ReactNode;\n');
    content = content.replace('  icon: any;\n', '  icon: React.ComponentType<React.SVGProps<SVGSVGElement>>;\n');
    content = content.replace('  children: any;\n', '  children: React.ReactNode;\n');
    content = content.replace('  appSettings: any;\n', '  appSettings: AppSettings;\n');
    content = content.replace('  onSettingsChange(settings: any): Promise<void>;\n', '  onSettingsChange(settings: AppSettings): Promise<void>;\n');
    content = content.replace(
      'const handleProcessingSettingChange = async (key: string, value: any)',
      'const handleProcessingSettingChange = async (key: string, value: unknown)'
    );
    content = content.replace('.then((l: any)', '.then((l: Array<{ name: string; models: string[] }>)');
    content = content.replace(/catch \(err: any\)/g, 'catch (err: unknown)');
    content = content.replace(/onChange=\{\(value: any\)/g, 'onChange={(value: string)');
  }

  // ===== FolderTree.tsx =====
  if (filePath.includes('FolderTree.tsx')) {
    content = content.replace(
      'onContextMenu(event: any, path: string | null, isPinned?: boolean): void;',
      'onContextMenu(event: React.MouseEvent<HTMLElement>, path: string | null, isPinned?: boolean): void;'
    );
    content = content.replace(
      'onAlbumContextMenu(event: any, item: AlbumItem | null): void;',
      'onAlbumContextMenu(event: React.MouseEvent<HTMLElement>, item: AlbumItem | null): void;'
    );
    content = content.replace(
      '  style: any;\n',
      '  style: React.CSSProperties;\n'
    );
    content = content.replace(
      'onContextMenu(event: any, path: string, isPinned?: boolean): void;',
      'onContextMenu(event: React.MouseEvent<HTMLElement>, path: string, isPinned?: boolean): void;'
    );
    content = content.replace(
      'const getAlbumImageCount = (item: any): number',
      'const getAlbumImageCount = (item: { children?: Array<{ children?: unknown[]; imageCount?: number }>; imageCount?: number }): number'
    );
    content = content.replace(
      'return item.children.reduce((sum: number, child: any)',
      'return item.children.reduce((sum: number, child: { children?: unknown[]; imageCount?: number })'
    );
    content = content.replace(
      'onContextMenu: (e: any, item: AlbumItem)',
      'onContextMenu: (e: React.MouseEvent<HTMLElement>, item: AlbumItem)'
    );
    content = content.replace(
      'let ItemIcon: any =',
      'let ItemIcon: React.ComponentType<React.SVGProps<SVGSVGElement>> | null ='
    );
    content = content.replace(
      'const handleFolderIconClick = (e: any)',
      'const handleFolderIconClick = (e: React.MouseEvent<HTMLElement>)'
    );
    content = content.replace(
      'const containerVariants: any =',
      'const containerVariants: Record<string, unknown> ='
    );
    content = content.replace(
      'let ResolvedIcon: any =',
      'let ResolvedIcon: React.ComponentType<React.SVGProps<SVGSVGElement>> ='
    );
    content = content.replace(
      '{node?.children?.map((childNode: any, index: number)',
      '{node?.children?.map((childNode: { name: string; path: string; children?: unknown[] }, index: number)'
    );
    content = content.replace(
      "invoke(Invokes.GetAlbums).then((res: any)",
      "invoke<Array<AlbumItem>>(Invokes.GetAlbums).then((res: Array<AlbumItem>)"
    );
    content = content.replace(
      'const handleEmptyAreaContextMenu = (e: any)',
      'const handleEmptyAreaContextMenu = (e: React.MouseEvent<HTMLElement>)'
    );
    content = content.replace(
      'base = base.map((tree: any) => filterTree(tree, trimmedQuery)).filter((t: any)',
      'base = base.map((tree: FolderTreeNode) => filterTree(tree, trimmedQuery)).filter((t: FolderTreeNode | null)'
    );
    content = content.replace(
      'filteredTrees.forEach((t: any)',
      'filteredTrees.forEach((t: FolderTreeNode)'
    );
    content = content.replace(
      "base = base.map((item: any) => filterAlbumTree(item, trimmedQuery)).filter((t: any)",
      "base = base.map((item: AlbumItem) => filterAlbumTree(item, trimmedQuery)).filter((t: AlbumItem | null)"
    );
    content = content.replace(
      'filteredAlbumTree.forEach((t: any)',
      'filteredAlbumTree.forEach((t: AlbumItem)'
    );
    content = content.replace(
      '{filteredAlbumTree.map((item: any)',
      '{filteredAlbumTree.map((item: AlbumItem)'
    );
    content = content.replace(
      '{filteredTrees.map((tree: any, index: number)',
      '{filteredTrees.map((tree: FolderTreeNode, index: number)'
    );
  }

  // ===== AIPanel.tsx =====
  if (filePath.includes('AIPanel.tsx')) {
    // SUB_MASK_CONFIG
    content = content.replace(
      'const SUB_MASK_CONFIG: any = {',
      `interface AiSubMaskParamConfig {
  key: string;
  min: number;
  max: number;
  step: number;
  defaultValue: number;
}
interface AiSubMaskConfigEntry {
  parameters?: AiSubMaskParamConfig[];
  showBrushTools?: boolean;
}
const SUB_MASK_CONFIG: Record<string, AiSubMaskConfigEntry> = {`
    );
    
    // BrushTools props
    content = content.replace(
      'const BrushTools = ({ settings, onSettingsChange }: { settings: any; onSettingsChange: any })',
      'const BrushTools = ({ settings, onSettingsChange }: { settings: { size: number; feather: number; tool: ToolType }; onSettingsChange: (updater: (prev: { size: number; feather: number; tool: ToolType }) => { size: number; feather: number; tool: ToolType }) => void })'
    );
    
    // (s: any) patterns
    content = content.replace(/\(s: any\) => \(\{ \.\.\.s,/g, '(s: { size: number; feather: number; tool: ToolType }) => ({ ...s,');
    
    // updater: any
    content = content.replace(
      '(updater: any)',
      '(updater: ((prev: { size: number; feather: number; tool: ToolType }) => { size: number; feather: number; tool: ToolType }) | { size: number; feather: number; tool: ToolType })'
    );
    
    // config.parameters.forEach((param: any)
    content = content.replace('(param: any)', '(param: AiSubMaskParamConfig)');
    
    // updatePatch, updateSubMask
    content = content.replace(
      'const updatePatch = (id: string, data: any)',
      'const updatePatch = (id: string, data: Record<string, unknown>)'
    );
    content = content.replace(
      'const updateSubMask = (id: string, data: any)',
      'const updateSubMask = (id: string, data: Partial<SubMask>)'
    );
    
    // DraggableGridItem props
    content = content.replace(
      'function DraggableGridItem({ maskType, isGenerating, onClick }: any)',
      'function DraggableGridItem({ maskType, isGenerating, onClick }: { maskType: MaskType; isGenerating: boolean; onClick: () => void })'
    );
    
    // Component props with }: any)
    // Need to identify each component
    content = content.replace(
      /\}: any\) \{\n  const \{ t \} = useTranslation\(\);\n  const \{ showContextMenu \} = useContextMenu/g,
      '}: { /* component props */ }) {\n  const { t } = useTranslation();\n  const { showContextMenu } = useContextMenu'
    );
    
    // Actually, let me be more specific about the component props
    // Line 1365 - AiPatchRow
    // Line 1655 - AiSubMaskRow  
    // Line 1870 - AiSettingsPanel
    
    // setCollapsibleState((prev: any)
    content = content.replace(
      'setCollapsibleState((prev: any)',
      'setCollapsibleState((prev: Record<string, boolean>)'
    );
  }

  // ===== ContextMenuContext.tsx =====
  if (filePath.includes('ContextMenuContext.tsx')) {
    // Read the specific patterns
  }
  
  // ===== AppProperties.tsx =====
  if (filePath.includes('AppProperties.tsx')) {
    content = content.replace('  decorations?: any;\n', '  decorations?: Record<string, unknown>;\n');
    content = content.replace('  lastFolderState?: any;\n', '  lastFolderState?: Record<string, unknown>;\n');
    content = content.replace('  pinnedFolders?: any;\n', '  pinnedFolders?: Array<{ id: string; name: string; path: string }>;\n');
    content = content.replace('  myLenses?: any;\n', '  myLenses?: Array<Record<string, unknown>>;\n');
    content = content.replace('  exif: any;\n', '  exif: Record<string, string | number | boolean | null>;\n');
    content = content.replace('  metadata?: any;\n', '  metadata?: Record<string, unknown>;\n');
    content = content.replace('  icon?: any;\n', '  icon?: React.ComponentType<React.SVGProps<SVGSVGElement>>;\n');
    content = content.replace('  submenu?: any;\n', '  submenu?: Array<Option>;\n');
    content = content.replace('  children: any;\n', '  children: Folder;\n');
    content = content.replace('  children?: any;\n', '  children?: Array<Folder | Preset>;\n');
  }

  // ===== LibraryHeader.tsx =====
  if (filePath.includes('LibraryHeader.tsx')) {
  }

  // ===== Editor.tsx =====
  if (filePath.includes('/Editor.tsx')) {
  }

  if (content !== original) {
    fs.writeFileSync(fullPath, content, 'utf8');
    console.log(`FIXED: ${filePath}`);
  } else {
    console.log(`NO CHANGE: ${filePath}`);
  }
}

const files = [
  'src/components/panel/right/PresetsPanel.tsx',
  'src/components/panel/SettingsPanel.tsx',
  'src/components/panel/FolderTree.tsx',
  'src/components/panel/right/AIPanel.tsx',
  'src/components/ui/AppProperties.tsx',
];

for (const file of files) {
  fixFile(file);
}
