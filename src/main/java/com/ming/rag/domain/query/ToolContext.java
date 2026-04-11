package com.ming.rag.domain.query;

import java.util.List;

public record ToolContext(
        List<ToolExecutionResult> results,
        boolean invoked,
        boolean fallbackTriggered,
        String fallbackReason
) {

    public static ToolContext empty() {
        return new ToolContext(List.of(), false, false, null);
    }

    public List<ToolExecutionResult> successfulResults() {
        return results.stream().filter(ToolExecutionResult::success).toList();
    }

    public List<ToolExecutionResult> failedResults() {
        return results.stream().filter(result -> !result.success()).toList();
    }
}
