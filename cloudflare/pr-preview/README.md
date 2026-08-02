# PR APK previews on Cloudflare Workers

Every same-repo pull request gets its `plainDebug` APK published to a
Cloudflare Worker with static assets, and a comment on the PR linking to a
`*.workers.dev` download page. The APK is well under the 25 MiB per-asset
limit, so the free Workers plan is enough.

## How it works

1. The `plain-debug` job in `.github/workflows/android.yml` builds the APK
   and uploads it as a workflow artifact.
2. The `pr-apk-preview` job downloads that artifact, runs `make_dist.py` to
   stage `dist/` (the APK plus a generated `index.html` download page), and
   runs `wrangler versions upload` from this directory.
3. `versions upload` creates a new *version* of the `alkitab-pr` worker.
   Every version gets its own immutable preview URL of the form
   `https://<8-hex>-alkitab-pr.<account-subdomain>.workers.dev`, so builds
   from different PRs (and successive pushes to the same PR) never
   overwrite each other.
4. The job posts (or updates) a PR comment pointing at the newest build's
   preview URL and the direct APK link inside it.

Fork PRs skip the deploy: GitHub does not expose repository secrets to
them, by design. They still get the plain CI build.

## One-time setup

1. **Cloudflare account** — any account on the free Workers plan. Make sure
   the account has a `workers.dev` subdomain registered (Cloudflare
   dashboard → Workers & Pages; new accounts are prompted to pick one).
2. **API token** — dashboard → My Profile → API Tokens → Create Token →
   use the **Edit Cloudflare Workers** template (the part that matters is
   the *Workers Scripts: Edit* account permission). Scope it to the one
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

- Preview URLs point at retained worker versions. Cloudflare eventually
  prunes old versions, so treat the links as review-time aids, not
  permanent hosting; the workflow artifact keeps the APK for its own
  retention window regardless.
- The worker's *live* URL (`alkitab-pr.<subdomain>.workers.dev`) serves
  whatever the one-time bootstrap deploy shipped and is not updated by CI —
  only the per-version preview URLs matter.
- To test locally: put an APK and its `output-metadata.json` in a
  directory, then

  ```bash
  APK_DIR=/path/to/apkdir PR_NUMBER=0 PR_TITLE=test \
    python3 make_dist.py
  npx wrangler@4 dev        # serves dist/ locally
  npx wrangler@4 versions upload   # needs CLOUDFLARE_API_TOKEN + CLOUDFLARE_ACCOUNT_ID
  ```
