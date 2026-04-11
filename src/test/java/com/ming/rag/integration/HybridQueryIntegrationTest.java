package com.ming.rag.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ming.rag.application.ingestion.IngestionApplicationService;
import com.ming.rag.application.ingestion.IngestionCommand;
import com.ming.rag.application.query.QueryApplicationService;
import com.ming.rag.application.query.QueryCommand;
import com.ming.rag.bootstrap.RagApplication;
import com.ming.rag.integration.support.IntegrationTestContainers;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = RagApplication.class)
@TestPropertySource(properties = {
        "rag.storage.file.base-path=target/test-hybrid-files",
        "rag.storage.search.initialize-index-on-startup=true",
        "rag.storage.search.dev-fallback-enabled=false",
        "rag.ai.chat.provider=none",
        "rag.mcp.enabled=true",
        "rag.mcp.servers[0].server-id=weather-mcp",
        "rag.mcp.servers[0].enabled=true",
        "rag.mcp.servers[0].transport=http",
        "rag.mcp.servers[0].endpoint=mock://weather",
        "rag.mcp.servers[0].timeout=3s",
        "rag.mcp.servers[0].allowed-tools[0]=get_weather",
        "rag.mcp.servers[0].tool-priority=10",
        "rag.advisor.enabled=true",
        "rag.advisor.fallback-enabled=true",
        "rag.advisor.never-rules[0].id=policy-only",
        "rag.advisor.never-rules[0].keywords[0]=policy",
        "rag.advisor.must-rules[0].id=live-weather",
        "rag.advisor.must-rules[0].keywords[0]=weather",
        "rag.advisor.realtime-patterns[0]=weather",
        "rag.advisor.realtime-patterns[1]=latest"
})
class HybridQueryIntegrationTest extends IntegrationTestContainers {

    @Autowired
    private IngestionApplicationService ingestionApplicationService;

    @Autowired
    private QueryApplicationService queryApplicationService;

    @Test
    void shouldReturnToolSourcesForHybridQuery() {
        ingest("# Travel Policy\napproved travel is allowed when budget and policy requirements are satisfied");

        var response = queryApplicationService.query(new QueryCommand(
                "latest weather and travel policy guidance",
                "default",
                Map.of(),
                10,
                10,
                10,
                5,
                true
        ));

        assertThat(response.toolSources()).isNotEmpty();
        assertThat(response.debug()).containsEntry("mcpInvoked", true);
        assertThat(response.citations()).isNotEmpty();
    }

    @Test
    void shouldFallbackWhenMockToolTimesOut() {
        ingest("# Travel Policy\napproved travel is allowed when budget and policy requirements are satisfied");

        var response = queryApplicationService.query(new QueryCommand(
                "weather timeout travel policy guidance",
                "default",
                Map.of(),
                10,
                10,
                10,
                5,
                true
        ));

        assertThat(response.debug()).containsEntry("mcpFallback", true);
        assertThat(response.citations()).isNotEmpty();
    }

    private void ingest(String content) {
        ingestionApplicationService.ingest(new IngestionCommand(
                "default",
                "hybrid.md",
                "text/markdown",
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                false,
                128,
                0
        ));
    }
}
