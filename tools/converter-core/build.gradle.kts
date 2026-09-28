plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

// The shared format code is compiled from the app's own modules so the converters write files with
// exactly the code the app reads them with. Those modules are Android libraries, so src/stubs/java
// supplies the few android.* classes they reference.
val sharedModules = listOf("AlkitabIo", "AlkitabModel", "AlkitabYes2", "BintexReader", "BintexWriter", "Snappy")

sourceSets {
    main {
        java.srcDir("src/stubs/java")
        sharedModules.forEach { java.srcDir(rootProject.file("$it/src/main/java")) }
    }
}

dependencies {
    api(libs.androidx.annotation)
    testImplementation(libs.junit)
}
