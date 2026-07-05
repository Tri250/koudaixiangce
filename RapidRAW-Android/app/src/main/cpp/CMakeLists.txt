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
# shaderc 在部分 NDK 分发中仅以源码形式提供，没有预编译库；此时跳过 vulkancompute
# 构建，应用会在运行时自动降级到 OpenGL ES / CPU 管线。

# 检查 shaderc 头文件与库是否同时存在
set(SHADERC_INCLUDE_DIR "")
if(EXISTS "${ANDROID_NDK}/sources/third_party/shaderc/libshaderc/include/shaderc/shaderc.hpp")
    set(SHADERC_INCLUDE_DIR "${ANDROID_NDK}/sources/third_party/shaderc/libshaderc/include")
elseif(EXISTS "${ANDROID_NDK}/sources/third_party/shaderc/include/shaderc/shaderc.hpp")
    set(SHADERC_INCLUDE_DIR "${ANDROID_NDK}/sources/third_party/shaderc/include")
endif()

find_library(SHADERC_LIB shaderc)
if(NOT SHADERC_LIB)
    find_library(SHADERC_COMBINED_LIB shaderc_combined)
    if(SHADERC_COMBINED_LIB)
        set(SHADERC_LIB ${SHADERC_COMBINED_LIB})
    endif()
endif()

if(Vulkan_FOUND AND SHADERC_INCLUDE_DIR AND SHADERC_LIB)
    message(STATUS "Vulkan + shaderc available - building vulkancompute")

    add_library(vulkancompute SHARED
        vulkan_compute_jni.cpp
        vulkan_compute.cpp
    )

    target_include_directories(vulkancompute PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${SHADERC_INCLUDE_DIR}
    )

    target_link_libraries(vulkancompute
        Vulkan::Vulkan
        ${SHADERC_LIB}
        jnigraphics
        log
        raw
        android
    )

    target_compile_options(vulkancompute PRIVATE -O2 -Wall)
else()
    message(WARNING "Vulkan/shaderc not available - skipping vulkancompute build; app will use OpenGL/CPU fallback")
endif()

# ── Native Crash Handler Library ─────────────────────────────────────────────
# Standalone shared library for native signal handling (SIGSEGV, SIGABRT, etc.).
# Does not depend on libraw; only needs log and android (for backtrace/dladdr).
add_library(nativecrash SHARED native_crash_handler.cpp)

target_link_libraries(nativecrash
    log
    android
)

# ── ONNX Runtime AI Inference Library ────────────────────────────────────────
# R-08/R-09/R-10/R-12: ONNX Runtime 推理引擎。
# 提供 AI 主体分割、NIND 降噪、深度估计、LaMa 修复等 ONNX 模型推理。
# 
# 模型文件放在 app/src/main/assets/onnx/ 目录，运行时拷贝到内部存储。
# 
# 如果 ONNX Runtime 未安装，构建会跳过此库，AI 功能在运行时自动降级为
# 启发式算法（AiMaskGenerator / GuidedFilter / DiffusionInpainter 兜底）。

# 尝试查找 ONNX Runtime 预编译库
find_library(ONNXRUNTIME_LIB onnxruntime)
if(NOT ONNXRUNTIME_LIB)
    set(ONNXRUNTIME_PATH "${CMAKE_CURRENT_SOURCE_DIR}/onnxruntime")
    if(EXISTS "${ONNXRUNTIME_PATH}/lib/${ANDROID_ABI}/libonnxruntime.so")
        set(ONNXRUNTIME_LIB "${ONNXRUNTIME_PATH}/lib/${ANDROID_ABI}/libonnxruntime.so")
    endif()
endif()

if(ONNXRUNTIME_LIB)
    message(STATUS "ONNX Runtime found at ${ONNXRUNTIME_LIB} — building ai_inference")

    add_library(ai_inference SHARED
        ai_inference_jni.cpp
        ai_inference.cpp
    )

    target_include_directories(ai_inference PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
    )

    # 如果存在 ONNX Runtime 头文件，添加 include
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/onnxruntime/include")
        target_include_directories(ai_inference PRIVATE
            ${CMAKE_CURRENT_SOURCE_DIR}/onnxruntime/include
        )
    endif()

    target_link_libraries(ai_inference
        ${ONNXRUNTIME_LIB}
        jnigraphics
        log
        android
    )

    # 启用 ONNX Runtime 代码路径：ai_inference.cpp 中所有 #ifdef HAS_ONNXRUNTIME
    # 包裹的真正推理实现（u2netp/skyseg/nind/depth/lama）只有在定义此宏后才会被编译。
    # 否则编译产物中 nativeIsAvailable() 永远返回 JNI_FALSE，全部 AI 推理静默失效。
    target_compile_definitions(ai_inference PRIVATE HAS_ONNXRUNTIME)

    target_compile_options(ai_inference PRIVATE -O2 -Wall)
else()
    message(WARNING "ONNX Runtime not found — skipping ai_inference build; AI features will use heuristic fallback")
endif()
