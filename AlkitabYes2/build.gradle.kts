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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    namespace = "yuku.alkitab.yes2"
    lint {
        abortOnError = true
        fatal += setOf("NewApi", "InlinedApi")
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":AlkitabIo"))
    implementation(project(":AlkitabModel"))
    implementation(project(":Bintex"))
    implementation(project(":Snappy"))
    testImplementation(libs.junit)
}
