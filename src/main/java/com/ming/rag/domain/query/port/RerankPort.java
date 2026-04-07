package com.ming.rag.domain.query.port;

import com.ming.rag.domain.query.ProcessedQuery;
import com.ming.rag.domain.query.RankedResult;
import java.util.List;

public interface RerankPort {

    List<RankedResult> rerank(ProcessedQuery query, List<RankedResult> candidates, int topK);
}
