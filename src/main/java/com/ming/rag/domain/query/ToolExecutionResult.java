package com.ming.rag.domain.query;

public record ToolExecutionResult(
        String toolCallId,
        String serverId,
        String toolName,
        boolean success,
        Object rawPayload,
        String summary,
        long latencyMs,
        String failureType,
        String failureMessage
) {

    public static ToolExecutionResult success(
            String toolCallId,
            String serverId,
            String toolName,
            Object rawPayload,
            String summary,
            long latencyMs
    ) {
        return new ToolExecutionResult(toolCallId, serverId, toolName, true, rawPayload, summary, latencyMs, null, null);
    }

    public static ToolExecutionResult failure(
            String toolCallId,
            String serverId,
            String toolName,
            long latencyMs,
            String failureType,
            String failureMessage
    ) {
        return new ToolExecutionResult(toolCallId, serverId, toolName, false, null, null, latencyMs, failureType, failureMessage);
    }
}
