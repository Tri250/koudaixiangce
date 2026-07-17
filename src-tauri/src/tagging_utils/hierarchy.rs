use once_cell::sync::Lazy;
use std::collections::HashMap;

pub static TAG_HIERARCHY: Lazy<HashMap<&'static str, Vec<&'static str>>> = Lazy::new(|| {
    let mut m = HashMap::new();

    // --- People Hierarchy ---
    let people_children = [
        "man",
        "woman",
        "child",
        "baby",
        "boy",
        "girl",
        "teenager",
        "adult",
        "senior",
        "crowd",
        "family",
        "couple",
        "portrait",
        "self-portrait",
        "face",
        "hands",
        "feet",
        "candid",
    ];
    for child in people_children {
        m.insert(child, vec!["person", "people"]);
    }
    m.insert("boy", vec!["person", "people", "child"]);
    m.insert("girl", vec!["person", "people", "child"]);
    m.insert("teenager", vec!["person", "people", "child"]);

    // --- Animal Hierarchy ---
    let animal_children = [
        "dog", "cat", "bird", "horse", "cow", "sheep", "pig", "goat", "chicken", "duck", "lion",
        "tiger", "bear", "wolf", "fox", "deer", "elephant", "giraffe", "zebra", "monkey", "panda",
        "snake", "lizard", "turtle", "frog", "fish", "shark", "whale", "dolphin", "insect",
    ];
    for child in animal_children {
        m.insert(child, vec!["animal"]);
    }
    m.insert("dog", vec!["animal", "pet"]);
    m.insert("cat", vec!["animal", "pet"]);
    m.insert("puppy", vec!["animal", "pet", "dog"]);
    m.insert("kitten", vec!["animal", "pet", "cat"]);
    m.insert("lion", vec!["animal", "wildlife", "cat"]);
    m.insert("tiger", vec!["animal", "wildlife", "cat"]);
    m.insert("butterfly", vec!["animal", "insect"]);
    m.insert("bee", vec!["animal", "insect"]);
    m.insert("spider", vec!["animal", "insect"]);

    // --- Nature & Landscape Hierarchy ---
    let nature_children = [
        "mountain",
        "hill",
        "valley",
        "canyon",
        "desert",
        "forest",
        "jungle",
        "tree",
        "flower",
        "field",
        "meadow",
        "grass",
        "farm",
        "garden",
        "park",
        "beach",
        "coast",
        "ocean",
        "sea",
        "river",
        "lake",
        "waterfall",
        "island",
        "cave",
        "rock",
        "volcano",
        "glacier",
        "snow",
    ];
    for child in nature_children {
        m.insert(child, vec!["nature", "landscape"]);
    }
    m.insert("rose", vec!["nature", "landscape", "flower"]);
    m.insert("tulip", vec!["nature", "landscape", "flower"]);
    m.insert("sunflower", vec!["nature", "landscape", "flower"]);
    m.insert("pine tree", vec!["nature", "landscape", "tree"]);
    m.insert("palm tree", vec!["nature", "landscape", "tree"]);

    // --- Sky & Weather Hierarchy ---
    m.insert("sunrise", vec!["sky", "sun"]);
    m.insert("sunset", vec!["sky", "sun"]);
    m.insert("aurora", vec!["sky", "night sky"]);
    m.insert("milky way", vec!["sky", "night sky", "galaxy"]);

    // --- Architecture & Urban Hierarchy ---
    let architecture_children = [
        "skyscraper",
        "bridge",
        "tunnel",
        "house",
        "home",
        "apartment",
        "cabin",
        "castle",
        "church",
        "cathedral",
        "tower",
        "lighthouse",
        "ruins",
        "monument",
        "statue",
        "fountain",
        "door",
        "window",
        "interior",
        "room",
    ];
    for child in architecture_children {
        m.insert(child, vec!["architecture", "building"]);
    }
    m.insert("cityscape", vec!["city", "urban", "architecture"]);
    m.insert("skyline", vec!["city", "urban", "architecture"]);
    m.insert("street", vec!["city", "urban"]);

    // --- Vehicle Hierarchy ---
    let vehicle_children = [
        "car",
        "bicycle",
        "motorcycle",
        "bus",
        "train",
        "airplane",
        "boat",
        "ship",
        "truck",
        "van",
        "scooter",
    ];
    for child in vehicle_children {
        m.insert(child, vec!["vehicle"]);
    }

    // --- Food & Drink Hierarchy ---
    let food_children = [
        "fruit",
        "apple",
        "banana",
        "orange",
        "vegetable",
        "carrot",
        "broccoli",
        "tomato",
        "bread",
        "cake",
        "pizza",
        "pasta",
        "sushi",
        "burger",
        "sandwich",
        "salad",
        "soup",
    ];
    for child in food_children {
        m.insert(child, vec!["food"]);
    }
    m.insert("apple", vec!["food", "fruit"]);
    m.insert("banana", vec!["food", "fruit"]);
    m.insert("orange", vec!["food", "fruit"]);
    m.insert("carrot", vec!["food", "vegetable"]);
    m.insert("broccoli", vec!["food", "vegetable"]);
    m.insert("tomato", vec!["food", "vegetable", "fruit"]); // Fun fact lol
    m.insert("coffee", vec!["drink"]);
    m.insert("tea", vec!["drink"]);
    m.insert("juice", vec!["drink"]);
    m.insert("wine", vec!["drink"]);
    m.insert("beer", vec!["drink"]);

    // --- Photography & Art Hierarchy ---
    m.insert("macro", vec!["close-up"]);
    m.insert("sepia", vec!["monochrome"]);
    m.insert("black and white", vec!["monochrome"]);
    m.insert("golden hour", vec!["lighting", "sunrise", "sunset"]);
    m.insert("blue hour", vec!["lighting", "sunrise", "sunset"]);
    m.insert("backlighting", vec!["lighting", "silhouette"]);
    m.insert("drone shot", vec!["aerial view"]);

    m
});

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_tag_hierarchy_not_empty() {
        assert!(!TAG_HIERARCHY.is_empty());
    }

    #[test]
    fn test_tag_hierarchy_dog_is_pet_and_animal() {
        let parents = TAG_HIERARCHY.get("dog");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"animal"));
        assert!(parents.contains(&"pet"));
    }

    #[test]
    fn test_tag_hierarchy_kitten_narrow() {
        let parents = TAG_HIERARCHY.get("kitten");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"animal"));
        assert!(parents.contains(&"pet"));
        assert!(parents.contains(&"cat"));
    }

    #[test]
    fn test_tag_hierarchy_boy_is_child() {
        let parents = TAG_HIERARCHY.get("boy");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"person"));
        assert!(parents.contains(&"people"));
        assert!(parents.contains(&"child"));
    }

    #[test]
    fn test_tag_hierarchy_nature_tags() {
        let parents = TAG_HIERARCHY.get("mountain");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"nature"));
        assert!(parents.contains(&"landscape"));
    }

    #[test]
    fn test_tag_hierarchy_flowers() {
        let parents = TAG_HIERARCHY.get("rose");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"nature"));
        assert!(parents.contains(&"landscape"));
        assert!(parents.contains(&"flower"));
    }

    #[test]
    fn test_tag_hierarchy_architecture_tags() {
        let parents = TAG_HIERARCHY.get("skyscraper");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"architecture"));
        assert!(parents.contains(&"building"));
    }

    #[test]
    fn test_tag_hierarchy_vehicle_tags() {
        let parents = TAG_HIERARCHY.get("car");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"vehicle"));
    }

    #[test]
    fn test_tag_hierarchy_food_tags() {
        let parents = TAG_HIERARCHY.get("apple");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"food"));
        assert!(parents.contains(&"fruit"));
    }

    #[test]
    fn test_tag_hierarchy_tomato_is_fruit_and_vegetable() {
        let parents = TAG_HIERARCHY.get("tomato");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"food"));
        assert!(parents.contains(&"vegetable"));
        assert!(parents.contains(&"fruit"));
    }

    #[test]
    fn test_tag_hierarchy_monochrome_related() {
        let parents = TAG_HIERARCHY.get("sepia");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"monochrome"));

        let parents = TAG_HIERARCHY.get("black and white");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"monochrome"));
    }

    #[test]
    fn test_tag_hierarchy_golden_hour() {
        let parents = TAG_HIERARCHY.get("golden hour");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"lighting"));
        assert!(parents.contains(&"sunrise"));
        assert!(parents.contains(&"sunset"));
    }

    #[test]
    fn test_tag_hierarchy_macro_is_closeup() {
        let parents = TAG_HIERARCHY.get("macro");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"close-up"));
    }

    #[test]
    fn test_tag_hierarchy_drone_shot() {
        let parents = TAG_HIERARCHY.get("drone shot");
        assert!(parents.is_some());
        let parents = parents.unwrap();
        assert!(parents.contains(&"aerial view"));
    }
}
