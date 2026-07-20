const fs = require('fs');

let content = fs.readFileSync('/workspace/src/components/panel/editor/ImageCanvas.tsx', 'utf8');

// Now apply the any fixes using the proper Konva types

// Pattern 1: (e: any) => in Konva event handlers → (e: KonvaEventObject<PointerEvent>)
// These are used in onPointerDown, onMouseDown, etc. Konva JSX event handlers
content = content.replace(/onPointerDown=\{\(e: any\)/g, 'onPointerDown={(e: KonvaEventObject<PointerEvent>)');
content = content.replace(/onMouseDown=\{\(e: any\)/g, 'onMouseDown={(e: KonvaEventObject<PointerEvent>)');
content = content.replace(/onMouseMove=\{\(e: any\)/g, 'onMouseMove={(e: KonvaEventObject<PointerEvent>)');
content = content.replace(/onMouseUp=\{\(e: any\)/g, 'onMouseUp={(e: KonvaEventObject<PointerEvent>)');
content = content.replace(/onTouchStart=\{\(e: any\)/g, 'onTouchStart={(e: KonvaEventObject<TouchEvent>)');
content = content.replace(/onTouchMove=\{\(e: any\)/g, 'onTouchMove={(e: KonvaEventObject<TouchEvent>)');
content = content.replace(/onTouchEnd=\{\(e: any\)/g, 'onTouchEnd={(e: KonvaEventObject<TouchEvent>)');
content = content.replace(/onClick=\{\(e: any\)/g, 'onClick={(e: KonvaEventObject<MouseEvent>)');
content = content.replace(/onDblClick=\{\(e: any\)/g, 'onDblClick={(e: KonvaEventObject<MouseEvent>)');

// Pattern 2: standalone (e: any) => in useCallback/useEffect callbacks that handle Konva events
// These are harder - need to determine if they're Konva events or React events
// For Konva-related handlers inside useCallback:
content = content.replace(
  /\(e: any\) => \{\n\s+if \(e\.evt/g,
  '(e: KonvaEventObject<PointerEvent>) => {\n        if (e.evt'
);

// Pattern 3: onMaskInteractionStart(event?: any) → Konva event type
content = content.replace(
  'onMaskInteractionStart(event?: any): void;',
  'onMaskInteractionStart(event?: KonvaEventObject<PointerEvent>): void;'
);

// Pattern 4: (stage: any) => in getPointer/getCanvasPointer
content = content.replace(/\(stage: any\)/g, '(stage: KonvaStageClass)');

// Pattern 5: getCanvasPointer(stage: any, pointerPos: any)
content = content.replace(
  'getCanvasPointer(stage: KonvaStageClass, pointerPos: any)',
  'getCanvasPointer(stage: KonvaStageClass, pointerPos: Coord)'
);

// Pattern 6: onLiveMaskPreview
content = content.replace(
  'onLiveMaskPreview?: (previewMaskDef: any) => void;',
  'onLiveMaskPreview?: (previewMaskDef: Record<string, unknown>) => void;'
);

// Pattern 7: useState<any>
content = content.replace(
  "const [localInitialDrawParams, setLocalInitialDrawParams] = useState<any>(null);",
  "const [localInitialDrawParams, setLocalInitialDrawParams] = useState<Record<string, number | string | boolean | null> | null>(null);"
);
content = content.replace(
  "const [straightenLine, setStraightenLine] = useState<any>(null);",
  "const [straightenLine, setStraightenLine] = useState<{ start: Coord; end: Coord } | null>(null);"
);

// Pattern 8: setStraightenLine((prev: any)
content = content.replace(
  'setStraightenLine((prev: any)',
  'setStraightenLine((prev: { start: Coord; end: Coord })'
);

// Pattern 9: updateP
content = content.replace(
  'const updateP = useCallback((newP: any)',
  'const updateP = useCallback((newP: Record<string, number | string | boolean | number[] | null>)'
);

// Pattern 10: dragStartParams
content = content.replace(
  'const dragStartParams = useRef<any>(null);',
  'const dragStartParams = useRef<Record<string, number> | null>(null);'
);

// Pattern 11: setAdjustments((prev: any)
content = content.replace(/setAdjustments\(\(prev: any\)/g, 'setAdjustments((prev: Adjustments)');

// Pattern 12: (prev: any) => ({...prev,
content = content.replace(/\(prev: any\) => \(\{ \.\.\.prev,/g, '(prev: Adjustments) => ({ ...prev,');

// Pattern 13: as any
content = content.replace(/\bas any\b/g, 'as unknown');

// Pattern 14: Record<string, any>
content = content.replace(/Record<string,\s*any>/g, 'Record<string, unknown>');

// Pattern 15: any[]
content = content.replace(/:\s*any\[\]/g, ': unknown[]');
content = content.replace(/<any\[\]>/g, '<unknown[]>');

// Pattern 16: function(this: any)
content = content.replace(/function\s*\(this: any\)/g, 'function (this: KonvaStageClass)');

// Pattern 17: remaining (e: any) in non-JSX Konva callback contexts
// These are in useCallback that handles Konva events
// Pattern: (e: any) => { if (... e.evt ...)
// We already handled those above with the if(e.evt pattern

// Pattern 18: handleWbClick, handleStraightenMouseDown, handleStraightenMouseMove, etc.
content = content.replace(
  'const handleStraightenMouseDown = (e: any)',
  'const handleStraightenMouseDown = (e: KonvaEventObject<PointerEvent>)'
);
content = content.replace(
  'const handleStraightenMouseMove = (e: any)',
  'const handleStraightenMouseMove = (e: KonvaEventObject<PointerEvent>)'
);

// Pattern 19: useRef<any> that wasn't caught by the generic replacement
// In this file, Konva refs should be specific Konva types
content = content.replace(/useRef<any>/g, 'useRef<unknown>');

// Fix specific refs that should be Konva types
// The stageRef should be KonvaStageClass
// trRef should be Konva.Transformer
// shapeRef should be Konva.Shape

fs.writeFileSync('/workspace/src/components/panel/editor/ImageCanvas.tsx', content, 'utf8');
console.log('FIXED: ImageCanvas.tsx');
