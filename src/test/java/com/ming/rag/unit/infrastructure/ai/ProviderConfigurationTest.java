package com.ming.rag.unit.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.infrastructure.ai.ChatModelProvider;
import com.ming.rag.infrastructure.ai.EmbeddingModelProvider;
import com.ming.rag.infrastructure.ai.RerankerProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProviderConfigurationTest {

    @Test
    void shouldTreatNoneProvidersAsDisabled() {
        var properties = properties("none", "none", false, "none");

        var chatProvider = new ChatModelProvider(properties, provider(null));
        var embeddingProvider = new EmbeddingModelProvider(properties, provider(null));
        var rerankerProvider = new RerankerProvider(properties, chatProvider);

        assertThat(chatProvider.isEnabled()).isFalse();
        assertThat(embeddingProvider.isEnabled()).isFalse();
        assertThat(rerankerProvider.isEnabled()).isFalse();
        assertThat(chatProvider.isAvailable()).isTrue();
        assertThat(embeddingProvider.isAvailable()).isTrue();
    }

    @Test
    void shouldRequireBeansWhenProvidersEnabled() {
        var properties = properties("openai", "openai", true, "llm");

        var chatProvider = new ChatModelProvider(properties, provider(null));
        var embeddingProvider = new EmbeddingModelProvider(properties, provider(null));

        assertThat(chatProvider.isEnabled()).isTrue();
        assertThat(embeddingProvider.isEnabled()).isTrue();
        assertThat(chatProvider.isAvailable()).isFalse();
        assertThat(embeddingProvider.isAvailable()).isFalse();
        assertThatThrownBy(chatProvider::require).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(embeddingProvider::require).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldExposeConfiguredBeansWhenAvailable() {
        var properties = properties("openai", "openai", true, "llm");
        ChatModel chatModel = mock(ChatModel.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        var chatProvider = new ChatModelProvider(properties, provider(chatModel));
        var embeddingProvider = new EmbeddingModelProvider(properties, provider(embeddingModel));
        var rerankerProvider = new RerankerProvider(properties, chatProvider);

        assertThat(chatProvider.isAvailable()).isTrue();
        assertThat(embeddingProvider.isAvailable()).isTrue();
        assertThat(rerankerProvider.isAvailable()).isTrue();
        assertThat(chatProvider.require()).isSameAs(chatModel);
        assertThat(embeddingProvider.require()).isSameAs(embeddingModel);
    }

    @Test
    void shouldDefaultLegacySearchConstructorToRequiredWithoutFallback() {
        var search = new RagProperties.Search("http://localhost:9200", "chunk_record_v1", true, true, false);

        assertThat(search.required()).isTrue();
        assertThat(search.devFallbackEnabled()).isFalse();
    }

    private RagProperties properties(String chatProvider, String embeddingProvider, boolean rerankEnabled, String rerankProvider) {
        return new RagProperties(
                new RagProperties.Ingestion(1000, 200, 100, 5),
                new RagProperties.Query(20, 20, 10, 60),
                new RagProperties.Rerank(rerankEnabled, rerankProvider, 5),
                new RagProperties.Ai(
                        new RagProperties.Model(chatProvider, "key", "chat-model", null),
                        new RagProperties.Model(embeddingProvider, "key", "embedding-model", null)
                ),
                new RagProperties.Storage(
                        new RagProperties.Metadata("jdbc:postgresql://localhost:5432/rag", "rag", "rag", true),
                        new RagProperties.Search("http://localhost:9200", "chunk_record_v1", false, true, false),
                        new RagProperties.File("./data/files")
                ),
                new RagProperties.Observability(true, true),
                new RagProperties.Evaluation(10, "default-evaluator")
        );
    }

    private <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }
}
