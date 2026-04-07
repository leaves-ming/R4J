package com.ming.rag.application.query;

import com.ming.rag.domain.common.exception.InvalidArgumentException;
import com.ming.rag.domain.query.ProcessedQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class QueryProcessorService {

    private static final Pattern FILTER_PATTERN = Pattern.compile("(?i)(collection|col|c|type|doc_type|t|source|src|s|tag|tags):([^\\s]+)");
    private static final Set<String> STOPWORDS = Set.of("the", "a", "an", "is", "are", "what", "how", "of", "and");

    public ProcessedQuery process(String rawQuery, String collectionId, Map<String, Object> structuredFilters) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new InvalidArgumentException("query must not be blank");
        }

        var normalizedWhitespace = rawQuery.trim().replaceAll("\\s+", " ");
        var extractedFilters = new LinkedHashMap<String, Object>();
        var strippedQuery = extractFilters(normalizedWhitespace, extractedFilters).trim().replaceAll("\\s+", " ");

        if (collectionId != null && !collectionId.isBlank() && extractedFilters.containsKey("collection")) {
            var extractedCollection = String.valueOf(extractedFilters.get("collection"));
            if (!collectionId.equals(extractedCollection)) {
                throw new InvalidArgumentException("collection filter conflicts with command scope");
            }
        }

        var mergedFilters = mergeFilters(extractedFilters, structuredFilters);
        var keywords = tokenize(strippedQuery);
        if (strippedQuery.isBlank() || keywords.isEmpty()) {
            throw new InvalidArgumentException("query must contain natural language content");
        }

        return new ProcessedQuery(rawQuery, strippedQuery, keywords, mergedFilters);
    }

    private String extractFilters(String query, Map<String, Object> extractedFilters) {
        Matcher matcher = FILTER_PATTERN.matcher(query);
        StringBuilder builder = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            builder.append(query, lastEnd, matcher.start());
            lastEnd = matcher.end();
            var key = normalizeFilterKey(matcher.group(1));
            var value = matcher.group(2).trim();
            if ("tags".equals(key)) {
                extractedFilters.put(key, splitTags(value));
            } else {
                extractedFilters.put(key, value);
            }
        }
        builder.append(query.substring(lastEnd));
        return builder.toString();
    }

    private Map<String, Object> mergeFilters(Map<String, Object> extractedFilters, Map<String, Object> structuredFilters) {
        var merged = new LinkedHashMap<>(extractedFilters);
        if (structuredFilters == null || structuredFilters.isEmpty()) {
            return Map.copyOf(merged);
        }

        for (var entry : structuredFilters.entrySet()) {
            if ("tags".equals(entry.getKey()) && merged.containsKey("tags")) {
                var tags = new LinkedHashSet<String>();
                tags.addAll(castTags(merged.get("tags")));
                tags.addAll(castTags(entry.getValue()));
                merged.put("tags", List.copyOf(tags));
                continue;
            }
            merged.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(merged);
    }

    private List<String> tokenize(String query) {
        var deduplicated = new LinkedHashSet<String>();
        for (String token : query.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}_]+")) {
            if (token.isBlank() || STOPWORDS.contains(token)) {
                continue;
            }
            deduplicated.add(token);
        }
        return List.copyOf(deduplicated);
    }

    private String normalizeFilterKey(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "collection", "col", "c" -> "collection";
            case "type", "doc_type", "t" -> "doc_type";
            case "source", "src", "s" -> "source_path";
            case "tag", "tags" -> "tags";
            default -> key.toLowerCase(Locale.ROOT);
        };
    }

    private List<String> splitTags(String value) {
        List<String> tags = new ArrayList<>();
        for (String token : value.split(",")) {
            var trimmed = token.trim();
            if (!trimmed.isBlank()) {
                tags.add(trimmed);
            }
        }
        return List.copyOf(tags);
    }

    @SuppressWarnings("unchecked")
    private List<String> castTags(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }
}
