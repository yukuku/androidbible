# Build environment setup

Use an installed JDK 21 and Android SDK when available. Check `java -version`
before installing another JDK. The SDK, minimum Android, and NDK versions are
pinned in [gradle/libs.versions.toml](../gradle/libs.versions.toml); the Gradle
wrapper pins Gradle itself. Snappy requires the NDK even for the open-source app.

## Set up a Linux machine

1. Install JDK 21 through your package manager or a trusted JDK distributor.
   Set `JAVA_HOME` to that installation if it is not already the default.
2. Install Android Studio's SDK tools or the
   [Android command-line tools](https://developer.android.com/studio#command-tools).
   For a command-line installation, put the unpacked tools under
   `<sdk>/cmdline-tools/latest/` so `sdkmanager` is on the expected path.
3. Set `ANDROID_HOME` to the SDK root. Add `$JAVA_HOME/bin`,
   `$ANDROID_HOME/cmdline-tools/latest/bin`, and `$ANDROID_HOME/platform-tools`
   to `PATH`. Alternatively set `sdk.dir` in the untracked `local.properties`.
4. Review and accept the SDK licenses with `sdkmanager --licenses`, then install
   `platform-tools`, `platforms;android-<compileSdk>`, and `ndk;<ndk>` using the
   catalog's values. Let the Android Gradle Plugin select its build-tools version
   unless the build explicitly pins one.
5. Run `./gradlew assemblePlainDebug`. Generated APKs are under
   `Alkitab/build/outputs/apk/plain/debug/`.

Allow several gigabytes for the SDK, NDK, Gradle distribution, dependencies,
and build outputs. Initial builds download dependencies and take longer.
Preserve proxy settings required by your environment. On a TLS-inspecting
network, Java may need the organization's approved CA in its truststore even
when system tools already trust it; keep certificate verification enabled.

## Validate changes

Use the `plain` flavor for development: its placeholder content and Firebase
configuration require no proprietary overlay. Firebase features need real
configuration to function at runtime.

Build code changes locally with `./gradlew assemblePlainDebug`, run relevant
unit tests, and use `./gradlew lintPlainDebug` when checking Android API and
resource compatibility. See [Build system](build-system.md) for commands,
flavors, signing, CI, and production artifacts.
