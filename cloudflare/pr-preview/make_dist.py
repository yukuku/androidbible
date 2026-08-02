#!/usr/bin/env python3
"""Stage dist/ for the PR-preview Cloudflare Worker.

Copies the plainDebug APK into dist/ and generates an index.html download
page next to it. Inputs come from environment variables set by the
pr-apk-preview job in .github/workflows/android.yml:

  APK_DIR    directory containing the built APK and AGP's output-metadata.json
  PR_NUMBER  pull request number
  PR_TITLE   pull request title (HTML-escaped before use)
  PR_URL     pull request page on GitHub
  HEAD_SHA   PR head commit (not the merge commit)
  BRANCH     PR head branch name
  RUN_URL    the workflow run that produced this build

When $GITHUB_OUTPUT is set, appends apk_name= and apk_size= so later
workflow steps can link directly to the APK inside the deployed assets.
"""

from __future__ import annotations

import html
import json
import os
import shutil
import sys
import urllib.parse
from pathlib import Path

PAGE = """<!doctype html>
<html lang="en">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Alkitab PR #{pr_number} preview build</title>
<style>
  :root {{ color-scheme: light dark; }}
  body {{
    font-family: system-ui, sans-serif;
    max-width: 40rem;
    margin: 3rem auto;
    padding: 0 1rem;
    line-height: 1.5;
  }}
  .download {{
    display: inline-block;
    padding: .75rem 1.5rem;
    border-radius: .5rem;
    background: #1a73e8;
    color: #fff;
    text-decoration: none;
    font-weight: 600;
  }}
  table {{ border-collapse: collapse; margin-top: 1.5rem; }}
  td {{ padding: .25rem .75rem .25rem 0; vertical-align: top; }}
  td:first-child {{ color: color-mix(in srgb, currentColor 60%, transparent); white-space: nowrap; }}
  code {{ font-size: .9em; }}
</style>
<body>
  <h1>Alkitab PR #{pr_number}</h1>
  <p>{pr_title}</p>
  <p><a class="download" href="{apk_href}">Download APK ({apk_size})</a></p>
  <table>
    <tr><td>Flavor</td><td><code>plainDebug</code> (open-source build, debug-signed)</td></tr>
    <tr><td>Version</td><td>{version_name} ({version_code})</td></tr>
    <tr><td>Commit</td><td><code>{head_sha}</code> on <code>{branch}</code></td></tr>
    <tr><td>PR</td><td><a href="{pr_url}">{pr_url}</a></td></tr>
    <tr><td>Build</td><td><a href="{run_url}">{run_url}</a></td></tr>
  </table>
  <p>Installs alongside the Play Store app (different application ID and
  signature). Enable "install unknown apps" for your browser if Android
  asks.</p>
</body>
</html>
"""


def main() -> None:
    apk_dir = Path(os.environ["APK_DIR"])
    apks = sorted(apk_dir.glob("*.apk"))
    if len(apks) != 1:
        sys.exit(f"expected exactly one APK in {apk_dir}, found {len(apks)}")
    apk = apks[0]

    version_name = version_code = "?"
    meta_path = apk_dir / "output-metadata.json"
    if meta_path.exists():
        element = json.loads(meta_path.read_text())["elements"][0]
        version_name = str(element["versionName"])
        version_code = str(element["versionCode"])

    apk_size = f"{apk.stat().st_size / (1024 * 1024):.1f} MB"

    dist = Path(__file__).resolve().parent / "dist"
    shutil.rmtree(dist, ignore_errors=True)
    dist.mkdir()
    shutil.copy2(apk, dist / apk.name)

    e = lambda key: html.escape(os.environ.get(key, ""))
    (dist / "index.html").write_text(
        PAGE.format(
            pr_number=e("PR_NUMBER"),
            pr_title=e("PR_TITLE"),
            pr_url=e("PR_URL"),
            head_sha=e("HEAD_SHA")[:7],
            branch=e("BRANCH"),
            run_url=e("RUN_URL"),
            apk_href=urllib.parse.quote(apk.name),
            apk_size=apk_size,
            version_name=html.escape(version_name),
            version_code=html.escape(version_code),
        )
    )

    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as f:
            f.write(f"apk_name={apk.name}\napk_size={apk_size}\n")
    print(f"staged {apk.name} ({apk_size}) into {dist}")


if __name__ == "__main__":
    main()
