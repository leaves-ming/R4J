package com.ming.rag.application.query;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.query.AdvisorDecision;
import com.ming.rag.domain.query.port.ToolRegistryPort;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AdvisorService {

    private final RagProperties ragProperties;
    private final ToolRegistryPort toolRegistryPort;

    public AdvisorService(RagProperties ragProperties, ToolRegistryPort toolRegistryPort) {
        this.ragProperties = ragProperties;
        this.toolRegistryPort = toolRegistryPort;
    }

    public AdvisorDecision decide(QueryCommand command) {
        if (!ragProperties.advisor().enabled() || !ragProperties.mcp().enabled()) {
            return AdvisorDecision.never("advisor disabled");
        }

        var query = command.query() == null ? "" : command.query().toLowerCase(Locale.ROOT);
        var matchedNever = matchedRuleIds(query, ragProperties.advisor().neverRules());
        var matchedPrefer = matchedRuleIds(query, ragProperties.advisor().preferRules());
        var matchedMust = matchedRuleIds(query, ragProperties.advisor().mustRules());

        String route;
        List<String> matchedRules;
        if (!matchedMust.isEmpty()) {
            route = "must";
            matchedRules = matchedMust;
        } else if (!matchedPrefer.isEmpty()) {
            route = "prefer";
            matchedRules = matchedPrefer;
        } else if (!matchedNever.isEmpty()) {
            route = "never";
            matchedRules = matchedNever;
        } else if (matchesRealtimePattern(query)) {
            route = "must";
            matchedRules = List.of("realtime-pattern");
        } else {
            route = "never";
            matchedRules = List.of("default-local");
        }

        if ("never".equals(route)) {
            return new AdvisorDecision(route, List.of(), true, matchedRules, "local knowledge only");
        }

        var candidateTools = toolRegistryPort.listRegisteredTools().stream()
                .map(tool -> tool.toolName())
                .toList();
        return new AdvisorDecision(route, candidateTools, ragProperties.advisor().fallbackEnabled(), matchedRules, "rule-matched");
    }

    private List<String> matchedRuleIds(String query, List<RagProperties.Rule> rules) {
        var matched = new ArrayList<String>();
        for (var rule : rules) {
            boolean allPresent = rule.keywords().stream()
                    .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                    .allMatch(query::contains);
            if (allPresent) {
                matched.add(rule.id());
            }
        }
        return List.copyOf(matched);
    }

    private boolean matchesRealtimePattern(String query) {
        return ragProperties.advisor().realtimePatterns().stream()
                .map(pattern -> pattern.toLowerCase(Locale.ROOT))
                .anyMatch(query::contains);
    }
}
