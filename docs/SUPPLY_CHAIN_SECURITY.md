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

Rules for the register: pin exact versions, never ranges; the control-surface
packages must not import any of these; bumping a version is a reviewed change
that updates this table in the same commit.

Redistribution note: the app's Gradle build excludes the duplicate
`META-INF/LICENSE*` / `NOTICE*` files the SDK's transitive jars ship (they
collide at packaging time). The required Apache-2.0 attribution for the bundled
SDK is therefore preserved in the repository-root `NOTICE` file, and surfaced
in-app on the About screen. Any future bundled dependency must be added to both
this table and `NOTICE` in the same commit.
