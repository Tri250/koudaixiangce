import { describe, it, expect } from 'vitest';
import { getOrientedDimensions, calculateCenteredCrop } from './cropUtils';

describe('getOrientedDimensions', () => {
  it('returns original dimensions when orientationSteps is 0', () => {
    const result = getOrientedDimensions(800, 600, 0);
    expect(result).toEqual({ width: 800, height: 600 });
  });

  it('swaps dimensions when orientationSteps is 1', () => {
    const result = getOrientedDimensions(800, 600, 1);
    expect(result).toEqual({ width: 600, height: 800 });
  });

  it('returns original dimensions when orientationSteps is 2', () => {
    const result = getOrientedDimensions(800, 600, 2);
    expect(result).toEqual({ width: 800, height: 600 });
  });

  it('swaps dimensions when orientationSteps is 3', () => {
    const result = getOrientedDimensions(800, 600, 3);
    expect(result).toEqual({ width: 600, height: 800 });
  });

  it('handles square images', () => {
    const result = getOrientedDimensions(500, 500, 1);
    expect(result).toEqual({ width: 500, height: 500 });
  });

  it('handles large dimensions', () => {
    const result = getOrientedDimensions(6000, 4000, 0);
    expect(result).toEqual({ width: 6000, height: 4000 });
  });
});

describe('calculateCenteredCrop', () => {
  it('returns null when aspectRatio is null', () => {
    const result = calculateCenteredCrop(800, 600, 0, null);
    expect(result).toBeNull();
  });

  it('returns null when aspectRatio is 0', () => {
    const result = calculateCenteredCrop(800, 600, 0, 0);
    expect(result).toBeNull();
  });

  it('returns null when aspectRatio is negative', () => {
    const result = calculateCenteredCrop(800, 600, 0, -1);
    expect(result).toBeNull();
  });

  it('returns a crop for a 4:3 landscape image with 16:9 ratio', () => {
    const result = calculateCenteredCrop(1600, 1200, 0, 16 / 9);
    expect(result).not.toBeNull();
    expect(result!.unit).toBe('px');
    expect(result!.width).toBeGreaterThan(0);
    expect(result!.height).toBeGreaterThan(0);
    // Check it's roughly centered
    expect(result!.x).toBeGreaterThanOrEqual(0);
    expect(result!.y).toBeGreaterThanOrEqual(0);
  });

  it('produces a crop with the correct aspect ratio', () => {
    const result = calculateCenteredCrop(1600, 1200, 0, 16 / 9);
    if (result) {
      const actualRatio = result.width / result.height;
      expect(actualRatio).toBeCloseTo(16 / 9, 1);
    }
  });

  it('crops a 1:1 ratio from a landscape image', () => {
    const result = calculateCenteredCrop(800, 600, 0, 1);
    expect(result).not.toBeNull();
    // The crop should be limited by height since image is wider
    expect(result!.height).toBeLessThanOrEqual(600);
    expect(result!.width).toBe(result!.height);
  });

  it('centers the crop region', () => {
    const result = calculateCenteredCrop(1000, 600, 0, 16 / 9);
    if (result) {
      const centerX = result.x + result.width / 2;
      const centerY = result.y + result.height / 2;
      expect(centerX).toBeCloseTo(1000 / 2, -1);
      expect(centerY).toBeCloseTo(600 / 2, -1);
    }
  });

  it('handles swapped orientation', () => {
    const result = calculateCenteredCrop(800, 600, 1, 16 / 9);
    expect(result).not.toBeNull();
    // With orientation steps 1, effective dimensions are 600x800
    if (result) {
      const actualRatio = result.width / result.height;
      expect(actualRatio).toBeCloseTo(16 / 9, 1);
    }
  });

  it('handles rotation parameter', () => {
    const resultNoRotation = calculateCenteredCrop(800, 600, 0, 16 / 9, 0);
    const resultWithRotation = calculateCenteredCrop(800, 600, 0, 16 / 9, 45);
    expect(resultNoRotation).not.toBeNull();
    expect(resultWithRotation).not.toBeNull();
    // Rotated crop should be smaller to fit within bounds
    expect(resultWithRotation!.width).toBeLessThanOrEqual(resultNoRotation!.width);
  });

  it('handles 180 degree rotation (same as 0)', () => {
    const result0 = calculateCenteredCrop(800, 600, 0, 16 / 9, 0);
    const result180 = calculateCenteredCrop(800, 600, 0, 16 / 9, 180);
    expect(result0).not.toBeNull();
    expect(result180).not.toBeNull();
    expect(result180!.width).toBe(result0!.width);
    expect(result180!.height).toBe(result0!.height);
  });

  it('all crop coordinates are non-negative', () => {
    const result = calculateCenteredCrop(800, 600, 0, 3 / 2);
    if (result) {
      expect(result.x).toBeGreaterThanOrEqual(0);
      expect(result.y).toBeGreaterThanOrEqual(0);
      expect(result.width).toBeGreaterThan(0);
      expect(result.height).toBeGreaterThan(0);
    }
  });

  it('crop fits within image bounds', () => {
    const W = 800;
    const H = 600;
    const result = calculateCenteredCrop(W, H, 0, 4 / 3);
    if (result) {
      expect(result.x + result.width).toBeLessThanOrEqual(W);
      expect(result.y + result.height).toBeLessThanOrEqual(H);
    }
  });
});
