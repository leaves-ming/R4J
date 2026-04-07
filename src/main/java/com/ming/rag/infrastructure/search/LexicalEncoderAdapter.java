package com.ming.rag.infrastructure.search;

import com.ming.rag.domain.ingestion.port.LexicalEncodingPort;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LexicalEncoderAdapter implements LexicalEncodingPort {

    @Override
    public List<Map<String, Integer>> encode(List<String> texts) {
        return texts.stream().map(this::encodeSingle).toList();
    }

    private Map<String, Integer> encodeSingle(String text) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}_]+"))
                .filter(token -> !token.isBlank())
                .forEach(token -> result.merge(token, 1, Integer::sum));
        return Map.copyOf(result);
    }
}
