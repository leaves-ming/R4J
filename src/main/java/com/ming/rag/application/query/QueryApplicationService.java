package com.ming.rag.application.query;

import com.ming.rag.domain.query.RetrievalResult;
import com.ming.rag.domain.response.AnswerResponse;
import com.ming.rag.domain.response.port.AnswerGeneratorPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class QueryApplicationService {

    private final RetrievalPipelineService retrievalPipelineService;
    private final AnswerGeneratorPort answerGeneratorPort;
    private final QueryObservationService queryObservationService;
    private final MeterRegistry meterRegistry;

    public QueryApplicationService(
            RetrievalPipelineService retrievalPipelineService,
            AnswerGeneratorPort answerGeneratorPort,
            QueryObservationService queryObservationService,
            MeterRegistry meterRegistry
    ) {
        this.retrievalPipelineService = retrievalPipelineService;
        this.answerGeneratorPort = answerGeneratorPort;
        this.queryObservationService = queryObservationService;
        this.meterRegistry = meterRegistry;
    }

    public AnswerResponse query(QueryCommand command) {
        return Timer.builder("rag.query.duration").register(meterRegistry).record(() -> doQuery(command));
    }

    private AnswerResponse doQuery(QueryCommand command) {
        RetrievalResult retrievalResult = retrievalPipelineService.retrieve(command);
        if (retrievalResult.topKResults().isEmpty()) {
            queryObservationService.onEmptyResponse(retrievalResult.traceId());
            return new AnswerResponse(true, "", java.util.List.of(), retrievalResult.traceId(), retrievalResult.debug());
        }
        var response = answerGeneratorPort.generate(command.query(), retrievalResult.topKResults(), retrievalResult.traceId(), command.debug());
        queryObservationService.onResponseBuilt(retrievalResult.traceId(), response.citations().size(), response.debug());
        return response;
    }
}
