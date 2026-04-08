package com.ming.rag.infrastructure.ai;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.common.exception.ProviderFailureException;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ChatModelProvider {

    private final RagProperties ragProperties;
    private final ChatModel chatModel;

    public ChatModelProvider(RagProperties ragProperties, ObjectProvider<ChatModel> chatModelProvider) {
        this.ragProperties = ragProperties;
        this.chatModel = chatModelProvider.getIfAvailable();
    }

    public boolean isEnabled() {
        return !"none".equalsIgnoreCase(ragProperties.ai().chat().provider());
    }

    public boolean isAvailable() {
        return !isEnabled() || chatModel != null;
    }

    public ChatModel require() {
        if (chatModel == null) {
            throw new ProviderFailureException("Chat model provider is enabled but no ChatModel bean is available");
        }
        return chatModel;
    }

    public String providerName() {
        return ragProperties.ai().chat().provider();
    }
}
