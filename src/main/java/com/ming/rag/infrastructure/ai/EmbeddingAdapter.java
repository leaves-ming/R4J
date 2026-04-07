package com.ming.rag.infrastructure.ai;

import com.ming.rag.domain.ingestion.port.EmbeddingPort;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingAdapter implements EmbeddingPort {

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream()
                .map(text -> new float[]{text.length(), Math.max(1, text.split("\\s+").length), Math.min(10, text.length())})
                .toList();
    }
}
