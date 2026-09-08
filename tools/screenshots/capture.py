#!/usr/bin/env python3
"""Capture Play Store screenshots and file them into the play/ metadata.

Runs StoreScreenshotTest on Gradle managed devices, then copies the results
into Alkitab/src/<flavor>/play/listings/<language>/graphics/. See
docs/screenshots.md.

Needs ALKITAB_PROPRIETARY_DIR, because store screenshots have to show the
real Bible text rather than the plain flavor's placeholders.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
APP_DIR = REPO_ROOT / "Alkitab"

# AGP pulls a run's files into intermediates and usually, but not always, moves
# them on into outputs, so a run's captures can end up in either. Both are wiped
# before a run and both are searched after it.
#
# The wipe is also what identifies the device: the directory AGP creates is
# named after one fixed managed device whichever one actually ran, so the name
# says nothing, and only "this is what appeared" is trustworthy.
ADDITIONAL_OUTPUTS = (
    APP_DIR / "build" / "intermediates" / "managed_device_android_test_additional_output",
    APP_DIR / "build" / "outputs" / "managed_device_android_test_additional_output",
)

# Play listing language -> the app's own pref_language value (see
# pref_language.xml). A listing whose language the app is not translated into
# has no entry: Play falls back to the default language's graphics for it.
LANGUAGES = {
    "yuku_alkitab": {"id": "in"},
    "yuku_quick_bible": {
        "en-US": "en",
        "de-DE": "de",
        "ja-JP": "ja",
        "ko-KR": "ko",
        "pl-PL": "pl",
        "ru-RU": "ru",
        "zh-CN": "zh-CN",
        "zh-TW": "zh-TW",
    },
    "sabda_alkitab": {"id": "in"},
}

# Managed device -> the graphics directories its captures belong in. Play wants
# at least four screenshots in each large-screen bucket, and accepts the same
# image in both tablet buckets.
DEVICES = {
    "storePhone": ["phone-screenshots"],
    "storeTablet": ["tablet-screenshots", "large-tablet-screenshots"],
}

# Play shows at most 8 screenshots per bucket, and shows them in this order, so
# the strongest screens come first. StoreScreenshotTest captures more than fit;
# whatever is not listed here is captured but not filed.
SHOT_ORDER = [
    "reader",
    "highlights",
    "goto",
    "search",
    "notes",
    "markers",
    "versions",
    "settings",
]


class Fail(Exception):
    pass


def gradle_task(device: str, flavor: str) -> str:
    return f":Alkitab:{device}{flavor[:1].upper()}{flavor[1:]}DebugAndroidTest"


def run_capture(device: str, flavor: str, app_languages: list[str]) -> None:
    for directory in ADDITIONAL_OUTPUTS:
        if directory.exists():
            shutil.rmtree(directory)

    command = [
        str(REPO_ROOT / "gradlew"),
        gradle_task(device, flavor),
        f"-Pandroid.testInstrumentationRunnerArguments.languages={','.join(app_languages)}",
        # Nothing about the build changes between two capture runs, so without
        # this the second one is up to date, writes no files, and leaves the
        # previous device's captures as the only thing on disk.
        "--rerun",
    ]
    print(f"$ {' '.join(command)}")
    result = subprocess.run(command, cwd=REPO_ROOT)
    if result.returncode != 0:
        raise Fail(f"{gradle_task(device, flavor)} failed")


def file_captures(device: str, flavor: str, languages: dict[str, str]) -> None:
    for play_language, app_language in languages.items():
        captured = sorted(
            path
            for directory in ADDITIONAL_OUTPUTS
            for path in directory.glob(f"**/store-screenshots/{app_language}/*.png")
        )
        if not captured:
            raise Fail(f"no screenshots captured for language '{app_language}' on {device}")
        by_id = {path.stem: path for path in captured}

        for bucket in DEVICES[device]:
            target = APP_DIR / "src" / flavor / "play" / "listings" / play_language / "graphics" / bucket
            if target.is_dir():
                shutil.rmtree(target)
            target.mkdir(parents=True)
            index = 0
            for shot in SHOT_ORDER:
                source = by_id.get(shot)
                if source is None:
                    continue
                index += 1
                shutil.copyfile(source, target / f"{index}.png")
            print(f"{play_language}: {bucket} ({index})")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--flavor", required=True, choices=sorted(LANGUAGES))
    parser.add_argument(
        "--devices",
        default=",".join(DEVICES),
        help=f"comma-separated managed devices (default: {','.join(DEVICES)})",
    )
    parser.add_argument(
        "--languages",
        help="comma-separated Play listing languages; defaults to every one the app is translated into",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    try:
        if not os.environ.get("ALKITAB_PROPRIETARY_DIR"):
            raise Fail(
                "ALKITAB_PROPRIETARY_DIR is not set. Store screenshots must show the "
                "real Bible text, so the production flavor needs the overlay."
            )

        languages = LANGUAGES[args.flavor]
        if args.languages:
            wanted = [lang.strip() for lang in args.languages.split(",")]
            unknown = [lang for lang in wanted if lang not in languages]
            if unknown:
                raise Fail(
                    f"{args.flavor} has no app translation for {', '.join(unknown)}; "
                    f"known: {', '.join(sorted(languages))}"
                )
            languages = {lang: languages[lang] for lang in wanted}

        devices = [device.strip() for device in args.devices.split(",")]
        unknown_devices = [device for device in devices if device not in DEVICES]
        if unknown_devices:
            raise Fail(f"unknown device(s): {', '.join(unknown_devices)}")

        for device in devices:
            run_capture(device, args.flavor, sorted(set(languages.values())))
            file_captures(device, args.flavor, languages)
    except Fail as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
