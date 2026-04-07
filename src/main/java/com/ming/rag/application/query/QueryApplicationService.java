package com.ming.rag.application.query;

import com.ming.rag.domain.query.RetrievalResult;
import com.ming.rag.domain.response.AnswerResponse;
import com.ming.rag.domain.response.port.AnswerGeneratorPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QueryApplicationService {

    private static final Logger log = LoggerFactory.getLogger(QueryApplicationService.class);

    private final RetrievalPipelineService retrievalPipelineService;
    private final AnswerGeneratorPort answerGeneratorPort;
    private final MeterRegistry meterRegistry;

    public QueryApplicationService(
            RetrievalPipelineService retrievalPipelineService,
            AnswerGeneratorPort answerGeneratorPort,
            MeterRegistry meterRegistry
    ) {
        this.retrievalPipelineService = retrievalPipelineService;
        this.answerGeneratorPort = answerGeneratorPort;
        this.meterRegistry = meterRegistry;
    }

    public AnswerResponse query(QueryCommand command) {
        return Timer.builder("rag.query.duration").register(meterRegistry).record(() -> doQuery(command));
    }

    private AnswerResponse doQuery(QueryCommand command) {
        RetrievalResult retrievalResult = retrievalPipelineService.retrieve(command);
        if (retrievalResult.topKResults().isEmpty()) {
            log.info("query completed with empty result traceId={} stage=response_build", retrievalResult.traceId());
            return new AnswerResponse(true, "", java.util.List.of(), retrievalResult.traceId(), retrievalResult.debug());
        }
        var response = answerGeneratorPort.generate(command.query(), retrievalResult.topKResults(), retrievalResult.traceId(), command.debug());
        log.info("query completed traceId={} stage=response_build citations={}", retrievalResult.traceId(), response.citations().size());
        return response;
    }
}
