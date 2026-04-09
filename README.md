# RAG Monolith

Java 21 + Spring Boot 的模块化单体 RAG 服务，当前已经具备三条主链路：

- 资料导入：`POST /api/v1/ingestions`
- 带引用问答：`POST /api/v1/queries`
- 质量评估：`POST /api/v1/evaluations`

## Tech Stack

- Java 21
- Spring Boot
- LangChain4j starters
- Micrometer + OpenTelemetry bridge
- PostgreSQL / Elasticsearch 作为真实后端基线

## Current Implementation Status

- 导入链路已具备真实文件落盘、文档状态落库、`ChunkRecord` 搜索写入与补偿删除逻辑
- 查询链路已具备 Dense / Sparse 双路并行、RRF 融合、single-path fallback、真实 rerank debug 语义与引用式回答
- 评估链路已具备 `version + cases` 版本化测试集加载、结果落库、汇总指标和检索 Top-K 解耦验证
- 主运行路径默认要求 PostgreSQL / Flyway + Elasticsearch(OpenSearch) 可用；仅 `test` 或显式 `dev-fallback` 配置允许搜索回退

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

## Verified Quickstart

以下路径已在本仓库于 **2026-04-09** 通过自动化验证：

1. `POST /api/v1/ingestions` 契约与集成测试
2. `POST /api/v1/queries` 契约、并行检索、single-path fallback 与双路失败测试
3. `POST /api/v1/evaluations` 契约、版本化 schema、持久化与 golden set 回归测试

当前样例评估结果：

- `hit_rate@10 = 1.0`
- `mrr@10 = 1.0`
- `answer_presence = 1.0`
- `rerank_hit_rate@10 = 1.0`

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
