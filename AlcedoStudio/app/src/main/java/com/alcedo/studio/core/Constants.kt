package com.alcedo.studio.core

object Constants {

    object Database {
        const val NAME = "alcedo_studio.db"
        const val VERSION = 1
        const val BACKUP_SUFFIX = ".backup"
    }

    object Image {
        const val MAX_DECODE_SIZE = 4096
        const val THUMBNAIL_SIZE = 256
        const val PREVIEW_SIZE = 1024
        const val FULL_SIZE_LIMIT = 8192
        const val DEFAULT_JPEG_QUALITY = 90
        const val DEFAULT_PNG_COMPRESSION = 6
    }

    object BitmapPool {
        const val MAX_SIZE_BYTES = 100 * 1024 * 1024
        const val MAX_BITMAPS = 20
    }

    object Storage {
        const val MAX_FILE_SIZE = 500 * 1024 * 1024
        const val BUFFER_SIZE = 8 * 1024
        const val TEMP_FILE_PREFIX = "alcedo_temp_"
        const val EXPORT_DIR_NAME = "AlcedoStudio"
        const val PRESET_DIR_NAME = "presets"
        const val LUT_DIR_NAME = "luts"
    }

    object WorkManager {
        const val MAX_RETRIES = 3
        const val BACKOFF_DELAY_MS = 5000L
        const val MAX_BACKOFF_DELAY_MS = 60000L
        const val WORK_NAME_EXPORT = "export_work"
        const val WORK_NAME_THUMBNAIL = "thumbnail_work"
    }

    object Network {
        const val TIMEOUT_CONNECT_MS = 10000L
        const val TIMEOUT_READ_MS = 30000L
        const val TIMEOUT_WRITE_MS = 30000L
        const val MAX_RETRY_COUNT = 3
        const val RETRY_DELAY_MS = 1000L
    }

    object UI {
        const val ANIMATION_DURATION_SHORT = 150L
        const val ANIMATION_DURATION_MEDIUM = 300L
        const val ANIMATION_DURATION_LONG = 500L
        const val SNACKBAR_DURATION_SHORT = 2000L
        const val SNACKBAR_DURATION_LONG = 4000L
        const val PROGRESS_UPDATE_INTERVAL_MS = 100L
    }

    object RawDecoder {
        const val MAX_SUPPORTED_WIDTH = 16384
        const val MAX_SUPPORTED_HEIGHT = 16384
        const val MEMORY_SAFETY_MARGIN = 200 * 1024 * 1024
    }

    object FaceDetection {
        const val MIN_FACE_SIZE_PX = 80
        const val MAX_FACES_PER_IMAGE = 10
        const val CONFIDENCE_THRESHOLD = 0.7f
    }

    object SmartAdjust {
        const val HISTOGRAM_BINS = 256
        const val CONTRAST_THRESHOLD_LOW = 0.1f
        const val CONTRAST_THRESHOLD_HIGH = 0.3f
        const val BRIGHTNESS_TARGET = 0.5f
    }

    object GeoTagging {
        const val COORDINATE_FORMAT = "%.6f"
        const val MAP_ZOOM_DEFAULT = 15f
        const val MAP_ZOOM_MIN = 3f
        const val MAP_ZOOM_MAX = 20f
    }

    object Metadata {
        const val XMP_NAMESPACE_ALCEDO = "http://www.alcedostudio.com/xmp/1.0/"
        const val XMP_TOOL_NAME = "AlcedoStudio"
        const val EXIF_USER_COMMENT_KEY = "UserComment"
    }

    object LayerSystem {
        const val MAX_LAYERS = 10
        const val MAX_BLEND_MODES = 10
        const val OPACITY_MIN = 0
        const val OPACITY_MAX = 100
        const val OPACITY_DEFAULT = 100
    }

    object BatchProcessing {
        const val MAX_CONCURRENT_TASKS = 4
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
    }

    object Preset {
        const val MAX_PRESET_NAME_LENGTH = 50
        const val MAX_PRESET_COUNT = 100
        const val PRESET_FILE_EXTENSION = ".json"
        const val DEFAULT_PRESET_CATEGORY = "自定义"
    }

    object ColorGrading {
        const val LUT_SIZE_3D = 33
        const val LUT_SCALE_MIN = -100f
        const val LUT_SCALE_MAX = 100f
        const val LUT_SCALE_DEFAULT = 100f
    }

    object Masking {
        const val MAX_MASK_RESOLUTION = 4096
        const val BRUSH_SIZE_MIN = 5f
        const val BRUSH_SIZE_MAX = 500f
        const val BRUSH_HARDNESS_MIN = 0f
        const val BRUSH_HARDNESS_MAX = 100f
    }

    object EXIF {
        const val DATETIME_FORMAT = "yyyy:MM:dd HH:mm:ss"
        const val DATE_FORMAT = "yyyy-MM-dd"
        const val TIME_FORMAT = "HH:mm:ss"
    }

    object Navigation {
        const val ARG_URI = "uri"
        const val ARG_ID = "id"
        const val ARG_TYPE = "type"
    }

    object Analytics {
        const val EVENT_EDIT_START = "edit_start"
        const val EVENT_EDIT_COMPLETE = "edit_complete"
        const val EVENT_EXPORT_START = "export_start"
        const val EVENT_EXPORT_COMPLETE = "export_complete"
        const val EVENT_RAW_DECODE = "raw_decode"
        const val EVENT_CRASH = "crash"
        const val EVENT_ANR = "anr"
    }
}
