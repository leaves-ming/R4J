package com.ming.rag.integration.support;

import org.junit.jupiter.api.Tag;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
public abstract class IntegrationTestContainers {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("rag")
            .withUsername("rag")
            .withPassword("rag");

    @Container
    @SuppressWarnings("resource")
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.15.3"
    ).withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("rag.storage.metadata.url", POSTGRESQL::getJdbcUrl);
        registry.add("rag.storage.metadata.username", POSTGRESQL::getUsername);
        registry.add("rag.storage.metadata.password", POSTGRESQL::getPassword);
        registry.add("rag.storage.search.url", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
        registry.add("rag.storage.search.initialize-index-on-startup", () -> false);
    }
}
