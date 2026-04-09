# Implementation Plan: 检索增强知识问答平台

**Branch**: `main` | **Date**: 2026-04-07 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/001-java-rag-monolith/spec.md`

**Note**: 本计划基于已有功能规格、根目录架构设计说明和项目宪章生成，作为进入实现前的详细设计与交付计划。

## Summary / 概述

本项目将实现一个基于 Java 21、Spring Boot 与 LangChain4j 的模块化单体 RAG 系统。
第一阶段聚焦三个核心闭环：知识资料入库、带引用问答、质量评估与统一追踪。技术设计将延续
根目录 `spec.md` 中已经明确的架构边界，优先保证确定性 ID、检索与生成解耦、READY-only
可见性、配置驱动扩展，以及从第一版开始具备可观测性与可评估性。本轮计划额外明确将真实
PostgreSQL 持久化、真实 Elasticsearch/OpenSearch Dense/Sparse 检索、`ChunkRecord`
检索契约、LangChain4j provider 装配边界，以及查询双路并行与单路降级语义作为必须落地的设计基线。
基于当前实现现状，本轮继续规划的重点不是“补新功能”，而是把以下仍未真正达标的能力闭环补齐：
真正启用 PostgreSQL / Flyway、真正把 Elasticsearch/OpenSearch 作为运行依赖、让 `ChunkRecord`
成为正式一等公民、将评估文件格式与规格对齐、完成真实 rerank、以及把 trace / 日志 / 指标从部分覆盖补齐到可运营级别。
当前实现阶段将按两个执行里程碑推进：首先完成 Phase 1-2 的默认真实依赖、配置校验、迁移约束与观测命名收敛；随后进入 US1-US3 的垂直切片实现，依次完成导入、查询、评估闭环。

## Technical Context / 技术上下文

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot, LangChain4j Spring Boot starters, Spring Boot Actuator, Micrometer, OpenTelemetry  
**Storage**: PostgreSQL（`document_registry`、`ingestion_job`、`evaluation_*`）, Elasticsearch/OpenSearch（`chunk_record_v1` 检索索引）, 本地文件系统（原始资料）  
**Testing**: JUnit 5, Spring Boot Test, Mockito, Testcontainers, ArchUnit  
**Target Platform**: Linux 服务端与 Docker 容器环境  
**Project Type**: 后端模块化单体 Web Service  
**Performance Goals**: HitRate@10 >= 0.85；MRR@10 >= 0.70；10k chunks 基线下查询 p95 <= 2s（无 rerank）/ <= 4s（启用 rerank）；10MB 文本资料导入 <= 60s  
**Constraints**: 确定性 ID 与检索语义；仅 `READY` 资料可见；Provider 与后端切换必须配置化；Dense/Sparse 端口必须独立；查询双路必须并行且支持单路 fallback；全链路 trace/log/metrics 必须从 V1 即可用  
**Scale/Scope**: V1 面向内部知识库场景；至少 50 条 golden set 用例；10k chunks 基线规模；单租户逻辑范围；默认本地运行依赖 PostgreSQL + Elasticsearch/OpenSearch + 一个聊天模型 provider + 一个 embedding provider

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
| Phase 0 | 技术栈、真实持久化、真实检索、provider 接线、并行检索与观测方案已形成可执行研究结论，且不存在未解决澄清项 |
| Phase 1 | 数据模型、HTTP 契约、快速启动路径和 agent context 已生成完成，并将 `ChunkRecord`、双路检索、补偿删除与评估落库的实现边界写清楚 |

## Current Implementation Gaps / 当前实现缺口

1. **PostgreSQL / Flyway 仍未真正启用**：当前代码虽然已有迁移脚本和 JDBC/Flyway 依赖，但默认应用启动路径仍带兼容性回退，尚未把数据库设为强依赖并完成真实运行校验。
2. **Elasticsearch/OpenSearch 仍非真正运行依赖**：搜索后端不可用时会回退到内存存储，这有利于当前测试，但不满足最终运行基线。
3. **`ChunkRecord` 仍未成为正式一等公民**：虽然已有索引映射与中间视图，但领域、持久化、检索和评估之间仍存在 `Chunk` / `ChunkRecord` 双轨语义。
4. **评估文件格式未完全对齐规格**：当前评估输入仍偏向最小 JSON 数组格式，尚未定义稳定版本、字段约束和校验门禁。
5. **真实 rerank 仍未完成**：当前 rerank provider 装配已存在，但默认实现仍主要是 no-op / 限流式占位。
6. **Trace / 日志 / 指标仍是部分实现**：链路级观测已覆盖主要路径，但字段稳定性、阶段化 span、失败分类指标、provider 级指标和运维文档仍不足。

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
5. `ChunkRecord` 必须先作为统一检索契约固定下来，再分别实现 Dense/Sparse 查询 DSL；禁止两路检索各自定义不同索引文档结构。
6. LangChain4j 相关 bean 只能在 `infrastructure.ai` 装配，并通过 provider/port 适配暴露给应用层；禁止应用服务直接持有具体模型对象。
7. Query 用例必须先完成双路并行编排与单路降级策略，再补充 rerank；禁止先接一个临时串行检索路径替代正式 pipeline。
8. 在 Phase 2 修复中，数据库与搜索后端必须逐步提升为真实运行依赖；保留回退逻辑只允许用于测试 profile 或显式 dev fallback 配置，不得继续作为默认运行模式。
9. 评估输入格式、快速启动文档和 CI 门禁必须使用同一份稳定 schema；禁止“测试样例格式”和“规格格式”长期分叉。

## Delivery Slices / 交付切片

### Slice A - 真实摄取持久化

- 落地 `document_registry`、`ingestion_job` 与文件存储写入。
- 完成 `DocumentStatus` 状态迁移、重复导入判定、补偿删除和重试语义。
- 去除默认运行路径对内存持久化的依赖，只保留测试桩。

### Slice B - ChunkRecord 与真实检索后端

- 固定 `ChunkRecord` 作为唯一索引文档契约，定义索引映射、metadata 标准字段和覆盖写入规则。
- 完成 `DenseSearchPort` 与 `LexicalSearchPort` 的 Elasticsearch/OpenSearch 适配器。
- 完成 RRF 融合和 `READY` 过滤，保证评估读取的仍是标准 `RetrievalResult.topKResults`。

### Slice C - LangChain4j / Provider 接线

- 在 `infrastructure.ai` 中装配 chat、embedding 与 rerank provider。
- 通过 `rag.ai.*`、`rag.rerank.*` 配置切换 provider，而不是修改用例逻辑。
- 为缺失凭据、模型不可用和 provider 关闭场景定义 fail-fast 或受控降级行为。

### Slice D - 查询并行与回答生成

- Dense 与 Sparse 检索并行执行，记录单路耗时、失败原因与 `partialFallback`。
- 将融合后的 Top-K 交给可选 rerank 和回答生成，引用只能来源于最终排序结果。
- 确保空结果、双路失败、单路失败三种情况在契约、日志和指标上可区分。

### Slice E - 评估与运维闭环

- 评估接口复用在线检索 pipeline，落库 `evaluation_run` 与 `evaluation_case_result`。
- 增补 golden set、契约、集成和架构边界测试，覆盖上述真实实现。
- 通过 Actuator、Micrometer 和 OpenTelemetry 暴露导入、检索、重排、评估与补偿指标。

## Phase 2 Remediation Slices / 第二阶段补齐切片

### Slice F - 启用真实 PostgreSQL / Flyway

- 去除主应用对 `DataSourceAutoConfiguration` / `FlywayAutoConfiguration` 的排除，默认 profile 启动即要求 PostgreSQL 可用。
- 修正迁移脚本版本、索引、约束和测试 profile，使 `document_registry`、`ingestion_job`、`evaluation_*` 都通过 Flyway 管理。
- 将 Repository 适配器从“JDBC 优先 + 内存回退”提升为“生产默认 JDBC，测试显式桩实现”。

### Slice G - 启用真实 Elasticsearch/OpenSearch 运行依赖

- 将搜索后端客户端、索引初始化和 `ChunkRecord` 写入从“可回退”改为“默认真实依赖”。
- 只在 `test` 或显式 `dev-fallback` 配置下允许回退到内存搜索存储。
- 为索引初始化失败、写入失败、删除补偿失败提供明确启动失败或运行失败策略。

### Slice H - 让 `ChunkRecord` 成为正式一等公民

- 明确领域到基础设施的映射边界：`Chunk` 负责切块，`ChunkRecord` 负责检索载体，不再使用临时中间结构。
- 为 `ChunkRecord` 增加专用 mapper / record / repository 边界，并在 Dense、Sparse、引用映射、评估输入中统一使用。
- 补齐 `ChunkRecord` 的 createdAt / updatedAt / ready / vector / sparseTerms 规则和测试。

### Slice I - 统一评估文件格式与规格

- 将评估输入从“最小 JSON 数组”提升为版本化 schema，例如 `version` + `cases` 的稳定结构，或在 contract 中明确兼容策略。
- 在 quickstart、契约、测试资源和应用加载器中统一使用同一份格式。
- 为缺失字段、未知字段、空 case、非法 chunkId 等情况提供 fail-fast 校验。

### Slice J - 完成真实 rerank

- 支持至少一种真实 rerank provider 路径：`llm` 或 `cross-encoder`，并明确默认关闭、启用条件、超时与失败回退。
- 将 rerank 指标、日志和 debug 字段从布尔标记提升为阶段化输出，如耗时、provider、回退原因。
- 在 golden set 评估中增加“启用 rerank”和“关闭 rerank”两组对照结果。

### Slice K - 完成可运营级观测

- 对 ingestion / query / evaluation / rerank / search / persistence 各阶段补全统一 trace span 命名和稳定日志字段。
- 为失败类型、fallback 类型、provider 类型、后端类型提供维度化指标。
- 在 README、quickstart 和 CI 中加入观测验证步骤，而不是只记录端点存在。

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
