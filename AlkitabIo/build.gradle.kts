plugins {
    alias(libs.plugins.android.library)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    namespace = "yuku.alkitab.io"
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(project(":AlkitabModel"))
    implementation(project(":BintexReader"))
    testImplementation(libs.junit)
}
