# open-android-use

这个仓库是 `open-computer-use` 的 fork，正在转向 Android：目标是成为 Android 上的
"Second Pair of Hands"（产品愿景见 `docs/design-docs/second-pair-of-hands.md`，
当前路线见 `docs/exec-plans/active/20260612-android-use-runtime.md`）。Android
runtime 位于 `apps/OpenAndroidUse`，与桌面 runtimes 共享同一组 9 个 Computer Use
tools。新文档默认用英文写，存量中文文档在迁移前仍然有效。

`AGENTS.md` 故意保持简短，只负责做导航，不负责塞满所有规则。仓库内的 `docs/` 才是本地知识的正式来源。

如果一次代码或流程变更会让某份文档过期，就在同一轮任务里顺手把它改掉。

## 每轮开始先读

- `docs/REPO_COLLAB_GUIDE.md`：仓库级协作、提交、文档同步与测试约定。
- `docs/ARCHITECTURE.md`：仓库整体结构和预期边界。
- `docs/design-docs/core-beliefs.md`：Agent-first 的工作原则和这个模板的设计出发点。

## 代码改完前要读

- `docs/HISTORY_GUIDE.md`：什么时候记 history、怎么命名、怎么脱敏。
- `docs/QUALITY_SCORE.md`：当前质量分层和主要短板。
- `docs/BACKLOG.md`：未排期的 someday/maybe 想法清单；把砍出范围的想法记在这里（已排期的进 execution plan）。

## 按任务需要选读

- `docs/PLANS_GUIDE.md`：什么时候要写 execution plan，怎么维护。
- `docs/PRODUCT_SENSE.md`：产品价值、取舍方式和优先级判断。
- `docs/RELIABILITY.md`：运行稳定性、观测性和上线前的基本要求。
- `docs/SECURITY.md`：认证、数据处理、外部集成等安全默认约束。
- `docs/SUPPLY_CHAIN_SECURITY.md`：依赖、SBOM、制品 provenance 和仓库级供应链安全默认做法。
- `docs/CICD.md`：仓库的 CI/CD 骨架以及后续如何接入真实项目。
- `docs/FRONTEND.md`：如果仓库包含前端界面，这里记录对应规范。
- `CONTRIBUTING.md`：提 PR 前后的默认检查项和协作要求。
- `docs/releases/README.md`：如何维护面向用户的发布记录。
- `docs/releases/RELEASE_GUIDE.md`：只要任务涉及 bump 版本、打 tag、推 release，先读这份发版必读。
- `docs/references/README.md`：沉淀到仓库里的外部参考资料。

## 工作规则

- 优先选择小而清晰、对仓库和 Agent 都友好的抽象。
- 回复默认跟随用户提问所使用的语言；如果用户切换语言，回复语言也随之切换。
- 如果用户这一轮输入是英文，则直接用英文回复。
- 推送前先同步远端最新代码，再执行 `git push`。
- prompt、规则、架构约束尽量都版本化落在仓库里。
- 复杂任务不要只靠聊天上下文，应该落 execution plan。
- 完成的代码变更要记到 `docs/histories/`。
- 工作节奏（merge-then-plan）：PR 的 CI 绿且评审无未决意见即可合并（绿=CI 通过、硬件待验，
  `VERIFICATION.md` 仍是设备侧的真值），随后立即进入 plan mode 规划下一个子阶段并交付审批。
  详见 `CLAUDE.md` 的 Memory harness 第 7 条。
