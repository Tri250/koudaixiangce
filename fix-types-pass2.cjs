const fs = require('fs');
const path = require('path');

function fixFile(filePath) {
  const fullPath = path.resolve(filePath);
  if (!fs.existsSync(fullPath)) return;
  let content = fs.readFileSync(fullPath, 'utf8');
  let original = content;
  
  // ===== ImageCanvas.tsx =====
  if (filePath.includes('ImageCanvas.tsx')) {
    // Replace KonvaPointerEvent with KonvaEventObject<PointerEvent>
    content = content.replace(/\bKonvaPointerEvent\b/g, 'KonvaEventObject<PointerEvent>');
    
    // Replace KonvaStage with KonvaStageType
    content = content.replace(/\bKonvaStage\b/g, 'KonvaStageType');
    
    // Fix onMaskInteractionStart
    content = content.replace(
      'onMaskInteractionStart(event?: KonvaEventObject<PointerEvent>): void;',
      'onMaskInteractionStart(event?: KonvaEventObject<PointerEvent>): void;'
    );
    
    // Fix useRef<HTMLElement> that should be more specific for Konva
    content = content.replace(
      'useRef<HTMLElement>(null);',
      'useRef<KonvaStageType>(null);'
    );
    // Some refs should be HTMLCanvasElement for the waveform/canvas
    // but for Konva Stage refs, they should be KonvaStageType
    
    // Fix the dragStartParams type - it stores {startX, startY, ...}
    content = content.replace(
      'const dragStartParams = useRef<Record<string, number> | null>(null);',
      'const dragStartParams = useRef<{ startX: number; startY: number; maskPreviewData?: Record<string, unknown> } | null>(null);'
    );
    
    // Fix the function(this: KonvaStageType) pattern
    // This is used in Konva's on() handler where 'this' is bound to the node
    // Replace with a simpler approach
    content = content.replace(
      'function (this: KonvaStageType)',
      'function (this: KonvaStageType | unknown)'
    );
    
    // Fix updateP - should accept the adjustment parameter types
    content = content.replace(
      'const updateP = useCallback((newP: Record<string, number | string | boolean | number[] | null>)',
      'const updateP = useCallback((newP: Partial<Adjustments>)'
    );
    
    // Fix localInitialDrawParams state
    content = content.replace(
      "const [localInitialDrawParams, setLocalInitialDrawParams] = useState<Record<string, number | string | boolean | null> | null>(null);",
      "const [localInitialDrawParams, setLocalInitialDrawParams] = useState<Record<string, number> | null>(null);"
    );
    
    // Fix straightenLine state  
    content = content.replace(
      "const [straightenLine, setStraightenLine] = useState<{ start: Coord; end: Coord } | null>(null);",
      "const [straightenLine, setStraightenLine] = useState<{ start: Coord; end: Coord; angle?: number } | null>(null);"
    );
    content = content.replace(
      'setStraightenLine((prev: { start: Coord; end: Coord })',
      'setStraightenLine((prev: { start: Coord; end: Coord; angle?: number })'
    );
    
    // Fix stage.container() → it returns HTMLDivElement not HTMLElement
    // Already handled by using KonvaStageType
  }

  if (content !== original) {
    fs.writeFileSync(fullPath, content, 'utf8');
    console.log(`FIXED: ${filePath}`);
  } else {
    console.log(`NO CHANGE: ${filePath}`);
  }
}

fixFile('src/components/panel/editor/ImageCanvas.tsx');
