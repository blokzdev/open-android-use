# open-android-use — Second Pair of Hands

You must read `AGENTS.md` first — it is the navigation layer for this repo's docs.

## Mission

Turn this fork of `open-computer-use` into the first robust, open, world-class
"Second Pair of Hands" for Android: an AI operator that works *with* the human on
their device through the same 9-tool Computer Use MCP surface the desktop runtimes
expose. Vision: `docs/design-docs/second-pair-of-hands.md`. Current roadmap:
`docs/exec-plans/active/20260612-android-use-runtime.md`.

## Memory harness (how knowledge persists here)

Chat context is disposable; the repo is the memory. Per round:

1. **Read before working**: `AGENTS.md` routes you to `docs/REPO_COLLAB_GUIDE.md`,
   `docs/ARCHITECTURE.md`, and `docs/design-docs/core-beliefs.md`.
2. **Plan multi-commit work**: execution plans live in `docs/exec-plans/active/`
   (template in `docs/exec-plans/templates/`); move to `completed/` when done, and
   update the plan's Progress/Decisions sections as you go.
3. **Record after working**: every substantive change gets a history record in
   `docs/histories/YYYY-MM/YYYYMMDD-HHmm-slug.md` (`make new-history SLUG=...`).
4. **Keep docs truthful in the same change**: if behavior changes, the matching
   doc changes in the same commit series. `docs/QUALITY_SCORE.md` tracks honest
   per-subsystem grades — update it when a subsystem moves.
5. **Defer with discipline**: when you cut something out of scope, record it in
   `docs/BACKLOG.md` (one line + rationale + rough priority) so it isn't lost.
   Scheduled work belongs in an exec-plan; `docs/BACKLOG.md` is the someday/maybe
   list — promote items into an exec-plan when they're scheduled.

## Language policy

New docs and code comments are English-first (the founder's language). Inherited
Chinese docs remain authoritative until individually migrated; do not bulk-rewrite
them without need.

## Android runtime quick reference

- Code: `apps/OpenAndroidUse` (Go, host-side ADB bridge; no device needed to build).
- Test: `make android-test` · Build: `make android-build`
- Hygiene: `gofmt -l apps/OpenAndroidUse` must be empty; `go vet ./...` clean.
- Protocol: 9-tool Computer Use surface, byte-compatible with the macOS/Windows/
  Linux runtimes — do not fork the schema; map Android semantics inside it.
- Coordinates: everything the model sees (frames, screenshots) is screenshot pixel
  space; one `CoordinateScale` per snapshot converts to device pixels. Keep that
  invariant pinned by tests.

## Legacy desktop runtimes

The Swift/macOS (`make build` / `make test`), Windows, and Linux runtimes are
inherited and still build; treat them as reference implementations and protocol
source of truth, not active feature work.
