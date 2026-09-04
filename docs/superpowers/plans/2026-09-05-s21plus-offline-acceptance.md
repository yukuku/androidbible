# Samsung S21+ Offline Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the integrated Yuku build satisfies recorded-audio priority, offline WEB audio, Google-TTS fallback, TalkBack accessibility, and fully offline local theme search on the user's Samsung Galaxy S21+.

**Architecture:** Automated Gradle gates establish deterministic code/build evidence; a controlled ADB pass installs over existing app data, snapshots device settings, runs online setup once, disables networking, validates offline flows, and restores every modified device setting. Evidence is kept under `artifacts/s21plus-acceptance/` without Bible text dumps or personal data.

**Tech Stack:** JDK 21, Gradle, Android SDK/ADB, Samsung SM-G996B on Android 15, Media3 media-session inspection, UIAutomator hierarchy dumps, screenshots.

**Spec:** `docs/superpowers/specs/2026-09-05-offline-audio-tts-theme-search-design.md`

## Global Constraints

- TB2 is excluded from every acceptance step.
- Preserve installed app data; do not uninstall or clear the package.
- Record Wi-Fi, mobile-data, airplane-mode, enabled-accessibility-service, and TalkBack state before changing anything.
- Restore all device settings at the end even if a test fails.
- Never grant broad local-media access; audio remains app-private.
- Do not use `monkey`; launch the explicit `yuku.alkitab.debug/yuku.alkitab.base.IsiActivity` activity.
- A passing build is not a device pass; every stated device behavior requires direct evidence.

---

### Task 1: Run the complete automated gate and capture build identity

**Files:**
- Create: `artifacts/s21plus-acceptance/automated-gate.txt`
- Create: `artifacts/s21plus-acceptance/build-identity.txt`

- [ ] **Step 1: Confirm worktree and toolchain identity**

```bash
git status --short
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin ./gradlew --version
```

- [ ] **Step 2: Run all tests and both build variants**

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin ./gradlew :Alkitab:testPlainDebugUnitTest :Alkitab:testPlainReleaseUnitTest :Alkitab:lintPlainDebug :Alkitab:assemblePlainDebug :Alkitab:assemblePlainRelease
```

- [ ] **Step 3: Record exact commit, APK, checksums, and sizes**

```bash
git rev-parse HEAD
find Alkitab/build/outputs/apk/plain -name '*.apk' -print
/usr/bin/openssl dgst -sha256 Alkitab/build/outputs/apk/plain/debug/*.apk Alkitab/build/outputs/apk/plain/release/*.apk
du -h Alkitab/build/outputs/apk/plain/debug/*.apk Alkitab/build/outputs/apk/plain/release/*.apk
```

- [ ] **Step 4: Inspect packaged assets/native libraries**

```bash
unzip -l Alkitab/build/outputs/apk/plain/debug/*.apk | rg "audiotreasure_web_manifest|web_granite97m_r2|libonnxruntime|model_quint8|tokenizer.json"
```

Expected: audio manifest, WEB int8 index, and supported ONNX runtime are present; downloaded model/tokenizer are absent.

- [ ] **Step 5: Commit only fixes required by this gate; do not commit generated APKs**

```bash
git status --short
```

---

### Task 2: Snapshot the device and install without data loss

**Files:**
- Create: `artifacts/s21plus-acceptance/device-before.txt`
- Create: `artifacts/s21plus-acceptance/install.txt`

- [ ] **Step 1: Confirm the exact device**

```bash
adb devices -l
adb -s RRCR100881M shell getprop ro.product.model
adb -s RRCR100881M shell getprop ro.build.version.release
adb -s RRCR100881M shell dumpsys package yuku.alkitab.debug | rg "versionName|versionCode|firstInstallTime|lastUpdateTime"
```

Expected: model `SM-G996B`, Android `15`, package already installed.

- [ ] **Step 2: Snapshot mutable settings**

```bash
adb -s RRCR100881M shell settings get global wifi_on
adb -s RRCR100881M shell settings get global mobile_data
adb -s RRCR100881M shell settings get secure enabled_accessibility_services
adb -s RRCR100881M shell settings get secure accessibility_enabled
adb -s RRCR100881M shell settings get secure tts_default_synth
```

- [ ] **Step 3: Install over the existing package**

```bash
adb -s RRCR100881M install -r -d Alkitab/build/outputs/apk/plain/debug/*.apk
```

- [ ] **Step 4: Launch the explicit activity and capture initial UI**

```bash
adb -s RRCR100881M shell am force-stop yuku.alkitab.debug
adb -s RRCR100881M shell am start -n yuku.alkitab.debug/yuku.alkitab.base.IsiActivity
adb -s RRCR100881M exec-out uiautomator dump /dev/tty
adb -s RRCR100881M shell screencap -p /sdcard/CodexInstalledReader.png
adb -s RRCR100881M pull /sdcard/CodexInstalledReader.png artifacts/s21plus-acceptance/installed-reader.png
```

- [ ] **Step 5: Verify existing TB/WEB modules and user data remain**

Use the version picker through resource IDs/text and verify TB plus WEB remain listed; do not extract protected Bible text into artifacts.

---

### Task 3: Verify recorded WEB audio and offline chapter storage

**Files:**
- Create: `artifacts/s21plus-acceptance/web-audio-online.txt`
- Create: `artifacts/s21plus-acceptance/web-audio-offline.txt`
- Create: `artifacts/s21plus-acceptance/web-audio-offline.png`

- [ ] **Step 1: Select WEB Genesis 1 and open audio**

Use UIAutomator text/resource IDs, confirm the selected source announces `WEB — David Williams (public domain)`, and start playback.

- [ ] **Step 2: Verify Media3 playback, notification, seek, speed, pause/resume, and next chapter**

```bash
adb -s RRCR100881M shell dumpsys media_session
adb -s RRCR100881M shell dumpsys notification --noredact | rg "BibleAudioService|WEB|David Williams|Kejadian|Genesis"
```

Expected: Media3 state reaches `PLAYING`; title/source is accurate; controls update state.

- [ ] **Step 3: Download Genesis 1 and verify app-private final file**

Trigger `Unduh pasal untuk offline`, wait for state `Tersimpan offline`, then inspect only the exact app-private path:

```bash
adb -s RRCR100881M shell run-as yuku.alkitab.debug ls -l files/audio/audiotreasure-web/01/001.mp3
```

- [ ] **Step 4: Disable network, force-stop/reopen, and play the downloaded chapter**

```bash
adb -s RRCR100881M shell svc wifi disable
adb -s RRCR100881M shell svc data disable
adb -s RRCR100881M shell am force-stop yuku.alkitab.debug
adb -s RRCR100881M shell am start -n yuku.alkitab.debug/yuku.alkitab.base.IsiActivity
adb -s RRCR100881M shell dumpsys media_session
```

Expected: local chapter reaches `PLAYING` with networking disabled and after process restart.

- [ ] **Step 5: Remove the download and verify only that exact file is gone**

Use `Hapus unduhan pasal`; verify `adb -s RRCR100881M shell run-as yuku.alkitab.debug ls -l files/audio/audiotreasure-web/01/001.mp3` reports the one target missing, without recursive delete commands.

---

### Task 4: Verify narration-first routing and Google-TTS fallback

**Files:**
- Create: `artifacts/s21plus-acceptance/listening-routing.txt`
- Create: `artifacts/s21plus-acceptance/google-tts-fallback.png`

- [ ] **Step 1: Confirm Google engine and Indonesian offline language**

```bash
adb -s RRCR100881M shell pm list packages com.google.android.tts
adb -s RRCR100881M shell settings get secure tts_default_synth
```

Expected: `com.google.android.tts` installed. The in-app engine check must report Indonesian available.

- [ ] **Step 2: Prove a working recorded passage does not start TTS**

On WEB Genesis 1, invoke `Dengarkan`; verify Media3 plays and `dumpsys audio_flinger`/AudioTrack attribution does not show a new Google-TTS stream.

- [ ] **Step 3: Prove a passage with no recording routes to Google TTS**

Select an installed version/book combination whose catalog has no recording, choose selected verses, invoke `Dengarkan`, and verify the UI labels `Bacakan dengan Google TTS` before activation.

- [ ] **Step 4: Verify actual synthesis, stop, and queue navigation offline**

With Wi-Fi/data still disabled, start TTS and inspect active audio tracks plus application state. Exercise next/previous and stop; confirm the spoken reference/state changes in UI.

- [ ] **Step 5: Prove a recorded-load failure requires explicit fallback**

Use a test-only deterministic failure seam in debug build, invoke `Dengarkan`, verify no automatic TTS starts, then explicitly choose the Google-TTS fallback and verify synthesis begins.

---

### Task 5: Install the model pack and verify theme search fully offline

**Files:**
- Create: `artifacts/s21plus-acceptance/model-pack.txt`
- Create: `artifacts/s21plus-acceptance/theme-search-offline.txt`
- Create: `artifacts/s21plus-acceptance/theme-search-offline.png`

- [ ] **Step 1: Re-enable network temporarily and install the model pack through the UI**

Restore Wi-Fi to its captured initial state, select `Pencarian tema offline`, activate install, and wait for `Siap digunakan`.

- [ ] **Step 2: Verify exact private files, lengths, and app-reported checksums**

```bash
adb -s RRCR100881M shell run-as yuku.alkitab.debug ls -l files/offline-search/packs/835ad14087e140460703cf0fae09f97d469d65c2
```

Expected: model `98,247,878` bytes, tokenizer `25,301,672` bytes, and `READY.json`; in-app diagnostics report the pinned SHA-256 values.

- [ ] **Step 3: Disable Wi-Fi/data and clear network evidence**

```bash
adb -s RRCR100881M shell svc wifi disable
adb -s RRCR100881M shell svc data disable
adb -s RRCR100881M shell am force-stop yuku.alkitab.debug
adb -s RRCR100881M shell am start -n yuku.alkitab.debug/yuku.alkitab.base.IsiActivity
```

- [ ] **Step 4: Run the seven approved Indonesian themes**

Run each query and capture references/ranks/timing (not full copyrighted verse bodies):

```text
kecemasan dan kekhawatiran
kekuatan saat lemah
kasih yang sabar
meminta hikmat
keberanian menghadapi ketakutan
mengampuni orang lain
penciptaan dunia
```

Expected: warm query-to-results below one second; at least four top-five results per theme are directly relevant; canonical fixture has a hit within top 50.

- [ ] **Step 5: Open a result and exercise “Dengarkan hasil” policy**

Verify result opens in the active installed version. If a recording covers it, narration is selected; otherwise Google TTS is offered. Confirm no network request or cloud fallback occurs.

---

### Task 6: Verify TalkBack traversal and restore settings

**Files:**
- Create: `artifacts/s21plus-acceptance/talkback.txt`
- Create: `artifacts/s21plus-acceptance/talkback-search.png`
- Create: `artifacts/s21plus-acceptance/device-restored.txt`

- [ ] **Step 1: Resolve the installed Samsung TalkBack service and enable only that exact component**

```bash
adb -s RRCR100881M shell dumpsys package com.samsung.android.accessibility.talkback | rg "AccessibilityService|service"
```

Append the exact component to the previously captured enabled-service string; never overwrite unrelated enabled services.

- [ ] **Step 2: Traverse without coordinate taps**

Use accessibility focus actions/key events to traverse search mode, pack status, query submit, result row, `Dengarkan hasil`, audio download, play/pause, speed, and close. Record UI hierarchy after each principal screen.

- [ ] **Step 3: Verify spoken labels and state changes**

Confirm each control has one concise label, the mode and result count announce once, result row is one focus target, progress changes do not steal focus, and recorded-vs-Google-TTS source is explicit.

- [ ] **Step 4: Restore TalkBack, Wi-Fi, mobile data, and every captured setting**

Use the exact values saved in `device-before.txt`. Do not assume defaults.

- [ ] **Step 5: Re-read settings and compare with the snapshot**

```bash
adb -s RRCR100881M shell settings get global wifi_on
adb -s RRCR100881M shell settings get global mobile_data
adb -s RRCR100881M shell settings get secure enabled_accessibility_services
adb -s RRCR100881M shell settings get secure accessibility_enabled
adb -s RRCR100881M shell settings get secure tts_default_synth
```

---

### Task 7: Final completion audit

**Files:**
- Create: `artifacts/s21plus-acceptance/ACCEPTANCE.md`

- [ ] **Step 1: Map every spec requirement to authoritative evidence**

Record PASS/FAIL for licensing/catalog, recorded priority, streaming, offline chapter, Google-TTS-only fallback, TalkBack, exact-search regression, theme relevance, network-off operation, performance, model integrity, build tests, and restored device state.

- [ ] **Step 2: Re-run any failed check after a test-first fix**

Do not waive or narrow a failed requirement. Add a failing automated regression test before changing production code.

- [ ] **Step 3: Run the final clean gate once**

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin ./gradlew :Alkitab:testPlainDebugUnitTest :Alkitab:testPlainReleaseUnitTest :Alkitab:assemblePlainDebug
```

- [ ] **Step 4: Confirm only intended worktree changes remain**

```bash
git status --short
git log --oneline --decorate -20
```

- [ ] **Step 5: Commit the acceptance evidence**

```bash
git add artifacts/s21plus-acceptance
git commit -m "test: verify offline Bible features on S21 plus"
```
