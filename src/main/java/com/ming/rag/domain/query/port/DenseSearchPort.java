package com.ming.rag.domain.query.port;

import com.ming.rag.domain.query.ProcessedQuery;
import com.ming.rag.domain.query.RetrievalCandidate;
import java.util.List;

public interface DenseSearchPort {

    List<RetrievalCandidate> search(String collectionId, ProcessedQuery query, int topK);
}
