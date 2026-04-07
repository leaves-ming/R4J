package com.ming.rag.domain.ingestion.port;

import java.util.List;

public interface EmbeddingPort {

    List<float[]> embed(List<String> texts);
}
