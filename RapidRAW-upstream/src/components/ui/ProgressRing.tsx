import { useEffect, useRef } from 'react';

interface ProgressRingProps {
  progress: number; // 0-100
  size?: number;
  strokeWidth?: number;
  color?: string;
  trackColor?: string;
  indeterminate?: boolean;
  label?: string;
}

export default function ProgressRing({
  progress,
  size = 48,
  strokeWidth = 4,
  color = 'var(--app-accent)',
  trackColor = 'var(--app-bg-base)',
  indeterminate = false,
  label,
}: ProgressRingProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const animRef = useRef<number>(0);
  const rotationRef = useRef(0);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = size * dpr;
    canvas.height = size * dpr;
    ctx.scale(dpr, dpr);

    const center = size / 2;
    const radius = (size - strokeWidth) / 2;

    const draw = () => {
      ctx.clearRect(0, 0, size, size);

      // Track
      ctx.beginPath();
      ctx.arc(center, center, radius, 0, Math.PI * 2);
      ctx.strokeStyle = trackColor;
      ctx.lineWidth = strokeWidth;
      ctx.lineCap = 'round';
      ctx.stroke();

      if (indeterminate) {
        rotationRef.current += 0.03;
        const startAngle = rotationRef.current;
        const endAngle = startAngle + Math.PI * 1.44;
        
        ctx.beginPath();
        ctx.arc(center, center, radius, startAngle, endAngle);
        ctx.strokeStyle = color;
        ctx.lineWidth = strokeWidth;
        ctx.lineCap = 'round';
        ctx.stroke();

        // Glow at arc end
        const glowX = center + radius * Math.cos(endAngle);
        const glowY = center + radius * Math.sin(endAngle);
        ctx.beginPath();
        ctx.arc(glowX, glowY, strokeWidth * 1.5, 0, Math.PI * 2);
        ctx.fillStyle = color.replace(')', ', 0.35)').replace('rgb', 'rgba');
        ctx.fill();

        animRef.current = requestAnimationFrame(draw);
      } else if (progress > 0) {
        const angle = (progress / 100) * Math.PI * 2 - Math.PI / 2;
        
        ctx.beginPath();
        ctx.arc(center, center, radius, -Math.PI / 2, angle);
        ctx.strokeStyle = color;
        ctx.lineWidth = strokeWidth;
        ctx.lineCap = 'round';
        ctx.stroke();

        // Glow at arc end
        const glowX = center + radius * Math.cos(angle);
        const glowY = center + radius * Math.sin(angle);
        ctx.beginPath();
        ctx.arc(glowX, glowY, strokeWidth * 1.5, 0, Math.PI * 2);
        const gradient = ctx.createRadialGradient(glowX, glowY, 0, glowX, glowY, strokeWidth * 2);
        gradient.addColorStop(0, color.replace(')', ', 0.4)').replace('rgb', 'rgba').replace('var(--app-accent)', 'rgba(91, 155, 213, 0.4)'));
        gradient.addColorStop(1, 'transparent');
        ctx.fillStyle = gradient;
        ctx.fill();
      }
    };

    if (indeterminate) {
      animRef.current = requestAnimationFrame(draw);
    } else {
      draw();
    }

    return () => {
      if (animRef.current) cancelAnimationFrame(animRef.current);
    };
  }, [progress, size, strokeWidth, color, trackColor, indeterminate]);

  return (
    <div className="relative inline-flex items-center justify-center">
      <canvas
        ref={canvasRef}
        style={{ width: size, height: size }}
        className="block"
      />
      {label && (
        <span className="absolute text-xs font-medium text-text-primary font-data">
          {label}
        </span>
      )}
      {!indeterminate && !label && progress > 0 && (
        <span className="absolute text-xs font-medium text-text-primary font-data">
          {Math.round(progress)}%
        </span>
      )}
    </div>
  );
}
