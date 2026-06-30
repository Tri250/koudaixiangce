cmake_minimum_required(VERSION 3.22.1)
project("rawdecoder")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Android 15+ (API 35+) 16KB page size support:
# Ensure native libraries are linked with 16KB max page size so they can be
# loaded on devices with 16KB pages (Pixel 8+ on Android 16, OPPO Find X8 series
# with ColorOS 16, etc.).
# https://developer.android.com/guide/practices/page-sizes
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# libraw source root
set(LIBRAW_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/libraw)

# libraw public headers
include_directories(${LIBRAW_ROOT})
include_directories(${LIBRAW_ROOT}/libraw)

# All libraw source files (from LibRaw 0.21.2 Makefile.am)
set(LIBRAW_SOURCES
    ${LIBRAW_ROOT}/src/libraw_c_api.cpp
    ${LIBRAW_ROOT}/src/libraw_datastream.cpp
    ${LIBRAW_ROOT}/src/decoders/canon_600.cpp
    ${LIBRAW_ROOT}/src/decoders/crx.cpp
    ${LIBRAW_ROOT}/src/decoders/decoders_dcraw.cpp
    ${LIBRAW_ROOT}/src/decoders/decoders_libraw_dcrdefs.cpp
    ${LIBRAW_ROOT}/src/decoders/decoders_libraw.cpp
    ${LIBRAW_ROOT}/src/decoders/dng.cpp
    ${LIBRAW_ROOT}/src/decoders/fp_dng.cpp
    ${LIBRAW_ROOT}/src/decoders/fuji_compressed.cpp
    ${LIBRAW_ROOT}/src/decoders/generic.cpp
    ${LIBRAW_ROOT}/src/decoders/kodak_decoders.cpp
    ${LIBRAW_ROOT}/src/decoders/load_mfbacks.cpp
    ${LIBRAW_ROOT}/src/decoders/smal.cpp
    ${LIBRAW_ROOT}/src/decoders/unpack_thumb.cpp
    ${LIBRAW_ROOT}/src/decoders/unpack.cpp
    ${LIBRAW_ROOT}/src/demosaic/aahd_demosaic.cpp
    ${LIBRAW_ROOT}/src/demosaic/ahd_demosaic.cpp
    ${LIBRAW_ROOT}/src/demosaic/dcb_demosaic.cpp
    ${LIBRAW_ROOT}/src/demosaic/dht_demosaic.cpp
    ${LIBRAW_ROOT}/src/demosaic/misc_demosaic.cpp
    ${LIBRAW_ROOT}/src/demosaic/xtrans_demosaic.cpp
    ${LIBRAW_ROOT}/src/integration/dngsdk_glue.cpp
    ${LIBRAW_ROOT}/src/integration/rawspeed_glue.cpp
    ${LIBRAW_ROOT}/src/metadata/adobepano.cpp
    ${LIBRAW_ROOT}/src/metadata/canon.cpp
    ${LIBRAW_ROOT}/src/metadata/ciff.cpp
    ${LIBRAW_ROOT}/src/metadata/cr3_parser.cpp
    ${LIBRAW_ROOT}/src/metadata/epson.cpp
    ${LIBRAW_ROOT}/src/metadata/exif_gps.cpp
    ${LIBRAW_ROOT}/src/metadata/fuji.cpp
    ${LIBRAW_ROOT}/src/metadata/identify_tools.cpp
    ${LIBRAW_ROOT}/src/metadata/identify.cpp
    ${LIBRAW_ROOT}/src/metadata/kodak.cpp
    ${LIBRAW_ROOT}/src/metadata/leica.cpp
    ${LIBRAW_ROOT}/src/metadata/makernotes.cpp
    ${LIBRAW_ROOT}/src/metadata/mediumformat.cpp
    ${LIBRAW_ROOT}/src/metadata/minolta.cpp
    ${LIBRAW_ROOT}/src/metadata/misc_parsers.cpp
    ${LIBRAW_ROOT}/src/metadata/nikon.cpp
    ${LIBRAW_ROOT}/src/metadata/normalize_model.cpp
    ${LIBRAW_ROOT}/src/metadata/olympus.cpp
    ${LIBRAW_ROOT}/src/metadata/hasselblad_model.cpp
    ${LIBRAW_ROOT}/src/metadata/p1.cpp
    ${LIBRAW_ROOT}/src/metadata/pentax.cpp
    ${LIBRAW_ROOT}/src/metadata/samsung.cpp
    ${LIBRAW_ROOT}/src/metadata/sony.cpp
    ${LIBRAW_ROOT}/src/metadata/tiff.cpp
    ${LIBRAW_ROOT}/src/postprocessing/aspect_ratio.cpp
    ${LIBRAW_ROOT}/src/postprocessing/dcraw_process.cpp
    ${LIBRAW_ROOT}/src/postprocessing/mem_image.cpp
    ${LIBRAW_ROOT}/src/postprocessing/postprocessing_aux.cpp
    ${LIBRAW_ROOT}/src/postprocessing/postprocessing_utils_dcrdefs.cpp
    ${LIBRAW_ROOT}/src/postprocessing/postprocessing_utils.cpp
    ${LIBRAW_ROOT}/src/preprocessing/ext_preprocess.cpp
    ${LIBRAW_ROOT}/src/preprocessing/raw2image.cpp
    ${LIBRAW_ROOT}/src/preprocessing/subtract_black.cpp
    ${LIBRAW_ROOT}/src/tables/cameralist.cpp
    ${LIBRAW_ROOT}/src/tables/colorconst.cpp
    ${LIBRAW_ROOT}/src/tables/colordata.cpp
    ${LIBRAW_ROOT}/src/tables/wblists.cpp
    ${LIBRAW_ROOT}/src/utils/curves.cpp
    ${LIBRAW_ROOT}/src/utils/decoder_info.cpp
    ${LIBRAW_ROOT}/src/utils/init_close_utils.cpp
    ${LIBRAW_ROOT}/src/utils/open.cpp
    ${LIBRAW_ROOT}/src/utils/phaseone_processing.cpp
    ${LIBRAW_ROOT}/src/utils/read_utils.cpp
    ${LIBRAW_ROOT}/src/utils/thumb_utils.cpp
    ${LIBRAW_ROOT}/src/utils/utils_dcraw.cpp
    ${LIBRAW_ROOT}/src/utils/utils_libraw.cpp
    ${LIBRAW_ROOT}/src/write/apply_profile.cpp
    ${LIBRAW_ROOT}/src/write/file_write.cpp
    ${LIBRAW_ROOT}/src/write/tiff_writer.cpp
    ${LIBRAW_ROOT}/src/x3f/x3f_parse_process.cpp
    ${LIBRAW_ROOT}/src/x3f/x3f_utils_patched.cpp
)

# Build libraw as a static library (single-threaded variant to avoid pthread issues)
add_library(raw STATIC ${LIBRAW_SOURCES})

target_compile_definitions(raw PRIVATE LIBRAW_NOTHREADS)
target_compile_options(raw PRIVATE -w -O2)

# JNI wrapper shared library
add_library(rawdecoder SHARED raw_decoder_jni.cpp)

target_link_libraries(rawdecoder
    raw
    jnigraphics
    log
)

# ── Vulkan Compute Library ────────────────────────────────────────────────
# Find Android NDK packages: Vulkan, shaderc
find_package(Vulkan REQUIRED)
# shaderc is provided by the Android NDK (API 24+)
# If shaderc is not available, we fall back at runtime

# Vulkan compute JNI shared library
add_library(vulkancompute SHARED
    vulkan_compute_jni.cpp
    vulkan_compute.cpp
)

target_include_directories(vulkancompute PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link libvulkan.so, libshaderc, jnigraphics (for AndroidBitmap), liblog, and libraw
target_link_libraries(vulkancompute
    Vulkan::Vulkan
    jnigraphics
    log
    raw
    android
)

# shaderc: available in NDK r21+ as a shared library
# On older NDKs, it may not exist; we handle this gracefully
if(EXISTS "${ANDROID_NDK}/sources/third_party/shaderc")
    target_include_directories(vulkancompute PRIVATE
        ${ANDROID_NDK}/sources/third_party/shaderc/include
    )
endif()

# Try to find shaderc in the NDK toolchain
find_library(SHADERC_LIB shaderc)
if(SHADERC_LIB)
    target_link_libraries(vulkancompute ${SHADERC_LIB})
    message(STATUS "Found shaderc: ${SHADERC_LIB}")
else()
    # shaderc not found - we'll need to handle this
    # Try looking in common NDK paths
    find_library(SHADERC_COMBINED_LIB shaderc_combined)
    if(SHADERC_COMBINED_LIB)
        target_link_libraries(vulkancompute ${SHADERC_COMBINED_LIB})
        message(STATUS "Found shaderc_combined: ${SHADERC_COMBINED_LIB}")
    else()
        message(WARNING "shaderc not found - Vulkan compute shader compilation will not be available")
    endif()
endif()

# Compile options for the Vulkan compute library
target_compile_options(vulkancompute PRIVATE -O2 -Wall)
