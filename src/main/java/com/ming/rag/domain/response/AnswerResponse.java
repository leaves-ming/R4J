package com.ming.rag.domain.response;

import java.util.List;
import java.util.Map;

public record AnswerResponse(
        boolean empty,
        String answer,
        List<Citation> citations,
        String traceId,
        Map<String, Object> debug
) {
}
