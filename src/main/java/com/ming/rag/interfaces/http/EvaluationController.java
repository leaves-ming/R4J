package com.ming.rag.interfaces.http;

import com.ming.rag.application.evaluation.EvaluationApplicationService;
import com.ming.rag.application.evaluation.EvaluationCommand;
import com.ming.rag.domain.evaluation.EvalReport;
import com.ming.rag.interfaces.http.dto.EvaluationRequest;
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
    public EvalReport evaluate(@RequestBody EvaluationRequest request) {
        return evaluationApplicationService.evaluate(new EvaluationCommand(
                request.testSetPath(),
                request.collectionId(),
                request.topK()
        ));
    }
}
