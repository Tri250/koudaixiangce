import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { invoke } from '@tauri-apps/api/core';
import {
  Move,
  Shrink,
  Maximize2,
  Wind,
  RotateCw,
  Undo2,
  RefreshCcw,
  Loader2,
  X,
  Check,
} from 'lucide-react';
import clsx from 'clsx';
import Slider from '../ui/Slider';
import Button from '../ui/Button';
import Text from '../ui/Text';
import { TextColors, TextVariants, TextWeights } from '../../types/typography';
import { BrushType, LiquifyStroke, useRetouching } from '../../hooks/useRetouching';

interface LiquifyModalProps {
  isOpen: boolean;
  onClose: () => void;
  imageUrl: string | null;
  imageWidth: number;
  imageHeight: number;
}

interface BrushTypeOption {
  id: BrushType;
  icon: typeof Move;
  labelKey: string;
}

const BRUSH_TYPES: BrushTypeOption[] = [
  { id: 'push', icon: Move, labelKey: 'editor.liquify.brushTypes.push' },
  { id: 'pull', icon: Shrink, labelKey: 'editor.liquify.brushTypes.pull' },
  { id: 'pucker', icon: Shrink, labelKey: 'editor.liquify.brushTypes.pucker' },
  { id: 'bloat', icon: Maximize2, labelKey: 'editor.liquify.brushTypes.bloat' },
  { id: 'twirl', icon: Wind, labelKey: 'editor.liquify.brushTypes.twirl' },
  { id: 'reconstruct', icon: RotateCw, labelKey: 'editor.liquify.brushTypes.reconstruct' },
];

export default function LiquifyModal({ isOpen, onClose, imageUrl, imageWidth, imageHeight }: LiquifyModalProps) {
  const { t } = useTranslation();
  const {
    liquifyStrokes,
    addLiquifyStroke,
    undoLiquifyStroke,
    clearLiquifyStrokes,
    applyLiquify,
    resetLiquifyMesh,
  } = useRetouching();

  const [isMounted, setIsMounted] = useState(false);
  const [show, setShow] = useState(false);
  const [activeBrushType, setActiveBrushType] = useState<BrushType>('push');
  const [brushSize, setBrushSize] = useState(80);
  const [brushPressure, setBrushPressure] = useState(50);
  const [isApplying, setIsApplying] = useState(false);

  // Drawing state
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const overlayRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const isDrawingRef = useRef(false);
  const currentStrokePointsRef = useRef<{ x: number; y: number }[]>([]);

  useEffect(() => {
    if (isOpen) {
      setIsMounted(true);
      // Don't clear strokes on open — they may already exist from prior drawing
      const timer = setTimeout(() => setShow(true), 10);
      return () => clearTimeout(timer);
    } else {
      setShow(false);
      const timer = setTimeout(() => setIsMounted(false), 300);
      return () => clearTimeout(timer);
    }
  }, [isOpen]);

  // Scale image to fit container
  const [scale, setScale] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });

  useEffect(() => {
    if (!isMounted || !containerRef.current) return;
    const container = containerRef.current;
    const containerW = container.clientWidth;
    const containerH = container.clientHeight;
    if (imageWidth <= 0 || imageHeight <= 0 || containerW <= 0 || containerH <= 0) return;
    const scaleX = containerW / imageWidth;
    const scaleY = containerH / imageHeight;
    const newScale = Math.min(scaleX, scaleY, 1);
    setScale(newScale > 0 && isFinite(newScale) ? newScale : 1);
    setOffset({
      x: (containerW - imageWidth * newScale) / 2,
      y: (containerH - imageHeight * newScale) / 2,
    });
  }, [isMounted, imageWidth, imageHeight]);

  // Draw image on canvas
  useEffect(() => {
    if (!canvasRef.current || !imageUrl) return;
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    canvas.width = imageWidth;
    canvas.height = imageHeight;
    const img = new Image();
    let cancelled = false;
    img.onload = () => {
      // Guard against stale loads: if imageUrl changed before this image
      // finished loading, drawing it would overwrite the newer canvas state.
      if (cancelled) return;
      ctx.drawImage(img, 0, 0, imageWidth, imageHeight);
    };
    img.src = imageUrl;
    return () => {
      cancelled = true;
    };
  }, [imageUrl, imageWidth, imageHeight]);

  // Draw overlay for strokes
  useEffect(() => {
    if (!overlayRef.current) return;
    const overlay = overlayRef.current;
    overlay.width = imageWidth;
    overlay.height = imageHeight;
    const ctx = overlay.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, imageWidth, imageHeight);

    // Draw accumulated strokes as circles along path
    for (const stroke of liquifyStrokes) {
      ctx.strokeStyle = stroke.brushType === 'reconstruct' ? 'rgba(255,200,0,0.4)' : 'rgba(0,150,255,0.4)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      if (stroke.points.length > 0) {
        ctx.moveTo(stroke.points[0].x, stroke.points[0].y);
        for (let i = 1; i < stroke.points.length; i++) {
          ctx.lineTo(stroke.points[i].x, stroke.points[i].y);
        }
      }
      ctx.stroke();

      // Draw brush size indicator at each point
      for (const pt of stroke.points) {
        ctx.beginPath();
        ctx.arc(pt.x, pt.y, stroke.brushSize / 2, 0, Math.PI * 2);
        ctx.fillStyle = stroke.brushType === 'reconstruct' ? 'rgba(255,200,0,0.1)' : 'rgba(0,150,255,0.1)';
        ctx.fill();
      }
    }
  }, [liquifyStrokes, imageWidth, imageHeight]);

  const getCanvasPoint = useCallback(
    (clientX: number, clientY: number): { x: number; y: number } | null => {
      if (!containerRef.current) return null;
      if (!isFinite(scale) || scale <= 0) return null;
      const rect = containerRef.current.getBoundingClientRect();
      const canvasX = (clientX - rect.left - offset.x) / scale;
      const canvasY = (clientY - rect.top - offset.y) / scale;
      if (!isFinite(canvasX) || !isFinite(canvasY)) return null;
      return { x: canvasX, y: canvasY };
    },
    [scale, offset],
  );

  const handlePointerDown = useCallback(
    (e: React.PointerEvent) => {
      e.preventDefault();
      (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
      isDrawingRef.current = true;
      const point = getCanvasPoint(e.clientX, e.clientY);
      if (point) {
        currentStrokePointsRef.current = [point];
      }
    },
    [getCanvasPoint],
  );

  const handlePointerMove = useCallback(
    (e: React.PointerEvent) => {
      if (!isDrawingRef.current) return;
      const point = getCanvasPoint(e.clientX, e.clientY);
      if (point) {
        currentStrokePointsRef.current.push(point);
        if (overlayRef.current) {
          const ctx = overlayRef.current.getContext('2d');
          if (ctx && currentStrokePointsRef.current.length >= 2) {
            const prevPoint = currentStrokePointsRef.current[currentStrokePointsRef.current.length - 2];
            ctx.strokeStyle = activeBrushType === 'reconstruct' ? 'rgba(255,200,0,0.6)' : 'rgba(0,150,255,0.6)';
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.moveTo(prevPoint.x, prevPoint.y);
            ctx.lineTo(point.x, point.y);
            ctx.stroke();
          }
        }
      }
    },
    [getCanvasPoint, activeBrushType],
  );

  const handlePointerUp = useCallback(() => {
    if (!isDrawingRef.current) return;
    isDrawingRef.current = false;
    if (currentStrokePointsRef.current.length > 0) {
      const stroke: LiquifyStroke = {
        points: [...currentStrokePointsRef.current],
        brushType: activeBrushType,
        brushSize,
        brushPressure,
      };
      addLiquifyStroke(stroke);
    }
    currentStrokePointsRef.current = [];
  }, [activeBrushType, brushSize, brushPressure, addLiquifyStroke]);

  const handlePointerCancel = handlePointerUp;

  const handleApply = useCallback(async () => {
    if (liquifyStrokes.length === 0) return;
    setIsApplying(true);
    try {
      // applyLiquify now handles jsAdjustments internally via useRetouching hook
      const result = await applyLiquify(liquifyStrokes);
      if (result) {
        onClose();
      }
      // If result is null, the operation failed — keep the modal open
    } finally {
      setIsApplying(false);
    }
  }, [liquifyStrokes, applyLiquify, onClose]);

  const handleReset = useCallback(() => {
    resetLiquifyMesh();
  }, [resetLiquifyMesh]);

  if (!isMounted) return null;

  return (
    <div
      className={clsx(
        'fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex flex-col transition-opacity duration-300',
        show ? 'opacity-100' : 'opacity-0',
      )}
    >
      {/* Toolbar */}
      <div className="h-14 bg-bg-primary border-b border-surface flex items-center px-4 gap-4 shrink-0">
        <Text variant={TextVariants.title} weight={TextWeights.bold}>
          {t('editor.liquify.modalTitle')}
        </Text>

        <div className="h-6 w-px bg-surface shrink-0" />

        {/* Brush type selector */}
        <div className="flex items-center gap-1">
          {BRUSH_TYPES.map(({ id, icon: Icon, labelKey }) => (
            <button
              key={id}
              className={clsx(
                'p-1.5 rounded-md transition-colors',
                activeBrushType === id
                  ? 'bg-accent text-button-text'
                  : 'text-text-secondary hover:bg-card-active hover:text-text-primary',
              )}
              onClick={() => setActiveBrushType(id)}
              data-tooltip={t(labelKey as any)}
            >
              <Icon size={18} />
            </button>
          ))}
        </div>

        <div className="h-6 w-px bg-surface shrink-0" />

        {/* Brush size / pressure */}
        <div className="flex items-center gap-4 w-[240px]">
          <Slider
            label={t('editor.liquify.brushSize')}
            min={10}
            max={500}
            step={1}
            value={brushSize}
            onChange={(e) => setBrushSize(Number(e.target.value))}
            fillOrigin="min"
          />
          <Slider
            label={t('editor.liquify.brushPressure')}
            min={1}
            max={100}
            step={1}
            value={brushPressure}
            onChange={(e) => setBrushPressure(Number(e.target.value))}
            fillOrigin="min"
          />
        </div>

        <div className="flex-1" />

        {/* Stroke count */}
        <Text variant={TextVariants.small} color={TextColors.secondary}>
          {t('editor.liquify.strokeCount', { count: liquifyStrokes.length })}
        </Text>

        {/* Undo */}
        <button
          className="p-2 rounded-md text-text-secondary hover:bg-card-active hover:text-text-primary disabled:opacity-50 disabled:cursor-not-allowed"
          onClick={undoLiquifyStroke}
          disabled={liquifyStrokes.length === 0}
          data-tooltip={t('editor.liquify.undoStroke')}
        >
          <Undo2 size={18} />
        </button>
      </div>

      {/* Canvas area */}
      <div
        ref={containerRef}
        className="flex-1 relative bg-bg-tertiary overflow-hidden cursor-crosshair touch-none"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerCancel}
      >
        <div
          style={{
            position: 'absolute',
            left: offset.x,
            top: offset.y,
            width: imageWidth * scale,
            height: imageHeight * scale,
          }}
        >
          <canvas
            ref={canvasRef}
            style={{
              width: imageWidth * scale,
              height: imageHeight * scale,
            }}
            className="absolute inset-0"
          />
          <canvas
            ref={overlayRef}
            style={{
              width: imageWidth * scale,
              height: imageHeight * scale,
            }}
            className="absolute inset-0"
          />
        </div>
      </div>

      {/* Bottom actions */}
      <div className="h-12 bg-bg-primary border-t border-surface flex items-center justify-between px-4 shrink-0">
        <Button className="bg-surface" onClick={handleReset}>
          <RefreshCcw size={16} />
          <span className="ml-2">{t('editor.liquify.resetMesh')}</span>
        </Button>

        <div className="flex gap-2">
          <button
            className="px-4 py-2 rounded-md text-text-secondary hover:bg-card-active transition-colors"
            onClick={onClose}
          >
            {t('editor.liquify.cancel')}
          </button>
          <Button onClick={handleApply} disabled={isApplying || liquifyStrokes.length === 0}>
            {isApplying ? <Loader2 size={16} className="animate-spin" /> : <Check size={16} />}
            <span className="ml-2">{t('editor.liquify.apply')}</span>
          </Button>
        </div>
      </div>
    </div>
  );
}
