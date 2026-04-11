package com.ming.rag.unit.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.ming.rag.application.query.AdvisorService;
import com.ming.rag.application.query.QueryCommand;
import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.query.RegisteredTool;
import com.ming.rag.domain.query.port.ToolRegistryPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvisorServiceTest {

    @Test
    void shouldReturnNeverForLocalOnlyQuestion() {
        var service = new AdvisorService(properties(), registry());

        var decision = service.decide(new QueryCommand("公司差旅报销制度是什么", "default", Map.of(), 10, 10, 10, 5, true));

        assertThat(decision.route()).isEqualTo("never");
        assertThat(decision.candidateTools()).isEmpty();
    }

    @Test
    void shouldPreferMustOverPreferWhenRealtimePatternMatches() {
        var service = new AdvisorService(properties(), registry());

        var decision = service.decide(new QueryCommand("最新天气怎么样", "default", Map.of(), 10, 10, 10, 5, true));

        assertThat(decision.route()).isEqualTo("must");
        assertThat(decision.candidateTools()).contains("get_weather");
    }

    private RagProperties properties() {
        return new RagProperties(
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
                new RagProperties.Mcp(true, List.of()),
                new RagProperties.Advisor(
                        true,
                        true,
                        List.of(new RagProperties.Rule("policy-only", List.of("制度"))),
                        List.of(new RagProperties.Rule("prefer-mixed", List.of("结合"))),
                        List.of(new RagProperties.Rule("must-live", List.of("天气"))),
                        List.of("最新", "实时", "天气")
                )
        );
    }

    private ToolRegistryPort registry() {
        return new ToolRegistryPort() {
            @Override
            public List<RegisteredTool> listRegisteredTools() {
                return List.of(new RegisteredTool("weather-mcp", "get_weather", "weather", Map.of(), 10, true));
            }

            @Override
            public List<RegisteredTool> resolveTools(List<String> candidateTools) {
                return listRegisteredTools();
            }
        };
    }
}
