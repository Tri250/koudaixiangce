pluginManagement {
    repositories {
        // Default to official Google/MavenCentral for release stability.
        // Aliyun mirrors can be enabled via ~/.gradle/init.gradle when behind the GFW.
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RapidRAW"
include(":app")
