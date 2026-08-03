# PR APK previews on Cloudflare Workers

Every same-repo pull request gets its **signed release APKs — all three
production flavors** — published to a Cloudflare Worker that serves them as
static assets, plus a comment on the PR linking to a `*.workers.dev`
download page. The APKs run ~8 MB each, well under the 25 MiB per-asset
limit, so the free Workers plan is enough.

## How it works

1. The `signed-release` job in `.github/workflows/android.yml` builds and
   signs all production flavors, then uploads a `pr-preview-apks` artifact
   containing only the APKs and AGP's `output-metadata.json` — the AABs and
   mapping files are deliberately excluded so they never reach a public
   URL.
2. The `pr-apk-preview` job downloads that artifact, runs `make_dist.py` to
   stage `dist/` (the APKs plus a generated `index.html` download page),
   and runs `wrangler versions upload` from this directory.
3. `versions upload` creates a new *version* of the `alkitab-pr` worker.
   Every version gets its own immutable preview URL of the form
   `https://<8-hex>-alkitab-pr.<account-subdomain>.workers.dev`, so builds
   from different PRs (and successive pushes to the same PR) never
   overwrite each other.
4. `render_comment.py` turns `build-info.json` plus that URL into the PR
   comment, which the job posts or updates in place.

Fork PRs skip this entirely: GitHub does not expose repository secrets to
them (by design), so neither the signing key nor the Cloudflare token is
available. They still get the `plain-debug` CI build.

## One-time setup

Already done for `yukuku/androidbible` — the worker is `alkitab-pr` and
preview links look like
`https://<8-hex>-alkitab-pr.yukuku.workers.dev`. These steps are for
setting the same thing up on a different Cloudflare account or a fork.

1. **Cloudflare account** — any account on the free Workers plan. Make sure
   the account has a `workers.dev` subdomain registered (Cloudflare
   dashboard → Workers & Pages; new accounts are prompted to pick one).
2. **API token** — dashboard → My Profile → API Tokens → Create Token →
   use the **Edit Cloudflare Workers** template (the part that matters is
   the *Workers Scripts: Edit* account permission). Scope it to just that
   account.
3. **GitHub repository secrets** (Settings → Secrets and variables →
   Actions):
   - `CLOUDFLARE_API_TOKEN` — the token from step 2
   - `CLOUDFLARE_ACCOUNT_ID` — shown on the dashboard's Workers & Pages
     overview page (also in any dashboard URL)

That's all. The worker itself does not need to be created by hand: the
first CI run bootstraps it (`versions upload` cannot create a worker, so on
that first failure the job runs `wrangler deploy` once, then retries).
Until the secrets exist, the `pr-apk-preview` job detects their absence and
skips cleanly, so CI stays green in the meantime.

## Notes and limits

- **These are production-signed builds.** They share their application IDs
  and signature with the Play Store apps, so installing one replaces the
  corresponding installed app (user data is kept, and Play Store restores
  the store build on its next update). The download page says so too.
- **Preview URLs are public by default** — unlisted, but anyone with the
  link can download, and the builds come from unmerged, potentially
  unreviewed branches. Cloudflare does not document an expiry for
  version preview URLs, so assume a build stays reachable until the version
  is deleted. If that is not acceptable, put the previews behind Cloudflare
  Access (worker → Settings → enable Access for preview URLs) and authorize
  specific accounts; CI needs no changes for that.
- The worker's *live* URL (`alkitab-pr.<subdomain>.workers.dev`) serves
  whatever the one-time bootstrap deploy shipped and is not updated by CI —
  only the per-version preview URLs matter. Running
  `wrangler versions deploy` would promote a version to it.
- Re-running an existing workflow run replays the original `pull_request`
  event payload, so the page and comment show the PR title as it was when
  the run was first created. Fresh runs (new pushes) always show the
  current title.
- The per-flavor `signed-release` artifacts keep the same APKs on the run
  page for 14 days, independent of Cloudflare.
- To test locally, point `APK_DIR` at a directory laid out like
  `<flavor>/release/<apk + output-metadata.json>`:

  ```bash
  APK_DIR=/path/to/apkdir PR_NUMBER=0 PR_TITLE=test python3 make_dist.py
  npx wrangler@4 dev                # serves dist/ locally, no account needed
  PREVIEW_URL=https://example.workers.dev python3 render_comment.py
  ```

  `npx wrangler@4 versions upload` also works locally (it needs
  `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`), but it publishes a
  real, publicly reachable version — prefer `wrangler dev` for iterating on
  the page.
