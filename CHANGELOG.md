# Changelog

A user-facing history of Bible for Android (Alkitab / Quick Bible). Dates
are the release / tag date (or the commit date of the version-bump for
untagged releases). Internal refactors, dependency bumps, CI tweaks, and
pure translation-only updates are generally omitted.

## 4.11.2 — 2025-09-12

- Dropped the `READ_SYNC_SETTINGS`, `WRITE_SYNC_SETTINGS`,
  `AUTHENTICATE_ACCOUNTS`, and `ACCESS_NETWORK_STATE` permissions, so the
  app asks for less at install time.
- Small polish: dialogs no longer use the old "DialogWhenLarge" theme,
  for a cleaner full-screen look on tablets.

## 4.11.1 — 2025-09-04

- Smaller install size: image assets converted to WebP.
- Snappy native library now supports newer Android devices that use
  16 KB memory pages; the shipped library is also properly stripped.
- Removed the `AD_ID` permission again.
- Leaner release builds through better ExoPlayer shrinking.

## 4.11.0 — 2025-09-04

- **Dark theme extended to Devotions and Songs**, so night mode is now
  consistent across the whole app.
- Background sync moved from the old `SyncAdapter` to `WorkManager`;
  sync no longer gets stuck and kicks in more reliably.
- Sync server host switched to `api.alkitab.app`.
- Audio stack upgraded from ExoPlayer 2 to AndroidX Media3.
- Targets Android 15 (SDK 35); the app stays in a non-edge-to-edge
  layout.
- Fixed a rare case where a chapter numbered 255 failed to load its
  section headings.
- Devotion prefetch shortened from 31 days to 15 days.
- Analytics event tracking removed.

## 4.10.1 — 2024-04-24

- Fixed a long-standing bug that made footnotes and cross-references
  silently disappear in later chapters of some Bibles (caused by
  treating a reference index as signed instead of unsigned).

## 4.10.0 — 2023-07-18

- **NFC / Android Beam** ("send verse to nearby device") feature
  removed; the platform dropped it in Android 10.
- **Streaming import/export** for bookmarks / notes / highlights / pins
  / history JSON files, so exporting a large account no longer runs out
  of memory.
- Split-view UI tweaks: thinner handle (20 dp), less padding, elevation
  instead of divider lines; updated Play Store icons.
- Footnote-dialog links are clickable again.
- Per-version text size is now correctly applied in more places.
- Sync uses a real email instead of a dummy account name; logging out
  removes the sync account.
- Download errors now properly detect non-2xx HTTP responses.
- Various small crash fixes.

## 4.9.0 — 2023-06-08

- **Minimum Android raised to 8.0 Oreo** (API 26), dropping support for
  Android 4.x–7.x.
- **Search-as-you-type** in a redesigned screen with a fast scroller
  and auto-focused input so you can start typing immediately.
- **System theme follow** — the app switches between light and dark
  with your phone's setting.
- Inline circular **progress bar** while downloading a Bible version;
  the separate "Download URL" field was removed from version details.
- YES / PDB files now open through the system document picker, which
  removes the need for the old file-chooser screen and storage
  permission prompts.
- **Devotion reminder removed** (only a tiny fraction of users ever
  used it).
- **Beta-version announcement banner** removed.
- **Search reverse-index removed**; search still works and translation
  downloads are smaller.
- Extensions system revived — installed extensions list auto-updates.
- Widget rendering fixed on Android 12 (tap responsiveness, Bible Maps
  hand-off).
- Progress-mark names can be reset to the default.
- Hamburger button replaced by a drag-handle (six dots) UI; bookmark
  and label dialogs use Material text inputs.
- Verse-reference parser now accepts en-dash and em-dash.
- `AD_ID` permission removed.

## 4.8.1 — 2021-04-05

- Bundles the fixes needed after 4.8.0 rolled out its bigger
  feature set: verses-dialog clicks on annotated verses, language
  option not being applied correctly again, scrollbar color on
  Android 10+, and several font-manager rough edges.
- Added Hungarian language.
- AYT audio disclaimer shown where relevant.

## 4.8.0 — 2021-02-08

- **Data Transfer**: export and import your bookmarks, notes,
  highlights, pins, labels, reading-plan progress, and history as a
  single JSON file — perfect for moving to a new device or keeping a
  backup. Includes a progress UI for both directions.
- **Copy from the marker list** with multi-selection and a bulk-copy
  context menu.
- Footnotes can now contain **verse-target links and URLs** (the dialog
  has a proper title now too).
- Settings screen rewritten with fragments for a cleaner, smoother
  layout.
- Searching with curly quote characters works.

## 4.7.1 — 2020-08-20

- Removed the 30-pericopes-per-chapter cap.
- Cleaned up the outdated "fonts are needed" message and a stale
  external-storage check.
- Fixed back/forward button placement when the toolbar is at the
  bottom.

## 4.7.0 — 2020-07-18

- **Back and forward navigation** on the Bible screen, with long-press
  to see a full history list.
- **Show / hide verse numbers** option.
- Cross-reference dialog can now render italics and handles a wider set
  of non-canonical OSIS book names.
- New language translations: Greek, Burmese, Portuguese, Ukrainian.
- Crashlytics upgraded to the Firebase-hosted version.

## 4.6.4 — 2020-02-14

- Startup no longer forces a sync; push notifications handle that.
- Fixed a text-view resizing bug on Android 4.4.
- Fixed crashes in the Compare dialog and when dismissing the
  Share-URL progress dialog.
- Fixed an undefined symbol in the Snappy native library.

## 4.6.3 — 2020-01-30

- **Downloads work again on Android 4.4 KitKat** by forcing TLS 1.2 via
  Google's ProviderInstaller.
- Devotion-reminder time picker replaced with a plain dialog to avoid a
  Samsung-device crash.
- A few null-safety fixes in the Goto grid and the verse dialog.

## 4.6.2 — 2020-01-23

- Scrollbar is back.
- Song activity shows position and duration in its title.
- Fixed: cancelling a song-book download crashed the app; MIDI playback
  error −38; language switching on Android 5.1.

## 4.6.1 — 2020-01-06

- **Full AndroidX migration** and library modernisation.
- **ExoPlayer replaces MediaPlayer** for non-MIDI song audio — MP3s
  and other formats stream more reliably.
- **Verse list rewritten** from ListView to RecyclerView for smoother
  check/highlight animations.
- **Adaptive launcher icon**.
- Bigger split handle, better selected-drawer-item highlighting, more
  contrast in the Goto dialer / grid.
- Fixes for: wrong cross-reference verse numbers, inability to dial
  song numbers, verses copied in the wrong order, and a couple of
  lifecycle-related crashes.
- "Manage versions" / "Manage reading plans" open automatically when
  nothing is installed yet.

## 4.5.7 — 2019-05-28

- **Push sync migrated from GCM to FCM** (Google was shutting GCM
  down).
- Navigation bar is forced to black.
- New accounts can't be registered with uppercase usernames (login
  still accepts them).
- Security fix for a zip path-traversal vulnerability on user-data
  import.

## 4.5.6 — 2019-04-24

- Added Cebuano and Tagalog UI translations.
- Ukrainian UI translation dropped.

## 4.5.5 — 2018-11-16

- **Hide the app icon on the daily-verse widget** via a new
  preference.
- **Song player** shows current playback position and total duration.
- **Right-to-left (RTL) layout support** for Arabic and Hebrew.
- Analytics moved from Google Analytics to Firebase; song-playing and
  devotion-reading events are tracked.
- Groundwork for AYT (Alkitab Yang Terbuka) as a downloadable Bible
  version arrives around this time.
- **Minimum Android raised to 4.2** (API 17).

## 4.5.4 — 2018-11-05

- **Ribka** support: report typos or suggest corrections in the AYT
  Bible text directly from the verse menu.

## 4.5.3 — 2018-09-26

- **"Download from URL"** for Bible versions works again.
- Clearer error messages when a version download fails.

## 4.5.2 — 2018-06-29

- Background services moved to `JobIntentService` to stop crashes on
  Android 8+ (affects the FCM sync receiver and widget updater).

## 4.5.1 — 2018-06-28

- **Preset versions are grouped** on the download list (e.g. by
  language family) — easier to scan.
- The "show hidden versions" setting was retired.
- Added Italian and Turkish UI translations.
- **Notification channels** added for Android 8+.
- Cleaned up the sync sign-up form (church / city / religion removed).
- Copy/share from the split view correctly uses the split version's
  reference.
- Websites updated to `alkitab.app`.

## 4.5.0 — 2018-02-21

- Sharing verses now uses **FileProvider**, so sharing to other apps is
  much more reliable.
- No longer depends on the system Download Manager — fixes downloads on
  Kindle Fire and similar devices.
- The Goto screen is a full screen on tablets instead of a dialog.
- Accompanied by a curated trim of the downloadable version list
  (following the license / maintenance clean-ups announced on the dev
  blog).

## 4.4.3 — 2017-11-02

- Crash reporting moved from an in-house reporter to **Crashlytics**.
- Song-book download page opens automatically if none are installed.
- Better error logs around sync login / register / forgot-password.
- Various widget and NFC crash fixes, plus a workaround for an
  Android 6.0.0 EditText crash.

## 4.4.1 — 2017-07-25

- Action-bar buttons are decided by screen-width at launch, so rotating
  no longer flips them in and out of the overflow menu.

## 4.4.0 — 2017-03-21

- **Per-version text size** (set from the Display panel; in split view
  each side can have its own size, expressed as a percentage).
- **Pinch-to-zoom on songs**, with reflow.
- **Private song books.**
- **Multi-word phrase search** — highlighting now works across
  formatting tags.
- "Text appearance" panel renamed to **Display**.
- Better Goto Direct autocomplete.
- Optional "bigger UI" scale (1.5× / 1.7× / 2.0×) for users with low
  vision.
- Version-download errors show as snackbars instead of toasts.
- Migrates stored YES files and custom fonts out of public external
  storage (no more storage permission for fonts); the Android 6.0+
  storage permission is only requested when actually needed.
- Long-press Play to **loop a song**.
- Reading plans now show how many days you're **ahead of schedule**.
- Attribute icons (bookmark, note, pin, map) scale with verse text.
- Added Vietnamese UI translation; devotion-reminder time picker now
  respects the user's locale.

## 4.3.8 — 2016-08-19

- Added Bulgarian and Danish UI languages, plus Korean app and
  feedback-module translations.

## 4.3.7 — 2016-04-08

- New devotions: **refheart** and **ROC**.
- Added Thai UI language.
- A reversed-offset partial highlight no longer crashes.

## 4.3.6 — 2016-02-29

- Sync skips past a disabled sync set and continues with the rest.

## 4.3.5 — 2016-02-26

- Fixed truncated verse lines on Android 6.0 Marshmallow (line-height
  workaround).
- Version-list spinner displays correctly on Android 6.0.1.

## 4.3.4 — 2015-11-30

- "Import old markers" now also recognizes the `org.sabda` variant's
  export format.
- Verse selection and copying available in **search results**, with a
  select-all button.
- Label-assignments are no longer lost on some imports.

## 4.3.3 — 2015-10-29

- Fixed a thread-pool crash on older Android versions.

## 4.3.2 — 2015-10-26

- Follow-up fix for upgrading from pre-2.0 installs.

## 4.3.1 — 2015-10-20

- Marker-list sorting moved to a submenu.
- Fix for upgrading the database from pre-2.0 installs.
- Suppressed a spurious dictionary-provider error.

## 4.3.0 — 2015-10-02

- **Extensions**: third-party apps can add attribute icons and popups
  to verses. The list auto-refreshes as extensions are installed or
  uninstalled.
- **Maps indicator** on verses with geographic references — tap for a
  popup. Companion **Bible Maps** app works with this, including
  star-able "anchor" locations that persist across both apps.
- **Your data on the cloud**: a companion web dashboard shows all
  synced bookmarks, pins, reading-plan progress, and verse history in
  a browser, including a JSON download.
- **Partial sync**: sync now sends markers in chunks of up to 100 per
  request, so initial sync succeeds even for users with tens of
  thousands of items.
- **Bottom toolbar** option on the Bible screen.
- **Goto screen redesigned** with an autocomplete dialer, scrollable
  book-filter categories, and a new grid.
- **Call-attention animation** when jumping to a verse (brief color
  flash so you can find it).
- **Mark-read-up-to** on reading plans via long-press.
- Runtime storage-permission handling for Android 6.0+.
- Split proportion is saved across rotations.
- Full backup support (Android auto-backup) enabled.
- Bigger default fonts; less-cramped navigation arrows.

## 4.2.2 — 2015-07-30

- Reading-plan progress bar updates when you change the start date;
  the start-date picker defaults to the current start date.
- Kindle / BlackBerry / Genymotion no longer see the Google Play
  Services prompt.

## 4.2.1 — 2015-07-08

- Critical stack-overflow fix.
- Crash reports are capped in length.

## 4.2.0 — 2015-07-07

- **Reading plans sync across devices** (progress and start-dates).
  The plan itself still needs to be downloaded per device. Deleting a
  plan keeps your check-marks locally, and there's a new **Restart**
  action.
- **Pins (progress marks) sync across devices**.
- **Partial highlight**: select just a word or phrase inside a verse
  with draggable handles. Switching to another Bible version shows
  the highlight as a full-verse highlight until you come back.
- **Custom background color for selected verses** (Settings > Display).
  The default also moved to a more vivid blue.
- **Current-reading indicator** in the left drawer, updating as you
  switch versions.
- **Share URL**: sharing a verse now includes an auto-generated
  bibleforandroid.com link that opens the same version in the browser
  (can be turned off in Settings > Copying and sharing).
- Copy / share primary, secondary, or **both** versions from split
  view.
- **True night mode**: the action bar and split handle are now dark
  too.
- Verse-item accessibility (TalkBack) support.
- Language switching takes effect almost instantly; no restart needed.

## 4.1.1 — 2015-05-25

- Fixes for several crashes: song keypad, passing too many songs,
  search-engine NPE, database-index upgrade from very old versions.
- Companion release to 4.1.0 (see below).

## 4.1.0 — 2015-05-08

- Indonesian **Alkitab** gets integration with SABDA's **Kamus
  Alkitab** (dictionary), **Tafsiran Alkitab** (commentary), and
  **AlkiPEDIA** — selecting a verse shows buttons that jump into
  those apps, and a new setting auto-underlines dictionary words.
- **Songbook in Quick Bible for the first time**; the Alkitab songbook
  UI was reworked with three tabs (Utama / Suku / Lainnya).
- New songbooks added: **English Hymns (EN)** (over 6,000 Cyber Hymnal
  songs with MIDI tunes and verse links), **SPSS** (Polish), **KLIK**,
  **NNBT**, and **JB** (Sunday School).
- **Reading plan upload & share** — anyone with an `.rpa` source file
  can publish a plan. Every plan has a unique ID; the add-plan screen
  has Featured, Newest, and By ID tabs.
- Copy and Share optionally includes the Bible **version name**
  alongside the text.
- Appconfig controls which dictionary / guide / commentary menus show
  up on the verse action bar.

## 4.0.1 — 2015-04-11

- Fixed a widget crash on Android < 5.0.
- Fixed a crash on Android < 4.1 from a mistyped API call.

## 4.0.0 — 2015-04-10

- **Material-design redesign**: Toolbar replaces the old ActionBar
  across every screen, a navigation drawer replaces the old
  overflow-style menu, Material-styled dialogs throughout, refreshed
  icons, adaptive widget, hamburger drawer animation.
- **Cloud sync**: register or log in to an account and sync your
  bookmarks, labels, highlights, notes, and progress marks across
  devices, with push updates. A one-time "import old markers" flow
  brings data over from earlier installs.
- **Notes screen** picks up your Display settings; read-only view lets
  you click where to start editing.
- Verse pop-ups and Compare lists collapse cross-chapter ranges into
  nice abbreviated form (e.g. "Matthew 5:3–12").
- **Delete a single song book** (previously all-or-nothing). Song
  books are now downloaded from an online list; new **NR** (Italian)
  song book added.
- Optional version short-name when copying / sharing a verse.
- Reading-plan list displayed as an HTML page; duplicates blocked;
  plans can be shared by ID.
- Auto-lookup dictionary, guide, and commentary when companion apps
  are installed.
- History / recent-verses stored in a sync-ready format with
  timestamps.
- Bookmark labels can be sorted alphabetically; label caption limit
  raised from 24 to 48 characters.
- Fixed Lollipop line-spacing, Download Manager prompts, and a
  "migrate from v3" overflow menu.

## (Quick Bible re-launch) — 2015-01-29

- Quick Bible returned to Play Store after the Yuku developer account
  was reinstated. The relaunch introduced the material-design UI,
  cloud sync, swipe-to-change-chapter, pinch-to-zoom,
  two-finger-fullscreen, drawer-based progress marks, background
  version downloads, and an upgraded cross-version search — all
  rolled into what shipped as the 4.0 series.

## (SABDA re-publish) — 2014-12-15

- While the Yuku developer account was suspended, the app was
  re-published on Play Store under SABDA as "SABDA Alkitab (Yuku
  Android)". On first launch it offered to transfer your bookmarks
  from the old app's automatic backup.

## 3.7.0 — 2016-01-06

- "Download reading plan by ID" support.
- Late maintenance release on the legacy 3.x branch — the 4.x series
  was already the main line by this point.

## 3.6.3 — 2014-09-19

- **Patch text** mechanism: propose edits to songs and devotions, with
  tappable verse references inside the editor.
- "Add YES file from URL" on the Versions screen.
- New preset versions: ILT, ALP-Alune, KJE-Kisar, and a new **NR**
  songbook.
- Share URLs moved to `bibleforandroid.com`.
- Fixed a widget crash when a version's data file was removed, and a
  startup crash when the previously-selected version was no longer
  loadable.

## 3.6.2 — 2014-07-24

- Added Malay and SDA Toraja versions.
- Help button now opens the website; official site link on About.
- Beta ribbon removed.

## 3.6.1 — 2014-06-06

- **Custom highlight color**.
- **Dark theme for the Goto screen.**
- When a search returns 0 results but looks like a verse reference,
  it falls back to Goto.
- **Samsung MultiWindow** support.
- Widget app logo taps through to the app.
- Added Indonesian tribal-language Bibles.
- Donation / About / Suggest / Help combined into a single **Support**
  screen; Full-screen and Night-mode toggles moved into the Text
  Appearance panel.

## 3.6.0 — 2014-06-02

- **Morning & Evening** devotion added (Indonesian meid-a).
- Refreshed font-download UI; new Roboto / Droid font names.
- Devotion-kind picker uses a spinner with subtitles.
- Marker **Transfer / Export** merged into a single menu item.
- Added King James 2000 (en-kj2000) and Romanian Cornilescu Revised.
- Verses opened from search or markers are selected by default.
- Songs accept A/B/C suffixes on the number; auto-backup correctly
  deletes old files.
- Split view stays in sync when scrolling or using volume-button
  chapter navigation.

## 3.5.6 — 2014-05-23

- Added English Lexham Bible (en-leb).
- Beta-tester program link added to About.
- PDB and YES files with uppercase extensions open correctly.
- KJV download combined with red-letter text (separate red-letter
  edition removed).

## 3.5.5 — 2014-04-22

- Fixed a bookmark edit / delete bug.
- Added NKI songbook.
- Cancelling a version download no longer flags the version as
  downloaded.
- Opening a verse from search results starts a new reader screen
  instead of replacing the current one.

## 3.5.4 — 2014-04-15

- **Sharing to WhatsApp omits the subject line** (no more "(no
  subject)" prefix).
- Devotion screen: proper up-navigation, tapping a verse returns
  correctly, and verse numbers with letters like "3a" now parse.
- High-Unicode characters (emoji) are escaped so marker / label
  backups succeed.
- Song player reliability fix on some devices.
- All preset versions are now YES2; the older YES1 entries were
  removed from the download list.

## 3.5.3 — 2014-04-08

- Goto dialog's extraneous action bar removed.
- The floating box on the goto grid shows an empty box for the
  current book / chapter so it's obvious which one is selected.
- Added the Pontic (pon-old) version.

## 3.5.2 — 2014-04-05

- Rollup of several small fixes: song-player stability, unicode
  emoji surviving backup / restore, WhatsApp share without an empty
  subject line, devotion up-navigation returning to the right verse,
  "5a"-style verse suffixes parsing, and prefetch of Renungan Harian
  devotion limited to 3 days ahead.

## 3.5.1 — 2014-04-03

- Narrower action-bar buttons for small and pre-ICS devices.

## 3.5.0 — 2014-04-02

- **Compare versions**: see the same verse in multiple versions in a
  single dialog.
- Bookmark-list sort order is remembered.
- Reading-plan date calculation no longer drifts around
  daylight-saving transitions.
- Refreshed About screen and higher-resolution icons.

## 3.4.7 — 2014-03-29

- Pericope and out-of-plan verse shading draws correctly.
- Copying or sharing from split view shows the book abbreviation
  instead of `[?]` when a book is unavailable.

## 3.4.6 — 2014-03-28

- **Immersive mode on Android 4.4 KitKat.**
- New songbooks: KPKL and KPPK.
- Pressing different zones of the split handle opens either the
  version-chooser or the split-version dialog.

## 3.4.5 — 2014-01-09

- Verses can also be selected via the floating menu.
- Highlight appearance tweaks.
- Fixed sharing verses from a version without a short-name.

## 3.4.4 — 2013-11-27

- Removed stray leading spaces in some TB verses.
- More fixes for the reading-plan progress-table corruption.

## 3.4.3 — 2013-11-25

- Fixed reading-plan progress-table corruption for users upgrading
  via 3.3.3.

## 3.4.2 — 2013-11-20

- Reading plans downloaded from a server (no longer built in);
  description and day count shown before download.
- Verses outside today's reading-plan portion are shaded more
  clearly.
- Added NET Bible.

## 3.4.1 — 2013-11-18

- **Night mode** menu (dark theme) with night-aware color settings.
- **Double-tap anywhere** toggles full-screen.
- Non-English Latin characters are now searchable.
- Warning when downloading a version in a locale you may not use.

## 3.4.0 — 2013-11-15

- **Reading plans**: daily readings with a hide/show detail toggle,
  a "Today" popup, a "Catch me up" shortcut when you fall behind,
  and per-verse tracking of how many times you've read each verse.
- Reading-plan overlay also works across the split-view pane.
- Progress stored per plan with indexed tables for fast lookups.
- Sample plans in the download list: Gospels (30d), Psalms (31d),
  Proverbs (31d), the Blue Letter yearly plans, M'Cheyne (1y), ESV
  Daily / Yearly plans, and Back To The Bible Chronological.
- Clicking a verse link inside a song now opens the verse in a
  popup dialog.

## 3.3.3 — 2013-11-15

- Last split version is remembered across app launches.
- New **text-size option for the daily-verse widget**.
- Added Hindi (hi-bsi) and Tamil (ta-bsi) versions.
- Devotion encoding issues fixed.

## 3.3.2 — 2013-11-11

- Feedback messages must be at least 40 characters.
- Donation page opens inside the app.
- Added Japanese locale; added Romanian Fidela, GBV 2001, and Telugu
  Bibles.

## 3.3.1 — 2013-10-24

- **TSI Bible** added.
- **Transparent daily-verse widget** variant (dark text on transparent
  background), and support for **lockscreen widgets**.
- New "show yesterday's verse" option in the widget.
- Fixed a Facebook-sharing bug on Quick Bible.

## 3.3.0 — 2013-10-21

- **Daily-verse home-screen widget** (scrollable on Android 4.0+).
  Pick the version, font size, transparency, and light / dark text;
  Next / Previous buttons browse; tap to jump into the app.
- **Footnotes**: tap the marker to read the footnote in a dialog.
- Numbered cross-reference marks with easier tap targets (hit radius
  expanded to ~24 dp) and a nicer xref dialog.
- **Automatic bookmark backup** (up to 10 rolling files) with a
  picker to restore any of them, and the last-backup date shown on
  the bookmark screen.
- Progress Marks can be **renamed** ("Morning", "Sermon", …); the app
  prompts for a name on save, and a second tap on the same verse
  offers to delete the mark.
- Reminder feature shipped as a separate companion module.
- **Swipeable tabs** on the Goto screen (keypad / dialer / grid).
- Reading screen gains a floating book / chapter picker ("floater")
  that follows your finger for quick navigation.
- Empty verses are collapsed.
- Reference parser now understands chapter-to-chapter ranges like
  "Ps 38-39".

## 3.2.0 — 2013-09-23

- **Progress marks**: 5 named pins for your current reading location,
  each with its own color and default name. Long-press to rename or
  delete (delete just empties the slot). Recent-verse / progress
  listing shows timestamps.
- **Export bookmarks, notes, and highlights as HTML**, preserving
  label colors, highlight colors, and dates; shareable to email /
  Dropbox / any file target.
- Pericope titles rendered in italic.
- Book abbreviations on the Goto grid.
- Bible version updates: KJV now includes proper exclamation marks
  and Psalm song titles (red-letter KJV kept as a separate version);
  Chinese CUV replaced with Chinese Union Version Modern Punctuation
  Simplified (fixes stray `?` characters); Reina Valera 1909 fixes a
  missing phrase in Genesis 17:23.
- New UI languages: Czech, Simplified Chinese, Traditional Chinese.
- Fixed a crash when upgrading from very old installs.

## 3.1.1 — 2013-06-12

- Shared verses now generate a version-specific URL for supported
  Bible versions (the shared link opens the right translation).
- Removed Afrikaans 1953 and Xhosa from the download list.

## 3.1.0 — 2013-06-03

- **Full-screen reading mode** (with a menu toggle).
- **Volume buttons change chapters** (optional).
- Switching versions while full-screen no longer unchecks your
  selected verses.
- A small easter egg.

## 3.0.6 — 2013-05-14

- Fixed swapped / broken font-preview images.
- Minor crash fix.

## 3.0.5 — 2013-05-11

- **Split view**: read two Bible versions side by side with a
  draggable divider, synchronized scrolling, cross-references that
  work across both panes, and verses selectable on either side.
- **Cross-references**: tap xref marks to open a rich dialog with
  clickable links, and both source and destination are added to
  navigation history.
- **Modern verse-selection action mode** replaces the old long-press
  menu.
- New **light-themed "desert" UI** (Holo Light / DarkActionBar) as
  the default.
- **Text Appearance slide-up panel**: change font size, line spacing,
  font family, and colors in one place without digging into Settings.
- Open `.yes` and `.pdb` files directly from file managers and other
  apps; corrupt YES files no longer crash the app.
- Unified color settings (including a custom highlight color).
- Up-navigation button on non-root screens; Share menu on every
  Bible version in the list.
- Delete menu for preset version data.
- Full-screen mode; old top-nav buttons replaced by action-bar
  actions.
- Bookmark labels can be reordered with a drag grip.
- Empty verses can be collapsed; verse numbers with letter suffixes
  handled correctly.
- Many new preset versions added.

## 2.9.5 — 2013-03-18

- Cold-start from an external app now scrolls the verse list
  correctly.
- Goto grid redesigned to fit all Old / New Testament books in 6
  columns without scrolling.
- **Snappy compression** support for YES files — smaller downloads,
  faster loads.
- Warn before importing corrupt or non-PalmBible+ PDB files.
- Released the **AlkitabIntegration** helper library for third-party
  apps.

## 2.9.4 — 2013-02-12

- New app translations: Afrikaans and Spanish (other locales
  updated).
- Skip the bookmark auto-backup step when there are no bookmarks.
- Songbook URL updated; song data moved to the v3 format.

## 2.9.3 — 2013-02-06

- KJV ships with a reverse index for faster first search.
- Tokenizer recognizes apostrophes inside words.
- Fixed KJV words containing `- `.

## 2.9.2 — 2013-02-05

- Fixed a KJV file with a truncated Revelation chapter.

## 2.9.1 — 2013-02-05

- Fixed TB John 12:34 text.

## 2.9.0 — 2013-02-04

- **Drag-and-reorder** for bookmark labels.
- Native search view on the search screen (replaces the old custom
  widget); clearing it triggers a fresh search.
- Verse references inside devotions are parsed with standard
  Indonesian book names regardless of the active version.
- Refreshed menu icons.
- Under the hood: **new YES2 Bible file format** supporting verses
  longer than 4000 bytes and single-file pericopes.

## 2.8.7 — 2013-01-15

- Keep-screen-on now also applies inside the devotion screen.
- Jumper accepts en-dash and em-dash in verse ranges.
- Four more UI translations added.

## 2.8.6 — 2013-01-11

- **FAQ page** shown before the Feedback screen.
- Optional "prepend the verse number on every verse" mode when
  copying or sharing multiple verses.
- Dutch translation added; German updated.

## 2.8.5 — 2012-12-21

- Devotion screen redesigned with a single action bar and a popup to
  pick the devotion source.
- Smaller devotion copyright footer; copyright notices updated.

## 2.8.4 — 2012-12-14

- Content-provider verse-range queries load a whole chapter at once,
  so external apps that show many verses are much faster.
- Parallel-reference support (@a / @o / @lid) for cross-links in
  verse text.
- Last-commit hash shown on About in debug builds.

## 2.8.3 — 2012-12-05

- New "redownload" menu in the devotion screen.
- Book and chapter in the Goto grid can be re-selected independently
  (two tap zones).
- Attempted fix for Goto-direct keyboard issues on some devices.

## 2.8.2 — 2012-11-28

- Content provider can be reached again from other apps on Android
  4.2+.
- Worked around crashes caused by HTML tags in devotion titles.

## 2.8.1 — 2012-11-22

- Silent bump — the 2.8.0 feature set rolled forward for a Play Store
  update.

## 2.8.0 — 2012-11-20

- **In-app Feedback / "suggest a feature"** wizard.
- Faster first-run search via a preloaded reverse index (TB).
- **Exact-phrase search** with `"..."` and per-book search filters.
- Fixed Android 4.2 flicker on rotation.
- Search tokenizer treats hyphens as part of a word, and "Sela" /
  "Higayon" no longer split into single letters.

## 2.7.1 — 2012-10-30

- Revamped formatted-verse rendering (paragraph indent, italics,
  line breaks).
- Selected verses no longer turn their text red — much easier to
  read.
- Version short-names are correctly exposed through the external
  content provider.
- Hidden "secret settings" screen with a toggle for the legacy verse
  renderer, plus a legacy-mode preference for verse-number font
  size.
- Added Ukrainian UI translation and new Bible presets: Afrikaans
  1953, Korean KRV, and Polish Gdańska 1632.

## 2.7.0 — 2012-10-09

- **New Content Provider API**: other apps can query
  `content://yuku.alkitab.provider/bible/verses/...` to fetch a
  single verse by LID / ARI or a range of verses; the versions list
  is also queryable.
- **Intent-based verse-lookup** (`yuku.alkitab.action.VIEW` with an
  `ari` or `lid` extra): any app can launch Alkitab at a specific
  verse.
- Configurable **red-letter color** for the words of Jesus.
- First release with the new formatted-verse renderer (paragraph
  indent, italics, line breaks).
- Jump references accept ranges with a single or double dash.

## 2.6.3 — 2012-09-11

- 100th release overall. Mostly patch work on top of 2.6.0.
- Fixed copy / share of a song when the song contains a scripture
  reference.
- KPKA and PPK song books upgraded to v3 with corrected data.

## 2.6.2 — 2012-09-02

- No longer forces the Holo theme on every device — the app follows
  the system look where appropriate.

## 2.6.1 — 2012-09-01

- Emergency fix so the on-screen keyboard pops up again on pre-4.0
  Android when using the "direct" Go-to tab.

## 2.6.0 — 2012-08-28

- **Go-to redesign**: three tabs (keypad, grid, direct-input) instead
  of a mode chooser.
- German and Simplified Chinese UI translations added.
- **Transfer bookmarks** menu to move bookmark files between devices
  via share; backup XML files can be opened directly from a file
  manager.
- Fixed a Jelly Bean bug where selected verses didn't turn black.

## 2.5.2 — 2012-07-09

- Fixed a serious search bug where many queries returned nothing
  because the query wasn't lowercased before matching.

## 2.5.1 — 2012-07-03

- Copy and Share added to the Song screen.
- Smarter search query parser: queries like `a +"b c"` are handled
  correctly.
- Song search remembers the last result, so you can open several
  songs in a row without retyping.
- About screen now lists translators.

## 2.5.0 — 2012-06-16

- **Song Book** feature introduced: a new Songs screen bundles four
  Indonesian hymn books (KPRI, KJ, NKB, PKJ) with full-text lyric
  search, a keypad for dialing a song by number, and a filter for
  searching by title or number.
- Song reader respects your Bible font family, font size, and line
  spacing, so hymns match your reading preferences.
- Last-viewed song is remembered and restored when you reopen the
  app.
- **Custom fonts**: install and use any font file from storage;
  includes a small Font Manager with a few downloadable fonts.

## 2.4.0 — 2012-06-05

- **Line Spacing** preference so you can loosen or tighten the space
  between verse lines.
- Preference screen shows the current value of each setting inline.
- New menu entry to launch the companion ESV Study Bible add-on if
  it's installed.

## 2.3.1 — 2012-05-16

- Search can be restricted to the **currently open book**.
- In-app help page for the NFC verse-sharing feature.
- Pericope title top margin scales with the chosen font size.

## 2.3.0 — 2012-05-12

- **NFC** support: tap two devices together to send or receive a
  verse reference.
- Search and Version actions are always visible on the action bar.

## 2.2.3 → 2.2.7 — 2012-01-12 → 2012-03-21

- New downloadable Bible versions added across the series: Tagalog
  (Ang Biblia 1905), German (Luther 1912), English (BBE), Hungarian
  (Károli), Romanian (Ortodoxă), English ASV, WEB, and YLT.
- App can now open compressed `.yes.gz` files directly.
- Deuterocanonical / Greek versions where books don't start at
  Genesis now load correctly.
- Long verse numbers (100+, e.g. Psalm 119) no longer overlap the
  verse text; more paragraph indentation levels added.
- Backed-up **label colors** are now restored along with the labels.
- Verse share link to Facebook fixed to produce the correct URL.

## 2.2.2 — 2012-01-11

- Companion of the blog's "2.2" release:
  - **Bookmark labels can have custom colors** — long-press a label
    and pick a color; the colors show up on the bookmark list.
  - **Custom share chooser** that loads icons in the background (no
    lag) and sends a link to Facebook instead of plain text.
  - Optional **volume-button chapter navigation**.
  - UI polish for **Ice Cream Sandwich (Android 4.0)**: new compact
    action-bar icons for Android 2.3+, with overflow; older icons
    kept for Android 2.2 and below. Fixed the ICS Preferences
    flicker caused by forcing a locale that clashed with the OS.

## 2.2.0 → 2.2.1 — 2012-01-05 → 2012-01-11

- New downloadable versions: **Romanian (Cornilescu, 1928)** and
  **Japanese (口語訳 Kougo)**.
- New Romanian UI translation; Japanese Bible includes indentation
  and paragraph breaks.
- Version list now shows the language of each version above its
  name.
- Switching languages no longer makes the screen flicker on rotation
  or resume.

## 2.1.0 — 2011-11-28

- KJV Bible data now includes **italic formatting for
  translator-added words** (not in the Hebrew / Aramaic / Greek
  original), alongside the existing red-letter formatting for
  Jesus' words. Indonesian users need to delete and re-download
  the KJV add-on.

## 2.0.6 — 2011-11-28

- Fixed invisible (white-on-white) search-result text on tablets on
  Android 3.0+.
- Home button returns to the Bible text instead of exiting.
- Recent-verses list expanded from 10 to 20 entries.

## 2.0.2 → 2.0.5 — 2011-10-07 → 2011-11-23

- Bookmark backup/restore now includes your **labels** too, and on
  restore merges labels into the existing database instead of
  overwriting.
- "Export / Import to SD card" renamed **Backup / Restore** for
  clarity.
- **Hardware acceleration** turned on for smoother scrolling.
- Fixed an internal reader crash on KJV when a chapter had no
  pericopes.

## 2.0.0 — 2011-10-01

- **Redesigned verse menu**: tap a verse and press the fading-in
  button at the bottom-right, instead of long-pressing.
- **Multiple-verse selection**: Copy / Share concatenates all
  selected verses; Highlight applies to all; Add-bookmark /
  Write-note still attach to the first.
- **Bookmark labels**: assign one or more labels to each bookmark,
  with long-click to rename or delete a label.
- **Bookmarks screen redesigned**: bookmarks, notes, and highlights
  live in one place, with search and label filtering.
- Six new highlight colors.

## 1.9.13 → 1.9.15 — 2011-08-17 → 2011-09-14

- **Final 1.x maintenance releases** — shipped after 1.9.12 was
  announced on the blog as the last 1.x release, but still before
  2.0.0 landed on the Play Store (2011-10-01).
- Added **Chinese Union Version Traditional (CUVT)** and
  **Simplified (CUVS)** as downloadable presets.
- Fixed a corrupted Psalms index in the Indonesian TB version
  (wrong verse offsets).
- Tablet default font bumped to 22sp and larger keypad / Go-to
  activity layouts added for xlarge screens.

## 1.9.12 — 2011-08-16

- Stability fixes for the PDB-related crashes introduced in 1.9.2.
- **English Standard Version** (with italics) added as a download.
- Note-bubble dialog auto-resizes to fit the screen.
- "Go to" no longer crashes on obviously invalid references.
- The dev blog announced this as "the last 1.x release before 2.x" —
  in the end, 1.9.13 through 1.9.15 followed as maintenance on the
  1.x branch.

## 1.9.2 → 1.9.11 — 2011-06-08 → 2011-07-09

- **PalmBible+ (`.pdb`) file reader**: import any PDB Bible; the
  app converts it to the internal `.yes` format with a progress
  indicator and an encoding picker (UTF-8 / ISO-8859-1) with live
  preview.
- **Version Manager** screen: add, enable / disable, reorder, and
  remove installed Bible versions.
- **Apocrypha / Deuterocanonical books** supported when present in
  the PDB.
- **Tablet support** lands in 1.9.8 (targets Android 3.1): menus are
  no longer inaccessible on tablets, the title / action bar stays
  visible, and there's a new tablet top toolbar with overflow for
  Suggest / Help / About. Minimum Android was also raised from 1.5
  to 1.6 in this wave.
- Flexible book lists per version; bookmarks referencing a book not
  in the current version show `[?]` or the book number.
- Pericope / section headings render in the right place in more
  versions; italic verse text renders correctly.
- Honeycomb (Android 3.x) polish: translucent search background,
  better icons across densities.
- Tapping a cross-reference or parallel is added to your
  navigation history; volume-key scrolling sound suppressed; title
  bar removed from the reading screen for more text space.
- Share URLs customized per build (Alkitab vs KJV).
- Locale-override language setting applied more reliably, without
  needing a handler-based refresh.

## 1.4.5 — 2011-03-30

- Amazon Appstore release: the "more versions" link points to the
  market generically.
- Redesigned feedback form and verse-content screen layout.

## 1.4.4 — 2011-03-17

- ~100 corrections to the Indonesian Bible text (20–30 meaningful,
  the rest minor), sourced from Yayasan Lembaga SABDA. Corrected
  raw text published publicly.
- Fixed the English Quick KJV volume-button chapter scrolling (no
  more phantom key-release sound).
- Book names display with spaces instead of underscores.

## 1.4.3 — 2011-01-20

- Updated the **Toba** and **KJV** Bibles (removed stray asterisks,
  fixed underscores in book names).
- Volume-button verse scrolling no longer makes the system beep.

## 1.4.2 — 2011-01-16

- **Batak Toba Bible** added as an optional download (Indonesian
  Alkitab only). First five books named "1–5 Musa".

## 1.4.1 — 2011-01-12

- ~30 text corrections in the Indonesian Terjemahan Baru Bible.
- KJV add-on updated to include **red-letter text for Jesus' words**
  (users on older versions must delete `kjv.yes` and re-download).

## 1.4 — 2010-12-28

- **Red Letters in KJV**: words spoken by Jesus are highlighted in
  red. Red-letter data sourced from CCEL.

## 1.3 — 2010-12-21

- **Color highlighting** with 6 selectable colors (50% alpha so text
  stays readable on any theme).
- **Whole-word search**: prefix a query with `+` (e.g. `+adam`) to
  match the exact word.

## 1.2.2 — 2010-11-04

- English KJV version published at 1.2.2 to match the Indonesian
  release; donation menu added; small enhancements.

## 1.2.1 — 2010-11-03

- Scroll thumb on the search-results list.
- Progress dialogs added to import / export bookmarks; import now
  runs in a transaction (much faster).
- Fixed shifted section headings in Ecclesiastes and an incorrect
  history entry when using Jump To without a book name.

## 1.2 — 2010-10-19

- **Navigation history**: long-press the verse-address button to see
  the last 10 navigations (Go To / Jump To / bookmark /
  search-result clicks).
- **Quoted-phrase search** with `"..."`.
- Font-size scaling applied to parallel-passage links.
- Faster Go To book list.
- Devotion text can be copied and shared.

## 1.1 — 2010-09-08

- **First English release**, branded "Quick KJV Bible" (emphasizing
  speed). KJV is bundled as the default.
- All UI strings translated to English.
- English build intentionally ships without devotions, version
  switching, or help screens.

## 1.0.2 — 2010-08-06

- Crash-recovery tweak: if the app crashes, it exits cleanly instead
  of hanging.

## 1.0.1 — 2010-08-05

- New **"Starry Night"** color theme.
- Fixed a bug where tapping a bookmark didn't jump straight to the
  verse.
- Corrected the on-screen keypad layout so it applies only on
  small-portrait screens.

## 1.0 — 2010-08-04

- **First major release.** Psalm indentations and neater verse
  display with regular line spacing.
- **Keep-screen-on** option while reading.
- **New scan-based search engine** (no pre-indexing required);
  searches can be scoped to Old or New Testament.
- Prev / Next chapter crosses book boundaries.
- **Share verses via SMS, Email**, etc.
- **Annotations on verses**; bookmarks gain **editable titles**.
- **Customizable colors** — backgrounds, verse text, verse numbers —
  with Photoshop-style color picker and preset themes (Blackboard,
  Tropic Lush, Moss Lake, Warm Ash, Sweet Orange). Font size with
  real-time preview.
- Small title bar for the chapter address; clickable verse
  addresses in Renungan Harian devotion; improved up/down-key
  scrolling; high-res icons for hdpi devices; many Bible-text typo
  corrections.

## 0.9 / 0.9.1 — 2010-04-21

- **Section headings (Judul Perikop)** above the relevant verses,
  with tappable parallel-reference links that jump to the related
  passage.
- Verse-address parser recognizes ranges (e.g. `Mat 26:47-56`
  navigates to `Mat 26:47`).
- **Devotion** feature introduced.
- 0.9.1 shipped shortly after with a small bug fix.

## 0.8 — 2010-03-26

- **First search feature**, powered by SQLite FTS3.
- Requires a ~9 MB initial index on SD card (indexing takes ~5
  minutes on a Nexus One); after that, search is sub-second.

## 0.7 — 2010-03-15

- **Flexible quick verse-jump**: accepts inputs like `1 pet 3:16`,
  `1 pe 3 16`, `1p3 16`, `1p3.16`.
- **Bookmarks**: long-press a verse and Add bookmark; stores the
  verse address, a text snippet, and last-added date. Bookmarks
  screen lists them; re-adding a verse updates the date instead of
  duplicating it.

## 0.6 — 2010-03-04

- Enlarged touch targets on the Go To screen (chapter and verse
  buttons are now finger-friendly).
- **Bible text view switched to a ListView** — dramatically faster
  chapter loads (renders only what's on screen).
- **Adjustable text brightness** (percentage).
- Simpler About screen.

## 0.5 — 2010-01-29

- **Chapter-address button** moved from the title bar to the bottom
  and also opens Go To.
- **New Settings screen**: font size (7 options), font family
  (serif / sans / monospace), bold toggle.
- Feedback prompt on every launch.

## 0.4 — 2010-01-12

- **First Play Store (Android Market) release.**
- Chapter-index data moved to a binary format — book info now loads
  in under a second.
- **Prev / Next chapter buttons** at the bottom of the main screen.
- Go To screen has a book-selection dropdown (previously the book
  had to be set separately from chapter:verse).

## 0.3 / 0.3.1 — 2010-01-12 / 2010-01-15

- Early development milestones before the Play Store launch: an
  on-launch warning dialog notes the app is unfinished and asks
  users to update; the phone-keypad chapter/verse input is split
  into its own layout, with a different key order in landscape.

## 0.2 — 2010-01-11

- **First version that actually shows Bible text** on screen (plain
  text sourced from bibledatabase.org).
- Left / right buttons navigate chapters; Menu > Book picks a
  book; Menu > Go To opens chapter / verse inputs.

## 0.1 (inception) — 2009-12-22

- Project begins. Author decides to build an Indonesian Bible app
  for Android because no such app exists at the time; earliest
  experimental screens include the main Bible-text view and a
  chapter / verse input screen (no book selector yet).

---

*Notes for readers:*

- *Dates are tag creation dates where a release was tagged; otherwise
  they're the commit date of the `versionName` bump. Pre-2.7.1 entries
  come from a mix of `AndroidManifest.xml` version bumps and the
  project's dev blog.*
- *Beta / alpha / "topic" tags used during development are
  intentionally omitted — their work is rolled into the next stable
  release.*
- *A few Play Store story beats that don't map to a version number are
  included where they help explain the timeline (the late-2014 SABDA
  re-publish and the January-2015 Quick Bible return).*
- *Around 2019, version 4.6.0 appears in the version history as an
  internal milestone; it looks like it was never rolled out to the
  Play Store, with the verse-view rewrite shipping publicly as 4.6.1
  instead.*
