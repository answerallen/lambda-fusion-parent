# Proposal · lambda-fusion-ai AgentScope 2.0 原生重建

## 背景

`lambda-fusion-ai` 的智能体编排以 langgraph4j（图引擎）+ langchain4j（运行时）为底座，把前端画布节点/边编译成 `StateGraph`，路由/条件/循环/并行全手搓，与 langgraph4j 内部 API 深度耦合。模型能力升级后，图编排脚手架已成多余开销；AgentScope 2.0（Alibaba，Apache-2.0，2026-07 GA）自身已顺此曲线弃用 v1 StateGraph 图 DSL，转向 `HarnessAgent` + subagent + Plan Mode + middleware 的模型自驱动范式。模块仍在开发、无在产下游消费方。

## 目标

- 以 AgentScope 2.0 为**唯一**智能体运行时，按其原生模型（`HarnessAgent` + subagent + Plan Mode + middleware + 分布式会话）重建。
- 移除 langgraph4j；编排/智能体路径移除 langchain4j（6 处耦合全清零）。
- **砍掉整个图层**：`AgentGraph`/9 节点/`GraphDefinition`/`AgentNode` SPI/拓扑编译器/图模板/前端画布集成。
- `apps`（`ai_robot`）升格为 **agent 模板载体**：DB 存储、运行时可编辑、多租户隔离；运行时按模板 `HarnessAgent.builder()` 构造。
- 保留真正有价值且与编排解耦的能力：DB 驱动的模型/工具/MCP 管理与密钥加密、Resilience4j、token 记账/结算、知识库/RAG（AgentScope `Knowledge`，多 KB）、聊天集成。

## 非目标

- 不接入第三方 RAG 平台（百炼/Dify/RAGFlow）本期，用 `-rag-simple` 自托管。
- 不替换 `lambda-fusion-*` 其它业务模块。
- 前端可视化编辑器本期放弃自研画布；运行时观测 UI（Studio/AG-UI）留 Phase 4。

## 决策

见方案 `docs/refactor/ai-agentscope-refactor.md` §10（D1–D9）。要点：D1 推倒重来 / D2 砍图层 / D3 RAG 路径 2 + middleware（砍 forRemoval 编排层，保留未废弃底层，agentic @Tool + static `RagMiddleware` hybrid） / D5 会话后端 PG（`agentscope-extensions-postgresql`，spike 已核实存在）/ D6 apps 升格 agent 模板 / D9 业务表库随 S8。

## 变更范围

- 影响：`lambda-fusion-ai` 模块（`agent/`、`workflow/`、`chat/`、`knowledge/`、`apps/`、`prompt/`、`mcp/`、`llm/` 子树 + DB changelog）。
- 不影响：`lambda-fusion-*` 其它模块的对外契约。
- 详见 `tasks.md`（Phase 0–4）与 `design.md`。
