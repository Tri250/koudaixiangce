# 阿里云镜像 / 清华大学镜像 — 国内加速配置
# Rust
export RUSTUP_DIST_SERVER=https://mirrors.aliyun.com/rustup
export RUSTUP_UPDATE_ROOT=https://mirrors.aliyun.com/rustup/rustup

# Cargo crates.io 镜像
export CARGO_HOME=$HOME/.cargo
export CARGO_REGISTRIES_CRATES_IO_INDEX=https://mirrors.aliyun.com/crates.io-index
export CARGO_NET_GIT_FETCH_WITH_CLI=true

# npm 淘宝镜像
export npm_config_registry=https://registry.npmmirror.com

# Gradle / Android — 阿里云镜像
export GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx4g -Dorg.gradle.daemon=false"
export GRADLE_USER_HOME=$HOME/.gradle

# Android 镜像（在 SDK 安装后会自动通过 init.gradle 生效）

# Android SDK
export ANDROID_HOME=/root/.android-sdk
export ANDROID_SDK_ROOT=/root/.android-sdk
export ANDROID_NDK_HOME=/root/.android-sdk/ndk/26.3.11579264
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator

# Rust
export RUSTUP_DIST_SERVER=https://mirrors.aliyun.com/rustup
export RUSTUP_UPDATE_ROOT=https://mirrors.aliyun.com/rustup/rustup

# Java 17 (Android Gradle required)
export JAVA_HOME=/root/.local/share/mise/installs/java/17
export PATH=$JAVA_HOME/bin:$PATH
