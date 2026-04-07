package com.ming.rag.domain.ingestion.port;

import java.util.List;
import java.util.Map;

public interface LexicalEncodingPort {

    List<Map<String, Integer>> encode(List<String> texts);
}
