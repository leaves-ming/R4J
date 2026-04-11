package com.ming.rag.domain.query;

import java.util.List;

public record AdvisorDecision(
        String route,
        List<String> candidateTools,
        boolean fallbackAllowed,
        List<String> matchedRules,
        String reason
) {

    public static AdvisorDecision never(String reason) {
        return new AdvisorDecision("never", List.of(), true, List.of(), reason);
    }

    public boolean isNever() {
        return "never".equalsIgnoreCase(route);
    }

    public boolean isPrefer() {
        return "prefer".equalsIgnoreCase(route);
    }

    public boolean isMust() {
        return "must".equalsIgnoreCase(route);
    }
}
