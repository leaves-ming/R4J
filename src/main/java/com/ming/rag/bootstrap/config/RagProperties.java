package com.ming.rag.bootstrap.config;

import jakarta.validation.Valid;
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
    }

    public record Storage(
            @NotNull @Valid Metadata metadata,
            @NotNull @Valid Search search,
            @NotNull @Valid File file
    ) {
    }

    public record Metadata(
            @NotBlank String url,
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record Search(
            @NotBlank String url,
            @NotBlank String chunkIndex,
            boolean initializeIndexOnStartup
    ) {
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
}
