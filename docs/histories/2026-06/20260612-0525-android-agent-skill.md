## [2026-06-12 05:25] | Task: Agent skill for Open Android Use + parameterized skill packaging

### 🤖 Execution Context
* **Agent ID**: Claude Code (cloud session, continuation of the Android pivot)
* **Base Model**: claude-fable-5
* **Runtime**: Claude Code remote execution container (Linux)

### 📥 User Query
> Continue autonomously (long-running build) per the approved plan: agent skill
> next.

### 🛠 Changes Overview
**Scope:** `skills/open-android-use` (new); `scripts/package-skill.sh`; docs.

**Key Actions:**
- **[Skill]**: `skills/open-android-use/SKILL.md` mirroring the desktop skill's
  structure — core workflow, Android-specific mappings (single foreground app,
  long-press via right button, Back/Menu keys, screenshot-pixel coordinates,
  ASCII limit), companion-mode guidance, and phone-appropriate operating rules
  (confirm irreversible actions, don't transliterate on ASCII failures, never
  guess between multiple devices). References split per convention:
  `installation.md`, `usage.md`, `troubleshooting.md`, plus `agents/openai.yaml`.
- **[Packaging]**: `scripts/package-skill.sh` now takes an optional skill-name
  argument (default `open-computer-use`, fully backward compatible): frontmatter
  validation, manifest name/rootDirectory, and error text all derive from the
  argument. Verified both skills package to identical-byte `.zip`/`.skill`.
- **[Docs]**: `docs/ARCHITECTURE.md` skills section updated for the two-skill
  layout.

### 🧠 Design Intent (Why)
The skill is the distribution surface for agent runtimes: same protocol as
desktop, so the skill mostly encodes what is *different* on a phone — and the
safety posture there matters more (messages, payments, personal data), so the
operating rules are explicit about confirmation and sensitive surfaces.
Parameterizing the existing packager beats forking it: one validation path for
all skills.

### 📁 Files Modified
- `skills/open-android-use/{SKILL.md,agents/openai.yaml,references/installation.md,references/usage.md,references/troubleshooting.md}`
- `scripts/package-skill.sh`
- `docs/ARCHITECTURE.md`
