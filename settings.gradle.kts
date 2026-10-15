pluginManagement {
    repositories {
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
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "androidbible"

include(":Alkitab")
include(":AlkitabModel")
include(":AlkitabIntegration")
include(":AlkitabIo")
include(":AlkitabYes2")
include(":Snappy")
include(":Bintex")
