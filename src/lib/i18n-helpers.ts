// Re-export useTranslation with simplified types to avoid TS2589 "excessively deep type instantiation"
// The i18next type system creates extremely deep recursive type instantiations that TypeScript
// can't handle. This wrapper isolates the problematic type into a single file.

/* eslint-disable @typescript-eslint/no-explicit-any */

type SimpleTFunction = (key: string, options?: Record<string, unknown> | string) => string;

let _cachedUseTranslation: (() => { t: SimpleTFunction; i18n: any; ready: boolean }) | null = null;

export function useTranslation(): { t: SimpleTFunction; i18n: any; ready: boolean } {
  if (!_cachedUseTranslation) {
    // Use dynamic import via eval to bypass TypeScript's type analysis entirely
    // This avoids TS2589 "excessively deep type instantiation" caused by i18next's recursive types
    const reactI18n = (0, eval)('require')('react-i18next');
    const originalFn = reactI18n.useTranslation;
    _cachedUseTranslation = function wrappedUseTranslation() {
      const result = originalFn();
      return {
        t: ((key: string, options?: Record<string, unknown>) => String(result.t(key, options))) as SimpleTFunction,
        i18n: result.i18n,
        ready: result.ready,
      };
    };
  }
  return _cachedUseTranslation();
}

// Re-export Trans component from react-i18next
// Using eval to avoid TS2589 type depth issues
export const Trans: React.FC<any> = (0, eval)('require')('react-i18next').Trans;
