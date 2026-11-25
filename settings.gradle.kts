rootProject.name = "waze-patches"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("app.morphe.patches") version "2.1.1"
}

// The in-app menu is an extension activity, not a row in Waze's settings list. The absolute path
// is required: extension projects in subfolders do not find a relative ProGuard file.
settings {
    extensions {
        proguardFiles(rootProject.projectDir.resolve("extensions/proguard-rules.pro").absolutePath)
    }
}
