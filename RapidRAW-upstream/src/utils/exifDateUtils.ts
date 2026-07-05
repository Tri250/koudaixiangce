/**
 * EXIF date parsing and locale-aware formatting utilities.
 *
 * EXIF spec stores DateTimeOriginal as "YYYY:MM:DD HH:MM:SS" (local time, no TZ).
 * Real-world files may also contain:
 *   - "YYYY-MM-DD HH:MM:SS" (some cameras)
 *   - sub-second fractions: "YYYY:MM:DD HH:MM:SS.SSS"
 *   - explicit timezone offsets: "YYYY:MM:DD HH:MM:SS+08:00" or "...Z"
 *
 * These helpers parse robustly and format with the user's i18next locale via Intl.
 */

export interface ExifDateParts {
  date: string;
  time: string;
}

/**
 * Parse an EXIF DateTimeOriginal string into a Date.
 * Returns null when the input is missing or unparseable.
 *
 * Absence of an explicit timezone is treated as local time, matching the EXIF spec
 * (DateTimeOriginal has no TZ field; OffsetTimeOriginal is optional and rare).
 */
export function parseExifDate(value: string | undefined | null): Date | null {
  if (!value || typeof value !== 'string') return null;
  const trimmed = value.trim();
  if (!trimmed) return null;

  // Match "YYYY[:/-]MM[:/-]DD[ T]HH:MM:SS[.sss][Z|+HH(:?)MM]"
  const m = trimmed.match(
    /^(\d{4})[:/-](\d{2})[:/-](\d{2})[ T](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,6}))?(Z|[+-]\d{2}:?\d{2})?$/,
  );
  if (m) {
    const [, year, month, day, hour, minute, second, frac, tz] = m;
    const ms = frac ? frac.padEnd(3, '0').slice(0, 3) : '';
    const tzNormalized =
      tz === undefined
        ? ''
        : tz === 'Z'
          ? 'Z'
          : tz.length === 5
            ? tz // +HHMM
            : tz.length === 6
              ? tz // +HH:MM
              : tz.slice(0, 3) + ':' + tz.slice(3);
    const iso = `${year}-${month}-${day}T${hour}:${minute}:${second}${ms ? '.' + ms : ''}${tzNormalized}`;
    const d = new Date(iso);
    return isNaN(d.getTime()) ? null : d;
  }

  // Fallback: some tags carry only the date part "YYYY:MM:DD"
  const dateOnly = trimmed.match(/^(\d{4})[:/-](\d{2})[:/-](\d{2})$/);
  if (dateOnly) {
    const d = new Date(`${dateOnly[1]}-${dateOnly[2]}-${dateOnly[3]}T00:00:00`);
    return isNaN(d.getTime()) ? null : d;
  }

  // Last resort: replace colons in the date segment and let Date parse
  const fallback = new Date(trimmed.replace(/^(\d{4}):(\d{2}):(\d{2})/, '$1-$2-$3'));
  return isNaN(fallback.getTime()) ? null : fallback;
}

/**
 * Resolve a BCP 47 locale tag for Intl from an i18next-like instance.
 * Falls back to navigator.language and then 'en'.
 */
export function resolveIntlLocale(language?: string | null): string {
  if (language && typeof language === 'string' && language.length > 0) return language;
  if (typeof navigator !== 'undefined' && navigator.language) return navigator.language;
  return 'en';
}

/**
 * Format an EXIF DateTimeOriginal as a full "date + time" string using Intl.
 * Falls back to the original string when parsing fails, or `fallback` when missing.
 */
export function formatExifDateTime(
  value: string | undefined | null,
  locale: string,
  fallback: string = '-',
): string {
  const d = parseExifDate(value);
  if (!d) return value || fallback;
  try {
    return new Intl.DateTimeFormat(locale, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(d);
  } catch {
    return d.toLocaleString();
  }
}

/**
 * Format an EXIF date as date-only ("YYYY/MM/DD" or locale equivalent).
 */
export function formatExifDate(
  value: string | undefined | null,
  locale: string,
  fallback: string = '-',
): string {
  const d = parseExifDate(value);
  if (!d) return value || fallback;
  try {
    return new Intl.DateTimeFormat(locale, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(d);
  } catch {
    return d.toLocaleDateString();
  }
}

/**
 * Format an EXIF date as time-only "HH:MM" (24h).
 */
export function formatExifTime(
  value: string | undefined | null,
  locale: string,
  fallback: string = '',
): string {
  const d = parseExifDate(value);
  if (!d) return value || fallback;
  try {
    return new Intl.DateTimeFormat(locale, {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(d);
  } catch {
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
  }
}
