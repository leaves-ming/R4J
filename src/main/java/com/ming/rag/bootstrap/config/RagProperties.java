package com.ming.rag.bootstrap.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        @NotNull @Valid Ingestion ingestion,
        @NotNull @Valid Query query,
        @NotNull @Valid Rerank rerank,
        @NotNull @Valid Ai ai,
        @NotNull @Valid Storage storage,
        @NotNull @Valid Observability observability,
        @NotNull @Valid Evaluation evaluation
) {

    public record Ingestion(
            @Min(1) int chunkSize,
            @Min(0) int chunkOverlap,
            @Min(1) int batchSize,
            @Min(1) int maxLlmWorkers
    ) {
    }

    public record Query(
            @Min(1) int denseTopK,
            @Min(1) int sparseTopK,
            @Min(1) int fusionTopK,
            @Min(1) int rrfK
    ) {
    }

    public record Rerank(
            boolean enabled,
            @NotBlank String provider,
            @Min(1) int topK
    ) {
        @AssertTrue(message = "rerank.provider must not be none when rerank is enabled")
        public boolean hasValidProviderWhenEnabled() {
            return !enabled || !isDisabled(provider);
        }
    }

    public record Ai(
            @NotNull @Valid Model chat,
            @NotNull @Valid Model embedding
    ) {
    }

    public record Model(
            @NotBlank String provider,
            String apiKey,
            String modelName,
            String baseUrl
    ) {
        @AssertTrue(message = "enabled AI providers must define apiKey and modelName")
        public boolean hasCredentialsWhenEnabled() {
            return isDisabled(provider) || (hasText(apiKey) && hasText(modelName));
        }
    }

    public record Storage(
            @NotNull @Valid Metadata metadata,
            @NotNull @Valid Search search,
            @NotNull @Valid File file
    ) {
    }

    public record Metadata(
            String url,
            String username,
            String password,
            boolean required
    ) {
        @AssertTrue(message = "rag.storage.metadata requires url, username and password when enabled")
        public boolean hasRequiredConnectionProperties() {
            return !required || (hasText(url) && hasText(username) && hasText(password));
        }
    }

    public record Search(
            String url,
            String chunkIndex,
            boolean initializeIndexOnStartup,
            boolean required,
            boolean devFallbackEnabled
    ) {
        @AssertTrue(message = "rag.storage.search requires url and chunkIndex when enabled")
        public boolean hasRequiredConnectionProperties() {
            return !required || (hasText(url) && hasText(chunkIndex));
        }
    }

    public record File(
            @NotBlank String basePath
    ) {
    }

    public record Observability(
            boolean traceEnabled,
            boolean structuredLogging
    ) {
    }

    public record Evaluation(
            @Min(1) int defaultTopK,
            @NotBlank String evaluatorName
    ) {
    }

    private static boolean isDisabled(String provider) {
        return !hasText(provider) || "none".equalsIgnoreCase(provider);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
