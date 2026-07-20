const fs = require('fs');

let content = fs.readFileSync('/workspace/src/components/panel/editor/ImageCanvas.tsx', 'utf8');

// Replace KonvaPointerEvent with Konva.KonvaEventObject<PointerEvent>
content = content.replace(/\bKonvaPointerEvent\b/g, 'Konva.KonvaEventObject<PointerEvent>');

// Replace KonvaStageType with Konva.Stage
content = content.replace(/\bKonvaStageType\b/g, 'Konva.Stage');

// Fix updateP - it's used with setParams which is more generic
// Revert to accepting the proper type from the parent component
content = content.replace(
  "const updateP = useCallback((newP: Partial<Adjustments>)",
  "const updateP = useCallback((newP: Record<string, number | string | boolean | number[] | null>)"
);

// Fix dragStartParams - it stores various properties
content = content.replace(
  "const dragStartParams = useRef<{ startX: number; startY: number; maskPreviewData?: Record<string, unknown> } | null>(null);",
  "const dragStartParams = useRef<Record<string, number | Record<string, unknown>> | null>(null);"
);

// Fix localInitialDrawParams - used to store initial mask params before drag
content = content.replace(
  "const [localInitialDrawParams, setLocalInitialDrawParams] = useState<Record<string, number> | null>(null);",
  "const [localInitialDrawParams, setLocalInitialDrawParams] = useState<Record<string, number | string | boolean | null> | null>(null);"
);

// Fix straightenLine
content = content.replace(
  "const [straightenLine, setStraightenLine] = useState<{ start: Coord; end: Coord; angle?: number } | null>(null);",
  "const [straightenLine, setStraightenLine] = useState<{ start: Coord; end: Coord } | null>(null);"
);
content = content.replace(
  "setStraightenLine((prev: { start: Coord; end: Coord; angle?: number })",
  "setStraightenLine((prev: { start: Coord; end: Coord })"
);

// Fix handleMaskInteractionStart event type
content = content.replace(
  '(e?: Konva.KonvaEventObject<PointerEvent>) => {',
  '(e?: Konva.KonvaEventObject<PointerEvent> | PointerEvent) => {'
);

// Fix (stage: Konva.Stage, pointerPos: Coord) patterns
// These are helper functions that take a stage object
// Since Konva.Stage is the actual class, this should work

// Fix handleStraightenMouseDown/Move
content = content.replace(
  'const handleStraightenMouseDown = (e: Konva.KonvaEventObject<PointerEvent>)',
  'const handleStraightenMouseDown = (e: Konva.KonvaEventObject<PointerEvent>)'
);
content = content.replace(
  'const handleStraightenMouseMove = (e: Konva.KonvaEventObject<PointerEvent>)',
  'const handleStraightenMouseMove = (e: Konva.KonvaEventObject<PointerEvent>)'
);

// Fix the stage ref type - was useRef<KonvaStageType>(null)
content = content.replace(
  'useRef<Konva.Stage>(null);',
  'useRef<Konva.Stage>(null);'
);

// Fix function(this: Konva.Stage | unknown) -> just use unknown
content = content.replace(
  'function (this: Konva.Stage | unknown)',
  'function (this: unknown)'
);

fs.writeFileSync('/workspace/src/components/panel/editor/ImageCanvas.tsx', content, 'utf8');
console.log('FIXED: ImageCanvas.tsx');
