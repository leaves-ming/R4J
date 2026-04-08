package com.ming.rag.interfaces.http;

import com.ming.rag.application.evaluation.EvaluationApplicationService;
import com.ming.rag.application.evaluation.EvaluationCommand;
import com.ming.rag.domain.evaluation.EvalReport;
import com.ming.rag.interfaces.http.dto.EvaluationDtos.EvaluationQueryResult;
import com.ming.rag.interfaces.http.dto.EvaluationDtos.EvaluationRequest;
import com.ming.rag.interfaces.http.dto.EvaluationDtos.EvaluationResponse;
import java.util.stream.IntStream;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

    private final EvaluationApplicationService evaluationApplicationService;

    public EvaluationController(EvaluationApplicationService evaluationApplicationService) {
        this.evaluationApplicationService = evaluationApplicationService;
    }

    @PostMapping
    public EvaluationResponse evaluate(@RequestBody EvaluationRequest request) {
        var report = evaluationApplicationService.evaluate(new EvaluationCommand(
                request.testSetPath(),
                request.collectionId(),
                request.topK()
        ));
        return new EvaluationResponse(
                report.runId(),
                report.evaluatorName(),
                report.testSetPath(),
                report.totalElapsedMs(),
                report.aggregateMetrics(),
                report.queryResults().stream()
                        .map(item -> new EvaluationQueryResult(
                                item.query(),
                                item.retrievedTopKChunkIds(),
                                item.generatedAnswer(),
                                item.metrics(),
                                item.elapsedMs()
                        ))
                        .toList()
        );
    }
}
