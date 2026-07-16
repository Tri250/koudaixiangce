import { Theme } from '../components/ui/AppProperties';

export interface ThemeProps {
  cssVariables: any;
  id: Theme;
  name: string;
  splashImage: string;
}

export const THEMES: Array<ThemeProps> = [
  {
    id: Theme.Dark,
    name: 'settings.themes.dark',
    splashImage: '/splash-dark.jpg',
    cssVariables: {
      // 4-layer luminance architecture (AlcedoStudio pattern)
      '--app-bg-canvas': 'rgb(15, 15, 15)',       // Floor - outermost surface
      '--app-bg-panel': 'rgb(26, 26, 26)',         // Primary workspaces
      '--app-bg-base': 'rgb(36, 36, 36)',          // Interactive panels, inputs
      '--app-bg-deep': 'rgb(46, 46, 46)',          // Floating modals / popovers
      // Backward compatibility aliases
      '--app-bg-primary': 'rgb(15, 15, 15)',
      '--app-bg-secondary': 'rgb(26, 26, 26)',
      '--app-surface': 'rgb(36, 36, 36)',
      '--app-card-active': 'rgb(46, 46, 46)',
      '--app-button-text': 'rgb(0, 0, 0)',
      '--app-text-primary': 'rgb(224, 224, 224)',
      '--app-text-secondary': 'rgb(136, 136, 136)',
      '--app-accent': 'rgb(91, 155, 213)',         // Brand blue #5B9BD5
      '--app-danger': 'rgb(138, 58, 58)',          // Wine red #8A3A3A
      '--app-border-color': 'rgb(45, 45, 45)',
      '--app-hover-color': 'rgb(38, 37, 37)',      // Subtle white overlay
      '--app-glass-stroke': 'rgba(200, 200, 200, 0.08)', // Glass morphism border
      '--app-shadow-shiny': '0 0 24px rgba(91, 155, 213, 0.12)', // Blue glow
      '--app-text-shadow-shiny': '0 0 18px rgba(91, 155, 213, 0.35)', // Blue text glow
    },
  },
  {
    id: Theme.Light,
    name: 'settings.themes.light',
    splashImage: '/splash-light.jpg',
    cssVariables: {
      '--app-bg-canvas': 'rgb(240, 240, 240)',
      '--app-bg-panel': 'rgb(248, 248, 248)',
      '--app-bg-base': 'rgb(255, 255, 255)',
      '--app-bg-deep': 'rgb(255, 255, 255)',
      '--app-bg-primary': 'rgb(240, 240, 240)',
      '--app-bg-secondary': 'rgb(248, 248, 248)',
      '--app-surface': 'rgb(255, 255, 255)',
      '--app-card-active': 'rgb(235, 235, 235)',
      '--app-button-text': 'rgb(255, 255, 255)',
      '--app-text-primary': 'rgb(20, 20, 20)',
      '--app-text-secondary': 'rgb(108, 108, 108)',
      '--app-accent': 'rgb(91, 155, 213)',
      '--app-danger': 'rgb(180, 60, 60)',
      '--app-border-color': 'rgb(224, 224, 224)',
      '--app-hover-color': 'rgb(91, 155, 213)',
      '--app-glass-stroke': 'rgba(0, 0, 0, 0.06)',
      '--app-shadow-shiny': '0 0 24px rgba(91, 155, 213, 0.15)',
      '--app-text-shadow-shiny': '0 0 18px rgba(91, 155, 213, 0.2)',
    },
  },
  {
    id: Theme.Grey,
    name: 'settings.themes.grey',
    splashImage: '/splash-grey.jpg',
    cssVariables: {
      '--app-bg-canvas': 'rgb(96, 96, 96)',
      '--app-bg-panel': 'rgb(108, 108, 108)',
      '--app-bg-base': 'rgb(118, 118, 118)',
      '--app-bg-deep': 'rgb(130, 130, 130)',
      '--app-bg-primary': 'rgb(96, 96, 96)',
      '--app-bg-secondary': 'rgb(108, 108, 108)',
      '--app-surface': 'rgb(118, 118, 118)',
      '--app-card-active': 'rgb(133, 133, 133)',
      '--app-button-text': 'rgb(45, 45, 45)',
      '--app-text-primary': 'rgb(240, 240, 240)',
      '--app-text-secondary': 'rgb(180, 180, 180)',
      '--app-accent': 'rgb(91, 155, 213)',
      '--app-danger': 'rgb(160, 70, 70)',
      '--app-border-color': 'rgb(138, 138, 138)',
      '--app-hover-color': 'rgb(140, 140, 140)',
      '--app-glass-stroke': 'rgba(255, 255, 255, 0.06)',
      '--app-shadow-shiny': '0 0 24px rgba(91, 155, 213, 0.1)',
      '--app-text-shadow-shiny': '0 0 18px rgba(91, 155, 213, 0.15)',
    },
  },
];

export const DEFAULT_THEME_ID = Theme.Dark;

// Font role system (16 roles, inspired by AlcedoStudio)
export const FONT_ROLES = {
  UiCaption: { size: '10px', weight: 500, family: 'var(--font-ui)' },
  UiBody: { size: '12px', weight: 500, family: 'var(--font-ui)' },
  UiBodyStrong: { size: '12px', weight: 600, family: 'var(--font-ui)' },
  UiHeadline: { size: '16px', weight: 700, family: 'var(--font-heading)' },
  UiOverline: { size: '9px', weight: 600, family: 'var(--font-ui)', letterSpacing: '1.6px', textTransform: 'uppercase' },
  DataBody: { size: '12px', weight: 500, family: 'var(--font-data)' },
  DataNumeric: { size: '13px', weight: 600, family: 'var(--font-data)' },
  DataOverlay: { size: '11px', weight: 600, family: 'var(--font-data)' },
} as const;
