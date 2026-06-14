# Open Android Use Companion

The on-device Android app — AccessibilityService control surface, loopback
control endpoint, and the on-device agent — that turns a phone into a "Second
Pair of Hands." This is the production product destined for the Play Store.

## Licensing (open-core)

This directory is licensed differently from the rest of the repository:

- **This app (`apps/OpenAndroidUseCompanion/`)** — **PolyForm Perimeter 1.0.0**,
  Copyright (c) 2026 Blokz Development Co. Source-available and no-compete: you
  may read, run, modify, and share it, but not to ship a competing product. See
  [`LICENSE`](LICENSE).
- **Everything else (the "engine")** — the host-side Go bridge
  (`apps/OpenAndroidUse`), the desktop runtimes, and the npm packaging — remains
  under the **MIT License** (see the repository-root `LICENSE`).

Attribution for the upstream project this fork builds on, and for third-party
code bundled into the APK (the Anthropic Java SDK, Apache-2.0), lives in the
repository-root [`NOTICE`](../../NOTICE).

## Build

```sh
make companion-build    # → dist/companion/open-android-use-companion.apk
```

Design and protocol: [`docs/design-docs/on-device-companion.md`](../../docs/design-docs/on-device-companion.md).
The product UI/UX direction is captured in
[`docs/design-docs/phase4-product-ui.md`](../../docs/design-docs/phase4-product-ui.md).
