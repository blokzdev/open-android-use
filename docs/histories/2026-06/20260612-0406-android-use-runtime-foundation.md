## [2026-06-12 04:06] | Task: Pivot fork to Android — vision, harness, and Android runtime foundation

### 🤖 Execution Context
* **Agent ID**: Claude Code (cloud session)
* **Base Model**: claude-fable-5
* **Runtime**: Claude Code remote execution container (Linux, Go 1.24, no device attached)

### 📥 User Query
> Forked open-computer-use as open-android-use; turn it into a robust "Second Pair
> of Hands" mobile paradigm app. Conceptualize the best version, formulate
> CLAUDE.md, memory harness, and production docs, then build/test/document
> end-to-end.

### 🛠 Changes Overview
**Scope:** new `apps/OpenAndroidUse` package; repo-level docs and build entries.

**Key Actions:**
- **[Vision & plan]**: Added `docs/design-docs/second-pair-of-hands.md` (product
  vision, three-phase roadmap, quality bar, non-goals) and
  `docs/exec-plans/active/20260612-android-use-runtime.md` (scope, risks,
  decisions, verification).
- **[Android runtime]**: New host-side Go ADB bridge `apps/OpenAndroidUse`
  exposing the same 9 Computer Use tools, stdio MCP server, and CLI surface
  (`mcp`/`doctor`/`devices`/`list-apps`/`snapshot`/`call --calls`) as the desktop
  runtimes. State via `uiautomator dump` + `screencap` (with the macOS image
  budget model and a single per-snapshot `CoordinateScale`); actions via `input`
  synthesis (tap, long-press, paged swipes, drag-and-drop, xdotool→Android
  keycode mapping, ASCII-guarded `input text`).
- **[Tests]**: ~30 unit tests with a fake bridge (service/MCP layer) and a fake
  adb runner (device layer, including a full click round trip against a real
  encoded PNG and coordinate-scale assertions). `go vet` and `gofmt` clean.
- **[Build & harness]**: `scripts/build-open-android-use.sh` (host-OS targets,
  version injected from `package.json`), `make android-build` / `android-test`;
  rewrote `CLAUDE.md` (mission, memory harness, language policy, quick
  reference); refreshed `AGENTS.md` identity; ARCHITECTURE.md section 8;
  QUALITY_SCORE Android row (grade C); README pivot + Android quick start.

### 🧠 Design Intent (Why)
A host-side ADB bridge (not an on-device app) ships first because it reuses the
proven Windows/Linux runtime pattern, is fully unit-testable without hardware,
and works against any device/emulator today. Keeping byte-level schema parity
with the desktop runtimes means every existing MCP host and skill works on
Android unchanged; Android-only semantics (long-press, Back/Menu keys,
single-foreground-app launches) are mapped inside the existing surface. Known
transport limits (ASCII-only typing, secure surfaces) fail loudly with the Phase
2 on-device companion named as the fix. New docs are English-first per the
founder's language; inherited Chinese docs remain valid until migrated.

### 📁 Files Modified
- `apps/OpenAndroidUse/{go.mod,main.go,device.go,keymap.go,image.go}`
- `apps/OpenAndroidUse/{main_test.go,device_test.go,keymap_test.go,image_test.go}`
- `scripts/build-open-android-use.sh`
- `Makefile`
- `CLAUDE.md`, `AGENTS.md`, `README.md`
- `docs/ARCHITECTURE.md`, `docs/QUALITY_SCORE.md`
- `docs/design-docs/second-pair-of-hands.md`
- `docs/exec-plans/active/20260612-android-use-runtime.md`
