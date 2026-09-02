#!/usr/bin/env python3
"""Stage dist/ for the PR-preview Cloudflare Worker.

Copies every production-flavor release APK into dist/ and generates an
index.html download page listing them. Also writes build-info.json beside
dist/ (not inside it, so it is not served) for render_comment.py.

APK_DIR is expected to hold one directory per flavor, each containing a
release/ directory with the APK and AGP's output-metadata.json — the
layout actions/upload-artifact produces from
Alkitab/build/outputs/apk/<flavor>/release/. Environment variables, all
set by the pr-apk-preview job in .github/workflows/android.yml:

  APK_DIR    directory holding the per-flavor APK directories
  PR_NUMBER  pull request number
  PR_TITLE   pull request title (HTML-escaped before use)
  PR_URL     pull request page on GitHub
  HEAD_SHA   PR head commit (not the merge commit)
  BRANCH     PR head branch name
  RUN_URL    the workflow run that produced this build
"""

from __future__ import annotations

import html
import json
import os
import shutil
import sys
import urllib.parse
from pathlib import Path

# Display names for the production flavors. An unlisted flavor falls back
# to its Gradle name, so adding a flavor cannot break the build.
FLAVOR_LABELS = {
    "yuku_alkitab": "Alkitab",
    "yuku_quick_bible": "Quick Bible",
    "sabda_alkitab": "Sabda Alkitab",
}

# Gradle's outputFileName (Alkitab/build.gradle.kts) always starts every
# flavor's APK with the literal "Alkitab" prefix, so the three production
# downloads are otherwise indistinguishable by filename alone. Swap it here
# for the per-flavor prefix instead of touching the shared Gradle naming,
# which other consumers (local builds, CI artifact names) still rely on.
FLAVOR_APK_PREFIXES = {
    "yuku_alkitab": "Alkitab",
    "yuku_quick_bible": "QuickBible",
    "sabda_alkitab": "SabdaAlkitab",
}
GRADLE_APK_PREFIX = "Alkitab"

PAGE = """<!doctype html>
<html lang="en">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Alkitab PR #{pr_number} preview builds</title>
<style>
  :root {{ color-scheme: light dark; }}
  body {{
    font-family: system-ui, sans-serif;
    max-width: 42rem;
    margin: 3rem auto;
    padding: 0 1rem;
    line-height: 1.5;
  }}
  .flavor {{
    border: 1px solid color-mix(in srgb, currentColor 20%, transparent);
    border-radius: .75rem;
    padding: 1rem 1.25rem;
    margin-bottom: 1rem;
  }}
  .flavor h2 {{ margin: 0 0 .25rem; font-size: 1.1rem; }}
  .appid {{
    font-size: .85rem;
    color: color-mix(in srgb, currentColor 60%, transparent);
  }}
  .download {{
    display: inline-block;
    margin-top: .75rem;
    padding: .6rem 1.25rem;
    border-radius: .5rem;
    background: #1a73e8;
    color: #fff;
    text-decoration: none;
    font-weight: 600;
  }}
  table {{ border-collapse: collapse; margin-top: 1.5rem; }}
  td {{ padding: .25rem .75rem .25rem 0; vertical-align: top; }}
  td:first-child {{
    color: color-mix(in srgb, currentColor 60%, transparent);
    white-space: nowrap;
  }}
  code {{ font-size: .9em; }}
  .warn {{
    border-left: 3px solid #e8a31a;
    padding-left: .75rem;
    margin-top: 2rem;
  }}
</style>
<body>
  <h1>Alkitab PR #{pr_number}</h1>
  <p>{pr_title}</p>
{cards}
  <table>
    <tr><td>Build</td><td>signed release, all production flavors</td></tr>
    <tr><td>Version</td><td>{version_name} ({version_code})</td></tr>
    <tr><td>Commit</td><td><code>{head_sha}</code> on <code>{branch}</code></td></tr>
    <tr><td>PR</td><td><a href="{pr_url}">{pr_url}</a></td></tr>
    <tr><td>CI run</td><td><a href="{run_url}">{run_url}</a></td></tr>
  </table>
  <p class="warn">These are signed with the production key and share their
  application IDs with the Play Store builds, so installing one
  <strong>replaces</strong> the corresponding installed app (your data is
  kept). Play Store will restore the store build on its next update. Enable
  "install unknown apps" for your browser if Android asks.</p>
</body>
</html>
"""

CARD = """  <div class="flavor">
    <h2>{label}</h2>
    <div class="appid"><code>{application_id}</code></div>
    <a class="download" href="{href}">Download APK ({size})</a>
  </div>
"""


def human_size(num_bytes: int) -> str:
    return f"{num_bytes / (1024 * 1024):.1f} MB"


def flavor_order(name: str) -> tuple[int, str]:
    """Sort known flavors in FLAVOR_LABELS order, unknown ones last."""
    known = list(FLAVOR_LABELS)
    return (known.index(name), "") if name in known else (len(known), name)


def apk_file_name(flavor: str, gradle_name: str) -> str:
    prefix = FLAVOR_APK_PREFIXES.get(flavor)
    if prefix is None or not gradle_name.startswith(GRADLE_APK_PREFIX):
        return gradle_name
    return prefix + gradle_name.removeprefix(GRADLE_APK_PREFIX)


def collect_apks(apk_dir: Path) -> list[dict]:
    """One entry per flavor directory found under apk_dir."""
    apks = []
    flavor_dirs = sorted(
        (p for p in apk_dir.iterdir() if p.is_dir()),
        key=lambda p: flavor_order(p.name),
    )
    for flavor_dir in flavor_dirs:
        release_dir = flavor_dir / "release"
        if not release_dir.is_dir():
            continue
        found = sorted(release_dir.glob("*.apk"))
        if len(found) != 1:
            sys.exit(f"expected exactly one APK in {release_dir}, found {len(found)}")
        apk = found[0]

        meta = {}
        meta_path = release_dir / "output-metadata.json"
        if meta_path.exists():
            meta = json.loads(meta_path.read_text())
        element = (meta.get("elements") or [{}])[0]

        apks.append(
            {
                "flavor": flavor_dir.name,
                "label": FLAVOR_LABELS.get(flavor_dir.name, flavor_dir.name),
                "applicationId": meta.get("applicationId", "?"),
                "versionName": str(element.get("versionName", "?")),
                "versionCode": str(element.get("versionCode", "?")),
                "file": apk_file_name(flavor_dir.name, apk.name),
                "size": human_size(apk.stat().st_size),
                "path": apk,
            }
        )

    if not apks:
        sys.exit(f"no <flavor>/release/*.apk found under {apk_dir}")
    return apks


def main() -> None:
    apk_dir = Path(os.environ["APK_DIR"])
    apks = collect_apks(apk_dir)

    here = Path(__file__).resolve().parent
    dist = here / "dist"
    shutil.rmtree(dist, ignore_errors=True)
    dist.mkdir()
    for apk in apks:
        shutil.copy2(apk["path"], dist / apk["file"])

    e = lambda key: html.escape(os.environ.get(key, ""))
    cards = "".join(
        CARD.format(
            label=html.escape(apk["label"]),
            application_id=html.escape(apk["applicationId"]),
            href=urllib.parse.quote(apk["file"]),
            size=apk["size"],
        )
        for apk in apks
    )
    # All flavors share versionName/versionCode, so the page shows one row
    # for the build rather than repeating it per card.
    (dist / "index.html").write_text(
        PAGE.format(
            pr_number=e("PR_NUMBER"),
            pr_title=e("PR_TITLE"),
            pr_url=e("PR_URL"),
            head_sha=e("HEAD_SHA")[:7],
            branch=e("BRANCH"),
            run_url=e("RUN_URL"),
            cards=cards,
            version_name=html.escape(apks[0]["versionName"]),
            version_code=html.escape(apks[0]["versionCode"]),
        )
    )

    build_info = {
        "prNumber": os.environ.get("PR_NUMBER", ""),
        "headSha": os.environ.get("HEAD_SHA", ""),
        "runUrl": os.environ.get("RUN_URL", ""),
        "versionName": apks[0]["versionName"],
        "versionCode": apks[0]["versionCode"],
        "apks": [{k: v for k, v in apk.items() if k != "path"} for apk in apks],
    }
    (here / "build-info.json").write_text(json.dumps(build_info, indent=2))

    for apk in apks:
        print(f"staged {apk['label']}: {apk['file']} ({apk['size']})")


if __name__ == "__main__":
    main()
