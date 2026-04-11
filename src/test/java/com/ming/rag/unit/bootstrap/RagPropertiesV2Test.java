package com.ming.rag.unit.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.ming.rag.bootstrap.config.RagProperties;
import jakarta.validation.Validator;
import jakarta.validation.Validation;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagPropertiesV2Test {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAcceptValidMcpAndAdvisorConfiguration() {
        var properties = new RagProperties(
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
                        new RagProperties.Server("weather-mcp", true, "http", "mock://weather", List.of(), Duration.ofSeconds(3), List.of("get_weather"), 10)
                )),
                new RagProperties.Advisor(true, true, List.of(), List.of(), List.of(
                        new RagProperties.Rule("must-live", List.of("天气"))
                ), List.of("最新", "天气"))
        );

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void shouldRejectEnabledHttpServerWithoutEndpoint() {
        var server = new RagProperties.Server("broken", true, "http", "", List.of(), Duration.ofSeconds(3), List.of("search_web"), 10);

        assertThat(validator.validate(server))
                .extracting("message")
                .contains("MCP server must define endpoint for http transport or command for stdio transport");
    }
}
