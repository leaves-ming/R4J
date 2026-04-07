# Tasks: 检索增强知识问答平台

**Input**: 设计文档来自 `/Users/mac/code/rag/specs/001-java-rag-monolith/`  
**Prerequisites**: `plan.md`、`spec.md`、`research.md`、`data-model.md`、`contracts/`、`quickstart.md`

**Tests**: 本特性涉及契约、导入、检索、评估、持久化与架构边界，因此测试任务为必需项。

**Organization**: 任务按用户故事组织，确保每个故事都能独立实现、独立验证、独立演示。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行，仅用于不同文件且不依赖未完成任务的工作
- **[Story]**: 仅用户故事阶段使用，如 `[US1]`、`[US2]`、`[US3]`
- 所有任务必须带精确文件路径

## Path Conventions

- 主代码路径：`src/main/java/com/ming/rag/`
- 测试路径：`src/test/java/com/ming/rag/`
- 测试资源路径：`src/test/resources/`
- 数据库迁移路径：`src/main/resources/db/migration/`

## Phase 1: Setup / 初始化

**Purpose**: 建立 Java 21 + Spring Boot 项目骨架和基础工程配置

- [X] T001 创建 Maven 工程骨架与目录结构，覆盖 `./pom.xml`、`src/main/java/com/ming/rag/`、`src/test/java/com/ming/rag/`、`src/test/resources/`
- [X] T002 初始化项目依赖与插件配置到 `./pom.xml`，包含 Spring Boot、LangChain4j、Actuator、Micrometer、OpenTelemetry、PostgreSQL、Elasticsearch/OpenSearch、Testcontainers、ArchUnit
- [X] T003 [P] 创建主包结构到 `src/main/java/com/ming/rag/bootstrap/`、`domain/`、`application/`、`interfaces/`、`infrastructure/`、`observability/`
- [X] T004 [P] 创建测试包结构到 `src/test/java/com/ming/rag/unit/`、`integration/`、`contract/`、`architecture/`
- [X] T005 [P] 初始化本地开发交付文件 `./Dockerfile`、`./docker-compose.yml`、`./.env.example`

---

## Phase 2: Foundational / 基础阻塞能力

**Purpose**: 任何用户故事开始前都必须具备的核心契约、配置、观测与持久化基础

**⚠️ CRITICAL**: 所有用户故事都依赖本阶段完成

- [X] T006 定义配置属性类到 `src/main/java/com/ming/rag/bootstrap/config/`，覆盖导入、检索、重排、存储、观测、评估配置
- [X] T007 [P] 定义基础值对象与领域记录到 `src/main/java/com/ming/rag/domain/`，覆盖 `DocumentId`、`ChunkId`、`SourceDocument`、`Chunk`、`RetrievalResult`、`EvalReport`
- [X] T008 [P] 定义领域异常与统一错误码到 `src/main/java/com/ming/rag/domain/common/exception/` 与 `src/main/java/com/ming/rag/interfaces/http/dto/ErrorResponse.java`
- [X] T009 [P] 定义端口接口到 `src/main/java/com/ming/rag/domain/**/port/`，覆盖导入、检索、重排、回答、评估、注册表与存储端口
- [X] T010 [P] 实现 trace 上下文、结构化日志字段与指标名称常量到 `src/main/java/com/ming/rag/observability/`
- [X] T011 创建数据库迁移脚本 `src/main/resources/db/migration/V1__document_registry.sql`
- [X] T012 [P] 创建数据库迁移脚本 `src/main/resources/db/migration/V2__evaluation_tables.sql`
- [X] T013 [P] 创建检索索引初始化组件到 `src/main/java/com/ming/rag/infrastructure/search/ChunkIndexInitializer.java`
- [X] T014 配置 HTTP 层统一异常映射与 trace 传播到 `src/main/java/com/ming/rag/interfaces/http/`

**Checkpoint**: 完成后，项目骨架、领域契约、观测约束和持久化基础已经就绪，用户故事可以开始实现

---

## Phase 3: User Story 1 - 管理知识资料入库 (Priority: P1) 🎯 MVP

**Goal**: 支持资料导入、状态流转、去重与重试，并保证仅 `READY` 资料可见

**Independent Test**: 上传一份资料后能看到状态进入 `READY`；重复上传相同内容时不会产生重复可见结果；失败后可重新处理

### Tests for User Story 1 / 用户故事 1 测试

- [X] T015 [P] [US1] 编写 `DocumentIdPolicy` 与 `ChunkIdPolicy` 单元测试到 `src/test/java/com/ming/rag/unit/domain/common/`
- [X] T016 [P] [US1] 编写导入状态流转与重试行为测试到 `src/test/java/com/ming/rag/unit/ingestion/IngestionStateMachineTest.java`
- [ ] T017 [P] [US1] 编写导入集成测试到 `src/test/java/com/ming/rag/integration/IngestionIntegrationTest.java`，覆盖去重、补偿删除与 READY-only 可见性
- [ ] T018 [P] [US1] 编写导入接口契约测试到 `src/test/java/com/ming/rag/contract/IngestionApiContractTest.java`

### Implementation for User Story 1 / 用户故事 1 实现

- [X] T019 [P] [US1] 实现 `DocumentIdPolicy` 与 `ChunkIdPolicy` 到 `src/main/java/com/ming/rag/domain/common/`
- [ ] T020 [P] [US1] 实现资料加载器到 `src/main/java/com/ming/rag/infrastructure/storage/DocumentLoaderAdapter.java`
- [ ] T021 [P] [US1] 实现切块与规范化清洗服务到 `src/main/java/com/ming/rag/application/ingestion/DocumentSplitterService.java` 与 `ChunkRefinerService.java`
- [ ] T022 [P] [US1] 实现元数据增强服务到 `src/main/java/com/ming/rag/application/ingestion/MetadataEnricherService.java`
- [ ] T023 [P] [US1] 实现 Embedding 与词项编码适配器到 `src/main/java/com/ming/rag/infrastructure/ai/EmbeddingAdapter.java` 与 `src/main/java/com/ming/rag/infrastructure/search/LexicalEncoderAdapter.java`
- [ ] T024 [P] [US1] 实现文档注册表适配器到 `src/main/java/com/ming/rag/infrastructure/persistence/DocumentRegistryRepository.java`
- [ ] T025 [P] [US1] 实现片段存储适配器到 `src/main/java/com/ming/rag/infrastructure/search/SearchChunkStore.java`
- [ ] T026 [US1] 实现导入应用服务到 `src/main/java/com/ming/rag/application/ingestion/IngestionApplicationService.java`
- [ ] T027 [US1] 实现导入 HTTP 控制器到 `src/main/java/com/ming/rag/interfaces/http/IngestionController.java`
- [ ] T028 [US1] 为导入链路补充日志、trace 和指标埋点到 `src/main/java/com/ming/rag/observability/` 与 `src/main/java/com/ming/rag/application/ingestion/`

**Checkpoint**: 到此为止，资料导入链路应可独立运行与验证，是最小可交付版本

---

## Phase 4: User Story 2 - 发起带引用的知识问答 (Priority: P2)

**Goal**: 支持混合检索、可选重排、引用式回答与空结果处理

**Independent Test**: 在已有资料前提下，提交问题后可以得到答案、引用与 traceId；无命中时返回明确空结果

### Tests for User Story 2 / 用户故事 2 测试

- [ ] T029 [P] [US2] 编写 QueryProcessor 行为测试到 `src/test/java/com/ming/rag/unit/query/QueryProcessorTest.java`
- [ ] T030 [P] [US2] 编写 RRF 融合与排序稳定性测试到 `src/test/java/com/ming/rag/unit/retrieval/RrfFusionPolicyTest.java`
- [ ] T031 [P] [US2] 编写检索链路集成测试到 `src/test/java/com/ming/rag/integration/RetrievalPipelineIntegrationTest.java`，覆盖单路降级、READY-only 可见性、空结果
- [ ] T032 [P] [US2] 编写问答接口契约测试到 `src/test/java/com/ming/rag/contract/QueryApiContractTest.java`

### Implementation for User Story 2 / 用户故事 2 实现

- [ ] T033 [P] [US2] 实现查询预处理服务到 `src/main/java/com/ming/rag/application/query/QueryProcessorService.java`
- [ ] T034 [P] [US2] 实现 Dense 检索适配器到 `src/main/java/com/ming/rag/infrastructure/search/DenseSearchAdapter.java`
- [ ] T035 [P] [US2] 实现 Sparse 检索适配器到 `src/main/java/com/ming/rag/infrastructure/search/LexicalSearchAdapter.java`
- [ ] T036 [P] [US2] 实现 RRF 融合策略到 `src/main/java/com/ming/rag/application/query/RrfFusionPolicy.java`
- [ ] T037 [P] [US2] 实现重排适配器到 `src/main/java/com/ming/rag/infrastructure/ai/RerankerAdapter.java`
- [ ] T038 [US2] 实现统一检索流水线到 `src/main/java/com/ming/rag/application/query/RetrievalPipelineService.java`
- [ ] T039 [P] [US2] 实现回答生成器与引用工厂到 `src/main/java/com/ming/rag/infrastructure/ai/AnswerGeneratorAdapter.java` 与 `src/main/java/com/ming/rag/application/query/CitationFactoryService.java`
- [ ] T040 [US2] 实现问答应用服务到 `src/main/java/com/ming/rag/application/query/QueryApplicationService.java`
- [ ] T041 [US2] 实现问答 HTTP 控制器到 `src/main/java/com/ming/rag/interfaces/http/QueryController.java`
- [ ] T042 [US2] 为问答链路补充 fallback 指标、trace 阶段与结构化日志到 `src/main/java/com/ming/rag/observability/` 与 `src/main/java/com/ming/rag/application/query/`

**Checkpoint**: 到此为止，系统应能够基于已导入资料完成带引用问答

---

## Phase 5: User Story 3 - 评估问答质量并追踪问题 (Priority: P3)

**Goal**: 支持标准测试集评估、结果持久化与质量回归分析

**Independent Test**: 指定一组测试问题后，系统能输出逐题结果、汇总指标，并保证检索评估使用检索阶段 Top-K

### Tests for User Story 3 / 用户故事 3 测试

- [ ] T043 [P] [US3] 编写评估解耦回归测试到 `src/test/java/com/ming/rag/unit/evaluation/EvaluationUsesRetrievalTopKTest.java`
- [ ] T044 [P] [US3] 编写评估持久化集成测试到 `src/test/java/com/ming/rag/integration/EvaluationPersistenceIntegrationTest.java`
- [ ] T045 [P] [US3] 编写评估接口契约测试到 `src/test/java/com/ming/rag/contract/EvaluationApiContractTest.java`
- [ ] T046 [P] [US3] 创建 golden set 测试集到 `src/test/resources/eval/golden-set.json`

### Implementation for User Story 3 / 用户故事 3 实现

- [ ] T047 [P] [US3] 实现评估结果持久化适配器到 `src/main/java/com/ming/rag/infrastructure/persistence/EvaluationReportRepository.java`
- [ ] T048 [US3] 实现评估应用服务到 `src/main/java/com/ming/rag/application/evaluation/EvaluationApplicationService.java`
- [ ] T049 [US3] 实现评估 HTTP 控制器到 `src/main/java/com/ming/rag/interfaces/http/EvaluationController.java`
- [ ] T050 [US3] 为评估链路补充汇总指标、阶段 trace 与运行日志到 `src/main/java/com/ming/rag/observability/` 与 `src/main/java/com/ming/rag/application/evaluation/`

**Checkpoint**: 到此为止，系统应能够通过标准测试集验证检索与回答质量

---

## Phase 6: Polish & Cross-Cutting Concerns / 收尾与跨故事能力

**Purpose**: 完善运行、交付、文档和跨阶段验证能力

- [ ] T051 [P] 更新本地启动与交付文档到 `/Users/mac/code/rag/specs/001-java-rag-monolith/quickstart.md` 与 `./README.md`
- [ ] T052 [P] 补充架构边界测试到 `src/test/java/com/ming/rag/architecture/ArchitectureBoundaryTest.java`
- [ ] T053 [P] 配置 Prometheus/Grafana 观测样例到 `ops/observability/`
- [ ] T054 配置 CI 流水线到 `.github/workflows/ci.yml`，运行单元、集成、契约、架构边界与 golden set 测试
- [ ] T055 运行 quickstart 验证并修正文档到 `/Users/mac/code/rag/specs/001-java-rag-monolith/quickstart.md`

---

## Checkpoints / 里程碑

- C1: 完成 Phase 1-2，工程骨架、核心契约、配置、观测与持久化基础已就绪
- C2: 完成 Phase 3，资料导入链路已可独立演示，是 MVP 最小范围
- C3: 完成 Phase 4，带引用问答路径可用，形成面向使用者的核心功能
- C4: 完成 Phase 5，质量评估与追踪闭环可用
- C5: 完成 Phase 6，交付文档、CI 与观测能力满足上线前检查

---

## Dependencies & Execution Order / 依赖与执行顺序

### Phase Dependencies

- **Setup / 初始化（Phase 1）**: 无前置依赖，可立即开始
- **Foundational / 基础阻塞（Phase 2）**: 依赖初始化完成，阻塞所有用户故事
- **User Story 1（Phase 3）**: 依赖基础阻塞阶段完成，是 MVP 起点
- **User Story 2（Phase 4）**: 依赖基础阻塞阶段完成，同时默认依赖用户故事 1 已提供可查询资料
- **User Story 3（Phase 5）**: 依赖用户故事 2 的检索与回答链路完成
- **Polish / 收尾（Phase 6）**: 依赖所有目标故事完成

### User Story Dependencies

- **User Story 1 (P1)**: 不依赖其他故事，是最小可交付能力
- **User Story 2 (P2)**: 依赖导入链路先具备可查询数据
- **User Story 3 (P3)**: 依赖问答链路和检索结果已经稳定输出

### Within Each User Story

- 测试优先于实现
- 领域规则与适配器先于应用服务
- 应用服务先于 HTTP 接口
- 核心链路先于观测补充与文档修订

### Parallel Opportunities

- 初始化阶段的目录和脚手架任务可并行
- 基础阻塞阶段中配置、异常、观测、迁移脚本可部分并行
- 用户故事阶段中不同文件的适配器与策略类可并行
- 契约测试、单元测试与部分集成测试可并行准备

---

## Parallel Example: User Story 1 / 并行示例

```bash
# 可以并行推进的测试任务
Task: "T015 [US1] 编写 DocumentIdPolicy 与 ChunkIdPolicy 单元测试到 src/test/java/com/ming/rag/unit/domain/common/"
Task: "T018 [US1] 编写导入接口契约测试到 src/test/java/com/ming/rag/contract/IngestionApiContractTest.java"

# 可以并行推进的实现任务
Task: "T020 [US1] 实现资料加载器到 src/main/java/com/ming/rag/infrastructure/storage/DocumentLoaderAdapter.java"
Task: "T022 [US1] 实现元数据增强服务到 src/main/java/com/ming/rag/application/ingestion/MetadataEnricherService.java"
Task: "T024 [US1] 实现文档注册表适配器到 src/main/java/com/ming/rag/infrastructure/persistence/DocumentRegistryRepository.java"
```

---

## Implementation Strategy / 实施策略

### MVP First / 先做最小可交付

1. 完成 Phase 1 初始化
2. 完成 Phase 2 基础阻塞能力
3. 完成 Phase 3 用户故事 1
4. 停下来验证资料导入链路

### Incremental Delivery / 增量交付

1. 先交付资料导入能力
2. 再交付带引用问答能力
3. 最后补齐评估与回归能力
4. 每个阶段都保证可以独立验证和演示

### Parallel Team Strategy / 并行协作建议

1. 一名成员负责基础契约与配置
2. 一名成员负责导入与持久化链路
3. 一名成员负责检索、问答与评估链路
4. 收尾阶段统一合并观测、CI 与文档交付

---

## Notes / 说明

- 所有任务都遵守 `- [ ] Txxx [P?] [US?] 描述 + 文件路径` 格式
- 用户故事阶段的任务均已带 `[US1]`、`[US2]`、`[US3]` 标签
- 建议 MVP 范围为 User Story 1，即先实现资料导入和状态闭环
- 后续如采用 TDD，可直接按当前测试任务先写失败用例再进入实现
