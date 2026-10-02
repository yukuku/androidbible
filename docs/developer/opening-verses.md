# Opening verses from other apps

Alkitab / Quick Bible can display a verse or passage requested by another
Android app. Use a verse dialog for a quick lookup, or open the main reader at
a particular verse. Your app does not need to bundle the Bible text.

## Open a verse dialog

Start an activity with the action `yuku.alkitab.action.SHOW_VERSES_DIALOG` and
a string extra named `target`:

```kotlin
val intent = Intent("yuku.alkitab.action.SHOW_VERSES_DIALOG").apply {
    putExtra("target", "o:Gen.2.3")
    addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
}
try {
    startActivity(intent)
} catch (_: ActivityNotFoundException) {
    Toast.makeText(this, "Install Alkitab or Quick Bible to open this passage.",
        Toast.LENGTH_LONG).show()
}
```

This snippet belongs in an activity. The classes are `android.content.Intent`,
`android.content.ActivityNotFoundException`, and `android.widget.Toast`.
`FLAG_ACTIVITY_NEW_DOCUMENT` requests a separate document task so a previous
dialog is not simply reused with its old passage.

![A verse dialog showing Hebrews 2:1-4](images/opening-verses.png)

The screenshot is from an older app release; the appearance may differ.

### Specify the target

Prefix the target with the addressing format:

| Prefix | Addressing format | Genesis 2:3 |
| --- | --- | --- |
| `a:` (also `ari:`) | ARI, in decimal or hexadecimal | `a:515` or `a:0x000203` |
| `o:` | OSIS identifier | `o:Gen.2.3` |
| `lid:` | Sequential KJV verse number | `lid:34` |

ARI (Alkitab Resource Identifier) packs the book, chapter, and verse into an
integer:

```text
ari = (bookId << 16) | (chapter << 8) | verse
    = bookId * 65536 + chapter * 256 + verse
```

For the 66 Old and New Testament books, `bookId` runs from `0` (Genesis) to
`65` (Revelation). Chapter and verse numbers start at `1`. ARI uses a
zero-based book ID, while [YET records](yet.md#book-names) use a one-based book
number. Additional book IDs follow the [book-number table](../../publication/doc/book%20numbers.txt),
with one subtracted from the YET number.

Examples:

```text
Genesis 2:3:  0 * 65536 + 2 * 256 + 3 = 515
Exodus 20:2:  1 * 65536 + 20 * 256 + 2 = 70658
```

An OSIS identifier uses `Book.chapter.verse`, for example `Gen.2.3` or
`Exod.20.2`. Use the standard [OSIS book names](reading-plans.md#osis-book-names),
not the localized book names displayed by the app.

LID numbers run from `1` (Genesis 1:1) to `31102` (Revelation 22:21), using
KJV verse order. They do not define a different numbering scheme for each
translation.

### Verse ranges

Use a hyphen for an inclusive range and a comma to separate ranges. Write
the format prefix once, before the entire list:

```text
o:Gen.2.3-Gen.2.5
o:Gen.2.3,Gen.2.10-Gen.2.12
a:515,522-524
lid:34-36
```

Both endpoints of an OSIS range must be complete. `o:Gen.2.3-5` is invalid for
this API. The shortened endings supported by [reading-plan uploads](reading-plans.md#shortened-range-endings)
do not apply to verse-dialog targets.

## Open the main reader

Use `yuku.alkitab.action.VIEW` with an **integer** `ari` extra. The main-reader
action does not accept the dialog's string `target` extra.

```kotlin
val intent = Intent("yuku.alkitab.action.VIEW").apply {
    putExtra("ari", (0 shl 16) or (2 shl 8) or 3)
    putExtra("selectVerse", true)
    putExtra("selectVerseCount", 3)
}
try {
    startActivity(intent)
} catch (_: ActivityNotFoundException) {
    Toast.makeText(this, "Install Alkitab or Quick Bible to open this passage.",
        Toast.LENGTH_LONG).show()
}
```

This opens Genesis 2:3 and selects three consecutive verses in that chapter.
`selectVerse` and `selectVerseCount` are optional; omit them to navigate without
selecting verses. The action also accepts an integer `lid` extra instead of
`ari`. If both are present, `ari` takes precedence.

The [Launcher class](../../AlkitabIntegration/src/main/java/yuku/alkitabintegration/display/Launcher.java)
in `AlkitabIntegration` provides intent builders for both actions, including
`openVersesDialogByTarget`, `openAppAtBibleLocation`, and
`openAppAtBibleLocationWithVerseSelected`.

## Choose an installed app

Leaving the intent unrestricted allows Android to choose among installed
handlers. To target a specific edition, call `intent.setPackage(...)` before
starting the activity:

| App | Package |
| --- | --- |
| Alkitab | `yuku.alkitab` |
| Quick Bible | `yuku.alkitab.kjv` |
| SABDA Alkitab | `org.sabda.alkitab` |

Only set a package if your app specifically intends to use that edition.
Handle `ActivityNotFoundException` if it is unavailable. When launching from
a context other than an activity, add `Intent.FLAG_ACTIVITY_NEW_TASK` and
follow Android's rules for starting activities from the background.

If you query installed handlers before launching, declare the relevant
actions inside a manifest `<queries>` element for Android package visibility:

```xml
<queries>
    <intent>
        <action android:name="yuku.alkitab.action.SHOW_VERSES_DIALOG" />
    </intent>
    <intent>
        <action android:name="yuku.alkitab.action.VIEW" />
    </intent>
</queries>
```

Place `<queries>` directly under `<manifest>`, outside `<application>`.
See Android's [package visibility documentation](https://developer.android.com/training/package-visibility/declaring).

Questions: [help@alkitab.app](mailto:help@alkitab.app).
