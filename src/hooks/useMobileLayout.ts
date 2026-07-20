import { useState, useEffect } from 'react';
import { useSettingsStore } from '../store/useSettingsStore';

interface MobileLayoutInfo {
  isMobile: boolean;
  isTablet: boolean;
  isDesktop: boolean;
  isLandscape: boolean;
  isPortrait: boolean;
  screenWidth: number;
  screenHeight: number;
  isAndroid: boolean;
}

export function useMobileLayout(): MobileLayoutInfo {
  const osPlatform = useSettingsStore((s) => s.osPlatform);
  const isAndroid = osPlatform === 'android';

  const [layout, setLayout] = useState<MobileLayoutInfo>(() => computeLayout(isAndroid));

  useEffect(() => {
    const update = () => setLayout(computeLayout(isAndroid));

    window.addEventListener('resize', update);
    window.addEventListener('orientationchange', update);

    // Also listen for screen orientation API if available
    let mediaQuery: MediaQueryList | null = null;
    if (window.matchMedia) {
      mediaQuery = window.matchMedia('(orientation: landscape)');
      mediaQuery.addEventListener('change', update);
    }

    return () => {
      window.removeEventListener('resize', update);
      window.removeEventListener('orientationchange', update);
      if (mediaQuery) {
        mediaQuery.removeEventListener('change', update);
      }
    };
  }, [isAndroid]);

  return layout;
}

function computeLayout(isAndroid: boolean): MobileLayoutInfo {
  const screenWidth = window.innerWidth;
  const screenHeight = window.innerHeight;

  const isMobile = isAndroid || screenWidth < 768;
  const isTablet = !isMobile && screenWidth < 1024;
  const isDesktop = !isMobile && !isTablet;
  const isLandscape = screenWidth > screenHeight;
  const isPortrait = !isLandscape;

  return {
    isMobile,
    isTablet,
    isDesktop,
    isLandscape,
    isPortrait,
    screenWidth,
    screenHeight,
    isAndroid,
  };
}
