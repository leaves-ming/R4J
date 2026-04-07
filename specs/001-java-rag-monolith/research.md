# Research: 检索增强知识问答平台

## 1. 应用基础框架

**Decision**: 使用 Java 21 + Spring Boot 作为应用基础框架。  
**Rationale**: 根目录架构说明已经把 Java 21 与 Spring Boot 定为基线；Spring Boot 提供配置管理、
依赖注入、Actuator、测试支持与容器化友好的运行方式，适合作为模块化单体的承载框架。  
**Alternatives considered**:
- Quarkus：启动更快，但当前项目已有 Spring Boot 基线与生态要求，不作为 V1 默认方案。
- Micronaut：配置与原生能力较强，但团队已有文档与约束均围绕 Spring Boot 展开。

## 2. 配置管理方式

**Decision**: 所有 `rag.*` 配置统一使用 `@ConfigurationProperties` 和校验注解承载，不采用散落的 `@Value`。  
**Rationale**: Spring Boot 官方文档明确给出了 `@ConfigurationProperties` 的类型安全、元数据支持、
松绑定与校验能力，并对比说明其能力强于 `@Value` 的零散注入方式。  
**Alternatives considered**:
- `@Value`：实现简单，但不利于集中配置、校验与演进，不符合现有架构说明中的 fail-fast 约束。

## 3. LangChain4j 与 Spring Boot 集成方式

**Decision**: 使用 LangChain4j 官方 Spring Boot starters 集成模型、Embedding 能力和 AI Services。  
**Rationale**: LangChain4j 官方文档说明 Spring Boot starter 可以通过配置自动创建 chat model、
embedding model、embedding store 等核心组件；同时 `langchain4j-spring-boot-starter` 支持
AI Services、RAG、Tools 等能力的自动装配。  
**Alternatives considered**:
- 手工创建所有 LangChain4j 组件：灵活但样板代码较多，不利于快速搭建 V1。
- 直接使用其他 LLM SDK：会削弱与现有架构文档中端口抽象和 provider 替换能力的一致性。

## 4. RAG 实现策略

**Decision**: 采用可定制的模块化 RAG 流程，而不是 Easy RAG。  
**Rationale**: LangChain4j 官方 RAG 教程指出其提供 Easy RAG、Naive RAG 与 Advanced RAG 三种形态，
其中 Advanced RAG 支持查询变换、多路检索与重排，更适合当前项目对确定性契约、混合检索、
重排和评估解耦的要求。  
**Alternatives considered**:
- Easy RAG：接入最快，但对索引、切块、过滤、排序和可观测性控制不足。
- Naive RAG：适合最小化向量检索样例，但不能很好覆盖本项目的混合检索目标。

## 5. 检索后端基线

**Decision**: V1 以 Elasticsearch / OpenSearch 作为默认检索后端，但混合检索融合逻辑放在应用层完成。  
**Rationale**: LangChain4j 官方 Elasticsearch 集成文档说明其同时支持向量检索、全文检索和内容检索器；
同时官方文档也指出 vendor 级 hybrid 配置依赖特定能力，甚至可能需要付费许可。为了满足
根目录架构说明中 “DenseSearchPort 与 LexicalSearchPort 必须独立” 的原则，V1 选择在应用层
维护 Dense、Sparse 与 RRF 融合，而不是将混合逻辑绑定在单一厂商实现中。  
**Alternatives considered**:
- PostgreSQL + PGVector：部署与一致性更简单，但全文检索与既定基线不如 Elasticsearch / OpenSearch 明确。
- 直接依赖搜索引擎的 hybrid search：实现更快，但会削弱端口隔离与跨后端迁移能力。

## 6. 可观测性方案

**Decision**: 使用 Spring Boot Actuator + Micrometer + OpenTelemetry 作为主观测方案，并在业务代码中补充手工埋点。  
**Rationale**: Spring Boot 文档明确将 Actuator、Metrics、Tracing 作为生产可用能力的一部分；
OpenTelemetry 官方文档说明 Java 侧既可以使用 Spring Boot starter，也可以在 Spring 组件中直接注入
`OpenTelemetry` 来创建自定义 span 与指标。LangChain4j 官方 observability 文档同时指出其
Micrometer 指标模块存在实验性约束，因此 V1 以框架级与业务级观测为主，把 LangChain4j 自带指标
视为可选增强。  
**Alternatives considered**:
- 仅靠日志排查：无法满足统一 traceId、指标告警与性能分析需求。
- 仅依赖 LangChain4j 的实验性观测模块：未来破坏性变化风险更高，不适合作为唯一观测基础。

## 7. 测试与验证策略

**Decision**: 使用 JUnit 5、Spring Boot Test、Testcontainers、ArchUnit 组合构建测试体系。  
**Rationale**: Spring Boot 官方测试文档提供了面向 Spring Boot 应用、Testcontainers 与测试模块的
支持路径；ArchUnit 用于落实模块边界与依赖方向约束。该组合可以覆盖单元、集成、契约与架构规则。  
**Alternatives considered**:
- 只做单元测试：无法验证数据库、搜索引擎、状态补偿与 HTTP 契约。
- 只做端到端测试：定位问题成本高，不利于演进。

## 8. 运行与交付方式

**Decision**: 本地开发默认使用 Docker Compose 拉起 PostgreSQL 与 Elasticsearch/OpenSearch，应用通过 Maven 启动。  
**Rationale**: 该方式与 Spring Boot 的本地运行、Testcontainers 测试和后续 Docker 化交付路径一致，
能够在开发、测试和交付之间保持环境模型接近。  
**Alternatives considered**:
- 全部使用本机安装依赖：环境漂移大，不利于团队协作。
- 一开始就上复杂部署编排：超出 V1 范围。

## 参考资料

- Spring Boot Externalized Configuration: https://docs.spring.io/spring-boot/reference/features/external-config.html
- Spring Boot Testing: https://docs.spring.io/spring-boot/reference/testing/index.html
- Spring Boot Actuator / Production-ready Features: https://docs.spring.io/spring-boot/reference/actuator/index.html
- LangChain4j Spring Boot Integration: https://docs.langchain4j.dev/tutorials/spring-boot-integration/
- LangChain4j RAG Tutorial: https://docs.langchain4j.dev/tutorials/rag/
- LangChain4j Elasticsearch Integration: https://docs.langchain4j.dev/integrations/embedding-stores/elasticsearch/
- LangChain4j Observability: https://docs.langchain4j.dev/tutorials/observability/
- OpenTelemetry Java zero-code instrumentation: https://opentelemetry.io/docs/zero-code/java/
- OpenTelemetry Spring Boot starter API extension: https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/api/
