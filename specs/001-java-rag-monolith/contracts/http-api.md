# HTTP API Contract / 接口契约

## 1. 通用规则

- 基础路径：`/api/v1`
- 所有成功与失败响应都必须带 `traceId`
- 所有非 2xx 错误统一使用共享错误结构
- 所有接口默认只允许访问 `READY` 资料

## 2. POST `/api/v1/ingestions`

### 用途

导入一份资料到指定知识库范围。

### 请求

Content-Type: `multipart/form-data`

| 字段 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `file` | 是 | binary | 上传资料 |
| `collectionId` | 否 | string | 知识库范围，默认 `default` |
| `forceReingest` | 否 | boolean | 是否强制重入库，默认 `false` |
| `chunkSize` | 否 | integer | 本次请求的切块大小覆盖值 |
| `chunkOverlap` | 否 | integer | 本次请求的切块重叠覆盖值 |

### 成功响应

```json
{
  "jobId": "uuid",
  "documentId": "sha256",
  "status": "READY",
  "skipped": false,
  "chunkCount": 42,
  "traceId": "5f2c9b0a8e4d4f4c9f3a1b2c7d8e9f01"
}
```

### 规则

- 如果同一 `collectionId + documentId` 已存在且状态为 `READY`，系统可以返回 `skipped=true`。
- 如果本次处理失败，中间结果不得进入可查询状态。

## 3. POST `/api/v1/queries`

### 用途

基于指定知识库范围执行检索增强问答。

### 请求

Content-Type: `application/json`

```json
{
  "query": "混合检索的原理是什么",
  "collectionId": "default",
  "options": {
    "filters": {
      "doc_type": "pdf"
    }
  },
  "denseTopK": 20,
  "sparseTopK": 20,
  "fusionTopK": 10,
  "rerankTopK": 5,
  "debug": false
}
```

### 校验规则

1. `query` 在移除过滤条件后仍必须保留自然语言内容。
2. 如果 `query` 文本中的 `collection:` 与 `collectionId` 冲突，则直接返回 `INVALID_ARGUMENT`。
3. 结构化 filters 优先于文本内同名 filters；`tags` 允许并集合并。

### 成功响应

成功响应直接返回问答结果结构：

```json
{
  "empty": false,
  "answer": "混合检索结合了语义匹配与关键词匹配。",
  "citations": [
    {
      "index": 1,
      "chunkId": "doc_x_0001_abcd1234",
      "documentId": "doc_x",
      "sourcePath": "docs/guide.pdf",
      "page": 3,
      "score": 0.91,
      "snippet": "混合检索结合了语义检索和关键词检索...",
      "metadata": {
        "title": "检索说明",
        "chunk_index": 1,
        "doc_type": "pdf"
      }
    }
  ],
  "traceId": "5f2c9b0a8e4d4f4c9f3a1b2c7d8e9f01",
  "debug": {}
}
```

## 4. POST `/api/v1/evaluations`

### 用途

运行标准测试集评估，验证检索和回答质量。

### 请求

Content-Type: `application/json`

```json
{
  "testSetPath": "src/test/resources/eval/golden-set.json",
  "collectionId": "default",
  "topK": 10
}
```

### 成功响应

```json
{
  "runId": "uuid",
  "evaluatorName": "default-evaluator",
  "testSetPath": "src/test/resources/eval/golden-set.json",
  "totalElapsedMs": 2100.5,
  "aggregateMetrics": {
    "hit_rate@10": 0.90,
    "mrr@10": 0.76,
    "answer_presence": 0.94
  },
  "queryResults": []
}
```

## 5. 错误响应结构

```json
{
  "errorCode": "INVALID_ARGUMENT",
  "message": "collection filter conflicts with command scope",
  "traceId": "5f2c9b0a8e4d4f4c9f3a1b2c7d8e9f01",
  "details": {}
}
```

### 标准错误码

| 错误码 | 含义 |
|---|---|
| `INVALID_ARGUMENT` | 输入非法或作用域冲突 |
| `NOT_FOUND` | 请求的资源不存在 |
| `CONFLICT` | 当前状态冲突 |
| `PROVIDER_FAILURE` | 外部模型或存储服务失败 |
| `RETRIEVAL_FAILED` | 检索链路完全失败 |
| `INGESTION_FAILED` | 导入链路失败 |
