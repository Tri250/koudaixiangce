# RapidRAW Android - Build Guide

## Prerequisites

- JDK 17 (Temurin/Adoptium recommended)
- Android SDK API 36 with:
  - `platforms;android-36`
  - `build-tools;36.0.0`
  - `ndk;26.3.11579264`
  - `cmake;3.22.1`
- Git

## Quick Start

1. Clone the repository.
2. Copy the template and set your SDK path:
   ```bash
   cp local.properties.template local.properties
   # Edit local.properties and set sdk.dir
   ```
3. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
4. Run unit tests:
   ```bash
   ./gradlew testDebugUnitTest
   ```
5. Run lint:
   ```bash
   ./gradlew lintDebug
   ```

## Release Build

Release builds require a keystore and environment variables:

```bash
export KEYSTORE_PASSWORD="..."
export KEY_PASSWORD="..."
# Optional: base64 decode CI keystore
# echo "$RELEASE_KEYSTORE_BASE64" | base64 -d > app/release.keystore
./gradlew assembleRelease
```

## CI

GitHub Actions workflow is in `.github/workflows/build.yml`.

## Notes

- If you are behind a firewall, configure Gradle proxies/mirrors in `~/.gradle/gradle.properties` or `~/.gradle/init.gradle` instead of committing them to the repo.
- R8 full mode is disabled in this release for stability; see `gradle.properties`.
