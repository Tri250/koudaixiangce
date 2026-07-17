import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ImageLRUCache, ImageCacheEntry } from './ImageLRUCache';

// Mock URL.revokeObjectURL
const revokedUrls: string[] = [];
vi.stubGlobal('URL', {
  revokeObjectURL: (url: string) => revokedUrls.push(url),
  createObjectURL: () => 'blob:test',
});

function createEntry(overrides: Partial<ImageCacheEntry> = {}): ImageCacheEntry {
  return {
    adjustments: {} as any, // eslint-disable-line @typescript-eslint/no-explicit-any
    histogram: null,
    waveform: null,
    finalPreviewUrl: null,
    uncroppedPreviewUrl: null,
    selectedImage: null,
    originalSize: { width: 100, height: 100 },
    previewSize: { width: 50, height: 50 },
    ...overrides,
  };
}

describe('ImageLRUCache', () => {
  let cache: ImageLRUCache;

  beforeEach(() => {
    cache = new ImageLRUCache(3);
    revokedUrls.length = 0;
  });

  describe('constructor', () => {
    it('creates cache with default maxSize of 20', () => {
      const defaultCache = new ImageLRUCache();
      // We can't directly access maxSize, but we can test behavior
      for (let i = 0; i < 20; i++) {
        defaultCache.set(`key${i}`, createEntry());
      }
      // Cache should still have entries
      expect(defaultCache.get('key19')).toBeDefined();
    });

    it('creates cache with custom maxSize', () => {
      const smallCache = new ImageLRUCache(1);
      smallCache.set('a', createEntry());
      smallCache.set('b', createEntry());
      expect(smallCache.get('a')).toBeUndefined();
      expect(smallCache.get('b')).toBeDefined();
    });
  });

  describe('get', () => {
    it('returns undefined for missing key', () => {
      expect(cache.get('nonexistent')).toBeUndefined();
    });

    it('returns stored entry', () => {
      const entry = createEntry();
      cache.set('key1', entry);
      expect(cache.get('key1')).toBe(entry);
    });

    it('moves accessed entry to end (LRU update)', () => {
      cache.set('a', createEntry());
      cache.set('b', createEntry());
      cache.set('c', createEntry());

      // Access 'a' to move it to end
      cache.get('a');

      // Add new entry, which should evict 'b' (oldest now)
      cache.set('d', createEntry());

      expect(cache.get('a')).toBeDefined();
      expect(cache.get('b')).toBeUndefined();
      expect(cache.get('c')).toBeDefined();
    });
  });

  describe('set', () => {
    it('stores an entry', () => {
      const entry = createEntry();
      cache.set('key1', entry);
      expect(cache.get('key1')).toBe(entry);
    });

    it('overwrites existing entry', () => {
      const entry1 = createEntry({ finalPreviewUrl: 'url1' });
      const entry2 = createEntry({ finalPreviewUrl: 'url2' });
      cache.set('key1', entry1);
      cache.set('key1', entry2);
      expect(cache.get('key1')!.finalPreviewUrl).toBe('url2');
    });

    it('evicts LRU entry when cache is full', () => {
      cache.set('a', createEntry());
      cache.set('b', createEntry());
      cache.set('c', createEntry());
      // Cache is full (maxSize=3), adding 'd' should evict 'a'
      cache.set('d', createEntry());
      expect(cache.get('a')).toBeUndefined();
      expect(cache.get('b')).toBeDefined();
      expect(cache.get('c')).toBeDefined();
      expect(cache.get('d')).toBeDefined();
    });

    it('protects blob URLs from new entries', () => {
      const entry = createEntry({ finalPreviewUrl: 'blob:test1' });
      cache.set('key1', entry);
      expect(cache.isProtected('blob:test1')).toBe(true);
    });

    it('unprotects blob URLs from evicted entries', () => {
      cache.set('a', createEntry({ finalPreviewUrl: 'blob:evict' }));
      cache.set('b', createEntry());
      cache.set('c', createEntry());
      cache.set('d', createEntry()); // evicts 'a'
      expect(cache.isProtected('blob:evict')).toBe(false);
    });

    it('revokes blob URLs when entries are evicted', () => {
      cache.set('a', createEntry({ finalPreviewUrl: 'blob:revoke-me' }));
      cache.set('b', createEntry());
      cache.set('c', createEntry());
      cache.set('d', createEntry()); // evicts 'a'
      expect(revokedUrls).toContain('blob:revoke-me');
    });

    it('does not revoke blob URLs that are reused in replacement', () => {
      const reusedUrl = 'blob:reused';
      cache.set('a', createEntry({ finalPreviewUrl: reusedUrl }));
      cache.set('a', createEntry({ finalPreviewUrl: reusedUrl }));
      expect(revokedUrls).not.toContain(reusedUrl);
    });
  });

  describe('isProtected', () => {
    it('returns false for unprotected URLs', () => {
      expect(cache.isProtected('blob:not-in-cache')).toBe(false);
    });

    it('returns true for URLs of current entries', () => {
      cache.set('key1', createEntry({ finalPreviewUrl: 'blob:protected' }));
      expect(cache.isProtected('blob:protected')).toBe(true);
    });
  });

  describe('delete', () => {
    it('removes an entry', () => {
      cache.set('key1', createEntry());
      cache.delete('key1');
      expect(cache.get('key1')).toBeUndefined();
    });

    it('revokes blob URLs on delete', () => {
      cache.set('key1', createEntry({ finalPreviewUrl: 'blob:delete-me' }));
      cache.delete('key1');
      expect(revokedUrls).toContain('blob:delete-me');
      expect(cache.isProtected('blob:delete-me')).toBe(false);
    });

    it('does nothing for non-existent key', () => {
      cache.delete('nonexistent'); // Should not throw
    });
  });

  describe('deleteByPrefix', () => {
    it('deletes entries matching prefix', () => {
      cache.set('img1?vc=1', createEntry());
      cache.set('img1?vc=2', createEntry());
      cache.set('img2?vc=1', createEntry());
      cache.deleteByPrefix('img1');
      expect(cache.get('img1?vc=1')).toBeUndefined();
      expect(cache.get('img1?vc=2')).toBeUndefined();
      expect(cache.get('img2?vc=1')).toBeDefined();
    });

    it('deletes exact prefix match', () => {
      cache.set('img1', createEntry());
      cache.deleteByPrefix('img1');
      expect(cache.get('img1')).toBeUndefined();
    });
  });

  describe('clear', () => {
    it('removes all entries', () => {
      cache.set('a', createEntry());
      cache.set('b', createEntry());
      cache.set('c', createEntry());
      cache.clear();
      expect(cache.get('a')).toBeUndefined();
      expect(cache.get('b')).toBeUndefined();
      expect(cache.get('c')).toBeUndefined();
    });

    it('revokes all blob URLs', () => {
      cache.set('a', createEntry({ finalPreviewUrl: 'blob:clear1' }));
      cache.set('b', createEntry({ uncroppedPreviewUrl: 'blob:clear2' }));
      cache.clear();
      expect(revokedUrls).toContain('blob:clear1');
      expect(revokedUrls).toContain('blob:clear2');
    });

    it('clears protected URLs set', () => {
      cache.set('a', createEntry({ finalPreviewUrl: 'blob:clear-protected' }));
      cache.clear();
      expect(cache.isProtected('blob:clear-protected')).toBe(false);
    });
  });

  describe('LRU behavior', () => {
    it('correctly evicts least recently used entry', () => {
      cache.set('a', createEntry());
      cache.set('b', createEntry());
      cache.set('c', createEntry());

      // Access 'a' so it's not the LRU
      cache.get('a');

      // 'b' should be the LRU now
      cache.set('d', createEntry());

      expect(cache.get('a')).toBeDefined();
      expect(cache.get('b')).toBeUndefined();
      expect(cache.get('c')).toBeDefined();
      expect(cache.get('d')).toBeDefined();
    });

    it('set updates LRU order', () => {
      cache.set('a', createEntry());
      cache.set('b', createEntry());
      cache.set('c', createEntry());

      // Update 'a' which moves it to end
      cache.set('a', createEntry({ finalPreviewUrl: 'updated' }));

      // 'b' is LRU
      cache.set('d', createEntry());
      expect(cache.get('a')).toBeDefined();
      expect(cache.get('b')).toBeUndefined();
    });
  });

  describe('uncroppedPreviewUrl protection', () => {
    it('protects uncroppedPreviewUrl as blob', () => {
      cache.set('key1', createEntry({ uncroppedPreviewUrl: 'blob:uncropped' }));
      expect(cache.isProtected('blob:uncropped')).toBe(true);
    });

    it('unprotects uncroppedPreviewUrl on get', () => {
      cache.set('key1', createEntry({ uncroppedPreviewUrl: 'blob:uncropped' }));
      // get removes blob URLs from protected set
      cache.get('key1');
      expect(cache.isProtected('blob:uncropped')).toBe(false);
    });

    it('unprotects finalPreviewUrl on get', () => {
      cache.set('key1', createEntry({ finalPreviewUrl: 'blob:final' }));
      cache.get('key1');
      expect(cache.isProtected('blob:final')).toBe(false);
    });
  });

  describe('non-blob URLs', () => {
    it('does not protect non-blob URLs', () => {
      cache.set('key1', createEntry({ finalPreviewUrl: 'https://example.com/img.png' }));
      expect(cache.isProtected('https://example.com/img.png')).toBe(false);
    });

    it('does not revoke non-blob URLs', () => {
      cache.set('a', createEntry({ finalPreviewUrl: 'https://example.com/img.png' }));
      cache.set('b', createEntry());
      cache.set('c', createEntry());
      cache.set('d', createEntry()); // evicts 'a'
      expect(revokedUrls).not.toContain('https://example.com/img.png');
    });
  });
});
