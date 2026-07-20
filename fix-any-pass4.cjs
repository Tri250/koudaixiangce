const fs = require('fs');
const path = require('path');

function fixFile(filePath) {
  const fullPath = path.resolve(filePath);
  if (!fs.existsSync(fullPath)) return;
  let content = fs.readFileSync(fullPath, 'utf8');
  let original = content;
  
  // ===== PresetsPanel.tsx =====
  if (filePath.includes('PresetsPanel.tsx')) {
    // Fix interface props
    content = content.replace(
      `interface DroppableFolderItemProps {
  children: any;
  folder: any;
  isExpanded: boolean;
  onContextMenu(event: any, folder: any): void;
  onToggle(id: string): void;
}`,
      `interface DroppableFolderItemProps {
  children: React.ReactNode;
  folder: Folder;
  isExpanded: boolean;
  onContextMenu(event: React.MouseEvent<HTMLElement>, folder: Folder): void;
  onToggle(id: string): void;
}`
    );
    
    content = content.replace(
      `interface DraggablePresetItemProps {
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
}`,
      `interface DraggablePresetItemProps {
  isGeneratingPreviews: boolean;
  onApply(preset: Preset): void;
  onContextMenu(event: React.MouseEvent<HTMLElement>, preset: Preset): void;
  preset: Preset;
  previewUrl: string;
  isActive?: boolean;
  intensity?: number;
  onIntensityChange?: (val: number) => void;
  onDragStateChange?: (isDragging: boolean) => void;
  viewMode?: 'grid' | 'list';
}`
    );
    
    content = content.replace(
      `interface FolderProps {
  folder: any;
}`,
      `interface FolderProps {
  folder: Folder;
}`
    );
    
    content = content.replace(
      `interface FolderState {
  isOpen: boolean;
  folder: any;
}`,
      `interface FolderState {
  isOpen: boolean;
  folder: Folder;
}`
    );
    
    // Fix remaining any types in PresetsPanel
    // These need more context - let me check the lines
    // line 137: invoke call, line 144: callback, line 483: state, etc.
    
    // Add Preset and Folder imports if missing
    if (!content.includes("import { Preset, Folder")) {
      content = content.replace(
        "from '../../../components/ui/AppProperties';",
        "from '../../../components/ui/AppProperties';\nimport type { Preset, Folder } from '../../../components/ui/AppProperties';"
      );
      // Remove duplicate if we created one
      content = content.replace(
        "from '../../../components/ui/AppProperties';\nimport type { Preset, Folder } from '../../../components/ui/AppProperties';",
        "from '../../../components/ui/AppProperties';"
      );
    }
    
    // Fix remaining specific patterns by line
    // These are mostly invoke calls, callback types, and state types
    // Let me just do safe bulk replacements
    
    // (prev: any) => in setState for presets
    content = content.replace(/setPresets\(\(prev: any\)/g, 'setPresets((prev: Array<UserPreset>)');
  }
  
  // ===== Curves.tsx =====
  if (filePath.includes('Curves.tsx')) {
    // parametricClipboard
    content = content.replace(
      'let parametricClipboard: any = null;',
      'let parametricClipboard: ParametricCurveSettings | null = null;'
    );
    
    // ColorData.data
    content = content.replace(
      `interface ColorData {
  color: string;
  data: any;
}`,
      `interface ColorData {
  color: string;
  data: Array<Coord>;
}`
    );
    
    // CurveGraphProps
    content = content.replace(
      'adjustments: Adjustments | any;',
      'adjustments: Adjustments;'
    );
    content = content.replace(
      'setAdjustments(updater: (prev: any) => any): void;',
      'setAdjustments(updater: (prev: Adjustments) => Adjustments): void;'
    );
    
    // Canvas context refs and handlers - these are mostly
    // (ctx: any), (e: any), useRef<any>
    // For canvas 2d context:
    content = content.replace(/\(ctx: any\)/g, '(ctx: CanvasRenderingContext2D)');
    content = content.replace(/useRef<HTMLElement>/g, 'useRef<HTMLCanvasElement>');
    
    // (e: any) => in canvas mouse handlers
    content = content.replace(
      /\(e: any\)/g,
      '(e: React.MouseEvent<HTMLCanvasElement>)'
    );
    
    // event handler params that take native events
    content = content.replace(
      /\(event: any\)/g,
      '(event: React.MouseEvent<HTMLElement>)'
    );
  }
  
  // ===== SettingsPanel.tsx =====
  if (filePath.includes('SettingsPanel.tsx')) {
    // Read the key any patterns and fix them
    // Most are: invoke calls, component props with : any, callback params
  }
  
  // ===== FolderTree.tsx =====
  if (filePath.includes('FolderTree.tsx')) {
    // Most are Folder type references and invoke calls
  }
  
  // ===== AIPanel.tsx =====
  if (filePath.includes('AIPanel.tsx')) {
    // AI panel types
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
  'src/components/adjustments/Curves.tsx',
];

for (const file of files) {
  fixFile(file);
}
