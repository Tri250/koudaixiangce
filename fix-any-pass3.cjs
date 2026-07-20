const fs = require('fs');
const path = require('path');

function fixFile(filePath) {
  const fullPath = path.resolve(filePath);
  if (!fs.existsSync(fullPath)) return;
  let content = fs.readFileSync(fullPath, 'utf8');
  let original = content;
  
  // ===== ImageCanvas.tsx =====
  if (filePath.includes('ImageCanvas.tsx')) {
    // Konva event type - used extensively in this file
    // Define a KonvaEvent type at the top if not already imported
    if (!content.includes('KonvaEvent')) {
      // Add KonvaEvent interface near the top of the file, after imports
      const insertAfter = "import { SubMask } from './Masks';";
      const konvaEventType = `

interface KonvaPointerEvent {
  evt: PointerEvent & { stopPropagation?: () => void };
  target: { getStage: () => KonvaStage; getPointerPosition: () => Coord | null };
  cancelBubble?: boolean;
}

interface KonvaStage {
  container: () => HTMLDivElement;
  getPointerPosition: () => Coord | null;
  getAbsolutePosition: () => Coord;
  setPointersPositions: (e: PointerEvent | TouchEvent | MouseEvent) => void;
  scaleX: () => number;
  scaleY: () => number;
  width: () => number;
  height: () => number;
  one: (evt: string, fn: () => void) => void;
}

interface DrawnLine {
  id: string;
  tool: string;
  points: number[];
  color: string;
  size: number;
  feather: number;
}`;
      content = content.replace(insertAfter, insertAfter + konvaEventType);
    }
    
    // onLiveMaskPreview?: (previewMaskDef: any) => void;
    content = content.replace(
      'onLiveMaskPreview?: (previewMaskDef: any) => void;',
      'onLiveMaskPreview?: (previewMaskDef: Record<string, unknown>) => void;'
    );
    
    // onMaskInteractionStart(event?: any): void;
    content = content.replace(
      'onMaskInteractionStart(event?: any): void;',
      'onMaskInteractionStart(event?: KonvaPointerEvent): void;'
    );
    
    // (stage: any) => in getPointer/getCanvasPointer
    content = content.replace(/\(stage: any\)/g, '(stage: KonvaStage)');
    
    // updateP(newP: any)
    content = content.replace(
      'const updateP = useCallback((newP: any)',
      'const updateP = useCallback((newP: Record<string, number | string | boolean | number[] | null>)'
    );
    
    // dragStartParams ref - was changed to HTMLElement by pass1, need to fix
    content = content.replace(
      'const dragStartParams = useRef<HTMLElement>(null);',
      'const dragStartParams = useRef<Record<string, number> | null>(null);'
    );
    
    // (e: any) => Konva event handlers
    content = content.replace(
      /\(e: any\) => \{/g,
      '(e: KonvaPointerEvent) => {'
    );
    content = content.replace(
      /\(e: any\)/g,
      '(e: KonvaPointerEvent)'
    );
    
    // function(this: any)
    content = content.replace(
      'function (this: any)',
      'function (this: KonvaStage)'
    );
    
    // (stage: any, pointerPos: any)
    content = content.replace(
      '(stage: KonvaStage, pointerPos: any)',
      '(stage: KonvaStage, pointerPos: Coord)'
    );
    
    // useState<any>(null) patterns
    content = content.replace(
      "const [localInitialDrawParams, setLocalInitialDrawParams] = useState<any>(null);",
      "const [localInitialDrawParams, setLocalInitialDrawParams] = useState<Record<string, number | string | boolean | null> | null>(null);"
    );
    content = content.replace(
      "const [straightenLine, setStraightenLine] = useState<any>(null);",
      "const [straightenLine, setStraightenLine] = useState<{ start: Coord; end: Coord } | null>(null);"
    );
    
    // setStraightenLine((prev: any) =>
    content = content.replace(
      'setStraightenLine((prev: any)',
      'setStraightenLine((prev: { start: Coord; end: Coord })'
    );
    
    // handleStraightenMouseDown = (e: any)
    content = content.replace(
      'const handleStraightenMouseDown = (e: any)',
      'const handleStraightenMouseDown = (e: KonvaPointerEvent)'
    );
    content = content.replace(
      'const handleStraightenMouseMove = (e: any)',
      'const handleStraightenMouseMove = (e: KonvaPointerEvent)'
    );
    
    // handleWbClick(e: any)
    content = content.replace(
      '(e: any) => {\n        if (!isWbPickerActive',
      '(e: KonvaPointerEvent) => {\n        if (!isWbPickerActive'
    );
    
    // handleStart(e: any)
    content = content.replace(
      '(e: any) => {\n        if (e.evt && typeof e.evt.button',
      '(e: KonvaPointerEvent) => {\n        if (e.evt && typeof e.evt.button'
    );
    
    // handleMove(e: any)
    content = content.replace(
      '(e: any) => {\n        if (isWbPickerActive)',
      '(e: KonvaPointerEvent | PointerEvent) => {\n        if (isWbPickerActive)'
    );
    
    // (e?: any) => { in handleMaskInteractionStart
    content = content.replace(
      '(e?: any) => {',
      '(e?: KonvaPointerEvent) => {'
    );
  }
  
  // ===== PresetsPanel.tsx =====
  if (filePath.includes('PresetsPanel.tsx')) {
    // Read the file to understand the specific patterns
    // Most of the any's are in: useState<any>, (prev: any), callback params, etc.
  }
  
  // ===== ContextMenuContext.tsx =====
  if (filePath.includes('ContextMenuContext.tsx')) {
    // Will handle specifically
  }

  if (content !== original) {
    fs.writeFileSync(fullPath, content, 'utf8');
    console.log(`FIXED: ${filePath}`);
  } else {
    console.log(`NO CHANGE: ${filePath}`);
  }
}

const files = [
  'src/components/panel/editor/ImageCanvas.tsx',
];

for (const file of files) {
  fixFile(file);
}
