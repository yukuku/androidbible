# Agent instructions

Read [CLAUDE.md](CLAUDE.md) for repository architecture, build commands, testing, and code conventions.

## Complete implementation tasks with a pull request

Unless the user explicitly requests otherwise, finish every implementation task by committing the changes, pushing a task branch to `yukuku/androidbible`, and opening a pull request targeting `develop`. This is part of completing the task, including requests phrased as building a feature or fixing a bug; do not ask for separate permission to create the PR.

Use a branch in this repository so the PR triggers GitHub Actions to build signed release APKs and publish APK preview links. See [the CI and preview documentation](docs/build-system.md#cicd).

- Run the checks appropriate to the changes before pushing; CI supplements local verification.
- When continuing a task that already has an open PR, push updates to its branch and update its description as needed.
- Return the PR link and the observed CI status to the user.
- Leave merging the PR and publishing a production release to an explicit user request.

Read-only analysis and advice do not require a PR.
