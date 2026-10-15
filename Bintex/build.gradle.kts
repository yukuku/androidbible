plugins {
    alias(libs.plugins.android.library)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    testOptions {
        targetSdk = libs.versions.targetSdk.get().toInt()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    namespace = "yuku.bintex.reader"
    lint {
        abortOnError = false
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
