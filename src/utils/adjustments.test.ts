import { describe, it, expect } from 'vitest';
import {
  INITIAL_ADJUSTMENTS,
  INITIAL_MASK_ADJUSTMENTS,
  INITIAL_MASK_CONTAINER,
  getDefaultCurves,
  getDefaultParametricCurve,
  DEFAULT_PARAMETRIC_CURVE_SETTINGS,
  normalizeLoadedAdjustments,
  COLOR_LABELS,
  ADJUSTMENT_GROUPS,
  COPYABLE_ADJUSTMENT_KEYS,
  ADJUSTMENT_SECTIONS,
  BasicAdjustment,
  ActiveChannel,
  DisplayMode,
  PasteMode,
  ColorGrading,
} from './adjustments';

describe('adjustments enums', () => {
  it('ActiveChannel has expected values', () => {
    expect(ActiveChannel.Blue).toBe('blue');
    expect(ActiveChannel.Green).toBe('green');
    expect(ActiveChannel.Luma).toBe('luma');
    expect(ActiveChannel.Red).toBe('red');
  });

  it('DisplayMode has expected values', () => {
    expect(DisplayMode.Luma).toBe('luma');
    expect(DisplayMode.Rgb).toBe('rgb');
    expect(DisplayMode.Histogram).toBe('histogram');
  });

  it('PasteMode has expected values', () => {
    expect(PasteMode.Merge).toBe('merge');
    expect(PasteMode.Replace).toBe('replace');
  });

  it('BasicAdjustment has expected values', () => {
    expect(BasicAdjustment.Exposure).toBe('exposure');
    expect(BasicAdjustment.Brightness).toBe('brightness');
    expect(BasicAdjustment.Contrast).toBe('contrast');
    expect(BasicAdjustment.Highlights).toBe('highlights');
    expect(BasicAdjustment.Shadows).toBe('shadows');
    expect(BasicAdjustment.Whites).toBe('whites');
    expect(BasicAdjustment.Blacks).toBe('blacks');
  });

  it('ColorGrading has expected values', () => {
    expect(ColorGrading.Balance).toBe('balance');
    expect(ColorGrading.Blending).toBe('blending');
    expect(ColorGrading.Global).toBe('global');
    expect(ColorGrading.Highlights).toBe('highlights');
    expect(ColorGrading.Midtones).toBe('midtones');
    expect(ColorGrading.Shadows).toBe('shadows');
  });
});

describe('getDefaultCurves', () => {
  it('returns default curves with 4 channels', () => {
    const curves = getDefaultCurves();
    expect(curves).toHaveProperty('blue');
    expect(curves).toHaveProperty('green');
    expect(curves).toHaveProperty('luma');
    expect(curves).toHaveProperty('red');
  });

  it('each channel has start and end points', () => {
    const curves = getDefaultCurves();
    for (const channel of ['blue', 'green', 'luma', 'red'] as const) {
      expect(curves[channel]).toEqual([
        { x: 0, y: 0 },
        { x: 255, y: 255 },
      ]);
    }
  });
});

describe('getDefaultParametricCurve', () => {
  it('returns parametric curve with 4 channels', () => {
    const pc = getDefaultParametricCurve();
    expect(pc).toHaveProperty('luma');
    expect(pc).toHaveProperty('red');
    expect(pc).toHaveProperty('green');
    expect(pc).toHaveProperty('blue');
  });

  it('each channel has default settings', () => {
    const pc = getDefaultParametricCurve();
    for (const ch of ['luma', 'red', 'green', 'blue'] as const) {
      expect(pc[ch]).toEqual(DEFAULT_PARAMETRIC_CURVE_SETTINGS);
    }
  });
});

describe('INITIAL_ADJUSTMENTS', () => {
  it('has zero values for basic numeric adjustments', () => {
    expect(INITIAL_ADJUSTMENTS.exposure).toBe(0);
    expect(INITIAL_ADJUSTMENTS.brightness).toBe(0);
    expect(INITIAL_ADJUSTMENTS.contrast).toBe(0);
    expect(INITIAL_ADJUSTMENTS.highlights).toBe(0);
    expect(INITIAL_ADJUSTMENTS.shadows).toBe(0);
    expect(INITIAL_ADJUSTMENTS.whites).toBe(0);
    expect(INITIAL_ADJUSTMENTS.blacks).toBe(0);
    expect(INITIAL_ADJUSTMENTS.saturation).toBe(0);
    expect(INITIAL_ADJUSTMENTS.temperature).toBe(0);
    expect(INITIAL_ADJUSTMENTS.tint).toBe(0);
  });

  it('has default boolean values', () => {
    expect(INITIAL_ADJUSTMENTS.flipHorizontal).toBe(false);
    expect(INITIAL_ADJUSTMENTS.flipVertical).toBe(false);
    expect(INITIAL_ADJUSTMENTS.showClipping).toBe(false);
  });

  it('has empty arrays for patches and masks', () => {
    expect(INITIAL_ADJUSTMENTS.aiPatches).toEqual([]);
    expect(INITIAL_ADJUSTMENTS.masks).toEqual([]);
  });

  it('has null crop by default', () => {
    expect(INITIAL_ADJUSTMENTS.crop).toBeNull();
  });

  it('has default transform values', () => {
    expect(INITIAL_ADJUSTMENTS.transformScale).toBe(100);
    expect(INITIAL_ADJUSTMENTS.transformDistortion).toBe(0);
    expect(INITIAL_ADJUSTMENTS.transformXOffset).toBe(0);
    expect(INITIAL_ADJUSTMENTS.transformYOffset).toBe(0);
  });

  it('has default lens values', () => {
    expect(INITIAL_ADJUSTMENTS.lensCorrectionMode).toBe('manual');
    expect(INITIAL_ADJUSTMENTS.lensDistortionAmount).toBe(100);
    expect(INITIAL_ADJUSTMENTS.lensDistortionEnabled).toBe(true);
  });

  it('has default section visibility', () => {
    expect(INITIAL_ADJUSTMENTS.sectionVisibility.basic).toBe(true);
    expect(INITIAL_ADJUSTMENTS.sectionVisibility.curves).toBe(false);
  });
});

describe('INITIAL_MASK_ADJUSTMENTS', () => {
  it('has zero values for core numeric adjustments', () => {
    expect(INITIAL_MASK_ADJUSTMENTS.exposure).toBe(0);
    expect(INITIAL_MASK_ADJUSTMENTS.contrast).toBe(0);
    expect(INITIAL_MASK_ADJUSTMENTS.sharpness).toBe(0);
  });

  it('has non-zero sharpnessThreshold', () => {
    expect(INITIAL_MASK_ADJUSTMENTS.sharpnessThreshold).toBe(15);
  });
});

describe('INITIAL_MASK_CONTAINER', () => {
  it('has correct default values', () => {
    expect(INITIAL_MASK_CONTAINER.invert).toBe(false);
    expect(INITIAL_MASK_CONTAINER.opacity).toBe(100);
    expect(INITIAL_MASK_CONTAINER.visible).toBe(true);
    expect(INITIAL_MASK_CONTAINER.subMasks).toEqual([]);
  });
});

describe('normalizeLoadedAdjustments', () => {
  it('returns INITIAL_ADJUSTMENTS for null input', () => {
    const result = normalizeLoadedAdjustments(null as any); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(result.exposure).toBe(INITIAL_ADJUSTMENTS.exposure);
    expect(result.brightness).toBe(INITIAL_ADJUSTMENTS.brightness);
  });

  it('returns INITIAL_ADJUSTMENTS for undefined input', () => {
    const result = normalizeLoadedAdjustments(undefined as any); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(result.exposure).toBe(INITIAL_ADJUSTMENTS.exposure);
  });

  it('preserves loaded values', () => {
    const loaded = { exposure: 5, brightness: 10, contrast: -3 };
    const result = normalizeLoadedAdjustments(loaded as any); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(result.exposure).toBe(5);
    expect(result.brightness).toBe(10);
    expect(result.contrast).toBe(-3);
  });

  it('fills in missing values from defaults', () => {
    const loaded = { exposure: 5 };
    const result = normalizeLoadedAdjustments(loaded as any); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(result.exposure).toBe(5);
    expect(result.brightness).toBe(0);
    expect(result.contrast).toBe(0);
  });

  it('normalizes masks array', () => {
    const loaded = {
      masks: [
        {
          adjustments: { exposure: 2 },
          subMasks: [{ type: 'brush' }],
        },
      ],
    };
    const result = normalizeLoadedAdjustments(loaded as any); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(result.masks).toHaveLength(1);
    expect(result.masks[0].adjustments.exposure).toBe(2);
    expect(result.masks[0].id).toBeDefined();
    expect(result.masks[0].subMasks[0].visible).toBe(true);
    expect(result.masks[0].subMasks[0].mode).toBe('additive');
  });

  it('normalizes aiPatches array', () => {
    const loaded = {
      aiPatches: [{ prompt: 'test', subMasks: [] }],
    };
    const result = normalizeLoadedAdjustments(loaded as any); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(result.aiPatches).toHaveLength(1);
    expect(result.aiPatches[0].visible).toBe(true);
  });

  it('normalizes colorGrading', () => {
    const loaded = { colorGrading: { balance: 10 } };
    const result = normalizeLoadedAdjustments(loaded as any); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(result.colorGrading.balance).toBe(10);
    expect(result.colorGrading.blending).toBe(50);
  });

  it('normalizes sectionVisibility', () => {
    const loaded = { sectionVisibility: { basic: false } };
    const result = normalizeLoadedAdjustments(loaded as any); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(result.sectionVisibility.basic).toBe(false);
    expect(result.sectionVisibility.color).toBe(false);
  });

  it('handles empty masks array', () => {
    const loaded = { masks: [] };
    const result = normalizeLoadedAdjustments(loaded as any); // eslint-disable-line @typescript-eslint/no-explicit-any
    expect(result.masks).toEqual([]);
  });
});

describe('COLOR_LABELS', () => {
  it('has 5 color labels', () => {
    expect(COLOR_LABELS).toHaveLength(5);
  });

  it('each label has name and color', () => {
    for (const label of COLOR_LABELS) {
      expect(label).toHaveProperty('name');
      expect(label).toHaveProperty('color');
      expect(label.color).toMatch(/^#[0-9a-f]{6}$/);
    }
  });
});

describe('ADJUSTMENT_GROUPS', () => {
  it('has all expected group categories', () => {
    expect(ADJUSTMENT_GROUPS).toHaveProperty('basic');
    expect(ADJUSTMENT_GROUPS).toHaveProperty('color');
    expect(ADJUSTMENT_GROUPS).toHaveProperty('details');
    expect(ADJUSTMENT_GROUPS).toHaveProperty('effects');
    expect(ADJUSTMENT_GROUPS).toHaveProperty('geometry');
    expect(ADJUSTMENT_GROUPS).toHaveProperty('masks');
  });

  it('each group is an array of AdjustmentGroup', () => {
    for (const key of Object.keys(ADJUSTMENT_GROUPS)) {
      const groups = ADJUSTMENT_GROUPS[key];
      expect(Array.isArray(groups)).toBe(true);
      for (const g of groups) {
        expect(g).toHaveProperty('label');
        expect(g).toHaveProperty('keys');
        expect(Array.isArray(g.keys)).toBe(true);
      }
    }
  });
});

describe('COPYABLE_ADJUSTMENT_KEYS', () => {
  it('is a flat array from all groups', () => {
    expect(Array.isArray(COPYABLE_ADJUSTMENT_KEYS)).toBe(true);
    expect(COPYABLE_ADJUSTMENT_KEYS.length).toBeGreaterThan(0);
  });
});

describe('ADJUSTMENT_SECTIONS', () => {
  it('has all expected sections', () => {
    expect(ADJUSTMENT_SECTIONS).toHaveProperty('basic');
    expect(ADJUSTMENT_SECTIONS).toHaveProperty('curves');
    expect(ADJUSTMENT_SECTIONS).toHaveProperty('color');
    expect(ADJUSTMENT_SECTIONS).toHaveProperty('details');
    expect(ADJUSTMENT_SECTIONS).toHaveProperty('effects');
  });
});
