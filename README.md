# RAG Monolith

Java 21 + Spring Boot 的模块化单体 RAG 服务，当前正按 `specs/001-java-rag-monolith/tasks.md`
推进真实后端、正式 `ChunkRecord` 契约、golden set schema 与 spec 级 observability 闭环。

- 资料导入：`POST /api/v1/ingestions`
- 带引用问答：`POST /api/v1/queries`
- 质量评估：`POST /api/v1/evaluations`

## Tech Stack

- Java 21
- Spring Boot
- LangChain4j starters
- Micrometer + OpenTelemetry bridge
- PostgreSQL / Elasticsearch 作为真实后端基线

## Current Implementation Focus

- 主运行路径要求 PostgreSQL / Flyway 与 Elasticsearch(OpenSearch) 可用
- `ChunkRecord` 作为唯一索引写入与检索投影契约继续加固
- golden set 正在统一到根 `spec.md` 定义的 `description/version/test_cases` schema
- trace / 日志 / metrics 正在向 spec 级固定字段与指标名收敛

## Local Run

1. 启动依赖：
```bash
docker compose up -d postgres elasticsearch
```

2. 配置环境变量：
```bash
cp .env.example .env
export $(grep -v '^#' .env | xargs)
```

关键变量：

- `RAG_STORAGE_SEARCH_CHUNK_INDEX=chunk_record_v1`
- `RAG_STORAGE_SEARCH_DEV_FALLBACK_ENABLED=false`
- `RAG_RERANK_ENABLED=false`（默认）

3. 启动应用：
```bash
mvn spring-boot:run
```

4. 跑测试：
```bash
mvn test
```

5. 如需只跑主回归链路：
```bash
mvn -q -Dtest=IngestionIntegrationTest,QueryApiContractTest,RetrievalPipelineIntegrationTest,EvaluationPersistenceIntegrationTest,EvaluationApiContractTest test
```

## Quickstart Status

请以 `specs/001-java-rag-monolith/quickstart.md` 作为当前真源。
该 quickstart 描述的是本轮实施与验证路径；截至 **2026-04-09**，主回归链路已通过编译、单测、契约测试与集成测试验证。

## API Summary

- `POST /api/v1/ingestions`
  上传 `multipart/form-data`，返回 `jobId`、`documentId`、`status`、`storage`、`traceId`
- `POST /api/v1/queries`
  提交 JSON 查询，返回 `answer`、`citations`、`traceId`、可选 `debug`
- `POST /api/v1/evaluations`
  提交测试集路径，返回 `runId`、`schemaVersion`、汇总指标和逐题结果

## Observability

- Metrics endpoint: `/actuator/prometheus`
- Sample Prometheus config: [ops/observability/prometheus.yml](ops/observability/prometheus.yml)
- Sample Grafana dashboard: [ops/observability/grafana-dashboard.json](ops/observability/grafana-dashboard.json)
- 关键业务日志已覆盖导入、检索、rerank、回答、评估五条链路，并统一输出 `traceId`
- 关键指标名已统一到：`rag_ingestion_total`、`rag_ingestion_compensation_total`、`rag_query_total`、`rag_retrieval_fallback_total`、`rag_rerank_latency_ms`、`rag_evaluation_run_total`

## Verified Commands (2026-04-09)

```bash
mvn -q -DskipTests compile
mvn -q -Dtest=ProviderConfigurationTest,ArchitectureBoundaryTest,EvaluationUsesRetrievalTopKTest test
mvn -q -Dtest=IngestionApiContractTest,IngestionIntegrationTest,ChunkIdPolicyTest,DocumentIdPolicyTest,IngestionStateMachineTest test
mvn -q -Dtest=QueryApiContractTest,RetrievalPipelineIntegrationTest,QueryProcessorTest,RrfFusionPolicyTest test
mvn -q -Dtest=EvaluationApiContractTest,EvaluationPersistenceIntegrationTest,EvaluationUsesRetrievalTopKTest test
```
