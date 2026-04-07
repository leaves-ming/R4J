# Quickstart / 快速开始

## 目标

在本地启动依赖环境，运行应用，导入一份资料，发起一次问答，并执行一次评估流程，
用于验证规划产物与实际交付路径一致。

## 前置条件

- Java 21+
- Maven 3.9+
- Docker 与 Docker Compose
- 可用的模型与向量服务凭据

## 1. 启动本地依赖

```bash
docker compose up -d postgres elasticsearch
```

预期：

- PostgreSQL 用于资料状态与评估结果存储
- Elasticsearch 或 OpenSearch 用于检索与索引

## 2. 配置环境变量

```bash
export SPRING_PROFILES_ACTIVE=local
export RAG_STORAGE_METADATA_URL=jdbc:postgresql://localhost:5432/rag
export RAG_STORAGE_METADATA_USERNAME=rag
export RAG_STORAGE_METADATA_PASSWORD=rag
export RAG_STORAGE_SEARCH_URL=http://localhost:9200
export RAG_AI_CHAT_API_KEY=replace_me
export RAG_AI_EMBEDDING_API_KEY=replace_me
```

## 3. 启动应用

```bash
./mvnw spring-boot:run
```

检查点：

- 健康检查端点返回 `UP`
- 数据库迁移成功
- 检索索引初始化成功
- 启动日志中可看到 trace / logging / metrics 已启用

## 4. 导入测试资料

```bash
curl -X POST http://localhost:8080/api/v1/ingestions \
  -F "file=@./samples/guide.pdf" \
  -F "collectionId=default"
```

预期：

- 响应包含 `jobId`、`documentId`、`status`、`traceId`
- 成功后状态为 `READY`
- 重复上传相同内容时，如未开启 `forceReingest`，可以返回跳过结果

## 5. 发起问答

```bash
curl -X POST http://localhost:8080/api/v1/queries \
  -H "Content-Type: application/json" \
  -d '{
    "query": "混合检索的原理是什么",
    "collectionId": "default",
    "denseTopK": 20,
    "sparseTopK": 20,
    "fusionTopK": 10,
    "rerankTopK": 5
  }'
```

预期：

- 响应包含 `answer`、`citations`、`traceId`
- 引用只指向 `READY` 状态资料
- 日志与指标可以通过同一个 `traceId` 关联

## 6. 执行评估

```bash
curl -X POST http://localhost:8080/api/v1/evaluations \
  -H "Content-Type: application/json" \
  -d '{
    "testSetPath": "src/test/resources/eval/golden-set.json",
    "collectionId": "default",
    "topK": 10
  }'
```

预期：

- 返回汇总指标与逐题结果
- 检索质量指标使用检索阶段 Top-K，而不是引用列表
- 评估结果可以落库并用于后续回归比较

## 7. 验证清单

- `DocumentIdPolicy` 与 `ChunkIdPolicy` 测试通过
- 导入补偿删除与重试测试通过
- 检索 fallback、READY-only 可见性测试通过
- 三个 V1 接口契约测试通过
- golden set 回归结果满足规格中的验收基线
