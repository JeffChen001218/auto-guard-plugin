pluginManagement {
    repositories {
        if ("true" == providers.gradleProperty("use_maven_local").get()) {
            mavenLocal()
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://raw.githubusercontent.com/martinloren/AabResGuard/mvn-repo")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://www.jetbrains.com/intellij-repository/releases")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://raw.githubusercontent.com/martinloren/AabResGuard/mvn-repo")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/central")
        jcenter()   //AndResGuard在jcenter上
    }
}

rootProject.name = "AutoGuard"
include(":app")
include(":auto_guard_plugin")
