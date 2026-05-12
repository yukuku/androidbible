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
include(":AlkitabFeedback")
include(":AlkitabIntegration")
include(":AlkitabIo")
include(":AlkitabYes2")
include(":BiblePlus")
include(":KpriModel")
include(":BintexWriter")
include(":Snappy")
include(":BintexReader")
include(":Afw")
include(":FlowLayout")
include(":ImportedDesktopVerseUtil")
include(":PrDownloaderFixed")
