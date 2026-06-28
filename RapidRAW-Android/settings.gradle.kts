pluginManagement {
    repositories {
        // 沙箱环境：本地 Maven 镜像优先（避免大型工件下载超时）
        maven { url = uri("/opt/local-maven") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("/opt/local-maven") }
        google()
        mavenCentral()
    }
}

rootProject.name = "RapidRAW"
include(":app")
