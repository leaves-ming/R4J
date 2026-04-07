package com.ming.rag.unit.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ming.rag.application.query.QueryProcessorService;
import com.ming.rag.domain.common.exception.InvalidArgumentException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryProcessorTest {

    private final QueryProcessorService service = new QueryProcessorService();

    @Test
    void shouldExtractFiltersAndPreserveNaturalLanguageTokens() {
        var result = service.process(
                "  collection:default   tags:java,spring  What is hybrid retrieval?  ",
                "default",
                Map.of("doc_type", "pdf", "tags", List.of("rag"))
        );

        assertThat(result.normalizedQuery()).isEqualTo("What is hybrid retrieval?");
        assertThat(result.keywords()).containsExactly("hybrid", "retrieval");
        assertThat(result.filters())
                .containsEntry("collection", "default")
                .containsEntry("doc_type", "pdf");
        assertThat(result.filters().get("tags")).isEqualTo(List.of("java", "spring", "rag"));
    }

    @Test
    void shouldRejectConflictingCollectionScope() {
        assertThatThrownBy(() -> service.process("collection:other how does rerank work", "default", Map.of()))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("collection filter conflicts");
    }
}
