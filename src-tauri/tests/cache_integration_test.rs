// Integration tests for cache operations
// Tests the cache utility functions and data structures

use std::collections::HashMap;
use std::sync::Arc;

use image::{DynamicImage, GenericImageView, RgbImage};

/// A simplified cache struct for integration testing
/// (mirrors the DecodedImageCache from cache_utils.rs)
struct TestCache<V> {
    data: HashMap<String, V>,
    capacity: usize,
    order: Vec<String>,
}

impl<V: Clone> TestCache<V> {
    fn new(capacity: usize) -> Self {
        Self {
            data: HashMap::new(),
            capacity,
            order: Vec::new(),
        }
    }

    fn insert(&mut self, key: String, value: V) {
        if self.capacity == 0 {
            return; // Zero capacity means nothing is stored
        }
        if self.data.contains_key(&key) {
            // Update: move to end
            self.order.retain(|k| k != &key);
            self.order.push(key.clone());
        } else {
            if self.order.len() >= self.capacity {
                // Evict LRU
                if let Some(lru_key) = self.order.first().cloned() {
                    self.data.remove(&lru_key);
                    self.order.remove(0);
                }
            }
            self.order.push(key.clone());
        }
        self.data.insert(key, value);
    }

    fn get(&mut self, key: &str) -> Option<V> {
        if self.data.contains_key(key) {
            // Move to end (most recently used)
            self.order.retain(|k| k != key);
            self.order.push(key.to_string());
            self.data.get(key).cloned()
        } else {
            None
        }
    }

    fn len(&self) -> usize {
        self.data.len()
    }

    fn is_empty(&self) -> bool {
        self.data.is_empty()
    }

    fn clear(&mut self) {
        self.data.clear();
        self.order.clear();
    }
}

#[test]
fn test_cache_basic_insert_and_get() {
    let mut cache: TestCache<String> = TestCache::new(5);
    cache.insert("key1".to_string(), "value1".to_string());
    cache.insert("key2".to_string(), "value2".to_string());

    assert_eq!(cache.get("key1"), Some("value1".to_string()));
    assert_eq!(cache.get("key2"), Some("value2".to_string()));
    assert_eq!(cache.get("key3"), None);
}

#[test]
fn test_cache_eviction() {
    let mut cache: TestCache<i32> = TestCache::new(2);
    cache.insert("a".to_string(), 1);
    cache.insert("b".to_string(), 2);
    cache.insert("c".to_string(), 3); // Should evict "a"

    assert_eq!(cache.get("a"), None);
    assert_eq!(cache.get("b"), Some(2));
    assert_eq!(cache.get("c"), Some(3));
}

#[test]
fn test_cache_lru_behavior() {
    let mut cache: TestCache<i32> = TestCache::new(2);
    cache.insert("a".to_string(), 1);
    cache.insert("b".to_string(), 2);

    // Access "a" to make it recently used
    cache.get("a");

    // Insert "c" should evict "b" (LRU)
    cache.insert("c".to_string(), 3);

    assert_eq!(cache.get("a"), Some(1));
    assert_eq!(cache.get("b"), None);
    assert_eq!(cache.get("c"), Some(3));
}

#[test]
fn test_cache_update_existing_key() {
    let mut cache: TestCache<i32> = TestCache::new(2);
    cache.insert("a".to_string(), 1);
    cache.insert("a".to_string(), 10);

    assert_eq!(cache.len(), 1);
    assert_eq!(cache.get("a"), Some(10));
}

#[test]
fn test_cache_clear() {
    let mut cache: TestCache<i32> = TestCache::new(5);
    cache.insert("a".to_string(), 1);
    cache.insert("b".to_string(), 2);
    cache.clear();

    assert!(cache.is_empty());
    assert_eq!(cache.get("a"), None);
}

#[test]
fn test_cache_with_image_data() {
    let mut cache: TestCache<Arc<DynamicImage>> = TestCache::new(3);

    let img1 = Arc::new(DynamicImage::ImageRgb8(RgbImage::new(100, 100)));
    let img2 = Arc::new(DynamicImage::ImageRgb8(RgbImage::new(200, 200)));

    cache.insert("img1".to_string(), img1.clone());
    cache.insert("img2".to_string(), img2.clone());

    let result = cache.get("img1");
    assert!(result.is_some());
    let retrieved = result.unwrap();
    assert_eq!(retrieved.dimensions(), (100, 100));
}

#[test]
fn test_cache_hash_deterministic() {
    use std::hash::{Hash, Hasher};
    use std::collections::hash_map::DefaultHasher;

    fn compute_hash<T: Hash>(t: &T) -> u64 {
        let mut hasher = DefaultHasher::new();
        t.hash(&mut hasher);
        hasher.finish()
    }

    // Same input should produce same hash
    let adj1 = r#"{"exposure":1.0,"contrast":0.5}"#;
    let adj2 = r#"{"exposure":1.0,"contrast":0.5}"#;
    assert_eq!(compute_hash(&adj1), compute_hash(&adj2));

    // Different input should produce different hash
    let adj3 = r#"{"exposure":2.0,"contrast":0.5}"#;
    assert_ne!(compute_hash(&adj1), compute_hash(&adj3));
}

#[test]
fn test_cache_zero_capacity() {
    let mut cache: TestCache<i32> = TestCache::new(0);
    cache.insert("a".to_string(), 1);
    // With zero capacity, nothing should be stored
    assert_eq!(cache.get("a"), None);
}
