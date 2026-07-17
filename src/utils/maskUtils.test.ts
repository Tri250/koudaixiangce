import { describe, it, expect, vi } from 'vitest';
import { Mask, SubMaskMode } from '../components/panel/right/Masks';

// Mock i18next before importing maskUtils
vi.mock('i18next', () => ({
  default: { t: (key: string) => key },
  t: (key: string) => key,
}));

// Mock uuid
vi.mock('uuid', () => ({
  v4: () => 'test-uuid-1234',
}));

import { createSubMask } from './maskUtils';

describe('createSubMask', () => {
  const defaultDimensions = { width: 1000, height: 800 };

  it('creates a radial mask with correct parameters', () => {
    const result = createSubMask(Mask.Radial, defaultDimensions);
    expect(result.type).toBe(Mask.Radial);
    expect(result.id).toBe('test-uuid-1234');
    expect(result.visible).toBe(true);
    expect(result.invert).toBe(false);
    expect(result.opacity).toBe(100);
    expect(result.mode).toBe(SubMaskMode.Additive);
    expect(result.parameters).toHaveProperty('centerX');
    expect(result.parameters).toHaveProperty('centerY');
    expect(result.parameters).toHaveProperty('radiusX');
    expect(result.parameters).toHaveProperty('radiusY');
    expect(result.parameters).toHaveProperty('rotation');
    expect(result.parameters).toHaveProperty('feather');
  });

  it('creates a linear mask with correct parameters', () => {
    const result = createSubMask(Mask.Linear, defaultDimensions);
    expect(result.type).toBe(Mask.Linear);
    expect(result.parameters).toHaveProperty('startX');
    expect(result.parameters).toHaveProperty('startY');
    expect(result.parameters).toHaveProperty('endX');
    expect(result.parameters).toHaveProperty('endY');
    expect(result.parameters).toHaveProperty('range');
  });

  it('creates a brush mask with empty lines', () => {
    const result = createSubMask(Mask.Brush, defaultDimensions);
    expect(result.type).toBe(Mask.Brush);
    expect(result.parameters.lines).toEqual([]);
  });

  it('creates a flow mask with flow parameter', () => {
    const result = createSubMask(Mask.Flow, defaultDimensions);
    expect(result.type).toBe(Mask.Flow);
    expect(result.parameters.lines).toEqual([]);
    expect(result.parameters.flow).toBe(10);
  });

  it('creates an AI subject mask with null base64', () => {
    const result = createSubMask(Mask.AiSubject, defaultDimensions);
    expect(result.type).toBe(Mask.AiSubject);
    expect(result.parameters.maskDataBase64).toBeNull();
    expect(result.parameters.grow).toBe(0);
    expect(result.parameters.feather).toBe(0);
  });

  it('creates an AI foreground mask', () => {
    const result = createSubMask(Mask.AiForeground, defaultDimensions);
    expect(result.type).toBe(Mask.AiForeground);
    expect(result.parameters.maskDataBase64).toBeNull();
  });

  it('creates a quick eraser mask with grow and feather', () => {
    const result = createSubMask(Mask.QuickEraser, defaultDimensions);
    expect(result.type).toBe(Mask.QuickEraser);
    expect(result.parameters.grow).toBe(50);
    expect(result.parameters.feather).toBe(50);
  });

  it('creates a clone mask with source coordinates', () => {
    const result = createSubMask(Mask.Clone, defaultDimensions);
    expect(result.type).toBe(Mask.Clone);
    expect(result.parameters.sourceX).toBe(0);
    expect(result.parameters.sourceY).toBe(0);
    expect(result.parameters.lines).toEqual([]);
  });

  it('creates a heal mask with empty lines', () => {
    const result = createSubMask(Mask.Heal, defaultDimensions);
    expect(result.type).toBe(Mask.Heal);
    expect(result.parameters.lines).toEqual([]);
  });

  it('creates a default mask for unknown type', () => {
    const result = createSubMask('unknown' as Mask, defaultDimensions);
    expect(result.type).toBe('unknown');
    expect(result.parameters).toEqual({});
  });

  it('respects custom mode parameter', () => {
    const result = createSubMask(Mask.Brush, defaultDimensions, SubMaskMode.Subtractive);
    expect(result.mode).toBe(SubMaskMode.Subtractive);
  });

  it('uses default image dimensions when null provided', () => {
    const result = createSubMask(Mask.Radial, null as any); // eslint-disable-line @typescript-eslint/no-explicit-any
    // Should use default 1000x1000
    expect(result.parameters.centerX).toBe(500);
    expect(result.parameters.centerY).toBe(500);
  });

  it('radial mask centers on image dimensions', () => {
    const result = createSubMask(Mask.Radial, { width: 2000, height: 1600 });
    expect(result.parameters.centerX).toBe(1000);
    expect(result.parameters.centerY).toBe(800);
    expect(result.parameters.radiusX).toBe(500);
  });

  it('linear mask uses proportional positions', () => {
    const result = createSubMask(Mask.Linear, { width: 1000, height: 800 });
    expect(result.parameters.startX).toBe(250);
    expect(result.parameters.startY).toBe(400);
    expect(result.parameters.endX).toBe(750);
    expect(result.parameters.endY).toBe(400);
  });
});
