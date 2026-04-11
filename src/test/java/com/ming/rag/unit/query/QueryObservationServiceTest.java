package com.ming.rag.unit.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.ming.rag.application.query.QueryObservationService;
import com.ming.rag.domain.query.ToolExecutionResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryObservationServiceTest {

    @Test
    void shouldRecordAdvisorAndMcpMetrics() {
        var registry = new SimpleMeterRegistry();
        var service = new QueryObservationService(registry);

        service.onAdvisorDecision("must", 1);
        service.onMcpInvocation(List.of(
                ToolExecutionResult.success("1", "weather-mcp", "get_weather", Map.of(), "summary", 15),
                ToolExecutionResult.failure("2", "weather-mcp", "search_web", 20, "TIMEOUT", "timeout")
        ));

        assertThat(registry.find("rag.mcp_advisor_decision_total").counter()).isNotNull();
        assertThat(registry.find("rag.mcp_latency_ms").timers()).isNotEmpty();
        assertThat(registry.find("rag.mcp_failure_total").counter()).isNotNull();
    }
}
