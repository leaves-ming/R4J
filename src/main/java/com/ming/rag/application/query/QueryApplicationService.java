package com.ming.rag.application.query;

import com.ming.rag.domain.query.AdvisorDecision;
import com.ming.rag.domain.query.RetrievalResult;
import com.ming.rag.domain.query.ToolContext;
import com.ming.rag.domain.response.AnswerResponse;
import com.ming.rag.domain.response.port.AnswerGeneratorPort;
import com.ming.rag.domain.query.port.McpExecutionPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QueryApplicationService {

    private final RetrievalPipelineService retrievalPipelineService;
    private final AnswerGeneratorPort answerGeneratorPort;
    private final QueryObservationService queryObservationService;
    private final AdvisorService advisorService;
    private final ToolContextAssembler toolContextAssembler;
    private final McpExecutionPort mcpExecutionPort;
    private final MeterRegistry meterRegistry;

    public QueryApplicationService(
            RetrievalPipelineService retrievalPipelineService,
            AnswerGeneratorPort answerGeneratorPort,
            QueryObservationService queryObservationService,
            AdvisorService advisorService,
            ToolContextAssembler toolContextAssembler,
            McpExecutionPort mcpExecutionPort,
            MeterRegistry meterRegistry
    ) {
        this.retrievalPipelineService = retrievalPipelineService;
        this.answerGeneratorPort = answerGeneratorPort;
        this.queryObservationService = queryObservationService;
        this.advisorService = advisorService;
        this.toolContextAssembler = toolContextAssembler;
        this.mcpExecutionPort = mcpExecutionPort;
        this.meterRegistry = meterRegistry;
    }

    public AnswerResponse query(QueryCommand command) {
        return Timer.builder("rag.query.duration").register(meterRegistry).record(() -> doQuery(command));
    }

    private AnswerResponse doQuery(QueryCommand command) {
        AdvisorDecision advisorDecision = advisorService.decide(command);
        queryObservationService.onAdvisorDecision(advisorDecision.route(), advisorDecision.candidateTools().size());

        ToolContext toolContext = ToolContext.empty();
        RetrievalResult retrievalResult = null;

        if (advisorDecision.isMust()) {
            toolContext = invokeTools(command, advisorDecision, false);
            if (!advisorDecision.fallbackAllowed() && toolContext.successfulResults().isEmpty()) {
                var debug = mergeDebug(toolContextAssembler.toDebug(advisorDecision, toolContext), java.util.Map.of());
                queryObservationService.onEmptyResponse("mcp-only");
                return new AnswerResponse(true, "", List.of(), List.of(), "mcp-only", debug);
            }
            retrievalResult = retrievalPipelineService.retrieve(command);
        } else {
            retrievalResult = retrievalPipelineService.retrieve(command);
            if (advisorDecision.isPrefer()) {
                toolContext = invokeTools(command, advisorDecision, retrievalResult.topKResults().isEmpty());
            }
        }

        if (retrievalResult == null) {
            retrievalResult = retrievalPipelineService.retrieve(command);
        }

        if (retrievalResult.topKResults().isEmpty() && toolContext.successfulResults().isEmpty()) {
            var mergedDebug = mergeDebug(retrievalResult.debug(), toolContextAssembler.toDebug(advisorDecision, toolContext));
            queryObservationService.onEmptyResponse(retrievalResult.traceId());
            return new AnswerResponse(true, "", List.of(), List.of(), retrievalResult.traceId(), mergedDebug);
        }

        var response = answerGeneratorPort.generate(
                command.query(),
                retrievalResult.topKResults(),
                toolContext,
                retrievalResult.traceId(),
                command.debug()
        );
        var mergedDebug = mergeDebug(retrievalResult.debug(), toolContextAssembler.toDebug(advisorDecision, toolContext), response.debug());
        queryObservationService.onResponseBuilt(retrievalResult.traceId(), response.citations().size(), mergedDebug);
        return new AnswerResponse(response.empty(), response.answer(), response.citations(), response.toolSources(), response.traceId(), mergedDebug);
    }

    private ToolContext invokeTools(QueryCommand command, AdvisorDecision advisorDecision, boolean fallbackTriggered) {
        var requests = toolContextAssembler.buildRequests(command.query(), advisorDecision);
        if (requests.isEmpty()) {
            return ToolContext.empty();
        }
        var results = mcpExecutionPort.execute(requests);
        queryObservationService.onMcpInvocation(results);
        return toolContextAssembler.buildContext(true, results, fallbackTriggered || !results.stream().allMatch(result -> result.success()), "mcp_failure");
    }

    private java.util.Map<String, Object> mergeDebug(java.util.Map<String, Object>... debugMaps) {
        var merged = new LinkedHashMap<String, Object>();
        for (var debugMap : debugMaps) {
            if (debugMap != null) {
                merged.putAll(debugMap);
            }
        }
        return java.util.Map.copyOf(merged);
    }
}
