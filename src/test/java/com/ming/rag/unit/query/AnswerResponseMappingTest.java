package com.ming.rag.unit.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.ming.rag.application.query.CitationFactoryService;
import com.ming.rag.domain.query.RankedResult;
import com.ming.rag.domain.query.ToolContext;
import com.ming.rag.domain.query.ToolExecutionResult;
import com.ming.rag.infrastructure.ai.AnswerGeneratorAdapter;
import com.ming.rag.infrastructure.ai.ChatModelProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AnswerResponseMappingTest {

    @Test
    void shouldPreserveToolSourcesFromToolContext() {
        var chatProvider = Mockito.mock(ChatModelProvider.class);
        Mockito.when(chatProvider.isEnabled()).thenReturn(false);
        Mockito.when(chatProvider.isAvailable()).thenReturn(false);
        Mockito.when(chatProvider.providerName()).thenReturn("none");
        var adapter = new AnswerGeneratorAdapter(new CitationFactoryService(), chatProvider);

        var response = adapter.generate(
                "今天上海天气如何",
                List.of(new RankedResult("chunk-1", 0.9, 1, "policy content", Map.of("document_id", "doc-1", "source_path", "policy.md"))),
                new ToolContext(List.of(ToolExecutionResult.success("1", "weather-mcp", "get_weather", Map.of(), "上海今日小雨。", 12)), true, false, null),
                "trace-1",
                true
        );

        assertThat(response.toolSources()).hasSize(1);
        assertThat(response.toolSources().getFirst().toolName()).isEqualTo("get_weather");
    }
}
