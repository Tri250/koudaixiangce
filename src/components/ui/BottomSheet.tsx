import { useRef, useState, useCallback, useEffect } from 'react';
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion';

interface BottomSheetProps {
  isOpen: boolean;
  onClose?: () => void;
  children: React.ReactNode;
  defaultHeight?: number;
  minHeight?: number;
  maxHeight?: number;
}

export default function BottomSheet({
  isOpen,
  onClose,
  children,
  defaultHeight = 280,
  minHeight = 120,
  maxHeight,
}: BottomSheetProps) {
  const sheetRef = useRef<HTMLDivElement>(null);
  const [height, setHeight] = useState(defaultHeight);
  const [isDragging, setIsDragging] = useState(false);
  const startY = useRef(0);
  const startHeight = useRef(0);
  const shouldReduceMotion = useReducedMotion();

  // Increase maxHeight to 75% of viewport for better content visibility on mobile
  const maxH = maxHeight || (typeof window !== 'undefined' ? window.innerHeight * 0.75 : 400);

  // Reset height to default when sheet opens, so user doesn't get stuck with a previously dragged size
  useEffect(() => {
    if (isOpen) {
      setHeight(Math.min(defaultHeight, maxH));
    }
  }, [isOpen, defaultHeight, maxH]);

  const handleDragStart = useCallback((clientY: number) => {
    startY.current = clientY;
    startHeight.current = height;
    setIsDragging(true);
  }, [height]);

  const handleDragMove = useCallback((clientY: number) => {
    if (!isDragging) return;
    const deltaY = startY.current - clientY;
    const newHeight = Math.min(maxH, Math.max(minHeight, startHeight.current + deltaY));
    setHeight(newHeight);
  }, [isDragging, maxH, minHeight]);

  const handleDragEnd = useCallback(() => {
    setIsDragging(false);
  }, []);

  const handleTouchStart = useCallback((e: React.TouchEvent) => {
    // Only handle drag from the drag handle area, not from content
    handleDragStart(e.touches[0].clientY);
  }, [handleDragStart]);

  const handleTouchMove = useCallback((e: React.TouchEvent) => {
    // Only preventDefault when actively dragging the handle,
    // so content scrolling inside the sheet still works
    if (isDragging) {
      e.preventDefault();
    }
    handleDragMove(e.touches[0].clientY);
  }, [handleDragMove, isDragging]);

  const handleTouchEnd = useCallback(() => {
    handleDragEnd();
  }, [handleDragEnd]);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    handleDragStart(e.clientY);
  }, [handleDragStart]);

  // Use window-level events for mouse move/up so dragging works even when
  // cursor leaves the handle area
  useEffect(() => {
    if (!isDragging) return;

    const onMouseMove = (e: MouseEvent) => {
      handleDragMove(e.clientY);
    };
    const onMouseUp = () => {
      handleDragEnd();
    };

    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
    return () => {
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    };
  }, [isDragging, handleDragMove, handleDragEnd]);

  const handleHandleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault(); // prevent text selection during drag
    handleDragStart(e.clientY);
  }, [handleDragStart]);

  // Don't attach onMouseMove/onMouseUp to the handle div directly;
  // those are handled by the window-level listeners above.
  // Only onMouseDown (to initiate drag) remains on the handle.
  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          ref={sheetRef}
          initial={{ y: shouldReduceMotion ? 0 : '100%' }}
          animate={{ y: 0 }}
          exit={{ y: shouldReduceMotion ? 0 : '100%' }}
          transition={shouldReduceMotion ? { duration: 0 } : { type: 'spring', damping: 28, stiffness: 350, mass: 0.8 }}
          className="fixed bottom-0 left-0 right-0 liquid-glass z-50"
          style={{
            height: `${height}px`,
            transition: isDragging ? 'none' : 'height 220ms cubic-bezier(0, 0, 0.2, 1)',
            touchAction: isDragging ? 'none' : 'pan-y',
          }}
        >
          {/* Drag handle */}
          <div
            className="flex justify-center py-3 cursor-grab active:cursor-grabbing select-none touch-none"
            onTouchStart={handleTouchStart}
            onTouchMove={handleTouchMove}
            onTouchEnd={handleTouchEnd}
            onMouseDown={handleHandleMouseDown}
          >
            <div className="w-10 h-1 rounded-full bg-text-secondary/30 transition-all duration-200" />
          </div>
          <div className="overflow-y-auto" style={{ height: `${Math.max(0, height - 32)}px` }}>
            {children}
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
