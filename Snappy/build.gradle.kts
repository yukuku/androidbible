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
    lint {
        abortOnError = false
    }

    // We have to specify the ndk version twice, in here and in the main app module
    // https://issuetracker.google.com/issues/353554169
    ndkVersion = libs.versions.ndk.get()

    externalNativeBuild {
        ndkBuild {
            path = file("jni/Android.mk")
        }
    }
    namespace = "yuku.snappy.codec"
}
