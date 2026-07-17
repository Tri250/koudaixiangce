import { describe, it, expect } from 'vitest';
import { THEMES, DEFAULT_THEME_ID } from './themes';
import { Theme } from '../components/ui/AppProperties';

describe('THEMES', () => {
  it('has at least 2 themes', () => {
    expect(THEMES.length).toBeGreaterThanOrEqual(2);
  });

  it('each theme has required properties', () => {
    for (const theme of THEMES) {
      expect(theme).toHaveProperty('id');
      expect(theme).toHaveProperty('name');
      expect(theme).toHaveProperty('splashImage');
      expect(theme).toHaveProperty('cssVariables');
    }
  });

  it('each theme has unique id', () => {
    const ids = THEMES.map((t) => t.id);
    const uniqueIds = new Set(ids);
    expect(uniqueIds.size).toBe(ids.length);
  });

  it('each theme has cssVariables with expected keys', () => {
    const expectedVars = [
      '--app-bg-primary',
      '--app-bg-secondary',
      '--app-surface',
      '--app-text-primary',
      '--app-text-secondary',
      '--app-accent',
      '--app-border-color',
    ];
    for (const theme of THEMES) {
      for (const varName of expectedVars) {
        expect(theme.cssVariables).toHaveProperty(varName);
      }
    }
  });

  it('contains Dark theme', () => {
    const darkTheme = THEMES.find((t) => t.id === Theme.Dark);
    expect(darkTheme).toBeDefined();
    expect(darkTheme!.name).toBe('settings.themes.dark');
    expect(darkTheme!.splashImage).toBe('/splash-dark.jpg');
  });

  it('contains Light theme', () => {
    const lightTheme = THEMES.find((t) => t.id === Theme.Light);
    expect(lightTheme).toBeDefined();
    expect(lightTheme!.name).toBe('settings.themes.light');
    expect(lightTheme!.splashImage).toBe('/splash-light.jpg');
  });

  it('contains Grey theme', () => {
    const greyTheme = THEMES.find((t) => t.id === Theme.Grey);
    expect(greyTheme).toBeDefined();
    expect(greyTheme!.name).toBe('settings.themes.grey');
  });

  it('Dark theme has dark background colors', () => {
    const darkTheme = THEMES.find((t) => t.id === Theme.Dark);
    expect(darkTheme!.cssVariables['--app-bg-primary']).toContain('rgb(8');
  });

  it('Light theme has light background colors', () => {
    const lightTheme = THEMES.find((t) => t.id === Theme.Light);
    expect(lightTheme!.cssVariables['--app-bg-primary']).toContain('rgb(245');
  });
});

describe('DEFAULT_THEME_ID', () => {
  it('defaults to Dark theme', () => {
    expect(DEFAULT_THEME_ID).toBe(Theme.Dark);
  });
});
