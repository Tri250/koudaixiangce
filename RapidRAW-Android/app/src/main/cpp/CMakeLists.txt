cmake_minimum_required(VERSION 3.22.1)
project("rawdecoder")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

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
