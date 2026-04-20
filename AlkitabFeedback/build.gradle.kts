plugins {
    alias(libs.plugins.android.library)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        buildConfigField("String", "SERVER_HOST", "\"https://api.alkitab.app\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    namespace = "yuku.alkitabfeedback"
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.viewpager)
    implementation(project(":Afw"))
}
