# ===== 国内镜像加速配置 =====

# Rust — rsproxy (字节跳动维护，同步最快)
export RUSTUP_DIST_SERVER=https://rsproxy.cn
export RUSTUP_UPDATE_ROOT=https://rsproxy.cn/rustup

# Cargo crates.io 镜像 (通过 config.toml 配置 sparse 索引)
export CARGO_HOME=$HOME/.cargo
export CARGO_NET_GIT_FETCH_WITH_CLI=true

# npm 淘宝镜像
export npm_config_registry=https://registry.npmmirror.com

# Gradle — 阿里云镜像 (通过 init.gradle 配置)
export GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx4g -Dorg.gradle.daemon=false"
export GRADLE_USER_HOME=$HOME/.gradle

# ===== Android SDK =====
export ANDROID_HOME=/root/.android-sdk
export ANDROID_SDK_ROOT=/root/.android-sdk
export ANDROID_NDK_HOME=/root/.android-sdk/ndk/26.3.11579264
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator

# ===== Java 17 (Android Gradle 必需) =====
export JAVA_HOME=/root/.local/share/mise/installs/java/17
export PATH=$JAVA_HOME/bin:$PATH
