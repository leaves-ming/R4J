# Implementation Plan: 检索增强知识问答平台

**Branch**: `main` | **Date**: 2026-04-09 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/001-java-rag-monolith/spec.md`

**Note**: 本次为继续修订版计划，目标不是扩 scope，而是把当前仍未闭环的实现缺口收敛为可执行的技术实施方案，并与根目录 `spec.md` 对齐。

## Summary / 概述

当前实现与计划工件存在“文档已宣称闭环、但关键能力仍未彻底落地”的偏差。此次计划以根 `spec.md` 与项目宪章为唯一真源，集中补齐五类问题：

1. `ChunkRecord` 尚未彻底贯穿摄取、存储、Dense/Sparse 检索、引用映射、评估输入和端口契约。
2. 搜索后端仍默认允许 fallback，违背“主运行 profile 真实依赖”的目标态。
3. golden set 文件格式仍未与根 `spec.md` 的 `description/version/test_cases` 规范完全对齐。
4. 真实 rerank 能力仍偏弱，尚未达到“受控启用、真实执行、可回退、可评估”的要求。
5. observability 尚未达到 spec 级别，固定 span 阶段名、日志字段、指标维度、fallback 分类和运行验证仍不完整。

本轮计划的核心原则是：**先统一契约，再切换真实依赖，再增强能力，最后用评估与观测把闭环压实。**

## Technical Context / 技术上下文

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot, LangChain4j Spring Boot starters, Spring Boot Actuator, Micrometer, OpenTelemetry  
**Storage**: PostgreSQL（`document_registry`、`ingestion_job`、`evaluation_*`）, Elasticsearch/OpenSearch（`chunk_record_v1`）, 本地文件系统（原始资料）  
**Testing**: JUnit 5, Spring Boot Test, Mockito, Testcontainers, ArchUnit  
**Target Platform**: Linux 服务端与 Docker 容器环境  
**Project Type**: 后端模块化单体 Web Service  
**Performance Goals**: HitRate@10 >= 0.85；MRR@10 >= 0.70；10k chunks 下查询 p95 <= 2s（`rerank=none`）/ <= 4s（启用 rerank）；10MB 文本导入 <= 60s  
**Constraints**: 确定性 ID；READY-only 可见性；Dense/Sparse 端口独立；检索评估直接读取 `RetrievalResult.topKResults`；主运行 profile 不允许默认 fallback；trace/log/metrics 从 V1 起即为正式能力  
**Scale/Scope**: 单租户内部知识库；>= 50 条 golden set；默认本地依赖 PostgreSQL + Elasticsearch/OpenSearch + 可配置 chat / embedding / rerank provider

## Constitution Check / 宪章检查

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] Contract-first changes are identified：本次优先修订端口契约、评估 schema、观测契约与运行依赖规则。
- [x] Deterministic semantics remain explicit：`DocumentId`、`ChunkId`、RRF、READY-only、检索/生成解耦均保持为硬约束。
- [x] Retrieval / ingestion changes include required tests：已规划契约、集成、架构边界、golden set、观测验证。
- [x] Observability is treated as a first-class feature：本次单独设立 observability 工作流与退出标准。
- [x] Backend/provider selection remains configuration-driven：保留配置驱动，但取消主运行路径的隐式 fallback。

## Phase Exit Criteria / 阶段退出标准

| Phase | Exit Criteria |
|---|---|
| Phase 0 | 根 `spec.md`、本 feature spec、plan、data-model、HTTP contract 对 `ChunkRecord`、golden set、fallback、rerank、observability 的定义一致，无冲突术语 |
| Phase 1 | `ChunkRecord` 已成为摄取到检索的唯一索引记录契约；端口边界、映射责任、READY 过滤责任均明确 |
| Phase 2 | 主运行 profile 默认要求 PostgreSQL/Flyway/Elasticsearch(OpenSearch) 可用；fallback 仅存在于 `test` 或显式 `dev-fallback` |
| Phase 3 | 查询链路支持 Dense/Sparse 并行、稳定 RRF、真实 rerank、单路降级与双路失败区分 |
| Phase 4 | golden set 文件格式、加载器、测试资源、契约文档、quickstart 完全对齐根 spec；评估复用正式 `RetrievalPipeline` |
| Phase 5 | 固定 span 阶段名、结构化日志字段、指标名称与维度齐备，并有验证脚本或测试证明 |

## Current Gaps To Close / 当前待补齐问题

### G1. `ChunkRecord` 未彻底贯穿端口契约

现状风险：
- `Chunk` 与 `ChunkRecord` 语义边界仍不够硬，导致摄取、检索、引用和评估之间可能出现双轨模型。
- Dense / Sparse / 引用映射若各自裁剪字段，容易造成 metadata 漂移与 `chunkId`/`source_path` 不一致。

本次计划决议：
- `Chunk` 只负责切块与 enrich 前后的领域语义。
- `ChunkRecord` 是 **唯一** 可写入 `ChunkStorePort`、可被 Dense/Sparse 检索消费、可被引用映射回表、可供评估还原的索引记录。
- `RetrievalCandidate` / `RankedResult` 必须来源于 `ChunkRecord` 投影，不允许自行拼装缺字段结果。

### G2. 搜索后端默认 fallback 仍存在

现状风险：
- 掩盖真实索引初始化、写入和查询问题。
- 让“主运行 profile 真实依赖”失效，导致 quickstart 与生产路径不一致。

本次计划决议：
- 默认 profile 启动期若搜索后端不可达，直接失败。
- 仅 `test` 或显式 `dev-fallback` profile 可启用内存适配器。
- fallback 必须是显式配置行为，不得是“探测失败后自动切换”。

### G3. golden set 格式未与根 spec 对齐

根 `spec.md` 第 10.1 节规定的真源格式为：
- 顶层字段：`description`、`version`、`test_cases`
- case 字段：`query`、`expected_chunk_ids`、`expected_sources`、`reference_answer`

本次计划决议：
- feature contract、loader、测试资源、quickstart 全部改为根 spec 的 snake_case schema。
- 如确需保留 `caseId`，只能作为向后兼容的可选扩展字段，且不得替代根 spec 字段。
- 不再把 `cases` / `expectedChunkIds` / `v1` 视为长期规范。

### G4. 真实 rerank 能力偏弱

现状风险：
- `provider=llm` 或 `cross-encoder` 仅具备装配意义，缺少稳定的真实执行、超时、错误分类和评估对照。

本次计划决议：
- 先打通一种真实 rerank 实现作为 V1 正式能力，推荐优先 `llm`。
- `provider=none` 与 rerank failure 都必须保持既定 rank 语义不变。
- 增加 rerank on/off 对照评估与观测维度。

### G5. observability 未达 spec 级别

现状风险：
- trace、日志、metrics 可能存在名称漂移、维度不足、阶段不固定、fallback 不可区分的问题。

本次计划决议：
- 以根 spec 第 11 节为准，固定 span 阶段名、日志字段和指标名称。
- query / ingestion / evaluation 的 partial fallback、provider failure、compensation failure 都要有明确观测出口。

## Workstreams / 技术实施工作流

### Workstream A - 契约统一与术语收敛

**Goal**: 先把设计真源统一，避免“按旧文档实现新代码”。

**Outputs**:
- 更新 `plan.md`
- 更新 `data-model.md`
- 更新 `contracts/http-api.md`
- 更新 `quickstart.md`
- 明确 root `spec.md` 为 golden set / observability / `ChunkRecord` 真源

**Key Decisions**:
1. `ChunkRecord` 贯穿 `Embed -> Upsert -> Dense/Lexical Search -> Citation Mapping -> Evaluation`。
2. golden set 以 `description/version/test_cases` 为正式格式。
3. 默认 profile 不允许搜索 fallback。

**Exit Criteria**:
- 所有 planning artifacts 不再存在“已完成”但与现状不符的表述。
- 术语无冲突：`test_cases` / `expected_chunk_ids` / `ChunkRecord` / `partialFallback` / 固定 stage names。

### Workstream B - `ChunkRecord` 端口契约闭环

**Goal**: 让 `ChunkRecord` 成为真正的一等公民，而不是索引层细节。

**Scope**:
- `Chunk -> ChunkRecord` mapper 责任归属
- `ChunkStorePort.upsert(collectionId, List<ChunkRecord>)`
- Dense/Lexical 检索适配器统一从 `ChunkRecord` 投影为 `RetrievalCandidate`
- 引用映射只依赖 `RankedResult` + `ChunkRecord.metadata`
- 评估只读取 `RetrievalResult.topKResults`

**Implementation Rules**:
1. `ChunkId` 只能由领域策略生成，搜索层不能改写。
2. `metadata.source_path`、`metadata.chunk_index`、`metadata.doc_type` 必须稳定。
3. `ChunkRecord` 若缺 `denseVector`，Dense path fail-fast；缺 `sparseTerms`，Sparse path fail-fast。
4. READY 过滤责任由检索适配器承担，不下推给调用方。

**Validation**:
- mapper 单元测试
- 检索适配器契约测试
- 引用映射一致性测试
- evaluation 使用 `topKResults` 的回归测试

### Workstream C - 真实依赖提升与 fallback 收口

**Goal**: 把 PostgreSQL/Flyway/Elasticsearch(OpenSearch) 从“推荐依赖”变成“主运行必需依赖”。

**Implementation Rules**:
1. 启动期索引初始化失败 -> 应用启动失败。
2. 搜索客户端不可达 -> 主运行 profile 启动失败。
3. repository / search fallback 只能由 `test` 或 `dev-fallback` 显式开启。
4. 运行时单路检索失败属于 query fallback；后端装配失败不属于 runtime graceful fallback。

**Validation**:
- profile 级启动测试
- `local` / `prod-like` 配置校验测试
- `test` 与 `dev-fallback` 行为隔离测试

### Workstream D - 检索主链与真实 rerank 增强

**Goal**: 完整落地 `Dense -> Sparse -> RRF -> Rerank -> AnswerAssembler`。

**Implementation Rules**:
1. Dense/Sparse 必须并行。
2. 单路降级时，先转 `RankedResult`，再决定是否 rerank。
3. rerank provider 超时、异常、空响应都必须回退到 fusion 结果。
4. `provider=none`、rerank failure 都不得改写已确定 `rank`。
5. `RetrievalResult.topKResults` 是生成和评估的唯一输入。

**Validation**:
- 并行检索与单路降级集成测试
- rerank on/off A/B golden set 评估
- 性能基线（10k chunks，p95）

### Workstream E - golden set 与评估闭环修正

**Goal**: 评估输入格式、加载器、契约和 quickstart 完整对齐根 spec。

**Implementation Rules**:
1. 正式 schema 使用：
   - `description`
   - `version`（先对齐根 spec `1.0`）
   - `test_cases`
2. `test_cases[*]` 正式字段使用 snake_case。
3. 允许可选扩展字段时，loader 必须明确兼容策略并产生日志。
4. 评估结果持久化、回归比较和 CI 统一使用同一份 schema。

**Validation**:
- schema loader 单元测试
- 非法字段/空 test_cases/未知 version 契约测试
- golden set 回归测试

### Workstream F - Spec 级 observability

**Goal**: 把 trace/log/metrics 从“有一些”提升为“可运维”。

**Required Trace Stages**:
- ingestion: `integrity_check`、`document_load`、`document_split`、`chunk_refine`、`metadata_enrich`、`embedding`、`upsert`、`mark_ready`
- query: `query_processing`、`dense_search`、`sparse_search`、`fusion`、`rerank`、`answer_assemble`
- evaluation: `evaluation_load_cases`、`evaluation_retrieve`、`evaluation_answer`、`evaluation_score`、`evaluation_persist`

**Required Log Fields**:
- `timestamp`
- `level`
- `traceId`
- `module`
- `operation`
- `collectionId`
- `documentId`（如有）
- `chunkId`（如有）
- `provider`（如有）
- `fallbackType`（如有）

**Required Metrics**:
- `query_total`
- `query_failure_total`
- `query_latency_ms`
- `dense_latency_ms`
- `sparse_latency_ms`
- `rerank_latency_ms`
- `llm_latency_ms`
- `ingestion_total`
- `ingestion_failure_total`
- `ingestion_chunk_count`
- `evaluation_run_total`
- `evaluation_metric_value`
- `retrieval_fallback_total`
- `ingestion_compensation_total`

**Validation**:
- trace propagation test
- structured log field assertion test
- meter registry assertion test
- quickstart 中加入观测验证步骤

## Acceptance Mapping / 验收映射

| Acceptance Target | Design Decision | Validation Artifact |
|---|---|---|
| HitRate@10 >= 0.85 | golden set schema 对齐 + 正式 `RetrievalPipeline` 复用 | golden set 评估报告 |
| MRR@10 >= 0.70 | 稳定 RRF + rerank 对照 | golden set 评估报告 |
| p95 <= 2s / 4s | 双路并行 + 受控 rerank | 压测记录 + metrics |
| 10MB 导入 <= 60s | 批量 embed + 正式 `ChunkRecord` upsert | ingestion 集成测试 |
| 100% traceId 贯穿 | OTel trace 复用 + 固定日志字段 | HTTP/日志/评估链路测试 |

## Critical Dependency Rules / 关键依赖规则

1. 先统一 planning artifacts，再开始代码级 remediation。
2. `ChunkRecord` 契约未固定前，禁止分别修 Dense / Sparse 行为。
3. fallback 收口必须早于性能评估，否则评估结论不可信。
4. golden set schema 对齐必须早于评估增强，否则数据资产会继续分叉。
5. observability 固定字段与指标名必须先于 dashboard/CI 适配。
6. 不允许通过 quickstart 或 README 的“说明性文字”掩盖默认运行路径仍依赖 fallback 的事实。

## Implementation Sequence / 推荐实施顺序

1. 修 planning artifacts：plan / data-model / contracts / quickstart / research。
2. 固定 `ChunkRecord` 端口契约与 mapper 边界。
3. 收口搜索 fallback，提升真实依赖为主运行必需项。
4. 补齐 Dense/Sparse 并行、RRF、真实 rerank 与失败分类。
5. 对齐 golden set schema 与评估 loader / CI / 测试资源。
6. 补齐 spec 级 observability，并用测试/脚本验证。
7. 重新生成 `tasks.md`，再进入实现。

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
```

**Structure Decision**: 继续采用模块化单体，但本轮重点不是扩展包结构，而是让契约、依赖和观测约束真正落到这些边界上。

## Complexity Tracking / 复杂度跟踪

- 不新增宪章豁免。
- 当前主要复杂度来自“文档假设闭环但代码与运行路径未闭环”的纠偏，不来自新功能。

## Post-Design Constitution Re-check / 设计后宪章复核

- [x] 设计重新锚定根 `spec.md`，未引入新的契约漂移
- [x] `ChunkRecord`、READY-only、retrieval-to-evaluation decoupling 仍为硬约束
- [x] 可观测性与 golden set 仍作为正式交付门禁
- [x] 当前无需宪章豁免，但必须在下一步重新生成 `tasks.md` 后再进入实现
