plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    applicationName = "alkitab-tools"
    mainClass = "yuku.alkitabconverter.cli.MainKt"
}

dependencies {
    implementation(project(":tools:converter-core"))
    implementation(libs.jcommander)
    testImplementation(libs.junit)
}

tasks.test {
    systemProperty("repoRoot", rootDir.absolutePath)
    // The golden tests read these through repoRoot, so declare them for up-to-date checks.
    inputs.file(rootProject.file("tools/in-ddd/in-ddd.yet")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("Alkitab/src/plain/assets/internal")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("tools/reading-plans")).withPathSensitivity(PathSensitivity.RELATIVE)
}

val fatJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Assembles a self-contained runnable jar of the converter CLI."
    archiveFileName = "alkitab-tools.jar"
    manifest {
        attributes("Main-Class" to application.mainClass)
    }
    from(sourceSets.main.map { it.output })
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { classpath -> classpath.map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class", "module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
