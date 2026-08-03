#!/usr/bin/env python3
"""Print the PR preview comment body (markdown) to stdout.

Reads build-info.json written by make_dist.py and $PREVIEW_URL, the
workers.dev URL of the version wrangler just uploaded. The leading HTML
marker is how the workflow finds its own earlier comment to update in
place.
"""

from __future__ import annotations

import json
import os
import urllib.parse
from pathlib import Path

MARKER = "<!-- alkitab-pr-apk-preview -->"


def main() -> None:
    info = json.loads((Path(__file__).resolve().parent / "build-info.json").read_text())
    url = os.environ["PREVIEW_URL"].rstrip("/")

    lines = [
        MARKER,
        "### 📱 Preview builds",
        "",
        f"Signed release builds of `{info['headSha'][:7]}` "
        f"— version {info['versionName']} ({info['versionCode']}) "
        f"— from [this run]({info['runUrl']}).",
        "",
        "| Flavor | Application ID | APK |",
        "| --- | --- | --- |",
    ]
    for apk in info["apks"]:
        href = f"{url}/{urllib.parse.quote(apk['file'])}"
        lines.append(
            f"| {apk['label']} | `{apk['applicationId']}` "
            f"| [Download]({href}) ({apk['size']}) |"
        )
    lines += [
        "",
        f"Or open {url} on an Android device.",
        "",
        "These share their application IDs and signature with the Play Store "
        "builds, so installing one replaces the corresponding installed app "
        "(data is kept).",
        "",
        "This comment tracks the latest build for this PR; earlier builds keep "
        "their own URLs.",
    ]
    print("\n".join(lines))


if __name__ == "__main__":
    main()
