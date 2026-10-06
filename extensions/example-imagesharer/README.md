# Image-sharing extension sample

This standalone Android app demonstrates the [Alkitab / Quick Bible extension
contract](../../docs/developer/extensions.md). Its Kotlin activities receive
selected verses from the reader:

- **Share as Image** accepts one verse, wraps its text into an image, and opens
  Android's share chooser. It shares a cache file through `FileProvider` with
  temporary read permission.
- **Multiple verses: Plain** displays the selected ARIs and plain verse text.
- **Multiple verses: Formatted** displays the selected ARIs and the text's
  formatting tags without interpreting them.

The image example labels the reference with its book number. A finished app
can substitute localized book names. Shared images stay in the app's cache;
periodically remove old files in a finished app when they are no longer being
shared.

## Build

Keep this directory inside a checkout of the full repository: its settings
import the main app's [version catalog](../../gradle/libs.versions.toml).
AGP, Kotlin, AndroidX dependencies, compile SDK, minimum SDK, and target SDK
come from that catalog. The sample's Gradle wrapper matches the repository's
wrapper; update both wrappers together when upgrading Gradle.

Use JDK 21 and the Android SDK required by the [main build](../../docs/build-system.md).
Set `ANDROID_HOME` or put `sdk.dir` in this directory's `local.properties`.

From this directory:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Open this directory in Android Studio as a separate project, or run the same
tasks from the repository root with `./gradlew -p extensions/example-imagesharer`.

## Try it

Install Alkitab / Quick Bible and the sample on the same device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open the Bible reader, select a verse, and open its verse-selection menu.
Choose **(example) Share as Image** to open the share chooser, or choose either
multiple-verse example to inspect the received extras. Select several verses
to see that only the two multiple-verse activities remain available.

For a quick single-verse check without the Bible reader, launch the exported
activity directly:

```sh
adb shell am start -n yuku.alkitab.imagesharer/.ShareVerseActivity \
  --eia aris 515 --esa verseTexts "Example verse text"
```

The automated checks exercise content URI sharing and malformed or missing
intent extras. They do not replace checking the actual chooser and verse
selection menu on a device.
