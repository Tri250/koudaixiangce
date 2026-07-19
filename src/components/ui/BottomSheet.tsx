import { useRef, useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

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

  const maxH = maxHeight || (typeof window !== 'undefined' ? window.innerHeight * 0.6 : 400);

  const handleTouchStart = useCallback((e: React.TouchEvent) => {
    startY.current = e.touches[0].clientY;
    startHeight.current = height;
    setIsDragging(true);
  }, [height]);

  const handleTouchMove = useCallback((e: React.TouchEvent) => {
    if (!isDragging) return;
    const deltaY = startY.current - e.touches[0].clientY;
    const newHeight = Math.min(maxH, Math.max(minHeight, startHeight.current + deltaY));
    setHeight(newHeight);
  }, [isDragging, maxH, minHeight]);

  const handleTouchEnd = useCallback(() => {
    setIsDragging(false);
  }, []);

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          ref={sheetRef}
          initial={{ y: '100%' }}
          animate={{ y: 0 }}
          exit={{ y: '100%' }}
          transition={{ type: 'spring', damping: 28, stiffness: 350, mass: 0.8 }}
          className="fixed bottom-0 left-0 right-0 liquid-glass z-50"
          style={{
            height: `${height}px`,
            transition: isDragging ? 'none' : 'height 220ms cubic-bezier(0, 0, 0.2, 1)',
          }}
        >
          {/* Drag handle */}
          <div
            className="flex justify-center py-3 cursor-grab active:cursor-grabbing"
            onTouchStart={handleTouchStart}
            onTouchMove={handleTouchMove}
            onTouchEnd={handleTouchEnd}
          >
            <div className="w-10 h-1 rounded-full bg-text-secondary/30 transition-all duration-200" />
          </div>
          <div className="overflow-y-auto" style={{ height: `${height - 24}px` }}>
            {children}
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
