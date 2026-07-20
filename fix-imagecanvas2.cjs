const fs = require('fs');

let content = fs.readFileSync('/workspace/src/components/panel/editor/ImageCanvas.tsx', 'utf8');

// Fix remaining (e: any) patterns in Konva JSX event handlers
// These are Konva component props that take KonvaEventObject events
const konvaEventMap = {
  'onMouseEnter': 'KonvaEventObject<MouseEvent>',
  'onMouseLeave': 'KonvaEventObject<MouseEvent>',
  'onDragMove': 'KonvaEventObject<DragEvent>',
  'onDragEnd': 'KonvaEventObject<DragEvent>',
  'onDragStart': 'KonvaEventObject<DragEvent>',
  'onTransformEnd': 'KonvaEventObject<Event>',
  'onTransform': 'KonvaEventObject<Event>',
};

for (const [event, type] of Object.entries(konvaEventMap)) {
  // Pattern: onEvent={(e: any) =>
  const regex = new RegExp(`${event}=\\{\\(e: any\\)`, 'g');
  content = content.replace(regex, `${event}={(e: ${type})`);
}

// Fix useCallback handlers that take Konva events (e: any) =>
// Lines 297, 325, 439, 469, 483, 493, 505, 528, 551, 571, 595, 626
// These are all Konva pointer/touch event handlers in useCallback
// Pattern: (e: any) => {\n        const pointerPos
content = content.replace(
  /\(e: any\) => \{\n\s+const pointerPos = getPointer\(e\.target/g,
  '(e: KonvaEventObject<PointerEvent>) => {\n        const pointerPos = getPointer(e.target'
);

// Pattern: (e: any) => {\n        if (isToolActive
content = content.replace(
  /\(e: any\) => \{\n\s+if \(isToolActive\)/g,
  '(e: KonvaEventObject<PointerEvent>) => {\n        if (isToolActive)'
);

// Pattern: (e: any) => {\n        if (e.evt.cancelable
content = content.replace(
  /\(e: any\) => \{\n\s+if \(e\.evt\.cancelable\)/g,
  '(e: KonvaEventObject<PointerEvent>) => {\n        if (e.evt.cancelable)'
);

// Pattern: (e: any) => {\n        if (e.evt && typeof e.evt.button
content = content.replace(
  /\(e: any\) => \{\n\s+if \(e\.evt && typeof e\.evt\.button/g,
  '(e: KonvaEventObject<PointerEvent>) => {\n        if (e.evt && typeof e.evt.button'
);

// setRotateCursor(stage: any, pointerPos: any)
content = content.replace(
  '(stage: any, pointerPos: any)',
  '(stage: KonvaStageClass, pointerPos: Coord)'
);

// Lines 1669, 1998 - let me check those
// They might be different patterns

// Fix any remaining (e: any) in useCallback with Konva events
// The general pattern is in useCallback callbacks
content = content.replace(
  /\(e: any\) => \{\n\s+onMaskInteractionStart\(e\)/g,
  '(e: KonvaEventObject<PointerEvent>) => {\n        onMaskInteractionStart(e)'
);

// Remaining generic (e: any) patterns in useCallback
content = content.replace(
  /\(e: any\) => \{\n\s+if \(!isToolActive\)/g,
  '(e: KonvaEventObject<PointerEvent>) => {\n        if (!isToolActive)'
);

content = content.replace(
  /\(e: any\) => \{\n\s+e\.evt\.stopPropagation/g,
  '(e: KonvaEventObject<PointerEvent>) => {\n        e.evt.stopPropagation'
);

// For the Konva component props that are onDragMove/onDragEnd (non-pointer events)
// These take DragEvent
content = content.replace(
  'onDragMove={(e: KonvaEventObject<PointerEvent>)',
  'onDragMove={(e: KonvaEventObject<DragEvent>)'
);
content = content.replace(
  'onDragEnd={(e: KonvaEventObject<PointerEvent>)',
  'onDragEnd={(e: KonvaEventObject<DragEvent>)'
);

fs.writeFileSync('/workspace/src/components/panel/editor/ImageCanvas.tsx', content, 'utf8');
console.log('FIXED: ImageCanvas.tsx');
