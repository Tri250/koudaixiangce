import { describe, it, expect } from 'vitest';
import {
  normalizeCombo,
  codeToDisplayLabel,
  isValidShortcutKey,
  formatKeyCode,
  arraysEqual,
  KEYBIND_DEFINITIONS,
  KEYBIND_SECTIONS,
} from './keyboardUtils';

function createKeyboardEvent(overrides: Partial<KeyboardEvent> = {}): KeyboardEvent {
  return {
    code: '',
    key: '',
    ctrlKey: false,
    shiftKey: false,
    altKey: false,
    metaKey: false,
    ...overrides,
  } as KeyboardEvent;
}

describe('KEYBIND_SECTIONS', () => {
  it('has all expected sections', () => {
    const sectionIds = KEYBIND_SECTIONS.map((s) => s.id);
    expect(sectionIds).toContain('library');
    expect(sectionIds).toContain('editing');
    expect(sectionIds).toContain('view');
    expect(sectionIds).toContain('rating');
    expect(sectionIds).toContain('panels');
  });
});

describe('KEYBIND_DEFINITIONS', () => {
  it('is a non-empty array', () => {
    expect(KEYBIND_DEFINITIONS.length).toBeGreaterThan(0);
  });

  it('each definition has required properties', () => {
    for (const def of KEYBIND_DEFINITIONS) {
      expect(def).toHaveProperty('action');
      expect(def).toHaveProperty('description');
      expect(def).toHaveProperty('defaultCombo');
      expect(def).toHaveProperty('section');
      expect(Array.isArray(def.defaultCombo)).toBe(true);
    }
  });

  it('each section matches a known section id', () => {
    const sectionIds = KEYBIND_SECTIONS.map((s) => s.id);
    for (const def of KEYBIND_DEFINITIONS) {
      expect(sectionIds).toContain(def.section);
    }
  });
});

describe('normalizeCombo', () => {
  it('returns empty array for plain key with no modifiers', () => {
    const event = createKeyboardEvent({ code: 'KeyA', key: 'a' });
    const result = normalizeCombo(event);
    expect(result).toEqual(['KeyA']);
  });

  it('includes ctrl when ctrlKey is pressed', () => {
    const event = createKeyboardEvent({ code: 'KeyC', key: 'c', ctrlKey: true });
    const result = normalizeCombo(event);
    expect(result).toContain('ctrl');
    expect(result).toContain('KeyC');
  });

  it('includes ctrl when metaKey is pressed', () => {
    const event = createKeyboardEvent({ code: 'KeyC', key: 'c', metaKey: true });
    const result = normalizeCombo(event);
    expect(result).toContain('ctrl');
  });

  it('includes shift when shiftKey is pressed', () => {
    const event = createKeyboardEvent({ code: 'Digit1', key: '1', shiftKey: true });
    const result = normalizeCombo(event);
    expect(result).toContain('shift');
  });

  it('includes alt when altKey is pressed', () => {
    const event = createKeyboardEvent({ code: 'KeyA', key: 'a', altKey: true });
    const result = normalizeCombo(event);
    expect(result).toContain('alt');
  });

  it('normalizes letter keys to KeyX format', () => {
    const event = createKeyboardEvent({ code: 'SomeOtherCode', key: 'z' });
    const result = normalizeCombo(event);
    expect(result).toContain('KeyZ');
  });

  it('normalizes Numpad digits to DigitX format', () => {
    const event = createKeyboardEvent({ code: 'Numpad5' });
    const result = normalizeCombo(event);
    expect(result).toContain('Digit5');
  });

  it('normalizes NumpadAdd to Equal', () => {
    const event = createKeyboardEvent({ code: 'NumpadAdd' });
    const result = normalizeCombo(event);
    expect(result).toContain('Equal');
  });

  it('normalizes NumpadSubtract to Minus', () => {
    const event = createKeyboardEvent({ code: 'NumpadSubtract' });
    const result = normalizeCombo(event);
    expect(result).toContain('Minus');
  });

  it('handles macOS Delete mapping', () => {
    const event = createKeyboardEvent({
      code: 'Backspace',
      ctrlKey: true,
      key: 'Backspace',
    });
    const result = normalizeCombo(event, 'macos');
    expect(result).toContain('Delete');
    expect(result).not.toContain('ctrl');
  });

  it('filters out invalid shortcut keys', () => {
    const event = createKeyboardEvent({ code: 'InvalidCode' });
    const result = normalizeCombo(event);
    expect(result).toEqual([]);
  });

  it('modifier order is ctrl, shift, alt, key', () => {
    const event = createKeyboardEvent({
      code: 'KeyA',
      key: 'a',
      ctrlKey: true,
      shiftKey: true,
      altKey: true,
    });
    const result = normalizeCombo(event);
    expect(result).toEqual(['ctrl', 'shift', 'alt', 'KeyA']);
  });
});

describe('codeToDisplayLabel', () => {
  it('converts KeyX codes to single letter', () => {
    expect(codeToDisplayLabel('KeyA')).toBe('A');
    expect(codeToDisplayLabel('KeyZ')).toBe('Z');
  });

  it('converts DigitX codes to single digit', () => {
    expect(codeToDisplayLabel('Digit0')).toBe('0');
    expect(codeToDisplayLabel('Digit9')).toBe('9');
  });

  it('converts NumpadX codes to Numpad X', () => {
    expect(codeToDisplayLabel('Numpad5')).toBe('Numpad 5');
  });

  it('converts known symbol codes', () => {
    expect(codeToDisplayLabel('Space')).toBe('Space');
    expect(codeToDisplayLabel('ArrowUp')).toBe('↑');
    expect(codeToDisplayLabel('ArrowDown')).toBe('↓');
    expect(codeToDisplayLabel('ArrowLeft')).toBe('←');
    expect(codeToDisplayLabel('ArrowRight')).toBe('→');
    expect(codeToDisplayLabel('BracketLeft')).toBe('[');
    expect(codeToDisplayLabel('BracketRight')).toBe(']');
    expect(codeToDisplayLabel('Minus')).toBe('-');
    expect(codeToDisplayLabel('Equal')).toBe('+');
    expect(codeToDisplayLabel('Enter')).toBe('Enter');
    expect(codeToDisplayLabel('Delete')).toBe('Delete');
    expect(codeToDisplayLabel('Backspace')).toBe('⌫');
  });

  it('returns null for unknown codes', () => {
    expect(codeToDisplayLabel('UnknownKey')).toBeNull();
  });
});

describe('isValidShortcutKey', () => {
  it('returns true for KeyX codes', () => {
    expect(isValidShortcutKey('KeyA')).toBe(true);
    expect(isValidShortcutKey('KeyZ')).toBe(true);
  });

  it('returns true for DigitX codes', () => {
    expect(isValidShortcutKey('Digit0')).toBe(true);
    expect(isValidShortcutKey('Digit9')).toBe(true);
  });

  it('returns true for function keys', () => {
    expect(isValidShortcutKey('F1')).toBe(true);
    expect(isValidShortcutKey('F12')).toBe(true);
  });

  it('returns true for Numpad digit keys', () => {
    expect(isValidShortcutKey('Numpad0')).toBe(true);
    expect(isValidShortcutKey('Numpad9')).toBe(true);
  });

  it('returns true for known symbol keys', () => {
    expect(isValidShortcutKey('Space')).toBe(true);
    expect(isValidShortcutKey('Enter')).toBe(true);
    expect(isValidShortcutKey('Delete')).toBe(true);
    expect(isValidShortcutKey('ArrowUp')).toBe(true);
  });

  it('returns false for invalid codes', () => {
    expect(isValidShortcutKey('Invalid')).toBe(false);
    expect(isValidShortcutKey('')).toBe(false);
    expect(isValidShortcutKey('ctrl')).toBe(false);
  });
});

describe('formatKeyCode', () => {
  it('formats ctrl as ⌘ on macOS', () => {
    expect(formatKeyCode('ctrl', 'macos')).toBe('⌘');
  });

  it('formats ctrl as Ctrl on non-macOS', () => {
    expect(formatKeyCode('ctrl', 'windows')).toBe('Ctrl');
    expect(formatKeyCode('ctrl', 'linux')).toBe('Ctrl');
  });

  it('formats shift as Shift', () => {
    expect(formatKeyCode('shift', 'macos')).toBe('Shift');
    expect(formatKeyCode('shift', 'windows')).toBe('Shift');
  });

  it('formats alt as ⌥ on macOS', () => {
    expect(formatKeyCode('alt', 'macos')).toBe('⌥');
  });

  it('formats alt as Alt on non-macOS', () => {
    expect(formatKeyCode('alt', 'windows')).toBe('Alt');
  });

  it('formats Delete specially on macOS', () => {
    expect(formatKeyCode('Delete', 'macos')).toBe('Delete / ⌘+⌫');
  });

  it('formats key codes using codeToDisplayLabel', () => {
    expect(formatKeyCode('KeyA', 'windows')).toBe('A');
    expect(formatKeyCode('ArrowUp', 'windows')).toBe('↑');
  });

  it('returns raw key for unknown codes', () => {
    expect(formatKeyCode('UnknownKey', 'windows')).toBe('UnknownKey');
  });
});

describe('arraysEqual', () => {
  it('returns true for identical arrays', () => {
    expect(arraysEqual(['a', 'b'], ['a', 'b'])).toBe(true);
  });

  it('returns false for different length arrays', () => {
    expect(arraysEqual(['a'], ['a', 'b'])).toBe(false);
  });

  it('returns false for same length different elements', () => {
    expect(arraysEqual(['a', 'b'], ['a', 'c'])).toBe(false);
  });

  it('returns true for empty arrays', () => {
    expect(arraysEqual([], [])).toBe(true);
  });

  it('returns true for single element arrays', () => {
    expect(arraysEqual(['x'], ['x'])).toBe(true);
  });
});
