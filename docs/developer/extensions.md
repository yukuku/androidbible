# Alkitab / Quick Bible extensions

An extension is an Android app that can be opened from the reader's verse
selection menu. It receives the selected verse locations and, optionally,
their text. This integration was introduced in Alkitab / Quick Bible 4.3.

Extensions can provide commentaries, dictionaries, study guides, devotions,
or additional sharing options. For example, an image-sharing app can expose
“Share as Image”: the user selects a verse, opens the overflow menu, and
chooses that item. The extension receives the verse address and text, draws
an image, and offers Android's share chooser.

![Share as Image in the verse-selection menu](images/extensions.png)

## Declare an extension activity

Inside `<application>` in your `AndroidManifest.xml`, declare an enabled,
exported activity with the action
`yuku.alkitab.extensions.action.SHOW_VERSE_INFO`:

```xml
<activity
    android:name=".VerseInfoActivity"
    android:label="Extension Title"
    android:exported="true"
    android:enabled="true">
    <intent-filter>
        <action android:name="yuku.alkitab.extensions.action.SHOW_VERSE_INFO" />
    </intent-filter>
    <meta-data android:name="supportsMultipleVerses" android:value="true" />
    <meta-data android:name="includeVerseText" android:value="true" />
    <meta-data android:name="includeVerseTextFormatting" android:value="false" />
</activity>
```

Replace the class name and label with your own. The activity's label becomes
the menu-item title. The reader discovers the activity through its intent
filter, then launches it using an explicit component. Alkitab / Quick Bible's
manifest already declares package visibility for the extension action.

### Optional metadata

All three values default to `false` when omitted:

| Metadata | Meaning |
| --- | --- |
| `supportsMultipleVerses` | If true, the extension is also offered when more than one verse is selected. Otherwise, it is offered only for a single verse. |
| `includeVerseText` | If true, include the verse text. Request it only when needed, since preparing the text adds work. |
| `includeVerseTextFormatting` | If true and `includeVerseText` is true, preserve the internal formatting tags. Otherwise, strip them before sending the text. |

Formatted text is not HTML. It uses the
[YET formatting tags](yet.md#text-formatting), including `@@`, `@6`, and
`@<...@>...@/`. Leave `includeVerseTextFormatting` false unless your extension
understands those tags.

## Receive selected verses

Read these extras from the activity's incoming intent:

| Extra | Kotlin type | Presence |
| --- | --- | --- |
| `aris` | `IntArray` | Selected verse addresses, in ascending order |
| `verseTexts` | `Array<String?>` | Only when `includeVerseText` is true |

The arrays correspond by index: `verseTexts[i]` is the text for `aris[i]`.
Validate the extras before using them, since the exported activity can also
be launched by other apps.

### Decode ARI

Each ARI encodes a zero-based book ID, a one-based chapter, and a one-based
verse:

```kotlin
val book = (ari ushr 16) and 0xff
val chapter = (ari ushr 8) and 0xff
val verse = ari and 0xff
```

Book `0` is Genesis and book `65` is Revelation. Additional books use the
[book-number table](../../publication/doc/book%20numbers.txt), with one
subtracted from the YET book number. See [Opening verses](opening-verses.md#specify-the-target)
for the encoding formula.

### Handle verse text

Text comes from the primary Bible version, including when the reader is in
split view. An element can be null or empty if that version lacks the selected
verse, even if the secondary version contains it. Check each element before
rendering or sharing it.

## Example: share one verse as an image

This example supports one verse and requests plain text. It draws a reference
and the verse into a bitmap, saves the image in the app's cache, and shares a
`content://` URI with temporary read permission. The sample reference uses
the book number so it also handles books beyond the standard 66; a finished
app can map the ID to a localized book name.

Add AndroidX Core KTX to your extension app's dependencies for `FileProvider`.
Choose the dependency version using your project's existing configuration.
These snippets belong to the extension app, not the Bible reader's manifest.

### AndroidManifest.xml

Inside `<application>`:

```xml
<activity
    android:name=".ShareVerseActivity"
    android:label="Share as Image"
    android:exported="true"
    android:enabled="true">
    <intent-filter>
        <action android:name="yuku.alkitab.extensions.action.SHOW_VERSE_INFO" />
    </intent-filter>
    <meta-data android:name="supportsMultipleVerses" android:value="false" />
    <meta-data android:name="includeVerseText" android:value="true" />
    <meta-data android:name="includeVerseTextFormatting" android:value="false" />
</activity>

<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/share_paths" />
</provider>
```

### res/xml/share_paths.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="shared_images" path="shared/" />
</paths>
```

### ShareVerseActivity.kt

Set the package declaration to your extension app's namespace. This minimal
example keeps image generation in `onCreate`; larger images or multiple verses
should use background work and show progress in the extension's UI.

```kotlin
package com.example.imagesharer

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.IOException

class ShareVerseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val aris = intent.getIntArrayExtra("aris")
        val verseTexts = intent.getStringArrayExtra("verseTexts")
        val verseText = verseTexts?.singleOrNull()
        if (aris == null || aris.size != 1 || aris[0] <= 0 || verseText.isNullOrEmpty()) {
            finish()
            return
        }

        val book = (aris[0] ushr 16) and 0xff
        val chapter = (aris[0] ushr 8) and 0xff
        val verse = aris[0] and 0xff
        val reference = "Book ${book + 1} $chapter:$verse"

        val text = TextView(this).apply {
            this.text = "$reference\n\n$verseText"
            textSize = 20f
            setTextColor(Color.BLUE)
            setBackgroundColor(Color.WHITE)
            setPadding(40, 40, 40, 40)
            measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            layout(0, 0, measuredWidth, measuredHeight)
        }

        val image = createBitmap(text.measuredWidth, text.measuredHeight,
            Bitmap.Config.ARGB_8888)
        text.draw(Canvas(image))

        try {
            val directory = File(cacheDir, "shared")
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Cannot create shared image directory")
            }
            val file = File.createTempFile("verse-", ".png", directory)
            file.outputStream().use { stream ->
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IOException("Cannot encode image")
                }
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Verse image", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Share verse image"))
        } catch (_: IOException) {
            Toast.makeText(this, "Unable to share this verse image.", Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Unable to share this verse image.", Toast.LENGTH_LONG).show()
        } finally {
            image.recycle()
        }
        finish()
    }
}
```

The `FileProvider` authority and `shared/` directory must match the manifest
and path XML above. See Android's [file-sharing documentation](https://developer.android.com/training/secure-file-sharing/share-file)
for content URIs and temporary permissions.

The repository's [example-imagesharer](../../extensions/example-imagesharer)
is a buildable Kotlin sample with image sharing and separate plain-text and
formatted-text activities for multiple verses. It uses the main app's shared
version catalog and matching Gradle wrapper. See its [README](../../extensions/example-imagesharer/README.md)
for build commands and instructions for trying it in Alkitab / Quick Bible.
Shared images stay in the app's cache; a finished app should periodically
remove old files when they are no longer being shared.

Questions: [help@alkitab.app](mailto:help@alkitab.app).
