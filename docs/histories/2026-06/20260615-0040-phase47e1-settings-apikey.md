## [2026-06-15 00:40] | Task: Phase 4.7e-1 — Settings: API-key depth (+ Phase 5 scope recorded)

### 🤖 Execution Context
* **Agent ID**: `claude-code (session 013vN6M9RAYBQnDAybhdYE7T)`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote)`

### 📥 User Query
> Phase 4.7 UX elevation — Settings/Privacy depth (4.7e). Also: founder approved an expanded
> Phase 5 (pluggable models + on-device Gemma 4 E2B tier) after research vetting; record it.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` Settings; docs (incl. durable Phase 5 record).

**Key Actions (4.7e-1):**
- **Show/hide key**: the API-key `OutlinedTextField` gains an eye toggle (`Visibility` /
  `VisibilityOff`) switching `PasswordVisualTransformation` ↔ `VisualTransformation.None`.
- **Test key**: a new `ModelCatalog.validateKey(apiKey, baseUrl)` makes a minimal authenticated
  Models-API call and returns `KeyTest.Valid` / `Invalid(message)`. Settings runs it off the main
  thread (`Dispatchers.IO`), shows a spinner in the button, and reports the result via a Snackbar.
  Tests the entered key, or the saved key when the field is empty (honors the debug baseUrl override).
- **Get a key**: a link opening the Anthropic console keys page (`ACTION_VIEW`).

**Docs / Phase 5 record:** roadmap `Phase 5` expanded from "multi-provider BYOK" to "pluggable
models + on-device edge tier" with the verified Gemma 4 E2B facts + founder decisions, plus a dated
Decisions entry; 4.7 sub-plan progress (4.7e-1 done; 4.7e-2 theme mode, 4.7e-3 privacy
storage/export next); `VERIFICATION.md` V114–V116.

### 🧠 Design Intent (Why)
These three are the standard "API key" affordances users expect: confirm what you typed (show/hide),
prove it works before relying on it (Test key — reusing the Models API we already call for the model
list), and a path to obtain one (link). `validateKey` lives in `ModelCatalog` next to the existing
Models-API use, keeping the Anthropic SDK contained in `agent/`. Test runs on IO with a Snackbar so
it never blocks the UI. Theme mode (threads through every Activity + recreate) and the Privacy
storage/export work are split to 4.7e-2/4.7e-3 to keep this diff focused.

### 📁 Files Modified
- `agent/ModelCatalog.kt` (`KeyTest` + `validateKey`), `agent/SettingsActivity.kt` (show/hide,
  Test key + spinner + Snackbar, Get-a-key link)
- `res/values/strings.xml` (key show/hide/test/valid/invalid/get + console URL)
- `docs/exec-plans/active/20260612-android-use-runtime.md` (Phase 5 expanded + decision),
  `docs/exec-plans/active/20260614-phase47-ux-elevation.md`, `VERIFICATION.md`, this history
