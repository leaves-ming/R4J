package com.ming.rag.domain.response;

public record ToolSource(
        String serverId,
        String toolName,
        String summary
) {
}
