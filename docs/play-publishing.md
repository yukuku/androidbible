# Google Play Publishing

Releases are pushed to Google Play by hand with `tools/play/publish.py`.
CI never talks to the Play Console: `.github/workflows/android.yml` builds
and signs the AABs and stops there, so nothing reaches the store without
someone running the tool.

The tool wraps the Google Play Developer API v3. It uploads bundles,
assigns them to tracks, promotes releases between tracks, and syncs the
store listing (text and graphics) in both directions.

## One-time setup

### Service accounts

Each Play developer account needs its own service account, so there are
two:

| Flavor | Package | Credential |
|--------|---------|------------|
| `yuku_alkitab` | `yuku.alkitab` | `PLAY_SERVICE_ACCOUNT_JSON_YUKU` |
| `yuku_quick_bible` | `yuku.alkitab.kjv` | `PLAY_SERVICE_ACCOUNT_JSON_YUKU` |
| `sabda_alkitab` | `org.sabda.alkitab` | `PLAY_SERVICE_ACCOUNT_JSON_SABDA` |

For each account:

1. Play Console, **Setup, API access**: link a Google Cloud project.
2. In that Cloud project, create a service account and download a JSON key.
3. Play Console, **Users and permissions**: invite the service account's
   email address, then grant it app-level (not account-level) access:
   *Release to testing tracks*, *Release to production*, and *Manage store
   presence* if you intend to run `push`.

Point the environment variable at the key file (or, if you prefer, at the
JSON itself; the tool accepts either):

```bash
export PLAY_SERVICE_ACCOUNT_JSON_YUKU=~/.config/alkitab/play-yuku.json
export PLAY_SERVICE_ACCOUNT_JSON_SABDA=~/.config/alkitab/play-sabda.json
```

Keep the keys outside the repo. `tools/play/.gitignore` also excludes
`*.json` there as a second line of defence.

### Python environment

```bash
python3 -m venv tools/play/.venv
tools/play/.venv/bin/pip install -r tools/play/requirements.txt
```

Run everything through `tools/play/.venv/bin/python`.

## Store metadata layout

Metadata lives under `Alkitab/src/<flavor>/play/`, in exactly the layout
Gradle Play Publisher expects, so switching to that plugin later needs no
file moves. `Alkitab/src/main/play/` is a shared base and a flavor
overrides it one file at a time (a flavor's `title.txt` wins, while a
`contact-website.txt` that only exists in `main/` is inherited).

```
Alkitab/src/<source set>/play/
├── contact-email.txt
├── contact-phone.txt
├── contact-website.txt
├── default-language.txt
├── listings/
│   └── <language>/                  Play language code, e.g. en-US, in
│       ├── title.txt                30 characters
│       ├── short-description.txt    80 characters
│       ├── full-description.txt     4000 characters
│       ├── video-url.txt
│       └── graphics/
│           ├── icon/                one 512x512 image
│           ├── feature-graphic/     one 1024x500 image
│           ├── tv-banner/
│           ├── phone-screenshots/   up to 8, uploaded in filename order
│           ├── tablet-screenshots/
│           ├── large-tablet-screenshots/
│           ├── tv-screenshots/
│           └── wear-screenshots/
├── release-notes/
│   └── <language>/
│       ├── default.txt              any track without its own file
│       └── <track>.txt              e.g. production.txt, 500 characters
└── release-names/
    ├── default.txt
    └── <track>.txt
```

Don't hand-write the initial listings. Run `pull` once per flavor and let
the live store listing seed the directory:

```bash
tools/play/.venv/bin/python tools/play/publish.py --flavor yuku_alkitab pull
```

`pull` overwrites the listing text and replaces each graphics directory it
finds on Play, so treat it as "reset to what the store has". Release notes
and release names are authored locally and are never pulled.

## Commands

All commands take `--flavor`, plus two global switches:

- `--dry-run` sends every change to Play for validation and then throws the
  edit away. Use it before anything that touches production.
- `--no-review` commits without submitting the changes for review, for
  accounts that need it.

### Inspect

```bash
tools/play/.venv/bin/python tools/play/publish.py --flavor yuku_alkitab status
```

### Upload a build

Build the bundle first, then upload. With no `--aab` the tool picks up the
single `.aab` in `Alkitab/build/outputs/bundle/<flavor>Release/`:

```bash
ALKITAB_PROPRIETARY_DIR=/path/to/proprietary \
SIGN_KEYSTORE=/path/to/keystore SIGN_ALIAS=mykey SIGN_PASSWORD=secret \
BUILD_DIST=market \
./gradlew bundleYuku_alkitabRelease

tools/play/.venv/bin/python tools/play/publish.py \
  --flavor yuku_alkitab upload --track internal
```

To publish an AAB that CI already built, download it from the run and pass
`--aab`:

```bash
gh run download <run-id> --name yuku_alkitab-<tag> --dir /tmp/alkitab-release
tools/play/.venv/bin/python tools/play/publish.py \
  --flavor yuku_alkitab upload --track internal \
  --aab /tmp/alkitab-release/bundle/yuku_alkitabRelease/*.aab
```

`upload` also sends `mapping.txt` and, when present, the native debug
symbols zip, both read from the Gradle output next to the bundle. Native
symbols are only produced if `debugSymbolLevel` is set on the release build
type, which it currently is not; add it to `Alkitab/build.gradle.kts` if
you want Snappy's native frames symbolicated in Play's crash reports.

### Promote

```bash
tools/play/.venv/bin/python tools/play/publish.py \
  --flavor yuku_alkitab promote --source internal --track production \
  --status inProgress --rollout 0.1 --dry-run
```

Release notes are re-read for the destination track, so promoting to
production picks up `release-notes/<language>/production.txt` even though
the internal release shipped with `default.txt`. Play removes the version
from the lower track by itself.

Continue a staged rollout by re-running `promote` on the same track with a
larger `--rollout`, or with `--status completed` to go to everyone.

### Push listing changes

```bash
tools/play/.venv/bin/python tools/play/publish.py --flavor yuku_alkitab push --dry-run
```

A graphics directory is replaced wholesale (Play has no per-image update),
but only if it exists locally. A media type with no local directory is left
untouched on Play.

## Caveats

- **Target API floor.** Play refuses updates below its current target API
  requirement, which rises every August. `targetSdk` is set in
  `Alkitab/build.gradle.kts`; check the requirement in the Play Console
  before a release, because the upload fails validation rather than warning.
- **Signing key.** The bundle must be signed with each app's Play *upload*
  key. `yuku_alkitab` and `yuku_quick_bible` share the keystore from
  `SIGN_KEYSTORE`, so that key has to be the upload key registered for both;
  `sabda_alkitab` uses its own from `SIGN_SABDA_KEYSTORE`. See "Signing keys"
  in [Build System](build-system.md).
- **versionCode.** Derived from wall-clock time in
  `Alkitab/build.gradle.kts`, so it always increases, and all three flavors
  share the value. That is fine: they are three separate Play apps.
- **First release.** An app's very first release cannot be created through
  the API. Irrelevant for these three, which all already ship.
- **`plain`.** The open-source flavor has no Play listing and is not
  publishable.
