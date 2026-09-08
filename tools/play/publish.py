#!/usr/bin/env python3
"""Google Play publishing for the production flavors.

Run by hand, never from CI. See docs/play-publishing.md.

Store metadata lives in Alkitab/src/<flavor>/play/ in the layout Gradle
Play Publisher uses, with Alkitab/src/main/play/ as a shared base that a
flavor overrides file by file.

Credentials come from a service-account JSON, one variable per Play
developer account:

  PLAY_SERVICE_ACCOUNT_JSON_YUKU   yuku_alkitab, yuku_quick_bible
  PLAY_SERVICE_ACCOUNT_JSON_SABDA  sabda_alkitab

Either may hold the JSON itself or a path to the key file.

Commands:
  status    print each track and the releases on it
  pull      download the live listing into the flavor's play/ directory
  push      upload the flavor's play/ directory to the listing
  upload    upload an AAB to a track
  promote   move an existing release to another track
"""

from __future__ import annotations

import argparse
import contextlib
import json
import os
import shutil
import sys
import urllib.request
from dataclasses import dataclass
from pathlib import Path

try:
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload
except ImportError:
    sys.exit(
        "Missing dependencies. Create a venv and install them:\n"
        "  python3 -m venv tools/play/.venv\n"
        "  tools/play/.venv/bin/pip install -r tools/play/requirements.txt\n"
        "then run this script with tools/play/.venv/bin/python."
    )

REPO_ROOT = Path(__file__).resolve().parents[2]
APP_DIR = REPO_ROOT / "Alkitab"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


@dataclass(frozen=True)
class Flavor:
    package: str
    credential_env: str


FLAVORS = {
    "yuku_alkitab": Flavor("yuku.alkitab", "PLAY_SERVICE_ACCOUNT_JSON_YUKU"),
    "yuku_quick_bible": Flavor("yuku.alkitab.kjv", "PLAY_SERVICE_ACCOUNT_JSON_YUKU"),
    "sabda_alkitab": Flavor("org.sabda.alkitab", "PLAY_SERVICE_ACCOUNT_JSON_SABDA"),
}

# Local directory name under listings/<language>/graphics/ -> Play imageType.
GRAPHICS = {
    "icon": "icon",
    "feature-graphic": "featureGraphic",
    "tv-banner": "tvBanner",
    "phone-screenshots": "phoneScreenshots",
    "tablet-screenshots": "sevenInchScreenshots",
    "large-tablet-screenshots": "tenInchScreenshots",
    "tv-screenshots": "tvScreenshots",
    "wear-screenshots": "wearScreenshots",
}

# Local file name under listings/<language>/ -> (Play field, character limit).
LISTING_FIELDS = {
    "title.txt": ("title", 30),
    "short-description.txt": ("shortDescription", 80),
    "full-description.txt": ("fullDescription", 4000),
    "video-url.txt": ("video", None),
}

# Local file name at the root of play/ -> Play field on the app details.
DETAILS_FIELDS = {
    "contact-email.txt": "contactEmail",
    "contact-phone.txt": "contactPhone",
    "contact-website.txt": "contactWebsite",
    "default-language.txt": "defaultLanguage",
}

RELEASE_NOTES_LIMIT = 500
COMMON_TRACKS = ("internal", "alpha", "beta", "production")


class Fail(Exception):
    pass


def play_dirs(flavor: str) -> list[Path]:
    """Metadata source sets, least to most specific."""
    return [APP_DIR / "src" / "main" / "play", APP_DIR / "src" / flavor / "play"]


def find_file(flavor: str, *parts: str) -> Path | None:
    found = None
    for base in play_dirs(flavor):
        candidate = base.joinpath(*parts)
        if candidate.is_file():
            found = candidate
    return found


def find_dir(flavor: str, *parts: str) -> Path | None:
    found = None
    for base in play_dirs(flavor):
        candidate = base.joinpath(*parts)
        if candidate.is_dir():
            found = candidate
    return found


def subdir_names(flavor: str, *parts: str) -> list[str]:
    names: set[str] = set()
    for base in play_dirs(flavor):
        parent = base.joinpath(*parts)
        if parent.is_dir():
            names.update(p.name for p in parent.iterdir() if p.is_dir())
    return sorted(names)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8").strip()


def load_credentials(flavor: str):
    env = FLAVORS[flavor].credential_env
    raw = (os.environ.get(env) or "").strip()
    if not raw:
        raise Fail(
            f"{env} is not set. It must hold the service-account JSON for "
            f"{FLAVORS[flavor].package}, or a path to the key file."
        )
    if raw.startswith("{"):
        info = json.loads(raw)
    else:
        path = Path(raw).expanduser()
        if not path.is_file():
            raise Fail(f"{env} points at a missing file: {path}")
        info = json.loads(path.read_text(encoding="utf-8"))
    return service_account.Credentials.from_service_account_info(info, scopes=[SCOPE])


def play_service(flavor: str):
    return build(
        "androidpublisher",
        "v3",
        credentials=load_credentials(flavor),
        cache_discovery=False,
    )


@contextlib.contextmanager
def play_edit(service, package: str, *, commit: bool, send_for_review: bool = True):
    """Open a Play edit, committing it only on a clean exit.

    An abandoned edit blocks nothing, but leaving one open means the next
    run inherits half-applied changes, so any failure deletes it.
    """
    edit_id = service.edits().insert(packageName=package, body={}).execute()["id"]
    committed = False
    try:
        yield edit_id
        if commit:
            service.edits().commit(
                packageName=package,
                editId=edit_id,
                changesNotSentForReview=not send_for_review,
            ).execute()
            committed = True
    finally:
        if not committed:
            with contextlib.suppress(Exception):
                service.edits().delete(packageName=package, editId=edit_id).execute()


def image_mime(path: Path) -> str:
    return "image/png" if path.suffix.lower() == ".png" else "image/jpeg"


def release_notes(flavor: str, track: str) -> list[dict]:
    notes = []
    for language in subdir_names(flavor, "release-notes"):
        path = find_file(flavor, "release-notes", language, f"{track}.txt") or find_file(
            flavor, "release-notes", language, "default.txt"
        )
        if path is None:
            continue
        text = read_text(path)
        if len(text) > RELEASE_NOTES_LIMIT:
            raise Fail(
                f"{path} is {len(text)} characters; Play allows {RELEASE_NOTES_LIMIT}."
            )
        notes.append({"language": language, "text": text})
    return notes


def build_release(flavor: str, track: str, version_codes: list[int], args) -> dict:
    release: dict = {
        "versionCodes": [str(code) for code in version_codes],
        "status": args.status,
    }
    if args.rollout is not None:
        release["userFraction"] = args.rollout
    notes = release_notes(flavor, track)
    if notes:
        release["releaseNotes"] = notes
    name_file = find_file(flavor, "release-names", f"{track}.txt") or find_file(
        flavor, "release-names", "default.txt"
    )
    if name_file is not None:
        release["name"] = read_text(name_file)
    return release


def cmd_status(args) -> None:
    service = play_service(args.flavor)
    package = FLAVORS[args.flavor].package
    with play_edit(service, package, commit=False) as edit_id:
        tracks = (
            service.edits()
            .tracks()
            .list(packageName=package, editId=edit_id)
            .execute()
            .get("tracks", [])
        )
    print(f"{package}")
    for track in tracks:
        print(f"  {track['track']}")
        for release in track.get("releases", []):
            codes = ", ".join(release.get("versionCodes", []) or ["-"])
            line = f"    {release.get('status', '?'):11} versionCodes {codes}"
            if "userFraction" in release:
                line += f"  rollout {release['userFraction']:.0%}"
            if "name" in release:
                line += f"  ({release['name']})"
            print(line)


def cmd_pull(args) -> None:
    service = play_service(args.flavor)
    package = FLAVORS[args.flavor].package
    target = APP_DIR / "src" / args.flavor / "play"
    with play_edit(service, package, commit=False) as edit_id:
        details = (
            service.edits().details().get(packageName=package, editId=edit_id).execute()
        )
        target.mkdir(parents=True, exist_ok=True)
        for filename, field in DETAILS_FIELDS.items():
            value = details.get(field)
            if value:
                (target / filename).write_text(f"{value}\n", encoding="utf-8")

        listings = (
            service.edits()
            .listings()
            .list(packageName=package, editId=edit_id)
            .execute()
            .get("listings", [])
        )
        for listing in listings:
            language = listing["language"]
            listing_dir = target / "listings" / language
            listing_dir.mkdir(parents=True, exist_ok=True)
            for filename, (field, _) in LISTING_FIELDS.items():
                value = listing.get(field)
                if value:
                    (listing_dir / filename).write_text(f"{value}\n", encoding="utf-8")
            print(f"{language}: listing text")
            pull_graphics(service, package, edit_id, language, listing_dir / "graphics")


def pull_graphics(service, package, edit_id, language, graphics_dir: Path) -> None:
    for local_name, image_type in GRAPHICS.items():
        images = (
            service.edits()
            .images()
            .list(
                packageName=package,
                editId=edit_id,
                language=language,
                imageType=image_type,
            )
            .execute()
            .get("images", [])
        )
        image_dir = graphics_dir / local_name
        if not images:
            continue
        if image_dir.is_dir():
            shutil.rmtree(image_dir)
        image_dir.mkdir(parents=True)
        for index, image in enumerate(images, start=1):
            # The listing API hands back a thumbnail URL; "=s0" asks the
            # image host for the original, unresized upload.
            with urllib.request.urlopen(image["url"] + "=s0") as response:
                data = response.read()
            suffix = ".png" if data.startswith(b"\x89PNG") else ".jpg"
            (image_dir / f"{index}{suffix}").write_bytes(data)
        print(f"{language}: {local_name} ({len(images)})")


def cmd_push(args) -> None:
    service = play_service(args.flavor)
    package = FLAVORS[args.flavor].package
    languages = subdir_names(args.flavor, "listings")
    if not languages:
        raise Fail(
            f"No listings found under {APP_DIR / 'src' / args.flavor / 'play'}/listings/. "
            f"Run `pull` first to bootstrap from the live listing."
        )

    with play_edit(
        service, package, commit=not args.dry_run, send_for_review=not args.no_review
    ) as edit_id:
        details = {}
        for filename, field in DETAILS_FIELDS.items():
            path = find_file(args.flavor, filename)
            if path is not None:
                details[field] = read_text(path)
        if details:
            service.edits().details().update(
                packageName=package, editId=edit_id, body=details
            ).execute()
            print(f"details: {', '.join(sorted(details))}")

        for language in languages:
            push_listing(service, package, edit_id, args.flavor, language)
            push_graphics(service, package, edit_id, args.flavor, language)

    print("dry run, nothing committed" if args.dry_run else "committed")


def push_listing(service, package, edit_id, flavor: str, language: str) -> None:
    body = {"language": language}
    for filename, (field, limit) in LISTING_FIELDS.items():
        path = find_file(flavor, "listings", language, filename)
        if path is None:
            continue
        text = read_text(path)
        if limit is not None and len(text) > limit:
            raise Fail(f"{path} is {len(text)} characters; Play allows {limit}.")
        body[field] = text
    if len(body) == 1:
        return
    service.edits().listings().update(
        packageName=package, editId=edit_id, language=language, body=body
    ).execute()
    print(f"{language}: listing text")


def push_graphics(service, package, edit_id, flavor: str, language: str) -> None:
    for local_name, image_type in GRAPHICS.items():
        image_dir = find_dir(flavor, "listings", language, "graphics", local_name)
        if image_dir is None:
            continue
        files = sorted(p for p in image_dir.iterdir() if p.is_file())
        if not files:
            continue
        # Play has no per-image update, so a media type is replaced wholesale.
        service.edits().images().deleteall(
            packageName=package, editId=edit_id, language=language, imageType=image_type
        ).execute()
        for path in files:
            service.edits().images().upload(
                packageName=package,
                editId=edit_id,
                language=language,
                imageType=image_type,
                media_body=MediaFileUpload(str(path), mimetype=image_mime(path)),
            ).execute()
        print(f"{language}: {local_name} ({len(files)})")


def resolve_aab(args) -> Path:
    if args.aab:
        path = Path(args.aab)
        if not path.is_file():
            raise Fail(f"No such bundle: {path}")
        return path
    bundle_dir = APP_DIR / "build" / "outputs" / "bundle" / f"{args.flavor}Release"
    candidates = sorted(bundle_dir.glob("*.aab"))
    if not candidates:
        raise Fail(
            f"No .aab in {bundle_dir}. Build one with "
            f"`./gradlew bundle{args.flavor[:1].upper()}{args.flavor[1:]}Release`, "
            f"or pass --aab."
        )
    if len(candidates) > 1:
        raise Fail(f"More than one .aab in {bundle_dir}; pass --aab to choose.")
    return candidates[0]


def cmd_upload(args) -> None:
    aab = resolve_aab(args)
    service = play_service(args.flavor)
    package = FLAVORS[args.flavor].package

    with play_edit(
        service, package, commit=not args.dry_run, send_for_review=not args.no_review
    ) as edit_id:
        result = (
            service.edits()
            .bundles()
            .upload(
                packageName=package,
                editId=edit_id,
                media_body=MediaFileUpload(
                    str(aab), mimetype="application/octet-stream", resumable=True
                ),
            )
            .execute()
        )
        version_code = int(result["versionCode"])
        print(f"uploaded {aab.name} as versionCode {version_code}")

        upload_symbols(service, package, edit_id, args.flavor, version_code)

        release = build_release(args.flavor, args.track, [version_code], args)
        service.edits().tracks().update(
            packageName=package,
            editId=edit_id,
            track=args.track,
            body={"track": args.track, "releases": [release]},
        ).execute()
        print(f"assigned to {args.track} as {args.status}")

    print("dry run, nothing committed" if args.dry_run else "committed")


def upload_symbols(service, package, edit_id, flavor: str, version_code: int) -> None:
    variant = f"{flavor}Release"
    artifacts = {
        "proguard": APP_DIR / "build" / "outputs" / "mapping" / variant / "mapping.txt",
        "nativeCode": APP_DIR
        / "build"
        / "outputs"
        / "native-debug-symbols"
        / variant
        / "native-debug-symbols.zip",
    }
    for file_type, path in artifacts.items():
        if not path.is_file():
            continue
        service.edits().deobfuscationfiles().upload(
            packageName=package,
            editId=edit_id,
            apkVersionCode=version_code,
            deobfuscationFileType=file_type,
            media_body=MediaFileUpload(str(path), mimetype="application/octet-stream"),
        ).execute()
        print(f"uploaded {file_type} symbols from {path.name}")


def cmd_promote(args) -> None:
    service = play_service(args.flavor)
    package = FLAVORS[args.flavor].package

    with play_edit(
        service, package, commit=not args.dry_run, send_for_review=not args.no_review
    ) as edit_id:
        source = (
            service.edits()
            .tracks()
            .get(packageName=package, editId=edit_id, track=args.source)
            .execute()
        )
        releases = [r for r in source.get("releases", []) if r.get("versionCodes")]
        if not releases:
            raise Fail(f"Track '{args.source}' has no release with a versionCode.")
        if len(releases) > 1:
            raise Fail(
                f"Track '{args.source}' has {len(releases)} releases; "
                f"promote from a track with exactly one."
            )
        version_codes = [int(code) for code in releases[0]["versionCodes"]]

        # Release notes are re-read for the destination track, so a
        # track-specific file wins over the one the source release carried.
        release = build_release(args.flavor, args.track, version_codes, args)
        service.edits().tracks().update(
            packageName=package,
            editId=edit_id,
            track=args.track,
            body={"track": args.track, "releases": [release]},
        ).execute()
        codes = ", ".join(str(code) for code in version_codes)
        print(f"promoted versionCode {codes} from {args.source} to {args.track}")

    print("dry run, nothing committed" if args.dry_run else "committed")


def add_release_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--track",
        required=True,
        help=f"destination track, e.g. {', '.join(COMMON_TRACKS)}",
    )
    parser.add_argument(
        "--status",
        default="completed",
        choices=("completed", "draft", "inProgress", "halted"),
        help="release status (default: completed)",
    )
    parser.add_argument(
        "--rollout",
        type=float,
        help="staged rollout fraction between 0 and 1; requires --status inProgress",
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--flavor", required=True, choices=sorted(FLAVORS))
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="send everything to Play for validation, then discard the edit",
    )
    parser.add_argument(
        "--no-review",
        action="store_true",
        help="commit without sending the changes for review",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("status", help="print each track and its releases")
    subparsers.add_parser("pull", help="download the live listing into play/")
    subparsers.add_parser("push", help="upload play/ to the live listing")

    upload = subparsers.add_parser("upload", help="upload an AAB to a track")
    upload.add_argument("--aab", help="path to the bundle (default: the Gradle output)")
    add_release_arguments(upload)

    promote = subparsers.add_parser("promote", help="move a release to another track")
    promote.add_argument("--source", required=True, help="track to promote from")
    add_release_arguments(promote)

    args = parser.parse_args(argv)
    if args.command in ("upload", "promote"):
        if args.rollout is not None:
            if args.status != "inProgress":
                parser.error("--rollout requires --status inProgress")
            if not 0 < args.rollout <= 1:
                parser.error("--rollout must be greater than 0 and at most 1")
        elif args.status == "inProgress":
            parser.error("--status inProgress requires --rollout")
    return args


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    commands = {
        "status": cmd_status,
        "pull": cmd_pull,
        "push": cmd_push,
        "upload": cmd_upload,
        "promote": cmd_promote,
    }
    try:
        commands[args.command](args)
    except Fail as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
