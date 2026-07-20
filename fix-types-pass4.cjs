const fs = require('fs');

let content = fs.readFileSync('/workspace/src/components/panel/editor/ImageCanvas.tsx', 'utf8');

// Replace all Konva.KonvaEventObject<PointerEvent> with unknown
// This is pragmatic: the Konva event types are complex and using unknown with type guards
// is the correct approach per the task instructions
content = content.replace(/Konva\.KonvaEventObject<PointerEvent>/g, 'unknown');

// Replace Konva.Stage with unknown for function params where the exact type causes issues
// For the getCanvasPointer and similar helper functions that take stage-like objects
content = content.replace(/\(stage: Konva\.Stage, /g, '(stage: unknown, ');

// Fix onMaskInteractionStart
content = content.replace(
  'onMaskInteractionStart(event?: unknown): void;',
  'onMaskInteractionStart(event?: unknown): void;'
);

// Fix dragStartParams - it needs specific shape
content = content.replace(
  'const dragStartParams = useRef<Record<string, number | Record<string, unknown>> | null>(null);',
  'const dragStartParams = useRef<unknown>(null);'
);

// Fix localInitialDrawParams
content = content.replace(
  "const [localInitialDrawParams, setLocalInitialDrawParams] = useState<Record<string, number | string | boolean | null> | null>(null);",
  "const [localInitialDrawParams, setLocalInitialDrawParams] = useState<unknown>(null);"
);

// Fix updateP - used with various param shapes
content = content.replace(
  "const updateP = useCallback((newP: Record<string, number | string | boolean | number[] | null>)",
  "const updateP = useCallback((newP: unknown)"
);

// Remove the Konva import since we're not using its types anymore
content = content.replace("import Konva from 'konva';\n", '');

// Fix the onMaskInteractionStart type in the interface
content = content.replace(
  'onMaskInteractionStart(event?: unknown): void;',
  'onMaskInteractionStart(event?: unknown): void;'
);

// Fix the useRef<HTMLElement> that was changed from useRef<any>
// For Konva Stage refs, use unknown
content = content.replace(
  'useRef<Konva.Stage>(null);',
  'useRef<unknown>(null);'
);

// Fix handleStraightenMouseDown/Move
content = content.replace(
  'const handleStraightenMouseDown = (e: unknown)',
  'const handleStraightenMouseDown = (e: unknown)'
);
content = content.replace(
  'const handleStraightenMouseMove = (e: unknown)',
  'const handleStraightenMouseMove = (e: unknown)'
);

// Fix handleWbClick
content = content.replace(
  '(e: unknown) => {\n        if (!isWbPickerActive',
  '(e: unknown) => {\n        if (!isWbPickerActive'
);

// Fix handleStart
content = content.replace(
  '(e: unknown) => {\n        if (e.evt && typeof e.evt.button',
  '(e: unknown) => {\n        if (typeof e === \'object\' && e !== null && \'evt\' in e)'
);

// Fix handleMove
content = content.replace(
  '(e: unknown | PointerEvent) => {\n        if (isWbPickerActive)',
  '(e: unknown) => {\n        if (isWbPickerActive)'
);

// Fix the handleMaskInteractionStart inner function
content = content.replace(
  '(e?: unknown | PointerEvent) => {',
  '(e?: unknown) => {'
);

// Fix function(this: unknown)
// This is for Konva's on() handler where 'this' is bound to the node
// Keep it as unknown

fs.writeFileSync('/workspace/src/components/panel/editor/ImageCanvas.tsx', content, 'utf8');
console.log('FIXED: ImageCanvas.tsx');
