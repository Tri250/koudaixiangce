import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useSettingsStore } from './useSettingsStore';

// Mock Tauri APIs
vi.mock('@tauri-apps/api/core', () => ({
  invoke: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('@tauri-apps/plugin-os', () => ({
  platform: vi.fn().mockReturnValue('linux'),
}));

describe('useSettingsStore', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useSettingsStore.setState({
      appSettings: null,
      theme: 'dark',
      supportedTypes: null,
      osPlatform: '',
    });
  });

  describe('initial state', () => {
    it('has null appSettings', () => {
      expect(useSettingsStore.getState().appSettings).toBeNull();
    });

    it('has dark theme as default', () => {
      expect(useSettingsStore.getState().theme).toBe('dark');
    });

    it('has null supportedTypes', () => {
      expect(useSettingsStore.getState().supportedTypes).toBeNull();
    });

    it('has empty osPlatform', () => {
      expect(useSettingsStore.getState().osPlatform).toBe('');
    });
  });

  describe('initPlatform', () => {
    it('sets osPlatform from Tauri plugin', async () => {
      await useSettingsStore.getState().initPlatform();
      expect(useSettingsStore.getState().osPlatform).toBe('linux');
    });
  });

  describe('setAppSettings', () => {
    it('updates appSettings', () => {
      const settings = { theme: 'light' } as any; // eslint-disable-line @typescript-eslint/no-explicit-any
      useSettingsStore.getState().setAppSettings(settings);
      expect(useSettingsStore.getState().appSettings).toEqual(settings);
    });

    it('allows setting to null', () => {
      useSettingsStore.getState().setAppSettings({ theme: 'light' } as any); // eslint-disable-line @typescript-eslint/no-explicit-any
      useSettingsStore.getState().setAppSettings(null);
      expect(useSettingsStore.getState().appSettings).toBeNull();
    });
  });

  describe('setTheme', () => {
    it('updates theme', () => {
      useSettingsStore.getState().setTheme('light');
      expect(useSettingsStore.getState().theme).toBe('light');
    });

    it('accepts grey theme', () => {
      useSettingsStore.getState().setTheme('grey');
      expect(useSettingsStore.getState().theme).toBe('grey');
    });
  });

  describe('setSupportedTypes', () => {
    it('updates supportedTypes', () => {
      const types = { extensions: ['jpg', 'png'] } as any; // eslint-disable-line @typescript-eslint/no-explicit-any
      useSettingsStore.getState().setSupportedTypes(types);
      expect(useSettingsStore.getState().supportedTypes).toEqual(types);
    });

    it('allows setting to null', () => {
      useSettingsStore.getState().setSupportedTypes({ extensions: ['jpg'] } as any); // eslint-disable-line @typescript-eslint/no-explicit-any
      useSettingsStore.getState().setSupportedTypes(null);
      expect(useSettingsStore.getState().supportedTypes).toBeNull();
    });
  });

  describe('handleSettingsChange', () => {
    it('updates appSettings', async () => {
      const newSettings = { theme: 'light', key: 'value' } as any; // eslint-disable-line @typescript-eslint/no-explicit-any
      await useSettingsStore.getState().handleSettingsChange(newSettings);
      expect(useSettingsStore.getState().appSettings).toEqual(newSettings);
    });

    it('updates theme when it changes in settings', async () => {
      const newSettings = { theme: 'light' } as any; // eslint-disable-line @typescript-eslint/no-explicit-any
      await useSettingsStore.getState().handleSettingsChange(newSettings);
      expect(useSettingsStore.getState().theme).toBe('light');
    });

    it('does not update theme when it is the same', async () => {
      useSettingsStore.getState().setTheme('dark');
      const newSettings = { theme: 'dark' } as any; // eslint-disable-line @typescript-eslint/no-explicit-any
      await useSettingsStore.getState().handleSettingsChange(newSettings);
      expect(useSettingsStore.getState().theme).toBe('dark');
    });

    it('calls invoke to save settings', async () => {
      const { invoke } = await import('@tauri-apps/api/core');
      const newSettings = { theme: 'light' } as any; // eslint-disable-line @typescript-eslint/no-explicit-any
      await useSettingsStore.getState().handleSettingsChange(newSettings);
      expect(invoke).toHaveBeenCalled();
    });

    it('aborts when called with null', async () => {
      const { invoke } = await import('@tauri-apps/api/core');
      await useSettingsStore.getState().handleSettingsChange(null as any); // eslint-disable-line @typescript-eslint/no-explicit-any
      expect(invoke).not.toHaveBeenCalled();
    });
  });
});
