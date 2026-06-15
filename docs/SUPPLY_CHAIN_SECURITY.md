# 供应链安全

这份文档定义模板默认采用的供应链安全做法。

## 默认控制项

- 在 Pull Request 上做依赖变更审查。
- 用 OSV 对仓库中的依赖声明和 lockfile 做漏洞扫描。
- 为 release 产物生成 SBOM。
- 为 release 产物生成 build provenance attestation。
- 用 OpenSSF Scorecard 做仓库级安全姿态分析。
- 所有 GitHub Actions 都固定到不可变的 commit SHA，而不是漂移的版本标签。

## 当前对应关系

- `actions/dependency-review-action`：阻止 PR 引入高风险依赖变更。
- `google/osv-scanner-action`：根据仓库里的依赖文件扫描已知漏洞。
- `anchore/sbom-action`：生成 SPDX 格式的 SBOM。
- `actions/attest-build-provenance`：为 release artifact 生成签名 provenance。
- `ossf/scorecard-action`：分析仓库级安全信号，例如工作流权限、分支保护等。
- `scripts/check-action-pinning.sh`：如果 workflow 里出现浮动 tag 而不是 SHA，直接让 CI 失败。

## 限制和前提

- Dependency Review 在 public repo 可以直接使用；private repo 通常需要 GitHub Advanced Security 或对应的代码安全能力。
- OSV 和 SBOM 的效果依赖仓库里存在可识别的依赖清单或 lockfile。
- 只有当 `scripts/release-package.sh` 真的代表项目的构建产物时，provenance 才真正有意义。
- Scorecard 的结果也依赖仓库本身是否开启了分支保护、工作流权限收敛等真实配置。

## 项目落地后建议继续做的事

- 锁定并提交项目真实依赖的 lockfile。
- 让构建过程尽量可重复、可验证。
- 如果条件允许，在部署链路里增加对 provenance 的校验。
- 把 attestation 校验继续下沉到部署平台或准入层。

## Runtime dependency register (English appendix)

The Android companion's control surface (accessibility service, loopback
endpoint, snapshot/action code) is deliberately dependency-free. Exceptions
are registered here, one entry per dependency, with the reasoning:

| Dependency | License | Where | Why | Added |
|---|---|---|---|---|
| `com.anthropic:anthropic-java` 2.40.1 (Maven Central) | Apache-2.0 | `apps/OpenAndroidUseCompanion` `agent` package only | First-party Anthropic SDK for the on-device agent's Claude API access; hand-rolled HTTP against a streaming LLM API is a larger risk than a pinned official SDK. Decision record: `docs/exec-plans/completed/20260612-phase3-on-device-agent.md`. | 2026-06-12 |
| `com.google.genai:google-genai` 1.58.0 (Maven Central) | Apache-2.0 | `apps/OpenAndroidUseCompanion` `agent` package only (`agent/llm`) | First-party Google Gen AI SDK for the on-device agent's Gemini API access (Phase 5.2 BYOK), same rationale as the Anthropic SDK. Pulls `com.google.http-client`, `com.google.code.gson`, `com.google.protobuf:protobuf-java` transitively (Apache-2.0/BSD-3-Clause). Decision record: `docs/exec-plans/active/20260615-phase5-pluggable-models.md`. | 2026-06-15 |
| Jetpack Compose (BOM `2024.09.03`) + Material 3 + `androidx.activity:activity-compose` 1.9.2 | Apache-2.0 | `apps/OpenAndroidUseCompanion` presentation layer (Activities, `ui/theme`, screens) | Phase 4 world-class UI; Compose/Material 3 is the modern Android UI standard (dynamic color, dark mode, accessibility). Presentation layer only. Design: `docs/design-docs/phase4-product-ui.md`. | 2026-06-14 |
| `androidx.work:work-runtime-ktx` 2.9.1 | Apache-2.0 | `apps/OpenAndroidUseCompanion` `agent` package (on-device model download) | Phase 5.5 on-device tier: runs the (multi-GB) Gemma model download as a constraint-aware background job that survives backgrounding. First-party Jetpack library; no model SDK. Decision record: `docs/exec-plans/active/20260615-phase5-pluggable-models.md`. | 2026-06-15 |
| `com.google.ai.edge.litertlm:litertlm-android` (Phase 5.5b) | Apache-2.0 | `apps/OpenAndroidUseCompanion` `agent/llm` only | First-party LiteRT-LM runtime for on-device Gemma 4 E2B inference. Ships native `.so` libraries (arm64-v8a); the model itself is downloaded at runtime, never bundled. To be added with 5.5b. | (pending 5.5b) |

Rules for the register: pin exact versions (Compose libs are pinned via the BOM),
never ranges; bumping a version is a reviewed change that updates this table in
the same commit. **Layer boundary:** the *control surface* — `CompanionService`,
`HttpServer`, snapshot/action code, the accessibility/loopback core — must not
import any of these (no `com.anthropic`, no `androidx`/`compose`). The `agent`
package may use the Anthropic SDK; the presentation layer (Activities, `ui/`) may
use Compose. Phase 5 also adds the Gemini SDK (`com.google.genai`) and, for the
on-device tier, `androidx.work` (download job, agent infra) and the LiteRT-LM
runtime (`com.google.ai.edge.litertlm`, `agent/llm` only) under the same rules.

On-device model integrity (Phase 5.5): the Gemma 4 E2B `.litertlm` file is
downloaded at runtime from a pinned Hugging Face repo (`litert-community/
gemma-4-E2B-it-litert-lm`, Apache-2.0, ungated) and verified against a **pinned
SHA-256** before use (`OnDeviceModelManager.EXPECTED_SHA256`) — the hash is the
git-LFS content id, so it pins exact bytes regardless of branch movement. A
mismatch deletes the download and refuses to run. The model is never bundled in
the APK.

Redistribution note: the app's Gradle build excludes the duplicate
`META-INF/LICENSE*` / `NOTICE*` files the SDK's transitive jars ship (they
collide at packaging time). The required Apache-2.0 attribution for the bundled
SDK is therefore preserved in the repository-root `NOTICE` file, and surfaced
in-app on the About screen. Any future bundled dependency must be added to both
this table and `NOTICE` in the same commit.
