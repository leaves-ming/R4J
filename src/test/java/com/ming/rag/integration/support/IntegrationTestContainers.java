package com.ming.rag.integration.support;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@Tag("integration")
public abstract class IntegrationTestContainers {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("rag")
            .withUsername("rag")
            .withPassword("rag");

    @SuppressWarnings("resource")
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.15.3"
    )
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.ml.enabled", "false")
            .withEnv("ingest.geoip.downloader.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .waitingFor(Wait.forLogMessage(".*\"message\":\"started.*", 1))
            .withStartupTimeout(Duration.ofMinutes(5));

    static {
        POSTGRESQL.start();
        ELASTICSEARCH.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("rag.storage.metadata.url", POSTGRESQL::getJdbcUrl);
        registry.add("rag.storage.metadata.username", POSTGRESQL::getUsername);
        registry.add("rag.storage.metadata.password", POSTGRESQL::getPassword);
        registry.add("rag.storage.search.url", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
    }
}
