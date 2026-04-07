# Implementation Plan: 检索增强知识问答平台

**Branch**: `001-java-rag-monolith` | **Date**: 2026-04-07 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/001-java-rag-monolith/spec.md`

**Note**: 本计划基于已有功能规格、根目录架构设计说明和项目宪章生成，作为进入实现前的详细设计与交付计划。

## Summary / 概述

本项目将实现一个基于 Java 21、Spring Boot 与 LangChain4j 的模块化单体 RAG 系统。
第一阶段聚焦三个核心闭环：知识资料入库、带引用问答、质量评估与统一追踪。技术设计将延续
根目录 `spec.md` 中已经明确的架构边界，优先保证确定性 ID、检索与生成解耦、READY-only
可见性、配置驱动扩展，以及从第一版开始具备可观测性与可评估性。

## Technical Context / 技术上下文

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot, LangChain4j Spring Boot starters, Spring Boot Actuator, Micrometer, OpenTelemetry  
**Storage**: PostgreSQL（资料状态与评估结果）, Elasticsearch/OpenSearch（检索与索引）, 本地文件系统（原始资料）  
**Testing**: JUnit 5, Spring Boot Test, Mockito, Testcontainers, ArchUnit  
**Target Platform**: Linux 服务端与 Docker 容器环境  
**Project Type**: 后端模块化单体 Web Service  
**Performance Goals**: HitRate@10 >= 0.85；MRR@10 >= 0.70；10k chunks 基线下查询 p95 <= 2s（无 rerank）/ <= 4s（启用 rerank）；10MB 文本资料导入 <= 60s  
**Constraints**: 确定性 ID 与检索语义；仅 `READY` 资料可见；Provider 与后端切换必须配置化；全链路 trace/log/metrics 必须从 V1 即可用  
**Scale/Scope**: V1 面向内部知识库场景；至少 50 条 golden set 用例；10k chunks 基线规模；单租户逻辑范围

## Constitution Check / 宪章检查

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] Contract-first changes are identified: 已明确核心记录、端口接口、HTTP 契约、持久化模型与验收基线。
- [x] Deterministic ID / retrieval semantics are preserved or explicitly amended: 已沿用 `DocumentId`、`ChunkId`、`RetrievalResult`、READY-only 可见性等确定性约束。
- [x] Required tests and golden-set evaluation work are planned for touched retrieval or ingestion behavior: 已规划单元、集成、契约、架构边界与 golden set 评估验证。
- [x] Observability work is included from the first implementation phase, not deferred to polish: 观测性将在基础阶段落地，而非放到收尾阶段。
- [x] Provider or backend changes remain configuration-driven and preserve modular boundaries: 模型、重排器与检索后端都通过配置切换，且保持端口隔离。

## Phase Exit Criteria / 阶段退出标准

| Phase | Exit Criteria |
|---|---|
| Phase 0 | 技术栈、关键集成策略、观测方案与测试策略已形成可执行研究结论，且不存在未解决澄清项 |
| Phase 1 | 数据模型、HTTP 契约、快速启动路径和 agent context 已生成完成，并再次通过宪章检查 |

## Acceptance Mapping / 验收映射

| Acceptance Target | Source of Truth | Validation Artifact |
|---|---|---|
| HitRate@10 >= 0.85 | `spec.md` SC-001 | golden set 评估报告 |
| MRR@10 >= 0.70 | `spec.md` SC-002 | golden set 评估报告 |
| 95% 查询在 4 秒内返回 | `spec.md` SC-003 | 压测基线与指标面板 |
| 10MB 文本资料 60 秒内可导入 | `spec.md` SC-004 | 导入集成测试与运行记录 |
| 响应与日志均带统一追踪标识 | `spec.md` SC-005 | Trace 传播测试与结构化日志校验 |

## Critical Dependency Rules / 关键依赖规则

1. 实现阶段必须先定义领域记录、配置契约和观测约束，再进入服务编排与基础设施适配。
2. 问答路径与评估路径必须复用同一条 `RetrievalPipeline`，禁止维护第二套临时检索逻辑。
3. 检索结果的评估输入必须来自检索阶段的标准 Top-K 结果，而不是答案阶段的引用结果。
4. 存储层必须同时满足文档状态登记与检索侧数据一致性；若 `READY` 转换失败，必须执行补偿删除并保持非可见状态。

## Project Structure / 项目结构

### Documentation (this feature) / 文档产物

```text
specs/001-java-rag-monolith/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── http-api.md
└── tasks.md
```

### Source Code (repository root) / 代码结构

```text
src/main/java/com/ming/rag
├── bootstrap
├── domain
│   ├── common
│   ├── ingestion
│   ├── query
│   ├── retrieval
│   ├── response
│   └── evaluation
├── application
│   ├── ingestion
│   ├── query
│   └── evaluation
├── interfaces
│   ├── http
│   ├── cli
│   └── scheduler
├── infrastructure
│   ├── ai
│   ├── search
│   ├── persistence
│   └── storage
└── observability

src/test/java/com/ming/rag
├── contract
├── integration
├── unit
└── architecture

src/test/resources
└── eval
```

**Structure Decision**: 采用单工程后端模块化单体结构。领域模型与应用编排分离，基础设施只实现端口；
测试按契约、集成、单元与架构边界拆分，确保后续任务可以围绕明确边界推进。

## Complexity Tracking / 复杂度跟踪

当前未发现需要额外豁免的宪章违反项，暂无必须单独记录的复杂度例外。

## Post-Design Constitution Re-check / 设计后宪章复核

- [x] 计划、数据模型、接口契约与快速启动文档均已生成，且与功能规格保持一致
- [x] 设计仍然遵守确定性 ID、检索与生成解耦、READY-only 可见性与配置驱动扩展原则
- [x] 观测、测试、评估与交付门禁已纳入设计产物，而非延后处理
- [x] 当前无需记录额外的宪章豁免项
