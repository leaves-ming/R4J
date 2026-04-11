package com.ming.rag.unit.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.infrastructure.mcp.McpToolRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpToolRegistryTest {

    @Test
    void shouldRegisterOnlyEnabledServerTools() {
        var registry = new McpToolRegistry(new RagProperties(
                new RagProperties.Ingestion(1000, 200, 100, 5),
                new RagProperties.Query(20, 20, 10, 60),
                new RagProperties.Rerank(false, "none", 5),
                new RagProperties.Ai(
                        new RagProperties.Model("none", null, null, null),
                        new RagProperties.Model("none", null, null, null)
                ),
                new RagProperties.Storage(
                        new RagProperties.Metadata("jdbc:h2:mem:rag", "sa", "", false),
                        new RagProperties.Search("http://localhost:9200", "chunk_record_v1", false, false, true),
                        new RagProperties.File("target/files")
                ),
                new RagProperties.Observability(true, true),
                new RagProperties.Evaluation(10, "default"),
                new RagProperties.Mcp(true, List.of(
                        new RagProperties.Server("weather-mcp", true, "http", "mock://weather", List.of(), Duration.ofSeconds(3), List.of("get_weather"), 10),
                        new RagProperties.Server("disabled-mcp", false, "http", "mock://search", List.of(), Duration.ofSeconds(3), List.of("search_web"), 20)
                )),
                RagProperties.Advisor.disabled()
        ));

        assertThat(registry.listRegisteredTools())
                .extracting("toolName")
                .containsExactly("get_weather");
    }
}
