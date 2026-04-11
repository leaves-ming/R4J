package com.ming.rag.domain.query;

import java.util.Map;

public record RegisteredTool(
        String serverId,
        String toolName,
        String description,
        Map<String, Object> inputSchema,
        int priority,
        boolean enabled
) {
}
