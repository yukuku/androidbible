# AGENTS.md

This file provides guidance to coding agents when working with code in this repository.

## Project Overview

**Bible for Android** (Alkitab / Quick Bible) — a 100% free, open-source Bible reader app for Android. Published on Google Play as "Alkitab" (Indonesian) and "Quick Bible" (non-Indonesian). The codebase supports multiple product flavors, 100+ downloadable Bible versions, song books, devotions, reading plans, cloud sync, and a daily verse widget.

- Official site: https://alkitab.app
- Developer docs: https://alkitab.app/developer

## Branches

**`develop` is the main integration branch. Base all work on `develop` and target every PR at `develop`.** Use short-lived release branches when needed and tags to identify beta and production releases. The retired `master` branch is preserved by the `archive/master` tag.

## Task completion and communication

### Explain agent-made choices in the final response

At the end of a task, explain the consequential choices you made that the user did not explicitly specify. Do this by default, without waiting for the user to ask. Distinguish the user’s requirements from your assumptions and decisions, and briefly explain why you chose them and what they mean for the resulting behavior.

Focus on choices the user would reasonably want to review, such as how you interpreted loose requirements, UI placement and interactions, defaults, persistence, scope, and tradeoffs. Scale the detail to the ambiguity and impact of the task. Do not bury these choices in a list of changed files or a chronological work log, and do not enumerate routine implementation details. If there were no consequential choices beyond the request, do not invent any.

Include the outcome, relevant validation, and remaining limitations alongside these decisions. This reporting requirement does not create an additional approval step for work already authorized.

### Task context and pull requests

When the agent runs in a cloud environment or the user assigns a task in a dedicated Git worktree, finish the task by committing the changes, pushing its task branch to `yukuku/androidbible`, and creating or updating a pull request targeting `develop`, unless the user explicitly requests otherwise. The cloud-environment requirement applies even when the checkout is not a dedicated worktree. Creating or updating the PR is part of completing the task; do not ask for separate permission.

When the agent runs locally and the user assigns a task in a regular checkout rather than a dedicated worktree, treat it as interactive collaboration: the user and agents may be editing the code together. Work in the existing checkout. A PR is not required, and do not create a new worktree unless the user explicitly asks. Do not automatically commit or push shared edits merely to finish the task.

When continuing work on an existing PR, update that PR rather than opening another one. The following guidance applies when creating or updating a PR:

Use a branch in this repository so the PR triggers GitHub Actions to build signed release APKs and publish APK preview links. See [the CI and preview documentation](docs/build-system.md#cicd).

- Run the checks appropriate to the changes before pushing; CI supplements local verification.
- When continuing a task that already has an open PR, push updates to its branch and update its description as needed.
- Return the PR link and the observed CI status to the user.
- Leave merging the PR and publishing a production release to an explicit user request.
- Write PR descriptions for a reviewer who has not followed the conversation. Describe the problem, resulting behavior, relevant implementation details, and validation. Omit the agent's work diary, temporary computer problems, process restarts, and environment troubleshooting unless they are necessary to reproduce the issue or explain a remaining verification limitation. Validation should state what was tested, the result, and any material coverage limits.

Read-only analysis and advice do not require a PR.

## Build Commands

For a new beta build, follow [Build a new beta through GitHub CI](docs/build-system.md#build-a-new-beta-through-github-ci).
Run `bumpBetaNumber` locally, commit and push `version.properties`, then dispatch
`release.yml` with `gh workflow run` and `stage=beta`. Build the signed APKs and
AABs on GitHub CI. A beta metadata bump does not require local APK/AAB builds or
unit tests. Use `dry_run=true` when publishing is not requested; use
`dry_run=false` when a GitHub pre-release is requested. Google Play publishing
is a separate action.

```bash
# Debug APK (open-source build, works out of the box)
./gradlew assemblePlainDebug

# Debug App Bundle
./gradlew bundlePlainDebug

# Unit tests (same as CI)
./gradlew testPlainDebugUnitTest testPlainReleaseUnitTest

# Run a single test class
./gradlew testPlainDebugUnitTest --tests "yuku.alkitab.base.util.QueryTokenizerTest"

# Run a single test method
./gradlew testPlainDebugUnitTest --tests "yuku.alkitab.base.util.QueryTokenizerTest.testQuotedPhrases"
```

**Requirements**: JDK 21 and the Android SDK/NDK versions in `gradle/libs.versions.toml`. The NDK is required for Snappy.

**Versioning**: `versionName` comes from `version.properties` (`versionBase` +
`betaNumber`) combined with a stage: `dev` by default (`5.0.0-dev.<commits
since versionBase last changed>`), `beta` via `-PversionStage=beta`
(`5.0.0-beta.<n>`), `release` via `-PversionStage=release` (`5.0.0`). `versionCode` is derived from wall-clock
time and is unrelated to the stage. Never hardcode a version in
`Alkitab/build.gradle.kts`; see the "Versioning" section of
`docs/build-system.md`.

The `plain` flavor is the open-source development build and works out of the box with the placeholder `Alkitab/google-services.json` checked into the repo (Firebase features won't function at runtime, but the app builds and runs). Production flavors (`yuku_alkitab`, `yuku_quick_bible`, `sabda_alkitab`) require:
- `$ALKITAB_PROPRIETARY_DIR/overlay/<applicationId>/text_raw/` — proprietary Bible text
- `$ALKITAB_PROPRIETARY_DIR/google-services.json` — real Firebase config (one file with client entries for all production applicationIds)
- Signing-key env vars: `SIGN_KEYSTORE`, `SIGN_ALIAS`, `SIGN_PASSWORD`. The `sabda_alkitab` flavor has its own upload key and needs `SIGN_SABDA_KEYSTORE`, `SIGN_SABDA_ALIAS`, `SIGN_SABDA_PASSWORD` instead (there is no fallback to `SIGN_*`)

With those set, build with a plain `./gradlew assembleYuku_alkitabRelease` (or any other production flavor).

### Environment setup

Use the installed JDK and Android SDK when available. Check `java -version`, and read SDK/NDK requirements from `gradle/libs.versions.toml`. Set `ANDROID_HOME` or `sdk.dir` in the untracked `local.properties` file. For a VM without these tools, see [Build Environment Setup](docs/build-environment.md).

Code changes should be verified with a local `./gradlew assemblePlainDebug` (and relevant `testPlainDebugUnitTest` invocations) before committing — do not treat the GitHub Actions run as the first build.

## Architecture and documentation

Start with the [documentation index](docs/README.md). [Architecture](docs/architecture.md)
owns module boundaries, application services, ARI addressing, UI architecture,
and localization. [Storage](docs/storage.md) owns persistence and migration
constraints; [feature guides](docs/README.md#feature-guides) describe capabilities.

Desktop converters share app-library source roots through
`tools/common-jvm/common-jvm.iml`; keep those roots synchronized when moving shared
code. See [Tools](docs/tools.md). Parser tests and their golden fixture belong to
the app test suite; see [parser compatibility tests](Alkitab/src/test/desktop-verse-parser.md).

## Code Conventions

- **Use `kotlinx.serialization` for JSON parsing and serialization in app code.** Deserialize fixed JSON schemas into `@Serializable` data classes, using an explicit `@SerialName` on every serialized property, even when the Kotlin property name matches the wire name. Reserve JSON-tree parsing for genuinely dynamic structures. Do not introduce new Gson usages or extend Gson-based implementations. Migrate the JSON handling being changed to kotlinx.serialization, while keeping unrelated legacy Gson code outside the task's scope.

- **Do not introduce `!!` in production code.** Handle nullable values explicitly. For nullable observable or delegated state, read a local snapshot and use a null check so Kotlin can smart-cast it.
- **Separate logical sections of long function bodies with blank lines.** Group related operations and make transitions between setup, state loading, rendering, and callbacks easy to scan.

- **IMPORTANT. Never write change-narrating comments.** Code comments must describe the code as it is *now*, not the history of how it got there. Do not reference past revisions, removed approaches, or the act of editing: no "previously", "used to", "formerly", "earlier iterations", "an earlier version", "we changed/renamed/moved this from…", "now uses…", "no longer…", and the like. Git history is the record of *change*; the code and its comments describe the *present*. When explaining why the current design was chosen over an alternative is genuinely useful, state it as present-tense rationale about the alternative ("the cluster is centered with Spacers because a weighted slot would clip the label"), not as a story about what the code used to do. This applies to all comments, KDoc/Javadoc, and commit-adjacent code. Keep the narrative of change out of the source. The same rule applies to milestone labels: do not tag comments with the sprint or task that produced the code ("(M3)", "Out of scope for M4"), because they date instantly and mean nothing to a later reader.
- **IMPORTANT. Never use em dashes to join clauses in comments or docs.** The "—" character (U+2014) is hard to read in running prose. Write two plain sentences instead, or use a comma, colon, semicolon, or parentheses, whichever reads most naturally. This applies to code comments, KDoc/Javadoc, Markdown docs, commit messages, and PR descriptions. (Existing prose in older files may still contain them; leave it alone unless you are already editing that comment or were asked to sweep the file.)
- **IMPORTANT. Do not add explaining comments except for non-obvious code.** Default to writing no comment. A comment earns its place only by explaining a non-obvious *why*: a hidden constraint, a subtle invariant, a threading or lifecycle rule, a workaround for a specific bug, or behavior that would surprise a reader. Never restate what a well-named symbol already says, never narrate the happy path line by line, and never write multi-paragraph rationale where one or two sentences do the job. Before adding a new comment, check whether its explanation is needed to understand the code.
- **Preserve useful existing comments.** Keep comment changes within the task's scope. "Default to no comments" governs adding new comments; it is not an instruction to remove existing comments throughout a touched file. Preserve comments that explain non-obvious constraints, invariants, lifecycle requirements, or design decisions. When changing code makes a comment inaccurate, update its useful explanation or remove it if it no longer applies. Do not remove useful context merely to shorten a diff.
- **Use `is` prefixes for Boolean names.** Name new Boolean variables, properties, and parameters with an `is` prefix, such as `isBottom`, `isToolbarAtBottom`, `isSelectionActive`, and `isFinished`. Apply this consistently to production code and tests. Preserve names required by overridden APIs or external contracts. Avoid unrelated renaming across the repository unless requested.
- **A Compose `modifier: Modifier = Modifier` parameter is an exception to any "drop the unused default" cleanup.** Keep it on every composable that emits UI, even when no call site passes one and the default is the only value it ever takes. It is the Compose convention, callers expect to be able to size, pad or position a composable without editing it, and a composable that has to be reopened to accept a modifier is a worse API than one carrying an unused parameter. This applies to the parameter and its default together: leave both in place.
- Mixed Java/Kotlin codebase (Kotlin preferred for new code, many files still Java)
- JVM toolchain 21 across all modules
- No obfuscation in ProGuard (`-dontobfuscate`), only shrinking
- EditorConfig enforces a single alphabetical import layout (`ij_kotlin_imports_layout=*`, no `java.*`/`kotlin.*`/static exceptions) and disables `no-wildcard-imports` for Kotlin
- Preference keys are defined as enum entries in `Prefkey.kt`
- GIDs (globally unique IDs) are used alongside database `_id` for sync-capable entities
- `Ari` encoding is used universally for verse references — never store book/chapter/verse separately
- Version IDs follow format `"internal"`, `"preset/[name]"`, or `"file/[path]"`
- **IMPORTANT. The app has its own in-app language setting (`pref_language`), independent of the device locale.** Only activities pick it up automatically: `BaseActivity.attachBaseContext` runs the context through `ConfigurationWrapper.wrap`, so anything resolving strings against an activity (including Compose `stringResource`, whose `LocalContext` is the activity) is already correct. Everything else is not. A `Service`, a `BroadcastReceiver`, a `Worker`, `App.context`, or any `applicationContext` resolves against the *device* locale, so `getString` there prints the wrong language for every user whose in-app choice differs from their device. When such a component produces user-visible text, resolve it against `ConfigurationWrapper.localizedContext(...)` instead. Cache that context rather than rebuilding it per string, and rebuild when `ConfigurationWrapper.getSerialCounter()` changes, which is how a language change is signalled. Prefer keeping the string resource unresolved (a res id plus args) until it reaches whatever component knows the right context — see `AudioLogMessage`.

## Unit Testing

- **Prefer Kotlin backtick identifiers with descriptive sentences** for test function names — they render as readable prose in JUnit output. Use full sentences, not short names.
  ```kotlin
  // Good
  @Test
  fun `patchNoConflict treats add on an existing gid as an overwrite (same as mod)`() { ... }

  // Avoid
  @Test
  fun patchNoConflict_addExistingGid_overwrites() { ... }
  ```
- **Use Robolectric if needed** for tests that exercise Android framework code (Context, Intents, Parcelable, DB helpers, etc.). Pure-logic tests should stay plain JUnit. If an Android method is blocking a pure-logic test with `"Method not mocked"`, first consider whether a tiny test-scope shadow (e.g. `src/test/java/android/util/Pair.java`) is enough before reaching for Robolectric.
- When writing unit tests, assume the production code is correct. If you spot what looks like an obvious bug while writing tests, flag it rather than silently working around it.

## Documentation Conventions

- **Do not include SHAs, commit hashes, line numbers, line counts, or library version numbers** in long-lived docs (`docs/tech-debt-remediation.md`, its per-item REM records, this `AGENTS.md`, etc.). They rot on rebase/squash-merge, refactors, and dependency bumps, and are fragile to maintain. Refer to code by symbol name (class/method), and point at `gradle/libs.versions.toml` instead of naming dependency versions. When recording that a step is done, describe what shipped (file paths, test counts, scope) and use an absolute date, never a SHA. (Older entries in these docs may still reference SHAs; leave them alone unless explicitly asked to clean up.)

## Important Caveats

- `IsiActivity.kt` is still large, but no longer monolithic: gesture handling, the action-mode callback, and split-view management were extracted (REM-06/07/08) into `ReaderGestureHandler`, `VerseActionModeController`, and `SplitViewManager` under `widget/`. Bible reading, navigation history, and volume-button handling are still inline; changes here require careful testing.
- `yuku.kpri.model.Song` is the legacy song decoder’s target type. Song storage uses Room with JSON `SongDocument` payloads; legacy Parcelable rows are converted lazily on first read. Preserve stored class-name compatibility. See `docs/modules/songs.md` and `docs/features/portable-songs/design.md`. Backend redirects and `kidung-data` authoring belong to separate repositories.
- The `Snappy` module has native C++ code — NDK must be installed for builds.
- A placeholder `Alkitab/google-services.json` is checked in so `plainDebug` works out of the box; Firebase features won't actually function with it. For production flavors, the real `google-services.json` is sourced from `$ALKITAB_PROPRIETARY_DIR/google-services.json` at build time and copied into the gitignored `Alkitab/src/<flavor>/google-services.json` (where the GMS plugin's source-set lookup finds it).
- The internal Bible version data is split per flavor: the placeholder `ddd_*` files live under `Alkitab/src/plain/assets/internal/` (used by the `plain` open-source build only). Production flavors get their `tb_*`/`kjv_*` files copied from the proprietary overlay into `build/generated/proprietaryAssets/<flavor>/internal/` at build time.
- **Backend host:** `BuildConfig.SERVER_HOST` resolves to `https://api.alkitab.app` (defined in `Alkitab/build.gradle.kts`), **not** the marketing site at `alkitab.app`. The two hostnames are different origins behind separate Cloudflare cache zones — when reproducing a backend issue with `curl`, hit `api.alkitab.app/...` to match what the app actually requests.
