package com.ming.rag.domain.response.port;

import com.ming.rag.domain.query.RankedResult;
import com.ming.rag.domain.response.AnswerResponse;
import java.util.List;

public interface AnswerGeneratorPort {

    AnswerResponse generate(String query, List<RankedResult> rankedResults, String traceId, boolean debug);
}
