# 安全默认约束

## 当前实现边界

- 对 MCP host 暴露的接口仍是本地 `stdio`；macOS CLI 与 `.app` app agent 之间会使用用户临时目录下的 Unix domain socket，socket 创建后会收紧为当前用户读写，且不对外监听 TCP/HTTP 端口。
- 所有动作都必须显式带 `app` 参数；当前不会在后台自动扫描并控制任意 app。
- macOS 真实 app 路径依赖 `Open Computer Use.app` 已获得 `Accessibility` 与 `Screen Recording` 权限；终端里的 CLI / Node launcher 会把 `mcp`、`doctor`、`call`、`snapshot` 和 `list-apps` 转发给由 LaunchServices 启动的本地 app agent，避免把权限要求落到 iTerm / Terminal 身上。
- 实验性 Linux runtime 依赖已登录桌面用户的 AT-SPI2 / D-Bus session；coordinate mouse、drag、keyboard synthesis 只是 best-effort fallback，不应被视为跨 Wayland compositor 的通用后台输入授权。

## 数据处理

- 普通 app 的 screenshot 默认只在内存中编码成 PNG，并通过 MCP `image` content block 直接回传；默认不长期持久化。
- Linux runtime 的 screenshot 是 best-effort；如果 GNOME Wayland 返回黑图，bridge 会省略 image block，避免把无效截图误当成真实画面。
- fixture app 的合成状态只写到本地临时 JSON 文件，目的是支撑 deterministic smoke test；当前写入走原子替换，减少测试期间的读写竞争。
- 桌面 runtime（macOS/Windows/Linux）默认不引入第三方推理服务，也不上传截图、AX tree 或输入内容。
- **Android companion（Phase 5 起）有两种模式**：默认的云端 BYOK 模型（Claude/Gemini）会把任务文本、
  系统提示、AX tree 和（开启 vision 时）截图发送到用户自选的服务商；**Local-only mode** 则完全在设备上
  用 Gemma 运行，不上传任何关于用户/设备/屏幕的内容。详见下方英文 "Egress & data flow (Phase 5)"。
- Android companion (Phase 4.5): conversations are saved **on the device, text-only**
  (one JSON file per session under the app's private `filesDir`, written atomically) so
  the user can revisit and resume them from History. Screenshots are **never** written to
  disk — they stay in memory only, and a resumed conversation's model history is rebuilt
  from the text transcript. Users can delete any single conversation or all of them, and
  clear the API key, from the in-app Privacy & data screen. Conversation export writes a
  Markdown file to the app cache and shares it via a `FileProvider` with a per-share,
  read-only URI grant.

## Egress & data flow (Phase 5, English)

Phase 5 added pluggable model providers to the Android companion, so what leaves the device now
depends on the chosen provider. There are two honest modes:

**Default — cloud (BYOK).** The user selects Claude or Gemini and supplies their **own** API key
(stored AES/GCM in the Android Keystore, never logged, never in a URL). Each turn sends, over TLS,
to the user's chosen provider under that provider's terms:

| Provider | Host (:443, TLS) | What is sent |
|---|---|---|
| Claude (Anthropic) | `api.anthropic.com` | task text + frozen system prompt + accessibility tree + (vision on) screenshots |
| Gemini (Google) | `generativelanguage.googleapis.com` | same payload class |

The 5.6 **text-only perception** toggle is the in-cloud privacy lever: turning vision off keeps
screenshots on the device while still using the cloud model.

**Local-only mode (Phase 5.7).** A single umbrella toggle forces the on-device Gemma provider, so
**nothing about the user, device, or screen leaves the device** — inference runs in-process via
LiteRT-LM with zero network egress. It is tier-gated (offered only on devices that can run the
model) and readiness-aware (enabling on a capable device prompts the one-time model download). The
**only** outbound network in this mode is that optional, user-initiated model fetch from
`huggingface.co` (open weights, **SHA-256 pinned**), which uploads nothing about the user.
Enforcement is structural: `AgentController.startTask` resolves the provider through
`effectiveProvider(localOnly, selected)`, so no cloud backend is ever constructed while the mode
is on; the Settings provider picker and key controls are locked accordingly. Saved cloud keys are
kept (just ignored) so the user can flip back without re-entry.

**Transport.** Release builds ship no `networkSecurityConfig` (platform default: cleartext
blocked); debug builds permit cleartext only for `127.0.0.1`/`localhost`, and the agent
additionally refuses any non-loopback `http` base-URL override (`AgentController.loopbackOrNull`).
The control surface (accessibility/loopback core) remains dependency-free and makes no provider
calls. Dependency provenance is in `docs/SUPPLY_CHAIN_SECURITY.md`.

## Sensitive-screen trust model & injection posture (Phase 6.5c, English)

The on-device agent treats credentials and payment data as **outside the model's trust boundary**.
The full design is `docs/design-docs/agent-security-trust-architecture.md`; the operative rules:

- **Secrets never enter the model loop.** The agent never receives, types, or transmits a user
  password/credit-card. At a secret field it **hands off** to the human (or to OS-mediated autofill,
  where the secret flows password-manager→field and the agent never reads it). This matches every
  major computer-use agent (Operator, Anthropic computer use, Mariner, Claude Code).
- **We redact secrets ourselves — we do not rely on the framework.** Password-field text masking to an
  accessibility service is version/capability-dependent (not guaranteed), and ordinary card `EditText`
  text is not masked at all. On a sensitive screen the agent redacts secret field *values* to
  `[redacted]` at snapshot emission (covering both the on-device agent and the host bridge / external
  MCP runtimes) and withholds the screenshot in vision mode, while keeping screen *structure*.
  `FLAG_SECURE` is pixel-only (it blocks screenshots, not the accessibility tree, and is not even
  readable via any `isSecure()` API), so suppression is driven by our own sensitivity inference.
- **Trust is scoped, not blanket.** Per-app "allow" is default-deny and offered as one-time / this-session
  / (effortful, risk-framed) persistent grants that are revocable and **decay** when unused, with a
  text-only audit log — never a single always-on toggle. A granted app still hands off secrets and still
  confirms high-risk/injection-suspected actions (enforced structurally, only the screen-level gate is
  grant-aware).
- **Injection posture.** On-screen text is untrusted data, delivered to the model only inside
  `tool_result` blocks with provenance, never as instructions; a v1 injection-signal classifier flags
  injection-like content; irreversible actions (send/pay/delete/post) require confirmation. We rely on
  the cloud provider's model hardening (Claude/Gemini); the on-device tier is weaker and leans harder on
  these structural layers. We adopt CaMeL's principles (control/data separation, least-privilege
  capabilities) but defer the full system (`docs/BACKLOG.md`).

## 授权与最小权限

- 当前只保留一层密码管理器 bundle denylist / bundle-id gate：
  - 会阻止对 1Password、Bitwarden、Dashlane、LastPass、NordPass 和 Proton Pass 做直接 `get_app_state` / action 调用。
  - 终端类 app、Chrome / Atlas 和系统组件不再属于内置阻止目标。
  - 对 bundle identifier 直传时返回 safety denial；对 app name 查询时默认不把这些密码管理器暴露成可解析目标。
- 但当前仍然没有官方闭源实现里的 session approval / 动态 app policy。
- 这意味着开源版当前的安全边界主要由：
  - 明确的 tool 调用参数
  - 内置密码管理器 denylist
  - `Open Computer Use.app` 的系统权限
  - 本地使用场景
  共同提供。
- 下一阶段应优先补：
  - session 级审批
  - 更清楚的敏感 app / 系统设置防护策略

## Fixture Bridge 约束

- `FixtureBridge` 只用于仓库内测试夹具，不是给第三方 app 的控制平面。
- 任何面向真实 app 的能力新增，都不应该复用这条测试专用通道。

仓库级的依赖、SBOM 和 provenance 默认能力，统一写在 `docs/SUPPLY_CHAIN_SECURITY.md`。
