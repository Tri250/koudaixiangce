pluginManagement {
    repositories {
        // v2026.07: 优先国内镜像加速；镜像失败时回退到 Google/MavenCentral
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
    }
    // v2026.07: Gradle 8.10+ 会自动加载 gradle/libs.versions.toml 作为
    // 版本目录 `libs`，无需显式调用 versionCatalogs { create("libs") { from(...) } }。
    // 显式调用 from() 会导致 "too many import invocation" 构建错误。
}

rootProject.name = "RapidRAW"
include(":app")
