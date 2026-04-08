package com.ming.rag.infrastructure.ai;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.common.exception.ProviderFailureException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingModelProvider {

    private final RagProperties ragProperties;
    private final EmbeddingModel embeddingModel;

    public EmbeddingModelProvider(RagProperties ragProperties, ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.ragProperties = ragProperties;
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
    }

    public boolean isEnabled() {
        return !"none".equalsIgnoreCase(ragProperties.ai().embedding().provider());
    }

    public boolean isAvailable() {
        return !isEnabled() || embeddingModel != null;
    }

    public EmbeddingModel require() {
        if (embeddingModel == null) {
            throw new ProviderFailureException("Embedding model provider is enabled but no EmbeddingModel bean is available");
        }
        return embeddingModel;
    }

    public String providerName() {
        return ragProperties.ai().embedding().provider();
    }
}
