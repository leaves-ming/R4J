# Data Model / 数据模型

## 1. 核心值对象

| 名称 | 说明 | 约束 |
|---|---|---|
| `CollectionId` | 知识库逻辑范围标识 | 同一请求必须显式或隐式归属一个范围 |
| `DocumentId` | 资料内容指纹 | 基于原始文件内容生成，重复导入同内容时保持稳定 |
| `ChunkId` | 内容片段标识 | 基于 `DocumentId`、片段序号和规范化文本生成 |
| `JobId` | 导入或评估任务标识 | 每次异步或长流程执行唯一 |
| `TraceId` | 统一追踪标识 | 同一入库、问答或评估链路内保持一致 |

## 2. 领域实体

### 2.1 知识库范围

| 字段 | 类型 | 说明 |
|---|---|---|
| `collectionId` | string | 逻辑知识库标识 |
| `displayName` | string | 展示名称 |
| `status` | enum | 可用、停用等逻辑状态 |

关系：

- 一个知识库范围包含多份源资料。
- 一个问答请求只作用于一个知识库范围。

### 2.2 源资料

| 字段 | 类型 | 说明 |
|---|---|---|
| `documentId` | string | 资料内容指纹 |
| `collectionId` | string | 所属知识库范围 |
| `sourcePath` | string | 原始来源路径或引用 |
| `originalFileName` | string | 原始文件名 |
| `mediaType` | string | 文件类型 |
| `status` | enum | `RECEIVED / PROCESSING / READY / FAILED` |
| `chunkCount` | integer | 已生成的片段数量 |
| `errorReason` | string | 失败原因 |
| `createdAt` | datetime | 创建时间 |
| `updatedAt` | datetime | 更新时间 |
| `readyAt` | datetime | 就绪时间 |

状态流转：

```text
RECEIVED -> PROCESSING -> READY
RECEIVED -> PROCESSING -> FAILED
FAILED -> PROCESSING -> READY
FAILED -> PROCESSING -> FAILED
```

规则：

- 只有 `READY` 状态的资料允许被查询与评估读取。
- 同一 `collectionId + documentId` 重入库时应替换旧片段，而不是追加。

### 2.3 内容片段

| 字段 | 类型 | 说明 |
|---|---|---|
| `chunkId` | string | 片段唯一标识 |
| `documentId` | string | 父资料标识 |
| `collectionId` | string | 所属知识库范围 |
| `chunkIndex` | integer | 在资料中的顺序 |
| `content` | string | 规范化后的片段文本 |
| `metadata` | map | 标题、摘要、标签、页码、来源等扩展信息 |
| `denseVector` | vector | 语义向量 |
| `sparseTerms` | map | 词项统计结果 |

规则：

- `content` 在切块清洗后即视为规范化文本，用于检索和 `ChunkId` 生成。
- 元数据增强不得改写 `content`、`DocumentId` 或 `ChunkId`。

### 2.4 检索查询

| 字段 | 类型 | 说明 |
|---|---|---|
| `rawQuery` | string | 原始提问 |
| `normalizedQuery` | string | 归一化后的提问 |
| `keywords` | list<string> | 关键词结果 |
| `filters` | map | 过滤条件 |

规则：

- 过滤条件抽取后剩余自然语言为空时，请求无效。
- 结构化过滤条件优先于 query 文本中的同名过滤条件。

### 2.5 检索结果

| 实体 | 关键字段 | 说明 |
|---|---|---|
| `RetrievalCandidate` | `chunkId`, `score`, `matchedBy`, `content`, `metadata` | Dense 或 Sparse 路径的统一候选结果 |
| `RankedResult` | `chunkId`, `score`, `rank`, `content`, `metadata` | 融合或重排后的有序结果 |
| `RetrievalResult` | `processedQuery`, `topKResults`, `partialFallback`, `traceId`, `debug` | 检索阶段标准输出 |

规则：

- `RetrievalResult.topKResults` 是检索质量评估的唯一输入来源。
- `Citation` 是展示对象，不能替代 `topKResults` 做评估。

### 2.6 问答结果

| 字段 | 类型 | 说明 |
|---|---|---|
| `empty` | boolean | 是否为空结果 |
| `answer` | string | 回答正文 |
| `citations` | list | 引用列表 |
| `traceId` | string | 请求追踪标识 |
| `debug` | map | 调试信息 |

### 2.7 评估任务

| 实体 | 关键字段 | 说明 |
|---|---|---|
| `EvalCase` | `query`, `expectedChunkIds`, `expectedSources`, `referenceAnswer` | 单条标准测试样例 |
| `EvalQueryResult` | `query`, `retrievedTopKChunkIds`, `generatedAnswer`, `metrics`, `elapsedMs` | 单条评估结果 |
| `EvalReport` | `runId`, `evaluatorName`, `testSetPath`, `aggregateMetrics`, `queryResults` | 一次评估任务汇总 |

## 3. 关系视图

```text
知识库范围 1 --- N 源资料
源资料 1 --- N 内容片段
问答结果 N --- N 内容片段（通过引用关联）
评估任务 1 --- N 评估结果
评估结果 1 --- N 内容片段（通过 retrievedTopKChunkIds 关联）
```

## 4. 持久化模型

### 4.1 `document_registry`

| 字段 | 类型 | 说明 |
|---|---|---|
| `collection_id` | varchar | 知识库范围 |
| `document_id` | varchar | 内容指纹 |
| `status` | varchar | 资料状态 |
| `source_path` | varchar | 来源路径 |
| `original_file_name` | varchar | 原始文件名 |
| `media_type` | varchar | 媒体类型 |
| `chunk_count` | int | 片段数 |
| `error_reason` | text | 失败原因 |
| `created_at` | timestamptz | 创建时间 |
| `updated_at` | timestamptz | 更新时间 |
| `ready_at` | timestamptz | READY 时间 |

约束：

- 主键或唯一键覆盖 `collection_id + document_id`。
- 只有 `READY` 可见。

### 4.2 `evaluation_run`

| 字段 | 类型 | 说明 |
|---|---|---|
| `run_id` | uuid | 评估任务标识 |
| `evaluator_name` | varchar | 评估器名称 |
| `test_set_path` | varchar | 测试集路径 |
| `collection_id` | varchar | 评估范围 |
| `aggregate_metrics_json` | jsonb | 汇总指标 |
| `total_elapsed_ms` | double | 总耗时 |
| `created_at` | timestamptz | 创建时间 |

### 4.3 `evaluation_case_result`

| 字段 | 类型 | 说明 |
|---|---|---|
| `run_id` | uuid | 所属评估任务 |
| `query_index` | int | 问题序号 |
| `query` | text | 问题内容 |
| `retrieved_top_k_chunk_ids_json` | jsonb | 检索结果片段 ID 列表 |
| `generated_answer` | text | 生成回答 |
| `metrics_json` | jsonb | 指标明细 |
| `elapsed_ms` | double | 单题耗时 |

## 5. 校验规则

1. 同一资料重复导入时，必须基于内容判断是否重复。
2. 当 `markReady` 失败时，检索侧必须执行补偿删除，保证旧新内容不混杂。
3. 引用结果必须能够回溯到 `documentId + chunkId + sourcePath`。
4. 评估结果中的检索命中列表必须来自检索阶段原始 Top-K，而不是答案阶段的引用列表。
