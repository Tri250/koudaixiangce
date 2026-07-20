import { useCallback, useRef } from 'react';

/**
 * Hook to provide long-press context menu support on mobile/touch devices.
 * On desktop, context menus are triggered by right-click (onContextMenu).
 * On mobile, this hook provides onTouchStart/onTouchEnd/onTouchCancel handlers
 * that trigger a callback after a 500ms hold — typically used to show a context menu.
 */
export function useLongPress(
  onLongPress: (e: { clientX: number; clientY: number }) => void,
  { delay = 500, minMove = 10 } = {},
) {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const posRef = useRef<{ x: number; y: number } | null>(null);
  const isLongPressRef = useRef(false);

  const start = useCallback(
    (e: React.TouchEvent) => {
      isLongPressRef.current = false;
      const touch = e.touches[0];
      posRef.current = { x: touch.clientX, y: touch.clientY };

      timerRef.current = setTimeout(() => {
        isLongPressRef.current = true;
        if (posRef.current) {
          onLongPress({ clientX: posRef.current.x, clientY: posRef.current.y });
        }
      }, delay);
    },
    [onLongPress, delay],
  );

  const move = useCallback(
    (e: React.TouchEvent) => {
      if (!posRef.current || !timerRef.current) return;
      const touch = e.touches[0];
      const dx = touch.clientX - posRef.current.x;
      const dy = touch.clientY - posRef.current.y;
      if (Math.abs(dx) > minMove || Math.abs(dy) > minMove) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    },
    [minMove],
  );

  const end = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  return {
    onTouchStart: start,
    onTouchMove: move,
    onTouchEnd: end,
    onTouchCancel: end,
    /** Whether the most recent touch sequence ended as a long press */
    get isLongPress() {
      return isLongPressRef.current;
    },
  };
}
