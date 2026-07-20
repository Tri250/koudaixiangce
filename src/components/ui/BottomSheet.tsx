import { useRef, useState, useCallback } from 'react';
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

  const maxH = maxHeight || (typeof window !== 'undefined' ? window.innerHeight * 0.6 : 400);

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
    handleDragStart(e.touches[0].clientY);
  }, [handleDragStart]);

  const handleTouchMove = useCallback((e: React.TouchEvent) => {
    e.preventDefault();
    handleDragMove(e.touches[0].clientY);
  }, [handleDragMove]);

  const handleTouchEnd = useCallback(() => {
    handleDragEnd();
  }, [handleDragEnd]);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    handleDragStart(e.clientY);
  }, [handleDragStart]);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    handleDragMove(e.clientY);
  }, [handleDragMove]);

  const handleMouseUp = useCallback(() => {
    handleDragEnd();
  }, [handleDragEnd]);

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
          }}
        >
          {/* Drag handle */}
          <div
            className="flex justify-center py-3 cursor-grab active:cursor-grabbing select-none"
            onTouchStart={handleTouchStart}
            onTouchMove={handleTouchMove}
            onTouchEnd={handleTouchEnd}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
          >
            <div className="w-10 h-1 rounded-full bg-text-secondary/30 transition-all duration-200" />
          </div>
          <div className="overflow-y-auto" style={{ height: `${Math.max(0, height - 28)}px` }}>
            {children}
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
