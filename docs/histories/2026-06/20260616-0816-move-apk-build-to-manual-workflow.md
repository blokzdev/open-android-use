## [2026-06-16 08:16] | Task: 把 APK 产物构建移到手动 workflow

### 🤖 Execution Context
* **Agent ID**: `claude-code (session 01SteoxNdFSThg2ULfifResK)`
* **Base Model**: `Opus 4.8 (1M)`
* **Runtime**: `Claude Code on the web (remote)`

### 📥 User Query
> I notice APKs are generated in every PR — move it to a separate Build APK
> workflow/action we can run manually as needed. Fold into PR #49.

### 🛠 Changes Overview
**Scope:** `.github/workflows` (CI only). No app code.

**Key Actions:**
- **New `build-apk.yml`**: a manual `workflow_dispatch` job that builds and uploads the
  companion APK on demand, with a `build_type` choice input (debug / release / both).
- **Trim `android-runtime.yml` `companion` job**: removed the per-PR debug-APK build +
  artifact upload. Kept the JVM unit tests and the **R8 keep-rules gate** (`assembleRelease`,
  not uploaded) on every PR — a correctness gate, not artifact distribution (founder call).
- Updated workflow comments to point at the new manual path.

### 🧠 Design Intent (Why)
The per-PR pipeline generated a distributable APK on every run — including docs-only PRs —
which is wasted CI. Moving artifact generation to a manual workflow keeps PRs lean while
preserving the regression safety net: the release build is still compiled each PR through the
R8 keep-rules gate, so a stripped-class regression is still caught at PR time, just without
uploading an artifact.

### 📁 Files Modified
- `.github/workflows/build-apk.yml` (new)
- `.github/workflows/android-runtime.yml`
