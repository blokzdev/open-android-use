# Standalone `open-android-use` npm package staged

- Date: 2026-06-12 17:00 UTC
- Scope: `scripts/npm/build-android-package.mjs`, Makefile (`android-npm`),
  package.json scripts, `.github/workflows/android-runtime.yml`, README,
  roadmap, quality score
- Decision: founder chose a standalone npm package (unscoped
  `open-android-use`) over extending the inherited `@qwen-code` meta-package
  or GitHub-Releases-only distribution. Publishing stays a manual maintainer
  step until the registry identity is claimed.

## What changed

- `scripts/npm/build-android-package.mjs` (also `make android-npm` /
  `npm run npm:build:android`): cross-compiles the Go bridge for all six host
  targets (darwin/linux/win32 × arm64/x64) and assembles
  `dist/npm/open-android-use/` — package.json (version read from
  `apps/OpenAndroidUse/main.go`, single source of truth), a thin Node
  launcher that selects the platform binary and passes everything through,
  bundled binaries under `runtimes/<os>-<cpu>/`, and a README.
- Deliberately a separate single-purpose builder, not a new target inside
  `build-packages.mjs`: the bridge has no app bundle, no installer helpers,
  and its own version line.
- CI (`test` job) now builds the package (which subsumes the old three-target
  cross-compile check — six targets now) plus a launcher smoke, and uploads
  `open-android-use-npm-package` as an artifact on every push.
- Publish path (manual): `npm publish dist/npm/open-android-use`.

## Verification

- `make android-npm` builds all six targets and assembles the package in
  container; `node dist/npm/open-android-use/bin/open-android-use.js version`
  → `0.2.3`; `help` renders the bridge CLI; `npm pack --dry-run` → 16.4 MB
  tarball, 9 files.
