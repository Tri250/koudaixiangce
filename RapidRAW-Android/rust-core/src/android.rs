//! Android-specific helpers.

use std::sync::Once;

use android_logger::Config;
use log::LevelFilter;

static LOGGING_INIT: Once = Once::new();

pub fn init_logging() {
    LOGGING_INIT.call_once(|| {
        android_logger::init_once(
            Config::default()
                .with_max_level(LevelFilter::Debug)
                .with_tag("RapidRAW"),
        );
    });
}
