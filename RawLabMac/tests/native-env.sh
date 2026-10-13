#!/bin/bash
# 与原生 Swift 测试目标使用同一架构的已构建依赖。
DEPENDENCY_PREFIX="$ROOT/build/macos15-deps/$(uname -m)/install"
export PKG_CONFIG_PATH="$DEPENDENCY_PREFIX/lib/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"
if ! pkg-config --exists libraw rawlab-dng-sdk opencv4 wavelib 'libtiff-4 >= 4.7.1'; then
    echo "Build Mac dependencies first: bash RawLabMac/build.sh" >&2
    exit 1
fi
