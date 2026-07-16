import type { Adjustments } from './adjustments';

export interface ImageCacheEntry {
  adjustments: Adjustments;
  histogram: any;
  waveform: any;
  finalPreviewUrl: string | null;
  uncroppedPreviewUrl: string | null;
  selectedImage: any;
  originalSize: { width: number; height: number };
  previewSize: { width: number; height: number };
}

export class ImageLRUCache {
  private maxSize: number;
  private cache = new Map<string, ImageCacheEntry>();
  private protectedBlobUrls = new Set<string>();

  constructor(maxSize = 20) {
    this.maxSize = maxSize;
  }

  get(key: string): ImageCacheEntry | undefined {
    const entry = this.cache.get(key);
    if (!entry) return undefined;

    this.cache.delete(key);
    this.cache.set(key, entry);

    if (entry.finalPreviewUrl) this.protectedBlobUrls.delete(entry.finalPreviewUrl);
    if (entry.uncroppedPreviewUrl) this.protectedBlobUrls.delete(entry.uncroppedPreviewUrl);

    return entry;
  }

  set(key: string, entry: ImageCacheEntry): void {
    if (this.cache.has(key)) {
      this.cleanupEntry(this.cache.get(key)!, entry);
      this.cache.delete(key);
    } else if (this.cache.size >= this.maxSize) {
      const lruKey = this.cache.keys().next().value;
      if (lruKey !== undefined) {
        this.cleanupEntry(this.cache.get(lruKey)!);
        this.cache.delete(lruKey);
      }
    }

    if (entry.finalPreviewUrl?.startsWith('blob:')) {
      this.protectedBlobUrls.add(entry.finalPreviewUrl);
    }
    if (entry.uncroppedPreviewUrl?.startsWith('blob:')) {
      this.protectedBlobUrls.add(entry.uncroppedPreviewUrl);
    }

    this.cache.set(key, entry);
  }

  /**
   * Dynamically adjust the cache capacity (M4).
   * When shrinking, LRU entries beyond the new capacity are evicted immediately
   * so blob URLs get revoked and memory is released on low-end devices
   * (notably Android phones with constrained RAM).
   */
  setMaxSize(newMax: number): void {
    if (!Number.isFinite(newMax) || newMax <= 0) return;
    this.maxSize = Math.max(1, Math.floor(newMax));
    while (this.cache.size > this.maxSize) {
      const lruKey = this.cache.keys().next().value;
      if (lruKey === undefined) break;
      this.cleanupEntry(this.cache.get(lruKey)!);
      this.cache.delete(lruKey);
    }
  }

  get size(): number {
    return this.cache.size;
  }

  get capacity(): number {
    return this.maxSize;
  }

  isProtected(url: string): boolean {
    return this.protectedBlobUrls.has(url);
  }

  delete(key: string): void {
    const entry = this.cache.get(key);
    if (entry) {
      this.cleanupEntry(entry);
      this.cache.delete(key);
    }
  }

  deleteByPrefix(prefix: string): void {
    for (const key of [...this.cache.keys()]) {
      if (key === prefix || key.startsWith(prefix + '?vc=')) {
        this.delete(key);
      }
    }
  }

  clear(): void {
    for (const entry of this.cache.values()) {
      this.cleanupEntry(entry);
    }
    this.cache.clear();
    this.protectedBlobUrls.clear();
  }

  private cleanupEntry(old: ImageCacheEntry, replacement?: ImageCacheEntry): void {
    const revokeIfUnused = (url: string | null) => {
      if (!url?.startsWith('blob:')) return;
      const reused = replacement && (replacement.finalPreviewUrl === url || replacement.uncroppedPreviewUrl === url);
      if (!reused) {
        this.protectedBlobUrls.delete(url);
        URL.revokeObjectURL(url);
      }
    };
    revokeIfUnused(old.finalPreviewUrl);
    revokeIfUnused(old.uncroppedPreviewUrl);
  }
}

/**
 * Adaptive sizing for the image preview LRU cache (M4).
 *
 * The cache holds full preview state (bitmaps, histograms, waveforms) per image,
 * so each entry can be several MB. On Android phones — where RAM is constrained
 * and the OS aggressively reclaims background memory — keeping 20 entries risks
 * OOM kills. We scale the cap by:
 *
 *   - platform: Android gets a much smaller cap than desktop
 *   - navigator.deviceMemory (Chrome / Android only, in GB): further shrink on
 *     low-RAM devices
 *
 * Numbers are conservative on purpose: cache misses are cheap (re-render), but
 * OOM crashes lose user work.
 */
export function computeAdaptiveImageCacheSize(platform?: string): number {
  // navigator.deviceMemory is non-standard but exposed on Chrome / Android.
  // Values are powers of two (0.25, 0.5, 1, 2, 4, 8). Missing on iOS / Safari / desktop Firefox.
  const deviceMemoryGb =
    typeof navigator !== 'undefined' && typeof (navigator as any).deviceMemory === 'number'
      ? (navigator as any).deviceMemory
      : undefined;

  if (platform === 'android') {
    if (deviceMemoryGb !== undefined && deviceMemoryGb <= 2) return 4;
    if (deviceMemoryGb !== undefined && deviceMemoryGb <= 4) return 6;
    // Unknown memory budget — assume a typical mid-range phone.
    return 6;
  }

  // Desktop / web build: keep the historical default unless the device is
  // clearly memory-starved (e.g. a low-end Chromebook exposing deviceMemory).
  if (deviceMemoryGb !== undefined && deviceMemoryGb <= 2) return 10;
  return 20;
}

// Initialize with an adaptive size so memory pressure is correct from the
// first interaction. The platform may not be known yet at module-eval time,
// in which case we fall back to the desktop default and re-tune later
// (see useAppInitialization → setMaxSize).
const initialSize =
  typeof navigator !== 'undefined' &&
  typeof (navigator as any).userAgent === 'string' &&
  /android/i.test((navigator as any).userAgent)
    ? computeAdaptiveImageCacheSize('android')
    : computeAdaptiveImageCacheSize();

export const globalImageCache = new ImageLRUCache(initialSize);
