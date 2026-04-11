package com.ming.rag.domain.query;

import java.util.Map;

public record ToolExecutionRequest(
        String toolCallId,
        String serverId,
        String toolName,
        String queryText,
        Map<String, Object> arguments,
        long timeoutMs
) {
}
