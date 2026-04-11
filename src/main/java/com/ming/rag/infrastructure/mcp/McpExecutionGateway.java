package com.ming.rag.infrastructure.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.query.ToolExecutionRequest;
import com.ming.rag.domain.query.ToolExecutionResult;
import com.ming.rag.domain.query.port.McpExecutionPort;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class McpExecutionGateway implements McpExecutionPort {

    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public McpExecutionGateway(RagProperties ragProperties, ObjectMapper objectMapper) {
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public List<ToolExecutionResult> execute(List<ToolExecutionRequest> requests) {
        return requests.stream().map(this::executeSingle).toList();
    }

    private ToolExecutionResult executeSingle(ToolExecutionRequest request) {
        long start = System.nanoTime();
        try {
            var server = ragProperties.mcp().servers().stream()
                    .filter(item -> item.serverId().equals(request.serverId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("unknown server " + request.serverId()));
            if ("http".equalsIgnoreCase(server.transport())) {
                return executeHttp(server, request, start);
            }
            if ("stdio".equalsIgnoreCase(server.transport())) {
                return executeStdio(server, request, start);
            }
            return ToolExecutionResult.failure(
                    request.toolCallId(),
                    request.serverId(),
                    request.toolName(),
                    elapsedMs(start),
                    "UNSUPPORTED_TRANSPORT",
                    "unsupported transport " + server.transport()
            );
        } catch (Exception exception) {
            return ToolExecutionResult.failure(
                    request.toolCallId(),
                    request.serverId(),
                    request.toolName(),
                    elapsedMs(start),
                    "EXECUTION_ERROR",
                    exception.getMessage()
            );
        }
    }

    private ToolExecutionResult executeHttp(RagProperties.Server server, ToolExecutionRequest request, long start) throws Exception {
        if (server.endpoint().startsWith("mock://")) {
            return mockResponse(request, start);
        }
        var payload = objectMapper.writeValueAsString(Map.of(
                "toolName", request.toolName(),
                "query", request.queryText(),
                "arguments", request.arguments()
        ));
        var httpRequest = HttpRequest.newBuilder(URI.create(server.endpoint()))
                .timeout(server.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            return ToolExecutionResult.failure(
                    request.toolCallId(),
                    request.serverId(),
                    request.toolName(),
                    elapsedMs(start),
                    "HTTP_ERROR",
                    "status=" + response.statusCode()
            );
        }
        var summary = summarize(response.body());
        return ToolExecutionResult.success(request.toolCallId(), request.serverId(), request.toolName(), response.body(), summary, elapsedMs(start));
    }

    private ToolExecutionResult executeStdio(RagProperties.Server server, ToolExecutionRequest request, long start) throws Exception {
        if (server.command().size() == 1 && "mock-mcp".equalsIgnoreCase(server.command().getFirst())) {
            return mockResponse(request, start);
        }
        var command = new java.util.ArrayList<>(server.command());
        command.add(request.toolName());
        command.add(request.queryText());
        var process = new ProcessBuilder(command).start();
        boolean finished = process.waitFor(server.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return ToolExecutionResult.failure(
                    request.toolCallId(),
                    request.serverId(),
                    request.toolName(),
                    elapsedMs(start),
                    "TIMEOUT",
                    "stdio command timed out"
            );
        }
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            var output = reader.lines().reduce((left, right) -> left + "\n" + right).orElse("");
            if (process.exitValue() != 0) {
                return ToolExecutionResult.failure(
                        request.toolCallId(),
                        request.serverId(),
                        request.toolName(),
                        elapsedMs(start),
                        "PROCESS_ERROR",
                        output.isBlank() ? "non-zero exit" : output
                );
            }
            return ToolExecutionResult.success(request.toolCallId(), request.serverId(), request.toolName(), output, summarize(output), elapsedMs(start));
        }
    }

    private ToolExecutionResult mockResponse(ToolExecutionRequest request, long start) {
        var query = request.queryText().toLowerCase(Locale.ROOT);
        if (query.contains("timeout")) {
            return ToolExecutionResult.failure(
                    request.toolCallId(),
                    request.serverId(),
                    request.toolName(),
                    elapsedMs(start),
                    "TIMEOUT",
                    "mock timeout"
            );
        }
        if (query.contains("天气")) {
            return ToolExecutionResult.success(
                    request.toolCallId(),
                    request.serverId(),
                    request.toolName(),
                    Map.of("condition", "rain", "temperature", "22C"),
                    "上海今日小雨，最高温 22C，晚高峰可能受降雨影响。",
                    elapsedMs(start)
            );
        }
        if (query.contains("latest") || query.contains("最新") || query.contains("实时")) {
            return ToolExecutionResult.success(
                    request.toolCallId(),
                    request.serverId(),
                    request.toolName(),
                    Map.of("result", "latest info"),
                    "外部工具返回了最新信息摘要，可作为回答补充。",
                    elapsedMs(start)
            );
        }
        return ToolExecutionResult.success(
                request.toolCallId(),
                request.serverId(),
                request.toolName(),
                Map.of("query", request.queryText()),
                "外部工具返回了与问题相关的补充信息。",
                elapsedMs(start)
        );
    }

    private String summarize(String payload) {
        if (payload == null) {
            return "";
        }
        String normalized = payload.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }

    private long elapsedMs(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }
}
