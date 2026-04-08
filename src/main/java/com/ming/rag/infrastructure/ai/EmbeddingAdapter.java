package com.ming.rag.infrastructure.ai;

import com.ming.rag.domain.ingestion.port.EmbeddingPort;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModelProvider embeddingModelProvider;

    public EmbeddingAdapter(EmbeddingModelProvider embeddingModelProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (embeddingModelProvider.isEnabled() && embeddingModelProvider.isAvailable()) {
            var response = embeddingModelProvider.require().embedAll(texts.stream().map(TextSegment::from).toList());
            return response.content().stream().map(embedding -> embedding.vector().clone()).toList();
        }
        return texts.stream()
                .map(text -> new float[]{text.length(), Math.max(1, text.split("\\s+").length), Math.min(10, text.length())})
                .toList();
    }
}
