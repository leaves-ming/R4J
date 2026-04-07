package com.ming.rag.interfaces.http;

import com.ming.rag.application.query.QueryApplicationService;
import com.ming.rag.application.query.QueryCommand;
import com.ming.rag.domain.response.AnswerResponse;
import com.ming.rag.interfaces.http.dto.QueryRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queries")
public class QueryController {

    private final QueryApplicationService queryApplicationService;

    public QueryController(QueryApplicationService queryApplicationService) {
        this.queryApplicationService = queryApplicationService;
    }

    @PostMapping
    public AnswerResponse query(@RequestBody QueryRequest request) {
        return queryApplicationService.query(new QueryCommand(
                request.query(),
                request.collectionId(),
                request.options(),
                request.denseTopK(),
                request.sparseTopK(),
                request.fusionTopK(),
                request.rerankTopK(),
                Boolean.TRUE.equals(request.debug())
        ));
    }
}
