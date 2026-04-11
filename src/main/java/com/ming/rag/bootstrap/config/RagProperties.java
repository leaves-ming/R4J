package com.ming.rag.bootstrap.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
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
        @NotNull @Valid Evaluation evaluation,
        @NotNull @Valid Mcp mcp,
        @NotNull @Valid Advisor advisor
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

        @AssertTrue(message = "dev fallback may only be enabled when search is not required")
        public boolean hasConsistentFallbackContract() {
            return !devFallbackEnabled || !required;
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

    public record Mcp(
            boolean enabled,
            @NotNull List<@Valid Server> servers
    ) {
        public static Mcp disabled() {
            return new Mcp(false, List.of());
        }
    }

    public record Server(
            @NotBlank String serverId,
            boolean enabled,
            @NotBlank String transport,
            String endpoint,
            List<String> command,
            @NotNull Duration timeout,
            @NotEmpty List<@NotBlank String> allowedTools,
            int toolPriority
    ) {
        @AssertTrue(message = "MCP server must define endpoint for http transport or command for stdio transport")
        public boolean hasConnectionTarget() {
            if (!enabled) {
                return true;
            }
            if ("http".equalsIgnoreCase(transport)) {
                return hasText(endpoint);
            }
            if ("stdio".equalsIgnoreCase(transport)) {
                return command != null && !command.isEmpty() && command.stream().allMatch(RagProperties::hasText);
            }
            return hasText(endpoint) || (command != null && !command.isEmpty());
        }
    }

    public record Advisor(
            boolean enabled,
            boolean fallbackEnabled,
            @NotNull List<@Valid Rule> neverRules,
            @NotNull List<@Valid Rule> preferRules,
            @NotNull List<@Valid Rule> mustRules,
            @NotNull List<@NotBlank String> realtimePatterns
    ) {
        public static Advisor disabled() {
            return new Advisor(false, true, List.of(), List.of(), List.of(), List.of());
        }
    }

    public record Rule(
            @NotBlank String id,
            @NotEmpty List<@NotBlank String> keywords
    ) {
    }

    private static boolean isDisabled(String provider) {
        return !hasText(provider) || "none".equalsIgnoreCase(provider);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
