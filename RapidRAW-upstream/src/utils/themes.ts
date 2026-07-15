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
      '--app-bg-canvas': 'rgb(5, 5, 8)',           // Deep space black
      '--app-bg-panel': 'rgb(12, 12, 15)',         // Primary workspaces
      '--app-bg-base': 'rgb(20, 20, 25)',          // Interactive panels, inputs
      '--app-bg-deep': 'rgb(30, 30, 38)',          // Floating modals / popovers
      '--app-bg-primary': 'rgb(5, 5, 8)',
      '--app-bg-secondary': 'rgb(12, 12, 15)',
      '--app-surface': 'rgb(20, 20, 25)',
      '--app-card-active': 'rgb(30, 30, 38)',
      '--app-button-text': 'rgb(255, 255, 255)',
      '--app-text-primary': 'rgb(230, 230, 235)',
      '--app-text-secondary': 'rgb(140, 140, 150)',
      '--app-accent': 'rgb(26, 130, 115)',         // Ink green #1a8273
      '--app-danger': 'rgb(180, 60, 60)',
      '--app-border-color': 'rgb(35, 40, 45)',
      '--app-hover-color': 'rgb(30, 35, 40)',
      '--app-glass-stroke': 'rgba(26, 130, 115, 0.15)',
      '--app-shadow-shiny': '0 0 24px rgba(26, 130, 115, 0.15)',
      '--app-text-shadow-shiny': '0 0 18px rgba(26, 130, 115, 0.4)',
    },
  },
  {
    id: Theme.Light,
    name: 'settings.themes.light',
    splashImage: '/splash-light.jpg',
    cssVariables: {
      '--app-bg-canvas': 'rgb(245, 245, 247)',
      '--app-bg-panel': 'rgb(250, 250, 252)',
      '--app-bg-base': 'rgb(255, 255, 255)',
      '--app-bg-deep': 'rgb(255, 255, 255)',
      '--app-bg-primary': 'rgb(245, 245, 247)',
      '--app-bg-secondary': 'rgb(250, 250, 252)',
      '--app-surface': 'rgb(255, 255, 255)',
      '--app-card-active': 'rgb(240, 240, 245)',
      '--app-button-text': 'rgb(255, 255, 255)',
      '--app-text-primary': 'rgb(20, 25, 30)',
      '--app-text-secondary': 'rgb(100, 105, 115)',
      '--app-accent': 'rgb(26, 130, 115)',
      '--app-danger': 'rgb(180, 60, 60)',
      '--app-border-color': 'rgb(220, 222, 228)',
      '--app-hover-color': 'rgb(26, 130, 115)',
      '--app-glass-stroke': 'rgba(26, 130, 115, 0.1)',
      '--app-shadow-shiny': '0 0 24px rgba(26, 130, 115, 0.15)',
      '--app-text-shadow-shiny': '0 0 18px rgba(26, 130, 115, 0.2)',
    },
  },
  {
    id: Theme.Grey,
    name: 'settings.themes.grey',
    splashImage: '/splash-grey.jpg',
    cssVariables: {
      '--app-bg-canvas': 'rgb(60, 62, 68)',
      '--app-bg-panel': 'rgb(70, 72, 80)',
      '--app-bg-base': 'rgb(80, 82, 92)',
      '--app-bg-deep': 'rgb(95, 97, 108)',
      '--app-bg-primary': 'rgb(60, 62, 68)',
      '--app-bg-secondary': 'rgb(70, 72, 80)',
      '--app-surface': 'rgb(80, 82, 92)',
      '--app-card-active': 'rgb(95, 97, 108)',
      '--app-button-text': 'rgb(255, 255, 255)',
      '--app-text-primary': 'rgb(240, 240, 248)',
      '--app-text-secondary': 'rgb(170, 172, 182)',
      '--app-accent': 'rgb(26, 130, 115)',
      '--app-danger': 'rgb(180, 70, 70)',
      '--app-border-color': 'rgb(100, 105, 115)',
      '--app-hover-color': 'rgb(100, 105, 115)',
      '--app-glass-stroke': 'rgba(26, 130, 115, 0.12)',
      '--app-shadow-shiny': '0 0 24px rgba(26, 130, 115, 0.1)',
      '--app-text-shadow-shiny': '0 0 18px rgba(26, 130, 115, 0.15)',
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
