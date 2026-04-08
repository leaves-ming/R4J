package com.ming.rag.infrastructure.ai;

import com.ming.rag.bootstrap.config.RagProperties;
import org.springframework.stereotype.Component;

@Component
public class RerankerProvider {

    private final RagProperties ragProperties;
    private final ChatModelProvider chatModelProvider;

    public RerankerProvider(RagProperties ragProperties, ChatModelProvider chatModelProvider) {
        this.ragProperties = ragProperties;
        this.chatModelProvider = chatModelProvider;
    }

    public boolean isEnabled() {
        return ragProperties.rerank().enabled() && !"none".equalsIgnoreCase(ragProperties.rerank().provider());
    }

    public boolean isAvailable() {
        if (!isEnabled()) {
            return true;
        }
        if ("llm".equalsIgnoreCase(ragProperties.rerank().provider())) {
            return chatModelProvider.isAvailable();
        }
        return true;
    }

    public String providerName() {
        return ragProperties.rerank().provider();
    }
}
