import { useState, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

interface BeforeAfterSliderProps {
  beforeSrc: string;
  afterSrc: string;
  isOpen: boolean;
  onClose: () => void;
}

export default function BeforeAfterSlider({
  beforeSrc,
  afterSrc,
  isOpen,
  onClose,
}: BeforeAfterSliderProps) {
  const [position, setPosition] = useState(50);
  const containerRef = useRef<HTMLDivElement>(null);
  const isDragging = useRef(false);

  const updatePosition = useCallback((clientX: number) => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const x = clientX - rect.left;
    const pct = Math.max(0, Math.min(100, (x / rect.width) * 100));
    setPosition(pct);
  }, []);

  const handlePointerDown = useCallback((e: React.PointerEvent) => {
    isDragging.current = true;
    e.currentTarget.setPointerCapture(e.pointerId);
    updatePosition(e.clientX);
  }, [updatePosition]);

  const handlePointerMove = useCallback((e: React.PointerEvent) => {
    if (!isDragging.current) return;
    updatePosition(e.clientX);
  }, [updatePosition]);

  const handlePointerUp = useCallback(() => {
    isDragging.current = false;
  }, []);

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          ref={containerRef}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
          className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center"
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
        >
          {/* Close button */}
          <button
            onClick={onClose}
            className="absolute top-4 right-4 w-10 h-10 rounded-full bg-deep/80 text-text-primary flex items-center justify-center z-10"
          >
            ✕
          </button>

          {/* Labels */}
          <div className="absolute top-4 left-1/2 -translate-x-1/2 flex gap-4 z-10">
            <span className="px-3 py-1 rounded-full bg-deep/80 text-xs font-medium text-text-primary">
              修图后
            </span>
            <span className="px-3 py-1 rounded-full bg-deep/80 text-xs font-medium text-text-secondary">
              原图
            </span>
          </div>

          {/* Image container */}
          <div className="relative w-full h-full max-w-[90vw] max-h-[80vh] overflow-hidden rounded-lg">
            {/* After image (full) */}
            <img
              src={afterSrc}
              alt="After"
              className="absolute inset-0 w-full h-full object-contain"
              draggable={false}
            />
            
            {/* Before image (clipped) */}
            <div
              className="absolute inset-0 overflow-hidden"
              style={{ width: `${position}%` }}
            >
              <img
                src={beforeSrc}
                alt="Before"
                className="absolute inset-0 w-full h-full object-contain"
                style={{ width: `${100 / (position / 100)}%`, maxWidth: 'none' }}
                draggable={false}
              />
            </div>

            {/* Divider line */}
            <div
              className="absolute top-0 bottom-0 w-0.5 bg-white/80 z-10"
              style={{ left: `${position}%` }}
            >
              {/* Drag handle */}
              <div className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 w-8 h-8 rounded-full bg-white/90 shadow-lg flex items-center justify-center">
                <div className="flex gap-0.5">
                  <div className="w-0.5 h-3 bg-gray-600 rounded-full" />
                  <div className="w-0.5 h-3 bg-gray-600 rounded-full" />
                </div>
              </div>
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
