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
- PostgreSQL / Elasticsearch 作为设计基线

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

## Verified Quickstart

以下路径已在本仓库实测通过：

1. `POST /api/v1/ingestions` 导入 [guide.md](/Users/mac/code/rag/samples/guide.md)
2. `POST /api/v1/queries` 查询 `hybrid retrieval`
3. `POST /api/v1/evaluations` 使用 [golden-set.json](/Users/mac/code/rag/src/test/resources/eval/golden-set.json)

当前样例评估结果：

- `hit_rate@10 = 1.0`
- `mrr@10 = 1.0`
- `answer_presence = 1.0`

## API Summary

- `POST /api/v1/ingestions`
  上传 `multipart/form-data`，返回 `jobId`、`documentId`、`status`、`traceId`
- `POST /api/v1/queries`
  提交 JSON 查询，返回 `answer`、`citations`、`traceId`
- `POST /api/v1/evaluations`
  提交测试集路径，返回 `runId`、汇总指标和逐题结果

## Observability

- Metrics endpoint: `/actuator/prometheus`
- Sample Prometheus config: [ops/observability/prometheus.yml](/Users/mac/code/rag/ops/observability/prometheus.yml)
- Sample Grafana dashboard: [ops/observability/grafana-dashboard.json](/Users/mac/code/rag/ops/observability/grafana-dashboard.json)
